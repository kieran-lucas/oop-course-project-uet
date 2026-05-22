package com.auction.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.auction.dao.NotificationDao;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService — read-state operations")
class NotificationServiceTest {

  @Mock private NotificationDao notificationDao;

  private NotificationService service;

  @BeforeEach
  void setUp() {
    service = new NotificationService(notificationDao);
  }

  @Test
  @DisplayName("getRecentNotifications() delegates to DAO")
  void getRecentDelegatesToDao() {
    List<Map<String, Object>> notifications =
        List.of(
            Map.of("id", 1L, "message", "You were outbid", "created_at", LocalDateTime.now()),
            Map.of("id", 2L, "message", "Auction ended", "created_at", LocalDateTime.now()));
    when(notificationDao.findRecentByUserId(42L)).thenReturn(notifications);

    List<Map<String, Object>> result = service.getRecentNotifications(42L);

    assertEquals(2, result.size());
    verify(notificationDao).findRecentByUserId(42L);
  }

  @Test
  @DisplayName("getRecentNotifications() returns empty list when none exist")
  void getRecentReturnsEmptyList() {
    when(notificationDao.findRecentByUserId(1L)).thenReturn(List.of());

    assertTrue(service.getRecentNotifications(1L).isEmpty());
  }

  @Test
  @DisplayName("markRead() delegates to DAO with correct arguments")
  void markReadDelegatesToDao() {
    service.markRead(7L, 42L);

    verify(notificationDao).markRead(7L, 42L);
  }

  @Test
  @DisplayName("markAllRead() returns count from DAO")
  void markAllReadReturnsDaoCount() {
    when(notificationDao.markAllRead(42L)).thenReturn(5);

    int count = service.markAllRead(42L);

    assertEquals(5, count);
    verify(notificationDao).markAllRead(42L);
  }

  @Test
  @DisplayName("markAllRead() returns 0 when no unread notifications exist")
  void markAllReadReturnsZeroWhenNoneUnread() {
    when(notificationDao.markAllRead(42L)).thenReturn(0);

    assertEquals(0, service.markAllRead(42L));
  }
}
