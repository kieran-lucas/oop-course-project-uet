package com.auction;

import com.auction.config.DatabaseConfig;
import com.auction.controller.AuthController;
import com.auction.controller.AuctionController;
import com.auction.controller.ItemController;
import com.auction.controller.BidController;
import com.auction.controller.AuctionWebSocketHandler;
import com.auction.dao.AuctionDao;
import com.auction.dao.ItemDao;
import com.auction.dao.UserDao;
import com.auction.dao.BidTransactionDao;
import com.auction.dto.ErrorResponse;
import com.auction.exception.*;
import com.auction.middleware.JwtMiddleware;
import com.auction.service.AuctionService;
import com.auction.service.ItemService;
import com.auction.service.UserService;
import com.auction.service.BidService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import org.jdbi.v3.core.Jdbi;

public class App {

    public static void main(String[] args) {

        // 1. Cấu hình Jackson (Rất quan trọng để parse LocalDateTime chuẩn ISO-8601)
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 2. Khởi tạo Database, DAO và Service (Dependency Injection thủ công)
        Jdbi jdbi = DatabaseConfig.create();
        var userDao = new UserDao(jdbi);
        var userService = new UserService(userDao);
        var authController = new AuthController(userService);

        var itemDao = new ItemDao(jdbi);
        var auctionDao = new AuctionDao(jdbi);

        var itemService = new ItemService(itemDao);
        var auctionService = new AuctionService(auctionDao, itemDao, userDao);

        var itemController = new ItemController(itemService);
        var auctionController = new AuctionController(auctionService);

        var bidTransactionDao = new BidTransactionDao(jdbi);
        var bidService = new BidService(auctionDao, bidTransactionDao, userDao, jdbi);
        var bidController = new BidController(bidService);

        var wsHandler = new AuctionWebSocketHandler();
        bidService.setBroadcaster(wsHandler); // Kết nối BidService ↔ WebSocket

        // 3. Tạo ứng dụng Javalin và cấu hình
        Javalin app = Javalin.create(config -> {
            // Cấu hình Jackson làm JSON Mapper mặc định
            config.jsonMapper(new JavalinJackson(mapper, false));
            config.http.defaultContentType = "application/json";

            // Cho phép CORS nếu Client (JavaFX WebEngine hoặc Web Frontend) gọi khác port
            config.bundledPlugins.enableCors(cors -> cors.addRule(it -> it.anyHost()));
        });

        // 4. Đăng ký Middleware xác thực JWT cho tất cả API (trừ /api/auth/ đã bỏ qua trong Middleware)
        app.before("/api/*", JwtMiddleware::handle);

        // 5. Đăng ký 5 Exception Handlers chuyển Exception thành JSON ErrorResponse
        app.exception(InvalidBidException.class, (e, ctx) -> {
            ctx.status(400).json(ErrorResponse.of("INVALID_BID", e.getMessage()));
        });

        app.exception(AuctionClosedException.class, (e, ctx) -> {
            ctx.status(400).json(ErrorResponse.of("AUCTION_CLOSED", e.getMessage()));
        });

        app.exception(UnauthorizedException.class, (e, ctx) -> {
            ctx.status(401).json(ErrorResponse.of("UNAUTHORIZED", e.getMessage()));
        });

        app.exception(NotFoundException.class, (e, ctx) -> {
            ctx.status(404).json(ErrorResponse.of("NOT_FOUND", e.getMessage()));
        });

        app.exception(DuplicateException.class, (e, ctx) -> {
            ctx.status(409).json(ErrorResponse.of("DUPLICATE", e.getMessage()));
        });

        // Catch-all cho các lỗi Server không lường trước
        app.exception(Exception.class, (e, ctx) -> {
            e.printStackTrace(); // Log ra console để dev debug
            ctx.status(500).json(ErrorResponse.of("INTERNAL_ERROR", "Lỗi hệ thống, vui lòng thử lại sau."));
        });

        // 6. Đăng ký các Routes

        // Endpoint Health Check
        app.get("/api/health", ctx -> ctx.json(java.util.Map.of("status", "ok")));

        // ===== ĐĂNG KÝ REST ROUTES =====
        authController.register(app);       // /api/auth/login, /register
        itemController.register(app);       // /api/items
        auctionController.register(app);    // /api/auctions

        // ═══════════ ĐĂNG KÝ ROUTES MỚI ═══════════
        bidController.register(app);        // /api/auctions/{id}/bid & /api/auctions/{id}/bids

        // WebSocket — KHÁC với REST routes
        // app.ws() đăng ký WebSocket endpoint (không phải app.get/post)
        app.ws("/ws/auction/{id}", ws -> {
            ws.onConnect(wsHandler::onConnect);
            ws.onClose(wsHandler::onClose);
            ws.onError(wsHandler::onError);
        });
        // ══════════════════════════════════════════

        // 7. Khởi động server
        app.start(8080);
        System.out.println("🚀 Server started on http://localhost:8080");
    }
}
