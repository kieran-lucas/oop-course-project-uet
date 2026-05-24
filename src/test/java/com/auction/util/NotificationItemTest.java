package com.auction.util;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử {@link NotificationItem} — value object đại diện cho một thông báo trong store client.
 *
 * <p>Các trường hợp kiểm thử:
 *
 * <ul>
 *   <li>Constructor đầy đủ lưu đúng tất cả trường.
 *   <li>Trường {@code read} có thể thay đổi qua {@code setRead()}.
 *   <li>Factory {@code clientOnly()} tạo thông báo không có id, chưa đọc, timestamp là now.
 * </ul>
 */
@DisplayName("NotificationItem")
class NotificationItemTest {

  @Test
  @DisplayName("constructor lưu đúng tất cả trường")
  void storesFields() {
    LocalDateTime now = LocalDateTime.now();
    NotificationItem item = new NotificationItem(10L, "Bid placed", "BID_UPDATE", false, now);

    assertEquals(10L, item.getId());
    assertEquals("Bid placed", item.getMessage());
    assertEquals("BID_UPDATE", item.getType());
    assertFalse(item.isRead());
    assertEquals(now, item.getCreatedAt());
  }

  @Test
  @DisplayName("trường read có thể thay đổi qua setRead()")
  void readFlagMutable() {
    NotificationItem item = new NotificationItem(1L, "msg", "T", false, LocalDateTime.now());

    item.setRead(true);

    assertTrue(item.isRead());
  }

  @Test
  @DisplayName("clientOnly() tạo thông báo chưa đọc, id null, timestamp là now")
  void clientOnlyFactory() {
    LocalDateTime before = LocalDateTime.now();
    NotificationItem item = NotificationItem.clientOnly("Bạn bị vượt giá");
    LocalDateTime after = LocalDateTime.now();

    assertNull(item.getId());
    assertEquals("Bạn bị vượt giá", item.getMessage());
    assertNull(item.getType());
    assertFalse(item.isRead());
    // createdAt phải nằm trong khoảng [before, after]
    assertFalse(item.getCreatedAt().isBefore(before));
    assertFalse(item.getCreatedAt().isAfter(after));
  }
}
