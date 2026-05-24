package com.auction.service;

import com.auction.dao.NotificationDao;
import java.util.List;
import java.util.Map;

/**
 * Service xử lý logic nghiệp vụ cho thông báo (notification).
 *
 * <p>Lớp này đóng vai trò tầng trung gian giữa {@code NotificationController} và {@link
 * NotificationDao}, cung cấp ba thao tác chính:
 *
 * <ul>
 *   <li>Lấy danh sách thông báo gần đây của người dùng.
 *   <li>Đánh dấu một thông báo cụ thể là đã đọc.
 *   <li>Đánh dấu toàn bộ thông báo chưa đọc của người dùng là đã đọc.
 * </ul>
 *
 * <p>Bảo mật: mọi thao tác đều gắn với {@code userId} từ JWT — không cho phép người dùng thao tác
 * trên thông báo của người khác (được thực thi ở tầng DAO thông qua ràng buộc {@code AND user_id =
 * ?} trong câu lệnh SQL).
 *
 * <p>Thông báo được ghi vào DB bởi {@link AuctionService}, {@code BidService} và các service khác.
 * Lớp này chỉ đọc và cập nhật trạng thái đọc — không tạo thông báo mới.
 */
public class NotificationService {

  private final NotificationDao notificationDao;

  /**
   * Tạo NotificationService với DAO thông báo.
   *
   * @param notificationDao DAO để truy cập bảng {@code notifications}
   */
  public NotificationService(NotificationDao notificationDao) {
    this.notificationDao = notificationDao;
  }

  /**
   * Lấy danh sách thông báo gần đây của người dùng (tối đa 50 bản ghi, mới nhất trước).
   *
   * @param userId ID người dùng cần lấy thông báo
   * @return danh sách thông báo dưới dạng Map với các key: id, message, type, is_read, created_at
   */
  public List<Map<String, Object>> getRecentNotifications(Long userId) {
    return notificationDao.findRecentByUserId(userId);
  }

  /**
   * Đánh dấu một thông báo là đã đọc.
   *
   * <p>DAO thực thi câu lệnh {@code WHERE id = ? AND user_id = ?} để đảm bảo chỉ chủ thông báo mới
   * có thể đánh dấu đọc — ngăn người dùng giả mạo ID thông báo của người khác.
   *
   * @param notificationId ID thông báo cần đánh dấu đã đọc
   * @param userId ID người dùng sở hữu thông báo (từ JWT)
   */
  public void markRead(Long notificationId, Long userId) {
    notificationDao.markRead(notificationId, userId);
  }

  /**
   * Đánh dấu tất cả thông báo chưa đọc của người dùng là đã đọc.
   *
   * <p>Thường được gọi khi người dùng mở panel thông báo lần đầu trong phiên làm việc, giúp xóa
   * badge đếm số thông báo chưa đọc.
   *
   * @param userId ID người dùng (từ JWT)
   * @return số lượng thông báo đã được cập nhật từ {@code is_read = false} sang {@code true}
   */
  public int markAllRead(Long userId) {
    return notificationDao.markAllRead(userId);
  }
}
