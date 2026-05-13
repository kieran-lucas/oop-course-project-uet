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
import com.auction.dao.PasswordResetRequestDao;
import com.auction.dao.UserDao;
import com.auction.dto.AutoBidRequest;
import com.auction.dto.ChangePasswordRequest;
import com.auction.dto.DepositRequest;
import com.auction.dto.ErrorResponse;
import com.auction.exception.AuctionClosedException;
import com.auction.exception.DuplicateException;
import com.auction.exception.InvalidBidException;
import com.auction.exception.NotFoundException;
import com.auction.exception.UnauthorizedException;
import com.auction.middleware.JwtMiddleware;
import com.auction.model.Admin;
import com.auction.model.AutoBidConfig;
import com.auction.model.DepositRecord;
import com.auction.pattern.observer.AuctionEventManager;
import com.auction.pattern.strategy.AutoBidStrategy;
import com.auction.service.AuctionScheduler;
import com.auction.service.AuctionService;
import com.auction.service.BidService;
import com.auction.service.ItemService;
import com.auction.service.PasswordResetService;
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

    // ── 3. Khởi tạo DAOs ────────────────────────────────────
    var userDao = new UserDao(jdbi);
    var itemDao = new ItemDao(jdbi);
    var auctionDao = new AuctionDao(jdbi);
    var bidTransactionDao = new BidTransactionDao(jdbi);
    var autoBidConfigDao = new AutoBidConfigDao(jdbi);
    var depositRequestDao = new DepositRequestDao(jdbi);
    var passwordResetRequestDao = new PasswordResetRequestDao(jdbi);

    // ── 3b. Seed tài khoản admin mặc định ───────────────────
    seedAdminIfNeeded(userDao);

    // ── 4. Khởi tạo Observer (EventManager) ─────────────────
    var eventManager = new AuctionEventManager();

    // ── 5. Khởi tạo WebSocket Handler + Observer integration ─
    var wsHandler = new AuctionWebSocketHandler(eventManager, jdbi);

    // ── 6. Khởi tạo Services ────────────────────────────────
    var userService = new UserService(userDao, depositRequestDao, jdbi);
    var passwordResetService = new PasswordResetService(userDao, passwordResetRequestDao, jdbi);
    var itemService = new ItemService(itemDao);
    var auctionService = new AuctionService(auctionDao, itemDao, userDao, eventManager, jdbi);
    var autoBidStrategy = new AutoBidStrategy(autoBidConfigDao);
    var bidService =
        new BidService(
            auctionDao,
            bidTransactionDao,
            autoBidConfigDao,
            eventManager,
            jdbi,
            auctionService,
            userDao,
            autoBidStrategy);
    var bidController = new BidController(bidService);

    // ── 7. Khởi tạo Scheduler (tự chuyển trạng thái phiên) ──
    var scheduler = new AuctionScheduler(auctionDao, userDao, itemDao, eventManager, jdbi);
    scheduler.start();

    // Đăng ký shutdown hook để dừng scheduler khi server tắt
    registerShutdownHook(scheduler);
    /*
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  LOGGER.info("Server đang tắt...");
                  scheduler.stop();
                  DatabaseConfig.shutDown();
                }));
    */

    // ── 8. Tạo Javalin instance ──────────────────────────────
    Javalin app = buildJavalin(mapper);
    /*
    Javalin ignoredApp =
        Javalin.create(
            config -> {
              config.jsonMapper(new JavalinJackson(mapper, false));
              config.http.defaultContentType = "application/json";
              config.bundledPlugins.enableCors(
                  cors ->
                      cors.addRule(
                          it -> {
                            it.allowHost("localhost:3000", "localhost:8080");
                            // TODO: replace with production domain before deployment
                          }));
            });
    */

    // ── 9. Đăng ký JWT Middleware ────────────────────────────
    app.before("/api/*", JwtMiddleware::handle);

    // ── 10. Đăng ký Exception Handlers ──────────────────────
    registerExceptionHandlers(app);

    // ── 11. Đăng ký Routes ───────────────────────────────────
    app.get("/api/health", ctx -> ctx.json(Map.of("status", "ok")));

    AuthController.register(app, userService);
    AuthController.registerPasswordReset(app, passwordResetService);
    ItemController.register(app, itemService);
    AuctionController.register(app, auctionService);

    // Đăng ký BidController với BidService mới
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
          requireAdmin(ctx);
          ctx.json(userService.getPendingDeposits());
        });

    app.post(
        "/api/admin/deposit-requests/{id}/approve",
        ctx -> {
          requireAdmin(ctx);
          Long requestId = Long.parseLong(ctx.pathParam("id"));
          com.auction.dto.UserResponse result = userService.approveDeposit(requestId);
          // Notify user qua WebSocket về biến động số dư
          wsHandler.notifyBalanceUpdate(result.getId(), result.getBalance(), true);
          ctx.json(result);
        });

    app.post(
        "/api/admin/deposit-requests/{id}/reject",
        ctx -> {
          requireAdmin(ctx);
          Long requestId = Long.parseLong(ctx.pathParam("id"));
          Long userId = userService.rejectDeposit(requestId);
          // Notify user qua WebSocket rằng yêu cầu bị từ chối
          wsHandler.notifyBalanceUpdate(userId, null, false);
          ctx.status(204);
        });

    // ── Admin password reset endpoints ───────────────────
    app.get(
        "/api/admin/password-reset-requests",
        ctx -> {
          requireAdmin(ctx);
          ctx.json(passwordResetService.getPendingRequests());
        });

    app.post(
        "/api/admin/password-reset-requests/{id}/approve",
        ctx -> {
          requireAdmin(ctx);
          Long requestId = Long.parseLong(ctx.pathParam("id"));
          String tempPwd = passwordResetService.approveReset(requestId);
          ctx.status(200)
              .json(
                  Map.of(
                      "message",
                      "Mật khẩu tạm thời đã được tạo. Hiển thị cho user một lần duy nhất.",
                      "tempPassword",
                      tempPwd));
        });

    app.post(
        "/api/admin/password-reset-requests/{id}/reject",
        ctx -> {
          requireAdmin(ctx);
          Long requestId = Long.parseLong(ctx.pathParam("id"));
          passwordResetService.rejectReset(requestId);
          ctx.status(204);
        });

    // ── Admin hard-delete auction endpoint ───────────────
    app.delete(
        "/api/admin/auctions/{id}",
        ctx -> {
          requireAdmin(ctx);
          Long auctionId = Long.parseLong(ctx.pathParam("id"));
          auctionService.hardDelete(auctionId);
          ctx.status(204);
        });

    // ── Admin user management endpoints ──────────────────
    app.get(
        "/api/admin/users",
        ctx -> {
          requireAdmin(ctx);
          ctx.json(userService.getAll());
        });

    app.delete(
        "/api/admin/users/{id}",
        ctx -> {
          requireAdmin(ctx);
          Long userId = Long.parseLong(ctx.pathParam("id"));
          Long requesterId = ctx.attribute("userId");
          if (userId.equals(requesterId)) {
            throw new UnauthorizedException("Admin không thể tự xóa chính mình.");
          }
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
          AutoBidRequest req = ctx.bodyAsClass(AutoBidRequest.class);
          if (req.getMaxBid() == null || req.getIncrement() == null) {
            throw new InvalidBidException("maxBid và increment là bắt buộc");
          }
          BigDecimal maxBid = req.getMaxBid();
          BigDecimal increment = req.getIncrement();

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
          String role = ctx.attribute("role");
          if (!"BIDDER".equals(role)) {
            throw new UnauthorizedException("Chỉ BIDDER mới được tắt auto-bid");
          }
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

    // ── Notifications endpoints ─────────────────────────────

    app.get(
        "/api/notifications",
        ctx -> {
          Long userId = ctx.attribute("userId");

          var result =
              jdbi.withHandle(
                  handle ->
                      handle
                          .createQuery(
                              """
                              SELECT id,
                                     message,
                                     notification_type,
                                     is_read,
                                     created_at
                              FROM notifications
                              WHERE user_id = :userId
                              ORDER BY created_at DESC
                              LIMIT 50
                              """)
                          .bind("userId", userId)
                          .mapToMap()
                          .list());

          ctx.json(result);
        });

    app.patch(
        "/api/notifications/{id}/read",
        ctx -> {
          Long userId = ctx.attribute("userId");
          Long notificationId = Long.parseLong(ctx.pathParam("id"));

          jdbi.useHandle(
              handle ->
                  handle.execute(
                      """
                      UPDATE notifications
                      SET is_read = true
                      WHERE id = ?
                        AND user_id = ?
                      """,
                      notificationId,
                      userId));

          ctx.status(204);
        });

    // Đánh dấu tất cả thông báo của user hiện tại là đã đọc
    // Gọi endpoint này khi user mở popup chuông
    app.patch(
        "/api/notifications/mark-all-read",
        ctx -> {
          Long userId = ctx.attribute("userId");

          int updated =
              jdbi.withHandle(
                  handle ->
                      handle.execute(
                          """
                          UPDATE notifications
                          SET is_read = true
                          WHERE user_id = ?
                            AND is_read = false
                          """,
                          userId));

          ctx.json(Map.of("updated", updated));
        });

    // ── 12. Đăng ký WebSocket ────────────────────────────────
    app.ws(
        "/ws/auction/{id}",
        ws -> {
          ws.onConnect(wsHandler::onConnect);
          ws.onClose(wsHandler::onClose);
          ws.onError(wsHandler::onError);
        });

    // Kênh WebSocket riêng cho từng user — nhận thông báo biến động số dư
    app.ws(
        "/ws/user/{id}",
        ws -> {
          ws.onConnect(wsHandler::onUserConnect);
          ws.onClose(wsHandler::onUserClose);
          ws.onError(wsHandler::onUserError);
        });

    // ── 13. Khởi động server ─────────────────────────────────
    app.start(SERVER_PORT);
    LOGGER.info("Server đang chạy tại http://localhost:{}", SERVER_PORT);
  }

  /** Ném UnauthorizedException nếu request không đến từ ADMIN. */
  private static Javalin buildJavalin(ObjectMapper mapper) {
    return Javalin.create(
        config -> {
          config.jsonMapper(new JavalinJackson(mapper, false));
          config.http.defaultContentType = "application/json";
          config.bundledPlugins.enableCors(
              cors ->
                  cors.addRule(
                      it -> {
                        it.allowHost("localhost:3000", "localhost:8080");
                        // TODO: replace with production domain before deployment
                      }));
        });
  }

  private static void registerShutdownHook(AuctionScheduler scheduler) {
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  LOGGER.info("Server Ä‘ang táº¯t...");
                  scheduler.stop();
                  DatabaseConfig.shutDown();
                }));
  }

  private static void requireAdmin(io.javalin.http.Context ctx) {
    if (!"ADMIN".equals(ctx.attribute("role"))) {
      throw new UnauthorizedException("Chỉ ADMIN mới có quyền thực hiện thao tác này");
    }
  }

  /** Tạo tài khoản admin mặc định (admin / 123456) khi server khởi động nếu chưa tồn tại. */
  private static void seedAdminIfNeeded(UserDao userDao) {
    try {
      var existing = userDao.findByUsername("admin");
      if (existing.isEmpty()) {
        String hash = BCrypt.withDefaults().hashToString(12, "123456".toCharArray());
        Admin admin = new Admin("admin", hash, "admin@auction.com");
        userDao.insert(admin);
        LOGGER.info("Đã tạo tài khoản admin mặc định: username=admin, password=123456");
      } else {
        LOGGER.info("Tài khoản admin đã tồn tại, bỏ qua seed.");
      }
    } catch (Exception e) {
      LOGGER.warn("Không thể seed admin: {}", e.getMessage());
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
        IllegalArgumentException.class,
        (e, ctx) -> {
          LOGGER.warn("Dữ liệu không hợp lệ: {}", e.getMessage());
          ctx.status(400).json(ErrorResponse.of("BAD_REQUEST", e.getMessage()));
        });

    app.exception(
        IllegalStateException.class,
        (e, ctx) -> {
          LOGGER.warn("Trạng thái không hợp lệ: {}", e.getMessage());
          ctx.status(409).json(ErrorResponse.of("INVALID_STATE", e.getMessage()));
        });

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
