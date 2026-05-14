package com.auction.dao;

import java.util.List;
import java.util.Map;
import org.jdbi.v3.core.Jdbi;

/** DAO for persisted user notifications. */
public class NotificationDao {

  private final Jdbi jdbi;

  public NotificationDao(Jdbi jdbi) {
    this.jdbi = jdbi;
  }

  public List<Map<String, Object>> findRecentByUserId(Long userId) {
    return jdbi.withHandle(
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
  }

  public int markRead(Long notificationId, Long userId) {
    return jdbi.withHandle(
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
  }

  public int markAllRead(Long userId) {
    return jdbi.withHandle(
        handle ->
            handle.execute(
                """
                UPDATE notifications
                SET is_read = true
                WHERE user_id = ?
                  AND is_read = false
                """,
                userId));
  }
}
