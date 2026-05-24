package com.auction.dao;

import java.util.List;
import java.util.Map;
import org.jdbi.v3.core.Jdbi;

/**
 * DAO quản lý bảng {@code notifications} — lưu trữ thông báo bền vững cho người dùng.
 *
 * <p>Thông báo được ghi vào bảng này để đảm bảo người dùng không bỏ sót khi offline. Khi họ online
 * trở lại, client gọi {@link #findRecentByUserId} để tải về 50 thông báo gần nhất.
 *
 * <p>Trạng thái đọc ({@code is_read}) chỉ được cập nhật qua {@link #markRead} và {@link
 * #markAllRead} — không bao giờ đặt lại về false sau khi đã đọc.
 */
public class NotificationDao {

  private final Jdbi jdbi;

  public NotificationDao(Jdbi jdbi) {
    this.jdbi = jdbi;
  }

  /**
   * Lấy 50 thông báo gần nhất của một người dùng, sắp xếp mới nhất trước.
   *
   * <p>Giới hạn 50 bản ghi để tránh tải quá nhiều dữ liệu khi người dùng có lịch sử dài. Kết quả
   * trả về dưới dạng {@code Map} để controller trực tiếp serialize thành JSON mà không cần thêm lớp
   * DTO.
   *
   * @param userId ID người dùng cần lấy thông báo
   * @return danh sách tối đa 50 thông báo, mỗi phần tử là một Map gồm: id, message,
   *     notification_type, is_read, created_at
   */
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

  /**
   * Đánh dấu một thông báo cụ thể là đã đọc.
   *
   * <p>Điều kiện {@code AND user_id = ?} ngăn người dùng đánh dấu đọc thông báo của người khác (bảo
   * vệ dữ liệu ngay ở tầng DB, không chỉ ở tầng service).
   *
   * @param notificationId ID thông báo cần đánh dấu
   * @param userId ID người dùng thực hiện thao tác — phải khớp với owner của thông báo
   * @return số hàng bị ảnh hưởng (0 nếu không tìm thấy hoặc không thuộc userId)
   */
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

  /**
   * Đánh dấu tất cả thông báo chưa đọc của một người dùng là đã đọc trong một lần gọi.
   *
   * <p>Điều kiện {@code is_read = false} giúp tránh UPDATE thừa trên các hàng đã đọc, cải thiện
   * hiệu năng khi người dùng có nhiều thông báo cũ.
   *
   * @param userId ID người dùng cần đánh dấu tất cả là đã đọc
   * @return số thông báo được cập nhật (= số thông báo chưa đọc trước đó)
   */
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
