package com.auction;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.auction.config.DatabaseConfig;
import com.auction.config.JwtUtil;
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
import com.auction.model.AutoBidStatus;
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
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * <p>Biến môi trường:
 *
 * <ul>
 *   <li>Database luôn dùng Embedded PostgreSQL và lưu tại {@code data/postgres}; không cần DB_URL.
 *   <li>JWT_SECRET — required JWT signing key; must be at least 32 bytes in UTF-8.
 * </ul>
 */
public class App {

  private static final Logger LOGGER = LoggerFactory.getLogger(App.class);
  private static final int SERVER_PORT = 8080;
  private static final Path DATA_DIR = Path.of("data");
  private static final Path SERVER_PID_FILE = DATA_DIR.resolve("server.pid");
  private static final Path SERVER_TOKEN_FILE = DATA_DIR.resolve("server.token");
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final AtomicBoolean SHUTTING_DOWN = new AtomicBoolean(false);

  public static void main(String[] args) {
    if (isServerAlreadyRunning()) {
      System.out.printf("Server is already running at http://localhost:%d%n", SERVER_PORT);
      return;
    }

    JwtUtil.validateConfiguration();

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
    var auctionService =
        new AuctionService(auctionDao, itemDao, userDao, eventManager, jdbi, bidTransactionDao);
    var autoBidStrategy = new AutoBidStrategy(autoBidConfigDao, userDao);
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
    // Shutdown hook is registered after the Javalin instance is created.

    // ── 8. Tạo Javalin instance ──────────────────────────────
    Javalin app = buildJavalin(mapper);
    String shutdownToken = loadOrCreateShutdownToken();
    registerShutdownHook(app, scheduler);

    // ── 9. Đăng ký JWT Middleware ────────────────────────────
    app.before("/api/*", JwtMiddleware::handle);

    // ── 10. Đăng ký Exception Handlers ──────────────────────
    registerExceptionHandlers(app);

    // ── 11. Đăng ký Routes ───────────────────────────────────
    app.get(
        "/api/health",
        ctx ->
            ctx.json(
                Map.of("status", "ok", "pid", ProcessHandle.current().pid(), "port", SERVER_PORT)));
    app.post(
        "/internal/shutdown",
        ctx -> {
          if (!isLocalRequest(ctx.ip())) {
            ctx.status(403).json(Map.of("error", "Shutdown is only allowed from localhost"));
            return;
          }
          if (!shutdownToken.equals(ctx.header("X-Shutdown-Token"))) {
            ctx.status(401).json(Map.of("error", "Invalid shutdown token"));
            return;
          }

          ctx.json(Map.of("status", "shutting_down"));
          Thread shutdownThread =
              new Thread(() -> stopServer(app, scheduler), "server-shutdown-request");
          shutdownThread.setDaemon(false);
          shutdownThread.start();
        });

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
          DepositRecord pending =
              depositRequestDao
                  .findById(requestId)
                  .orElseThrow(
                      () -> new NotFoundException("Không tìm thấy yêu cầu nạp tiền: " + requestId));
          com.auction.dto.UserResponse result = userService.approveDeposit(requestId);
          // Notify user qua WebSocket về biến động số dư
          wsHandler.notifyBalanceUpdate(
              result.getId(), result.getBalance(), pending.getAmount(), true);
          ctx.json(result);
        });

    app.post(
        "/api/admin/deposit-requests/{id}/reject",
        ctx -> {
          requireAdmin(ctx);
          Long requestId = Long.parseLong(ctx.pathParam("id"));
          Long userId = userService.rejectDeposit(requestId);
          // Notify user qua WebSocket rằng yêu cầu bị từ chối
          wsHandler.notifyBalanceUpdate(userId, null, null, false);
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
    app.get(
        "/api/auctions/{id}/auto-bid",
        ctx -> {
          String role = ctx.attribute("role");
          if (!"BIDDER".equals(role)) {
            throw new UnauthorizedException("Chỉ BIDDER mới được xem auto-bid của mình");
          }
          Long auctionId = Long.parseLong(ctx.pathParam("id"));
          Long bidderId = ctx.attribute("userId");
          var config = autoBidConfigDao.findByAuctionAndBidder(auctionId, bidderId);
          if (config.isPresent()) {
            ctx.json(config.get());
          } else {
            ctx.json(Map.of("active", false));
          }
        });

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
          var config =
              bidService.createAutoBid(auctionId, bidderId, req.getMaxBid(), req.getIncrement());
          ctx.status(201).json(config);
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
                    config.setStatus(AutoBidStatus.STOPPED);
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
    writeServerPid();
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

  private static void registerShutdownHook(Javalin app, AuctionScheduler scheduler) {
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  LOGGER.info("Server Ä‘ang táº¯t...");
                  stopServer(app, scheduler);
                }));
  }

  private static void stopServer(Javalin app, AuctionScheduler scheduler) {
    if (!SHUTTING_DOWN.compareAndSet(false, true)) {
      return;
    }

    try {
      scheduler.stop();
    } catch (Exception e) {
      LOGGER.warn("Error while stopping scheduler: {}", e.getMessage());
    }

    try {
      app.stop();
    } catch (Exception e) {
      LOGGER.warn("Error while stopping HTTP server: {}", e.getMessage());
    }

    DatabaseConfig.shutDown();
    deleteServerPid();
  }

  private static boolean isServerAlreadyRunning() {
    try (HttpClient client =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create("http://localhost:" + SERVER_PORT + "/api/health"))
              .timeout(Duration.ofSeconds(2))
              .GET()
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      return response.statusCode() == 200 && response.body().contains("\"status\":\"ok\"");
    } catch (Exception ignored) {
      return false;
    }
  }

  private static String loadOrCreateShutdownToken() {
    try {
      Files.createDirectories(DATA_DIR);
      if (Files.exists(SERVER_TOKEN_FILE)) {
        String token = Files.readString(SERVER_TOKEN_FILE, StandardCharsets.UTF_8).trim();
        if (!token.isBlank()) {
          return token;
        }
      }

      byte[] bytes = new byte[32];
      SECURE_RANDOM.nextBytes(bytes);
      String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
      Files.writeString(SERVER_TOKEN_FILE, token, StandardCharsets.UTF_8);
      return token;
    } catch (IOException e) {
      throw new IllegalStateException("Could not initialize server shutdown token", e);
    }
  }

  private static void writeServerPid() {
    try {
      Files.createDirectories(DATA_DIR);
      Files.writeString(
          SERVER_PID_FILE, Long.toString(ProcessHandle.current().pid()), StandardCharsets.UTF_8);
    } catch (IOException e) {
      LOGGER.warn("Could not write server PID file: {}", e.getMessage());
    }
  }

  private static void deleteServerPid() {
    try {
      Files.deleteIfExists(SERVER_PID_FILE);
    } catch (IOException e) {
      LOGGER.warn("Could not delete server PID file: {}", e.getMessage());
    }
  }

  private static boolean isLocalRequest(String ip) {
    return "127.0.0.1".equals(ip)
        || "0:0:0:0:0:0:0:1".equals(ip)
        || "::1".equals(ip)
        || "localhost".equalsIgnoreCase(ip);
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
