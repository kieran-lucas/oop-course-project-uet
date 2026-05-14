package com.auction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.auction.config.DatabaseConfig;
import com.auction.dao.NotificationDao;
import com.auction.service.NotificationService;
import java.util.List;
import java.util.Map;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests verifying that notification read-state is persisted to the database and that
 * the server-side ownership guard prevents cross-user marking.
 *
 * <p>These tests exercise the exact SQL statements used by the App.java notification endpoints:
 *
 * <ul>
 *   <li>{@code PATCH /api/notifications/{id}/read} → {@code UPDATE ... WHERE id=? AND user_id=?}
 *   <li>{@code PATCH /api/notifications/mark-all-read} → {@code UPDATE ... WHERE user_id=? AND
 *       is_read=false}
 *   <li>{@code GET /api/notifications} → {@code SELECT ... WHERE user_id=?}
 * </ul>
 *
 * <p>Requires the embedded PostgreSQL started by {@link DatabaseConfig}. The suite is skipped
 * (ABORTED) when no database is available so it does not fail CI in offline environments.
 */
@DisplayName("Notification persistence — read-state survives server round-trip")
class NotificationPersistenceTest {

  private static Jdbi jdbi;
  private static NotificationService notificationService;

  private static final long USER_A_ID = 101L;
  private static final long USER_B_ID = 102L;

  @BeforeAll
  static void connectDb() {
    try {
      jdbi = DatabaseConfig.create();
      notificationService = new NotificationService(new NotificationDao(jdbi));
    } catch (Exception e) {
      Assumptions.abort("No DB available, skipping: " + e.getMessage());
    }
  }

  @BeforeEach
  void seed() {
    jdbi.useHandle(
        handle -> {
          handle.execute("TRUNCATE notifications CASCADE");
          handle.execute("DELETE FROM users WHERE id IN (101, 102)");

          handle.execute(
              """
              INSERT INTO users (id, username, password_hash, email, role, created_at, balance)
              VALUES (101, 'user_a', 'hash', 'a@test.com', 'BIDDER', NOW(), 0),
                     (102, 'user_b', 'hash', 'b@test.com', 'BIDDER', NOW(), 0)
              """);
        });
  }

  // ── helpers ──────────────────────────────────────────────────────────

  private long insertNotification(long userId, String message) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(
                    """
                    INSERT INTO notifications (user_id, message, notification_type, is_read)
                    VALUES (:userId, :message, 'OUTBID', false)
                    RETURNING id
                    """)
                .bind("userId", userId)
                .bind("message", message)
                .mapTo(Long.class)
                .one());
  }

  private boolean isRead(long notificationId) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery("SELECT is_read FROM notifications WHERE id = :id")
                .bind("id", notificationId)
                .mapTo(Boolean.class)
                .one());
  }

  private long countUnread(long userId) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(
                    "SELECT COUNT(*) FROM notifications WHERE user_id = :uid AND is_read = false")
                .bind("uid", userId)
                .mapTo(Long.class)
                .one());
  }

  // ── tests ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("Mark single notification read → is_read=true persists in DB")
  void markSingleReadPersistsToDb() {
    long notifId = insertNotification(USER_A_ID, "Bạn bị vượt giá phiên #1");
    assertFalse(isRead(notifId), "Precondition: notification starts unread");

    notificationService.markRead(notifId, USER_A_ID);

    assertTrue(isRead(notifId), "is_read must be true after marking");
  }

  @Test
  @DisplayName("markAllRead → all user notifications are is_read=true in DB")
  void markAllReadPersistsAllToDb() {
    long n1 = insertNotification(USER_A_ID, "Thông báo 1");
    long n2 = insertNotification(USER_A_ID, "Thông báo 2");
    assertEquals(2L, countUnread(USER_A_ID), "Precondition: 2 unread");

    int updated = notificationService.markAllRead(USER_A_ID);

    assertEquals(2, updated, "Both notifications should be marked");
    assertTrue(isRead(n1), "Notification 1 must be read");
    assertTrue(isRead(n2), "Notification 2 must be read");
    assertEquals(0L, countUnread(USER_A_ID), "No unread notifications remain");
  }

  @Test
  @DisplayName("User B cannot mark User A's notification — WHERE user_id guard")
  void userBCannotMarkUserANotification() {
    long notifId = insertNotification(USER_A_ID, "Riêng của A");
    assertFalse(isRead(notifId), "Precondition: notification is unread");

    notificationService.markRead(notifId, USER_B_ID); // wrong user

    assertFalse(isRead(notifId), "Notification must remain unread");
  }

  @Test
  @DisplayName("Refetch after markAllRead returns zero unread — logout/login regression")
  void refetchAfterMarkAllReadReturnsZeroUnread() {
    insertNotification(USER_A_ID, "Old notification 1");
    insertNotification(USER_A_ID, "Old notification 2");
    assertEquals(2L, countUnread(USER_A_ID));

    // Simulate PATCH /api/notifications/mark-all-read
    notificationService.markAllRead(USER_A_ID);

    // Simulate GET /api/notifications filtering unread (client only adds is_read=false)
    long unreadAfterRefetch = countUnread(USER_A_ID);
    assertEquals(
        0L,
        unreadAfterRefetch,
        "After mark-all-read, no unread notifications should appear on next login");
  }

  @Test
  @DisplayName("markAllRead for User A does not affect User B's notifications")
  void markAllReadIsolatedToUser() {
    long notifA = insertNotification(USER_A_ID, "Của A");
    long notifB = insertNotification(USER_B_ID, "Của B");

    // User A marks all read
    notificationService.markAllRead(USER_A_ID);

    assertTrue(isRead(notifA), "User A's notification should be read");
    assertFalse(isRead(notifB), "User B's notification must remain unread");
  }

  @Test
  @DisplayName("GET notifications returns only current user's latest notifications")
  void listNotificationsIsolatedToCurrentUser() {
    insertNotification(USER_A_ID, "A newest");
    insertNotification(USER_A_ID, "A older");
    insertNotification(USER_B_ID, "B private");

    List<Map<String, Object>> notifications = notificationService.getRecentNotifications(USER_A_ID);

    assertEquals(2, notifications.size(), "User A should only see User A notifications");
    assertTrue(
        notifications.stream().allMatch(row -> row.containsKey("notification_type")),
        "Response shape keeps notification_type key");
    assertTrue(
        notifications.stream().noneMatch(row -> "B private".equals(row.get("message"))),
        "User A must not see User B notifications");
  }
}
