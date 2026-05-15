package com.auction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.auction.config.DatabaseConfig;
import com.auction.config.JwtUtil;
import com.auction.controller.NotificationController;
import com.auction.dao.NotificationDao;
import com.auction.dao.UserDao;
import com.auction.dto.ErrorResponse;
import com.auction.exception.UnauthorizedException;
import com.auction.middleware.JwtMiddleware;
import com.auction.service.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationApiPersistenceTest {

  private static final long USER_A_ID = 201L;
  private static final long USER_B_ID = 202L;

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private static Jdbi jdbi;
  private static Javalin app;
  private static HttpClient client;
  private static String baseUrl;

  private String userAToken;
  private String userBToken;

  @BeforeAll
  static void startServer() throws IOException {
    jdbi = DatabaseConfig.create();

    UserDao userDao = new UserDao(jdbi);
    NotificationService notificationService = new NotificationService(new NotificationDao(jdbi));

    app =
        Javalin.create(
            config -> {
              config.jsonMapper(new JavalinJackson(MAPPER, false));
              config.http.defaultContentType = "application/json";
            });
    JwtMiddleware.configure(userDao);
    app.before("/api/*", JwtMiddleware::handle);
    app.exception(
        UnauthorizedException.class,
        (e, ctx) -> ctx.status(401).json(ErrorResponse.of("UNAUTHORIZED", e.getMessage())));
    NotificationController.register(app, notificationService);

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
  void seedUsers() {
    jdbi.useHandle(
        handle -> {
          handle.execute("DELETE FROM notifications WHERE user_id IN (201, 202)");
          handle.execute("DELETE FROM users WHERE id IN (201, 202)");
          handle.execute(
              """
              INSERT INTO users
                  (id, username, password_hash, email, role, balance, reserved_balance,
                   token_version)
              VALUES
                  (201, 'notification-user-a', 'hash', 'notification-a@example.com',
                   'BIDDER', 0, 0, 0),
                  (202, 'notification-user-b', 'hash', 'notification-b@example.com',
                   'BIDDER', 0, 0, 0)
              """);
          handle
              .createQuery(
                  """
                  SELECT setval(
                      pg_get_serial_sequence('notifications', 'id'),
                      COALESCE((SELECT MAX(id) FROM notifications), 1),
                      (SELECT COUNT(*) FROM notifications) > 0
                  )
                  """)
              .mapTo(Long.class)
              .one();
        });

    userAToken = JwtUtil.createToken(USER_A_ID, "notification-user-a", "BIDDER", 0);
    userBToken = JwtUtil.createToken(USER_B_ID, "notification-user-b", "BIDDER", 0);
  }

  @Test
  void markReadPersistsThroughPatchThenGet() throws Exception {
    long notificationId = insertNotification(USER_A_ID, "A unread notification");

    HttpResponse<String> patchResponse =
        send("PATCH", "/api/notifications/" + notificationId + "/read", userAToken);
    HttpResponse<String> getResponse = send("GET", "/api/notifications", userAToken);

    assertEquals(204, patchResponse.statusCode());
    assertEquals(200, getResponse.statusCode());
    assertTrue(findNotification(getResponse.body(), notificationId).get("is_read").asBoolean());
  }

  @Test
  void crossUserPatchDoesNotFlipOwnersNotification() throws Exception {
    long notificationId = insertNotification(USER_A_ID, "A private notification");

    HttpResponse<String> patchResponse =
        send("PATCH", "/api/notifications/" + notificationId + "/read", userBToken);
    HttpResponse<String> refetchOwnerResponse = send("GET", "/api/notifications", userAToken);

    assertEquals(204, patchResponse.statusCode());
    assertEquals(200, refetchOwnerResponse.statusCode());
    assertFalse(
        findNotification(refetchOwnerResponse.body(), notificationId).get("is_read").asBoolean());
  }

  @Test
  void markAllReadPersistsThroughPatchThenGet() throws Exception {
    long firstId = insertNotification(USER_A_ID, "A first notification");
    long secondId = insertNotification(USER_A_ID, "A second notification");
    long userBId = insertNotification(USER_B_ID, "B private notification");

    HttpResponse<String> patchResponse =
        send("PATCH", "/api/notifications/mark-all-read", userAToken);
    HttpResponse<String> userAResponse = send("GET", "/api/notifications", userAToken);
    HttpResponse<String> userBResponse = send("GET", "/api/notifications", userBToken);

    assertEquals(200, patchResponse.statusCode());
    assertEquals(2, MAPPER.readTree(patchResponse.body()).get("updated").asInt());
    assertTrue(findNotification(userAResponse.body(), firstId).get("is_read").asBoolean());
    assertTrue(findNotification(userAResponse.body(), secondId).get("is_read").asBoolean());
    assertFalse(findNotification(userBResponse.body(), userBId).get("is_read").asBoolean());
  }

  private long insertNotification(long userId, String message) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(
                    """
                    INSERT INTO notifications (user_id, message, notification_type, is_read)
                    VALUES (:userId, :message, 'TEST', false)
                    RETURNING id
                    """)
                .bind("userId", userId)
                .bind("message", message)
                .mapTo(Long.class)
                .one());
  }

  private static JsonNode findNotification(String body, long notificationId) throws IOException {
    for (JsonNode notification : MAPPER.readTree(body)) {
      if (notification.get("id").asLong() == notificationId) {
        return notification;
      }
    }
    throw new AssertionError("Notification not found in response: " + notificationId);
  }

  private static int findFreePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  private static HttpResponse<String> send(String method, String path, String token)
      throws IOException, InterruptedException {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(10))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json");

    if ("GET".equals(method)) {
      builder.GET();
    } else {
      builder.method(method, HttpRequest.BodyPublishers.noBody());
    }

    return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }
}
