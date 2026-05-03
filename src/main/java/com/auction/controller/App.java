package com.auction;

import com.auction.config.DatabaseConfig;
import com.auction.controller.AuctionController;
import com.auction.controller.AuthController;
import com.auction.controller.ItemController;
import com.auction.dao.AuctionDao;
import com.auction.dao.AutoBidConfigDao;
import com.auction.dao.BidTransactionDao;
import com.auction.dao.ItemDao;
import com.auction.dao.UserDao;
import com.auction.dto.ErrorResponse;
import com.auction.exception.AuctionClosedException;
import com.auction.exception.DuplicateException;
import com.auction.exception.InvalidBidException;
import com.auction.exception.NotFoundException;
import com.auction.exception.UnauthorizedException;
import com.auction.middleware.JwtMiddleware;
import com.auction.service.AuctionService;
import com.auction.service.ItemService;
import com.auction.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import java.util.Map;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Điểm khởi động chính của server Javalin — Online Auction System.
 *
 * <p>Lớp này chịu trách nhiệm:
 * <ol>
 *   <li>Khởi tạo kết nối cơ sở dữ liệu thông qua {@link DatabaseConfig}.</li>
 *   <li>Cấu hình Jackson ObjectMapper để serialize/deserialize kiểu thời gian Java 8
 *       ({@code LocalDateTime}, {@code BigDecimal}) sang JSON.</li>
 *   <li>Tạo Javalin instance và đăng ký JWT middleware cho tất cả route {@code /api/*}.</li>
 *   <li>Đăng ký 5 exception handler ánh xạ custom exception → HTTP status code + JSON body.</li>
 *   <li>Khởi tạo toàn bộ DAOs, Services và đăng ký Controllers.</li>
 *   <li>Khởi động server trên cổng 8080.</li>
 * </ol>
 *
 * <p>Luồng xử lý request điển hình:
 * <pre>
 *   HTTP Request
 *     → JWT Middleware (xác thực token, set userId/role vào context)
 *     → Controller (parse body, gọi service)
 *     → Service (business logic)
 *     → DAO (truy vấn database)
 *     → JSON Response
 * </pre>
 *
 * <p>Biến môi trường yêu cầu (đọc từ {@code .env}):
 * <ul>
 *   <li>{@code DB_URL} — JDBC URL của PostgreSQL.</li>
 *   <li>{@code DB_USER} — Tên người dùng database.</li>
 *   <li>{@code DB_PASSWORD} — Mật khẩu database.</li>
 *   <li>{@code JWT_SECRET} — Khóa bí mật ký JWT (mặc định: "auction-secret-key-dev").</li>
 * </ul>
 *
 * @see DatabaseConfig
 * @see JwtMiddleware
 * @see AuthController
 * @see ItemController
 * @see AuctionController
 */
public class App {

    /** Logger cho lớp App, ghi log thông tin khởi động server. */
    private static final Logger LOGGER = LoggerFactory.getLogger(App.class);

    /** Cổng mặc định server lắng nghe. */
    private static final int SERVER_PORT = 8080;

    /**
     * Phương thức khởi động ứng dụng server.
     *
     * <p>Thứ tự khởi tạo rất quan trọng:
     * <ol>
     *   <li>Database → DAOs → Services → Controllers.</li>
     *   <li>Middleware phải được đăng ký TRƯỚC các routes.</li>
     *   <li>Exception handlers phải được đăng ký TRƯỚC khi start.</li>
     * </ol>
     *
     * @param args tham số dòng lệnh (không sử dụng)
     */
    public static void main(String[] args) {
        // ── Bước 1: Khởi tạo ObjectMapper với hỗ trợ kiểu thời gian Java 8 ──
        ObjectMapper objectMapper = buildObjectMapper();

        // ── Bước 2: Khởi tạo kết nối database và JDBI ──
        Jdbi jdbi = DatabaseConfig.buildJdbi();
        LOGGER.info("Kết nối database thành công");

        // ── Bước 3: Khởi tạo DAOs (JDBI on-demand proxy — thread-safe) ──
        UserDao userDao = jdbi.onDemand(UserDao.class);
        ItemDao itemDao = jdbi.onDemand(ItemDao.class);
        AuctionDao auctionDao = jdbi.onDemand(AuctionDao.class);
        BidTransactionDao bidTransactionDao = jdbi.onDemand(BidTransactionDao.class);
        AutoBidConfigDao autoBidConfigDao = jdbi.onDemand(AutoBidConfigDao.class);

        // ── Bước 4: Khởi tạo Services với constructor injection ──
        UserService userService = new UserService(userDao);
        ItemService itemService = new ItemService(itemDao);
        AuctionService auctionService = new AuctionService(auctionDao, itemDao, userDao);

        // ── Bước 5: Tạo Javalin instance ──
        Javalin app = Javalin.create(config -> {
            // Cấu hình JSON mapper dùng Jackson (hỗ trợ LocalDateTime, BigDecimal)
            config.jsonMapper(new JavalinJackson(objectMapper));

            // Cho phép tất cả origin trong môi trường dev (JavaFX client chạy cùng máy)
            config.bundledPlugins.enableCors(cors ->
                cors.addRule(it -> it.anyHost())
            );
        });

        // ── Bước 6: Đăng ký JWT Middleware ──
        // Áp dụng cho TẤT CẢ route /api/* trừ /api/auth/* (login, register không cần token)
        app.before("/api/*", ctx -> {
            String path = ctx.path();
            if (!path.startsWith("/api/auth/")) {
                JwtMiddleware.handle(ctx);
            }
        });

        // ── Bước 7: Đăng ký Exception Handlers ──
        // Mỗi custom exception ánh xạ sang HTTP status code phù hợp + JSON body chuẩn
        registerExceptionHandlers(app);

        // ── Bước 8: Đăng ký Health Check endpoint ──
        app.get("/api/health", ctx -> {
            ctx.json(Map.of("status", "ok", "version", "1.0.0"));
        });

        // ── Bước 9: Đăng ký Controllers ──
        AuthController.register(app, userService);
        ItemController.register(app, itemService);
        AuctionController.register(app, auctionService);

        // ── Bước 10: Khởi động server ──
        app.start(SERVER_PORT);
        LOGGER.info("Server đang chạy tại http://localhost:{}", SERVER_PORT);
    }

    /**
     * Xây dựng ObjectMapper với cấu hình cần thiết cho dự án.
     *
     * <p>Lý do cấu hình:
     * <ul>
     *   <li>{@code JavaTimeModule} — hỗ trợ serialize {@code LocalDateTime} thành chuỗi ISO-8601
     *       thay vì mảng số nguyên (mặc định của Jackson).</li>
     *   <li>{@code WRITE_DATES_AS_TIMESTAMPS = false} — trả về {@code "2025-01-15T10:30:00"}
     *       thay vì {@code [2025, 1, 15, 10, 30, 0]}.</li>
     * </ul>
     *
     * @return ObjectMapper đã cấu hình
     */
    private static ObjectMapper buildObjectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Đăng ký toàn bộ exception handler cho Javalin.
     *
     * <p>Bảng ánh xạ exception → HTTP status:
     * <pre>
     *   InvalidBidException      → 400 Bad Request
     *   AuctionClosedException   → 400 Bad Request
     *   UnauthorizedException    → 401 Unauthorized
     *   NotFoundException        → 404 Not Found
     *   DuplicateException       → 409 Conflict
     *   Exception (catch-all)    → 500 Internal Server Error
     * </pre>
     *
     * <p>Tất cả response đều dùng {@link ErrorResponse} để đảm bảo format JSON nhất quán.
     * Client không bao giờ nhận được stacktrace — chỉ nhận message thân thiện.
     *
     * @param app Javalin instance cần đăng ký handler
     */
    private static void registerExceptionHandlers(Javalin app) {
        // 400 — Giá đặt không hợp lệ (thấp hơn giá hiện tại, tự bid sản phẩm của mình...)
        app.exception(InvalidBidException.class, (e, ctx) -> {
            LOGGER.warn("Lỗi đặt giá không hợp lệ: {}", e.getMessage());
            ctx.status(400).json(ErrorResponse.of("INVALID_BID", e.getMessage()));
        });

        // 400 — Phiên đấu giá đã đóng hoặc chưa bắt đầu
        app.exception(AuctionClosedException.class, (e, ctx) -> {
            LOGGER.warn("Lỗi trạng thái phiên đấu giá: {}", e.getMessage());
            ctx.status(400).json(ErrorResponse.of("AUCTION_CLOSED", e.getMessage()));
        });

        // 401 — Chưa đăng nhập hoặc sai role
        app.exception(UnauthorizedException.class, (e, ctx) -> {
            LOGGER.warn("Lỗi xác thực: {}", e.getMessage());
            ctx.status(401).json(ErrorResponse.of("UNAUTHORIZED", e.getMessage()));
        });

        // 404 — Không tìm thấy tài nguyên (auction, user, item không tồn tại)
        app.exception(NotFoundException.class, (e, ctx) -> {
            LOGGER.warn("Không tìm thấy tài nguyên: {}", e.getMessage());
            ctx.status(404).json(ErrorResponse.of("NOT_FOUND", e.getMessage()));
        });

        // 409 — Xung đột dữ liệu (username/email đã tồn tại)
        app.exception(DuplicateException.class, (e, ctx) -> {
            LOGGER.warn("Xung đột dữ liệu: {}", e.getMessage());
            ctx.status(409).json(ErrorResponse.of("DUPLICATE", e.getMessage()));
        });

        // 500 — Lỗi server không mong muốn (catch-all)
        app.exception(Exception.class, (e, ctx) -> {
            LOGGER.error("Lỗi server không xác định", e);
            ctx.status(500).json(ErrorResponse.of("INTERNAL_ERROR",
                "Lỗi hệ thống, vui lòng thử lại sau"));
        });
    }
}
