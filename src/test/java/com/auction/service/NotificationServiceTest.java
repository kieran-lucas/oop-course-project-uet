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

/**
 * Unit test kiểm tra các thao tác đọc trạng thái notification của {@link NotificationService}.
 *
 * <p>Xác nhận rằng service ủy quyền đúng cho {@link NotificationDao} và truyền đúng tham số cho các
 * phương thức: lấy danh sách, đánh dấu đọc một notification, đánh dấu tất cả đã đọc.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService — thao tác trạng thái đọc notification")
class NotificationServiceTest {

  @Mock private NotificationDao notificationDao;

  private NotificationService service;

  @BeforeEach
  void setUp() {
    service = new NotificationService(notificationDao);
  }

  @Test
  @DisplayName("getRecentNotifications() ủy quyền cho DAO")
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
  @DisplayName("getRecentNotifications() trả về danh sách rỗng khi không có notification")
  void getRecentReturnsEmptyList() {
    when(notificationDao.findRecentByUserId(1L)).thenReturn(List.of());

    assertTrue(service.getRecentNotifications(1L).isEmpty());
  }

  @Test
  @DisplayName("markRead() ủy quyền cho DAO với đúng tham số")
  void markReadDelegatesToDao() {
    service.markRead(7L, 42L);

    verify(notificationDao).markRead(7L, 42L);
  }

  @Test
  @DisplayName("markAllRead() trả về số bản ghi từ DAO")
  void markAllReadReturnsDaoCount() {
    when(notificationDao.markAllRead(42L)).thenReturn(5);

    int count = service.markAllRead(42L);

    assertEquals(5, count);
    verify(notificationDao).markAllRead(42L);
  }

  @Test
  @DisplayName("markAllRead() trả về 0 khi không có notification chưa đọc")
  void markAllReadReturnsZeroWhenNoneUnread() {
    when(notificationDao.markAllRead(42L)).thenReturn(0);

    assertEquals(0, service.markAllRead(42L));
  }
}
