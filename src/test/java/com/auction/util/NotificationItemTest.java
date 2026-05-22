package com.auction.util;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("NotificationItem")
class NotificationItemTest {

  @Test
  @DisplayName("stores all fields supplied to the constructor")
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
  @DisplayName("read flag is mutable via setRead()")
  void readFlagMutable() {
    NotificationItem item = new NotificationItem(1L, "msg", "T", false, LocalDateTime.now());

    item.setRead(true);

    assertTrue(item.isRead());
  }

  @Test
  @DisplayName("clientOnly() factory produces unread item with null id and current timestamp")
  void clientOnlyFactory() {
    LocalDateTime before = LocalDateTime.now();
    NotificationItem item = NotificationItem.clientOnly("Bạn bị vượt giá");
    LocalDateTime after = LocalDateTime.now();

    assertNull(item.getId());
    assertEquals("Bạn bị vượt giá", item.getMessage());
    assertNull(item.getType());
    assertFalse(item.isRead());
    assertFalse(item.getCreatedAt().isBefore(before));
    assertFalse(item.getCreatedAt().isAfter(after));
  }
}
