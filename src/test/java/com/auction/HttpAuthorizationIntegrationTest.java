package com.auction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.auction.config.DatabaseConfig;
import com.auction.config.JwtUtil;
import com.auction.controller.AuctionController;
import com.auction.controller.BidController;
import com.auction.controller.ItemController;
import com.auction.controller.NotificationController;
import com.auction.dao.AuctionDao;
import com.auction.dao.AutoBidConfigDao;
import com.auction.dao.BidTransactionDao;
import com.auction.dao.ItemDao;
import com.auction.dao.NotificationDao;
import com.auction.dao.UserDao;
import com.auction.dto.ErrorResponse;
import com.auction.exception.AuctionClosedException;
import com.auction.exception.DuplicateException;
import com.auction.exception.InvalidBidException;
import com.auction.exception.NotFoundException;
import com.auction.exception.UnauthorizedException;
import com.auction.middleware.JwtMiddleware;
import com.auction.model.AutoBidStatus;
import com.auction.pattern.observer.AuctionEventManager;
import com.auction.pattern.strategy.AutoBidStrategy;
import com.auction.service.AuctionService;
import com.auction.service.BidService;
import com.auction.service.ItemService;
import com.auction.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpAuthorizationIntegrationTest {

  private static final long ADMIN_ID = 1L;
  private static final long SELLER_A_ID = 2L;
  private static final long SELLER_B_ID = 3L;
  private static final long BIDDER_A_ID = 4L;
  private static final long BIDDER_B_ID = 5L;
  private static final long RUNNING_AUCTION_ID = 1L;
  private static final long OPEN_AUCTION_ID = 2L;
  private static final long OTHER_USER_NOTIFICATION_ID = 1L;

  private static Jdbi jdbi;
  private static AutoBidConfigDao autoBidConfigDao;
  private static Javalin app;
  private static HttpClient client;
  private static String baseUrl;

  private String adminToken;
  private String sellerAToken;
  private String sellerBToken;
  private String bidderAToken;

  @BeforeAll
  static void startServer() throws IOException {
    jdbi = DatabaseConfig.create();

    UserDao userDao = new UserDao(jdbi);
    ItemDao itemDao = new ItemDao(jdbi);
    AuctionDao auctionDao = new AuctionDao(jdbi);
    BidTransactionDao bidTransactionDao = new BidTransactionDao(jdbi);
    autoBidConfigDao = new AutoBidConfigDao(jdbi);
    NotificationDao notificationDao = new NotificationDao(jdbi);

    AuctionEventManager eventManager = new AuctionEventManager();
    ItemService itemService = new ItemService(itemDao);
    NotificationService notificationService = new NotificationService(notificationDao);
    AuctionService auctionService =
        new AuctionService(auctionDao, itemDao, userDao, eventManager, jdbi, bidTransactionDao);
    AutoBidStrategy autoBidStrategy = new AutoBidStrategy(autoBidConfigDao, userDao);
    var wsHandler = new com.auction.controller.AuctionWebSocketHandler(eventManager, jdbi);
    BidService bidService =
        new BidService(
            auctionDao,
            bidTransactionDao,
            autoBidConfigDao,
            eventManager,
            jdbi,
            auctionService,
            userDao,
            autoBidStrategy,
            wsHandler);

    app = buildTestApp();
    JwtMiddleware.configure(userDao);
    app.before("/api/*", JwtMiddleware::handle);
    registerExceptionHandlers(app);
    ItemController.register(app, itemService);
    AuctionController.register(app, auctionService);
    NotificationController.register(app, notificationService);
    new BidController(bidService).register(app);
    registerAutoBidStopRoute(app);
    registerAdminProbeRoute(app);

    int port = findFreePort();
    app.start(port);
    baseUrl = "http://localhost:" + port;
    client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  @AfterAll
  static void stopServer() {
    if (app != null) {
      app.stop();
    }
  }

  @BeforeEach
  void seedData() {
    jdbi.useHandle(
        handle -> {
          handle.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
          handle.execute(
              """
              INSERT INTO users
                  (id, username, password_hash, email, role, balance, reserved_balance,
                   token_version)
              VALUES
                  (1, 'admin-authz', 'hash', 'admin-authz@example.com', 'ADMIN', 0, 0, 0),
                  (2, 'seller-a-authz', 'hash', 'seller-a-authz@example.com', 'SELLER', 0, 0, 0),
                  (3, 'seller-b-authz', 'hash', 'seller-b-authz@example.com', 'SELLER', 0, 0, 0),
                  (4, 'bidder-a-authz', 'hash', 'bidder-a-authz@example.com',
                   'BIDDER', 10000000, 0, 0),
                  (5, 'bidder-b-authz', 'hash', 'bidder-b-authz@example.com',
                   'BIDDER', 10000000, 0, 0)
              """);
          handle.execute(
              """
              INSERT INTO items (id, seller_id, name, description, category, brand)
              VALUES
                  (1, 2, 'Seller A phone', 'Authz fixture', 'ELECTRONICS', 'Acme'),
                  (2, 2, 'Seller A laptop', 'Authz fixture', 'ELECTRONICS', 'Acme'),
                  (3, 3, 'Seller B camera', 'Authz fixture', 'ELECTRONICS', 'Acme')
              """);
          handle.execute(
              """
              INSERT INTO auctions
                  (id, item_id, seller_id, starting_price, current_price, start_time, end_time,
                   status)
              VALUES
                  (1, 1, 2, 100000, 100000, NOW() - INTERVAL '1 hour',
                   NOW() + INTERVAL '1 hour', 'RUNNING'),
                  (2, 2, 2, 100000, 100000, NOW() + INTERVAL '1 day',
                   NOW() + INTERVAL '2 days', 'OPEN')
              """);
          handle.execute(
              """
              INSERT INTO notifications
                  (id, user_id, message, notification_type, is_read)
              VALUES
                  (1, 5, 'Private notification for bidder B', 'TEST', false)
              """);
          handle.execute(
              """
              INSERT INTO auto_bid_configs
                  (id, auction_id, bidder_id, max_bid, increment_amount, active, status,
                   registered_at)
              VALUES
                  (1, 1, 5, 500000, 50000, true, 'ACTIVE', NOW())
              """);
        });

    adminToken = JwtUtil.createToken(ADMIN_ID, "admin-authz", "ADMIN", 0);
    sellerAToken = JwtUtil.createToken(SELLER_A_ID, "seller-a-authz", "SELLER", 0);
    sellerBToken = JwtUtil.createToken(SELLER_B_ID, "seller-b-authz", "SELLER", 0);
    bidderAToken = JwtUtil.createToken(BIDDER_A_ID, "bidder-a-authz", "BIDDER", 0);
  }

  @Test
  void sellerCannotPostBid() throws Exception {
    HttpResponse<String> response =
        send("POST", "/api/auctions/" + RUNNING_AUCTION_ID + "/bid", sellerAToken, bidJson());

    assertEquals(401, response.statusCode());
  }

  @Test
  void adminCannotPostBid() throws Exception {
    HttpResponse<String> response =
        send("POST", "/api/auctions/" + RUNNING_AUCTION_ID + "/bid", adminToken, bidJson());

    assertEquals(401, response.statusCode());
  }

  @Test
  void bidderCannotCreateSellerOnlyResources() throws Exception {
    HttpResponse<String> itemResponse =
        send(
            "POST",
            "/api/items",
            bidderAToken,
            """
            {
              "name": "Unauthorized item",
              "description": "Should not be created",
              "category": "ELECTRONICS",
              "categoryDetail": "Acme"
            }
            """);
    HttpResponse<String> auctionResponse =
        send("POST", "/api/auctions", bidderAToken, auctionJson(1L));

    assertEquals(401, itemResponse.statusCode());
    assertEquals(401, auctionResponse.statusCode());
  }

  @Test
  void sellerCannotUpdateOrCancelAnotherSellersAuction() throws Exception {
    HttpResponse<String> updateResponse =
        send("PUT", "/api/auctions/" + OPEN_AUCTION_ID, sellerBToken, auctionJson(2L));
    HttpResponse<String> cancelResponse =
        send("DELETE", "/api/auctions/" + OPEN_AUCTION_ID, sellerBToken, null);

    String status =
        jdbi.withHandle(
            handle ->
                handle
                    .createQuery("SELECT status FROM auctions WHERE id = :id")
                    .bind("id", OPEN_AUCTION_ID)
                    .mapTo(String.class)
                    .one());
    assertEquals(401, updateResponse.statusCode());
    assertEquals(401, cancelResponse.statusCode());
    assertEquals("OPEN", status);
  }

  @Test
  void bidderCannotStopAnotherBiddersAutoBid() throws Exception {
    HttpResponse<String> response =
        send("DELETE", "/api/auctions/" + RUNNING_AUCTION_ID + "/auto-bid", bidderAToken, null);

    var otherBidderConfig =
        autoBidConfigDao.findByAuctionAndBidder(RUNNING_AUCTION_ID, BIDDER_B_ID).orElseThrow();
    assertEquals(204, response.statusCode());
    assertEquals(AutoBidStatus.ACTIVE, otherBidderConfig.getStatus());
  }

  @Test
  void userCannotMarkAnotherUsersNotificationRead() throws Exception {
    HttpResponse<String> response =
        send(
            "PATCH",
            "/api/notifications/" + OTHER_USER_NOTIFICATION_ID + "/read",
            bidderAToken,
            null);

    Boolean isRead =
        jdbi.withHandle(
            handle ->
                handle
                    .createQuery("SELECT is_read FROM notifications WHERE id = :id")
                    .bind("id", OTHER_USER_NOTIFICATION_ID)
                    .mapTo(Boolean.class)
                    .one());
    assertEquals(204, response.statusCode());
    assertFalse(isRead);
  }

  @Test
  void nonAdminCannotCallAdminRoutes() throws Exception {
    HttpResponse<String> sellerResponse = send("GET", "/api/admin/users", sellerAToken, null);
    HttpResponse<String> bidderResponse = send("GET", "/api/admin/users", bidderAToken, null);

    assertEquals(401, sellerResponse.statusCode());
    assertEquals(401, bidderResponse.statusCode());
  }

  private static Javalin buildTestApp() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    return Javalin.create(
        config -> {
          config.jsonMapper(new JavalinJackson(mapper, false));
          config.http.defaultContentType = "application/json";
        });
  }

  private static void registerExceptionHandlers(Javalin app) {
    app.exception(UnauthorizedException.class, (e, ctx) -> ctx.status(401).json(error(e)));
    app.exception(NotFoundException.class, (e, ctx) -> ctx.status(404).json(error(e)));
    app.exception(IllegalArgumentException.class, (e, ctx) -> ctx.status(400).json(error(e)));
    app.exception(InvalidBidException.class, (e, ctx) -> ctx.status(400).json(error(e)));
    app.exception(AuctionClosedException.class, (e, ctx) -> ctx.status(400).json(error(e)));
    app.exception(IllegalStateException.class, (e, ctx) -> ctx.status(409).json(error(e)));
    app.exception(DuplicateException.class, (e, ctx) -> ctx.status(409).json(error(e)));
  }

  private static ErrorResponse error(Exception e) {
    return ErrorResponse.of("ERROR", e.getMessage());
  }

  private static void registerAutoBidStopRoute(Javalin app) {
    app.delete(
        "/api/auctions/{id}/auto-bid",
        ctx -> {
          String role = ctx.attribute("role");
          if (!"BIDDER".equals(role)) {
            throw new UnauthorizedException("Only bidders can stop auto-bid");
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
  }

  private static void registerAdminProbeRoute(Javalin app) {
    app.get(
        "/api/admin/users",
        ctx -> {
          if (!"ADMIN".equals(ctx.attribute("role"))) {
            throw new UnauthorizedException("Only admin can access admin routes");
          }
          ctx.json(Map.of("ok", true));
        });
  }

  private static int findFreePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  private static HttpResponse<String> send(String method, String path, String token, String body)
      throws IOException, InterruptedException {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(10))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json");

    HttpRequest.BodyPublisher publisher =
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body);

    if ("GET".equals(method)) {
      builder.GET();
    } else if ("DELETE".equals(method) && body == null) {
      builder.DELETE();
    } else {
      builder.method(method, publisher);
    }

    return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static String bidJson() {
    return """
        {"amount": 200000}
        """;
  }

  private static String auctionJson(Long itemId) {
    return String.format(
        """
        {
          "itemId": %d,
          "startingPrice": 150000,
          "startTime": "2030-01-01T10:00:00",
          "endTime": "2030-01-02T10:00:00"
        }
        """,
        itemId);
  }
}
