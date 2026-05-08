package com.auction;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.auction.config.DatabaseConfig;
import com.auction.controller.AuctionController;
import com.auction.controller.AuctionWebSocketHandler;
import com.auction.controller.AuthController;
import com.auction.controller.BidController;
import com.auction.controller.ItemController;
import com.auction.dao.AuctionDao;
import com.auction.dao.AutoBidConfigDao;
import com.auction.dao.BidTransactionDao;
import com.auction.dao.DepositRequestDao;
import com.auction.dao.ItemDao;
import com.auction.dao.UserDao;
import com.auction.dto.ChangePasswordRequest;
import com.auction.dto.DepositRequest;
import com.auction.model.DepositRecord;
import com.auction.dto.ErrorResponse;
import com.auction.exception.AuctionClosedException;
import com.auction.exception.DuplicateException;
import com.auction.exception.InvalidBidException;
import com.auction.exception.NotFoundException;
import com.auction.exception.UnauthorizedException;
import com.auction.middleware.JwtMiddleware;
import com.auction.model.Admin;
import com.auction.model.AutoBidConfig;
import com.auction.pattern.observer.AuctionEventManager;
import com.auction.service.AuctionScheduler;
import com.auction.service.AuctionService;
import com.auction.service.BidService;
import com.auction.service.ItemService;
import com.auction.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import java.math.BigDecimal;
import java.util.Map;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Điểm khởi động chính của server Javalin — Online Auction System.
 *
 * <p>Thứ tự khởi tạo:
 *
 * <ol>
 *   <li>Jackson ObjectMapper (hỗ trợ LocalDateTime, BigDecimal).
 *   <li>Database connection (HikariCP + JDBI).
 *   <li>DAOs → Services → Controllers.
 *   <li>Observer pattern: AuctionEventManager + WebSocketObserver.
 *   <li>AuctionScheduler (OPEN→RUNNING→FINISHED transition).
 *   <li>Javalin: Middleware → Exception handlers → Routes → Start.
 * </ol>
 *
 * <p>Biến môi trường yêu cầu (đọc từ .env):
 *
 * <ul>
 *   <li>DB_URL, DB_USER, DB_PASSWORD — kết nối PostgreSQL.
 *   <li>JWT_SECRET — khóa ký JWT (default: "auction-secret-key-dev").
 * </ul>
 */
public class App {

  private static final Logger LOGGER = LoggerFactory.getLogger(App.class);
  private static final int SERVER_PORT = 8080;

  public static void main(String[] args) {
    // ── 1. Cấu hình Jackson ───────────────────────────��──────
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // ── 2. Khởi tạo Database ─────────────────────────────────
    Jdbi jdbi = DatabaseConfig.create();
    LOGGER.info("Kết nối database thành công");
    ensureSchemaExists(jdbi);

    // ── 3. Khởi tạo DAOs ────────────────────────────────────
    var userDao = new UserDao(jdbi);
    var itemDao = new ItemDao(jdbi);
    var auctionDao = new AuctionDao(jdbi);
    var bidTransactionDao = new BidTransactionDao(jdbi);
    var autoBidConfigDao = new AutoBidConfigDao(jdbi);
    var depositRequestDao = new DepositRequestDao(jdbi);

    // ── 3b. Seed tài khoản admin mặc định ───────────────────
    seedAdminIfNeeded(userDao);

    // ── 4. Khởi tạo Observer (EventManager) ─────────────────
    var eventManager = new AuctionEventManager();

    // ── 5. Khởi tạo WebSocket Handler + Observer integration ─
    var wsHandler = new AuctionWebSocketHandler(eventManager);

    // ── 6. Khởi tạo Services ────────────────────────────────
    var userService = new UserService(userDao, depositRequestDao);
    var itemService = new ItemService(itemDao);
    var auctionService = new AuctionService(auctionDao, itemDao, userDao);
    var bidService =
        new BidService(
            auctionDao, bidTransactionDao, autoBidConfigDao, eventManager, jdbi,
            auctionService, userDao);

    // ── 7. Khởi tạo Scheduler (tự chuyển trạng thái phiên) ──
    var scheduler = new AuctionScheduler(auctionDao, userDao, itemDao, eventManager);
    scheduler.start();

    // Đăng ký shutdown hook để dừng scheduler khi server tắt
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  scheduler.stop();
                  LOGGER.info("Server đang tắt...");
                }));

    // ── 8. Tạo Javalin instance ──────────────────────────────
    Javalin app =
        Javalin.create(
            config -> {
              config.jsonMapper(new JavalinJackson(mapper, false));
              config.http.defaultContentType = "application/json";
              config.bundledPlugins.enableCors(cors -> cors.addRule(it -> it.anyHost()));
            });

    // ── 9. Đăng ký JWT Middleware ────────────────────────────
    app.before("/api/*", JwtMiddleware::handle);

    // ── 10. Đăng ký Exception Handlers ──────────────────────
    registerExceptionHandlers(app);

    // ── 11. Đăng ký Routes ───────────────────────────────────
    app.get("/api/health", ctx -> ctx.json(Map.of("status", "ok")));

    AuthController.register(app, userService);
    ItemController.register(app, itemService);
    AuctionController.register(app, auctionService);

    // Đăng ký BidController với BidService mới
    var bidController = new BidController(bidService);
    bidController.register(app);

    // ── User profile endpoints ────────────────────────────
    app.get(
        "/api/users/me",
        ctx -> {
          Long userId = ctx.attribute("userId");
          ctx.json(userService.findById(userId));
        });

    app.put(
        "/api/users/me/password",
        ctx -> {
          Long userId = ctx.attribute("userId");
          ChangePasswordRequest req = ctx.bodyAsClass(ChangePasswordRequest.class);
          userService.changePassword(userId, req);
          ctx.status(204);
        });

    // Lịch sử yêu cầu nạp tiền của chính user
    app.get(
        "/api/users/me/deposit-requests",
        ctx -> {
          Long userId = ctx.attribute("userId");
          ctx.json(depositRequestDao.findByUserId(userId));
        });

    // Bidder gửi yêu cầu nạp tiền — tạo PENDING record, chờ Admin duyệt
    app.post(
        "/api/users/me/deposit",
        ctx -> {
          Long userId = ctx.attribute("userId");
          DepositRequest req = ctx.bodyAsClass(DepositRequest.class);
          DepositRecord record = userService.requestDeposit(userId, req.getAmount());
          ctx.status(202).json(record);
        });

    // ── Admin deposit management endpoints ───────────────
    app.get(
        "/api/admin/deposit-requests",
        ctx -> {
          if (!"ADMIN".equals(ctx.attribute("role"))) {
            throw new UnauthorizedException("Chỉ ADMIN mới có quyền truy cập");
          }
          ctx.json(userService.getPendingDeposits());
        });

    app.post(
        "/api/admin/deposit-requests/{id}/approve",
        ctx -> {
          if (!"ADMIN".equals(ctx.attribute("role"))) {
            throw new UnauthorizedException("Chỉ ADMIN mới có quyền phê duyệt");
          }
          Long requestId = Long.parseLong(ctx.pathParam("id"));
          ctx.json(userService.approveDeposit(requestId));
        });

    app.post(
        "/api/admin/deposit-requests/{id}/reject",
        ctx -> {
          if (!"ADMIN".equals(ctx.attribute("role"))) {
            throw new UnauthorizedException("Chỉ ADMIN mới có quyền từ chối");
          }
          Long requestId = Long.parseLong(ctx.pathParam("id"));
          userService.rejectDeposit(requestId);
          ctx.status(204);
        });

    // ── Admin user management endpoints ──────────────────
    app.get(
        "/api/admin/users",
        ctx -> {
          String role = ctx.attribute("role");
          if (!"ADMIN".equals(role)) {
            throw new UnauthorizedException("Chỉ ADMIN mới có quyền truy cập");
          }
          ctx.json(userService.getAll());
        });

    app.delete(
        "/api/admin/users/{id}",
        ctx -> {
          String role = ctx.attribute("role");
          if (!"ADMIN".equals(role)) {
            throw new UnauthorizedException("Chỉ ADMIN mới có quyền xóa user");
          }
          Long userId = Long.parseLong(ctx.pathParam("id"));
          userService.delete(userId);
          ctx.status(204);
        });

    // ── Auto-bid endpoints ────────────────────────────────
    app.post(
        "/api/auctions/{id}/auto-bid",
        ctx -> {
          String role = ctx.attribute("role");
          if (!"BIDDER".equals(role)) {
            throw new UnauthorizedException("Chỉ BIDDER mới được bật auto-bid");
          }
          Long auctionId = Long.parseLong(ctx.pathParam("id"));
          Long bidderId = ctx.attribute("userId");
          var body = ctx.bodyAsClass(Map.class);
          Object rawMaxBid = body.get("maxBid");
          Object rawIncrement = body.get("increment");
          if (rawMaxBid == null || rawIncrement == null) {
            throw new com.auction.exception.InvalidBidException("maxBid và increment là bắt buộc");
          }
          BigDecimal maxBid = new BigDecimal(rawMaxBid.toString());
          BigDecimal increment = new BigDecimal(rawIncrement.toString());

          var existing = autoBidConfigDao.findByAuctionAndBidder(auctionId, bidderId);
          if (existing.isPresent()) {
            AutoBidConfig config = existing.get();
            config.setMaxBid(maxBid);
            config.setIncrement(increment);
            config.setActive(true);
            autoBidConfigDao.update(config);
            ctx.status(200).json(config);
          } else {
            AutoBidConfig config = new AutoBidConfig(auctionId, bidderId, maxBid, increment);
            autoBidConfigDao.insert(config);
            ctx.status(201).json(config);
          }
        });

    app.delete(
        "/api/auctions/{id}/auto-bid",
        ctx -> {
          Long auctionId = Long.parseLong(ctx.pathParam("id"));
          Long bidderId = ctx.attribute("userId");
          autoBidConfigDao
              .findByAuctionAndBidder(auctionId, bidderId)
              .ifPresent(
                  config -> {
                    config.setActive(false);
                    autoBidConfigDao.update(config);
                  });
          ctx.status(204);
        });

    // ── 12. Đăng ký WebSocket ────────────────────────────────
    app.ws(
        "/ws/auction/{id}",
        ws -> {
          ws.onConnect(wsHandler::onConnect);
          ws.onClose(wsHandler::onClose);
          ws.onError(wsHandler::onError);
        });

    // ── 13. Khởi động server ─────────────────────────────────
    app.start(SERVER_PORT);
    LOGGER.info("Server đang chạy tại http://localhost:{}", SERVER_PORT);
  }

  /**
   * Tạo hoặc cập nhật tài khoản admin mặc định (admin / 123456) khi server khởi động. Đảm bảo admin
   * luôn có thể đăng nhập trong môi trường dev/test.
   */
  private static void seedAdminIfNeeded(UserDao userDao) {
    try {
      String hash = BCrypt.withDefaults().hashToString(12, "123456".toCharArray());
      var existing = userDao.findByUsername("admin");
      if (existing.isEmpty()) {
        Admin admin = new Admin("admin", hash, "admin@auction.com");
        userDao.insert(admin);
        LOGGER.info("Đã tạo tài khoản admin mặc định: username=admin, password=123456");
      } else {
        existing.get().setPasswordHash(hash);
        userDao.update(existing.get());
        LOGGER.info("Đã đồng bộ mật khẩu admin: username=admin, password=123456");
      }
    } catch (Exception e) {
      LOGGER.warn("Không thể seed admin: {}", e.getMessage());
    }
  }

  /**
   * Áp dụng các migration còn thiếu theo kiểu idempotent (IF NOT EXISTS).
   * Thay thế cho Flyway vì project không cấu hình auto-migration.
   * An toàn để gọi nhiều lần — không ảnh hưởng schema đã tồn tại.
   */
  private static void ensureSchemaExists(Jdbi jdbi) {
    try {
      jdbi.useHandle(handle -> {
        // V3: cột balance cho users (nếu chưa có)
        handle.execute(
            "ALTER TABLE users ADD COLUMN IF NOT EXISTS balance DECIMAL(15,2) NOT NULL DEFAULT 0");

        // V4: bảng deposit_requests (nếu chưa có)
        handle.execute("""
            CREATE TABLE IF NOT EXISTS deposit_requests (
                id          BIGSERIAL PRIMARY KEY,
                user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                amount      DECIMAL(15,2) NOT NULL,
                status      VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
                created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
                reviewed_at TIMESTAMP
            )
            """);
        handle.execute(
            "CREATE INDEX IF NOT EXISTS idx_deposit_requests_status ON deposit_requests(status)");
      });
      LOGGER.info("Schema kiểm tra xong — tất cả bảng tồn tại.");
    } catch (Exception e) {
      LOGGER.error("Lỗi khi đảm bảo schema: {}", e.getMessage(), e);
    }
  }

  /**
   * Đăng ký exception handlers — ánh xạ custom exception → HTTP status code.
   *
   * <p>Bảng ánh xạ:
   *
   * <pre>
   *   InvalidBidException      → 400 Bad Request
   *   AuctionClosedException   → 400 Bad Request
   *   UnauthorizedException    → 401 Unauthorized
   *   NotFoundException        → 404 Not Found
   *   DuplicateException       → 409 Conflict
   *   Exception (catch-all)    → 500 Internal Server Error
   * </pre>
   */
  private static void registerExceptionHandlers(Javalin app) {
    app.exception(
        InvalidBidException.class,
        (e, ctx) -> {
          LOGGER.warn("Lỗi đặt giá: {}", e.getMessage());
          ctx.status(400).json(ErrorResponse.of("INVALID_BID", e.getMessage()));
        });

    app.exception(
        AuctionClosedException.class,
        (e, ctx) -> {
          LOGGER.warn("Lỗi trạng thái phiên: {}", e.getMessage());
          ctx.status(400).json(ErrorResponse.of("AUCTION_CLOSED", e.getMessage()));
        });

    app.exception(
        UnauthorizedException.class,
        (e, ctx) -> {
          LOGGER.warn("Lỗi xác thực: {}", e.getMessage());
          ctx.status(401).json(ErrorResponse.of("UNAUTHORIZED", e.getMessage()));
        });

    app.exception(
        NotFoundException.class,
        (e, ctx) -> {
          LOGGER.warn("Không tìm thấy tài nguyên: {}", e.getMessage());
          ctx.status(404).json(ErrorResponse.of("NOT_FOUND", e.getMessage()));
        });

    app.exception(
        DuplicateException.class,
        (e, ctx) -> {
          LOGGER.warn("Xung đột dữ liệu: {}", e.getMessage());
          ctx.status(409).json(ErrorResponse.of("DUPLICATE", e.getMessage()));
        });

    app.exception(
        Exception.class,
        (e, ctx) -> {
          LOGGER.error("Lỗi server không xác định", e);
          ctx.status(500)
              .json(ErrorResponse.of("INTERNAL_ERROR", "Lỗi hệ thống, vui lòng thử lại sau."));
        });
  }
}
