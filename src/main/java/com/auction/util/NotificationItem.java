package com.auction.util;

import java.time.LocalDateTime;

/**
 * Value object bất biến đại diện cho một thông báo trong store phía client (JavaFX).
 *
 * <p>Có hai nguồn gốc của {@code NotificationItem}:
 *
 * <ul>
 *   <li><b>Server-loaded</b> — tải từ API {@code GET /api/notifications}, luôn có {@code id} và
 *       {@code createdAt} từ DB, phản ánh lịch sử thông báo kể cả khi client offline.
 *   <li><b>Client-only</b> — tạo bởi {@link #clientOnly(String)} khi nhận sự kiện WebSocket
 *       real-time. Không có {@code id} ({@code null}) vì chưa được persist lên server. Chỉ tồn tại
 *       trong phiên làm việc hiện tại — biến mất khi khởi động lại ứng dụng.
 * </ul>
 *
 * <p>Trường {@code read} là trường duy nhất có thể thay đổi sau khi khởi tạo — được flip sang
 * {@code true} khi store nhận xác nhận từ server rằng thông báo đã được đánh dấu đọc.
 */
public class NotificationItem {

  private final Long id;
  private final String message;
  private final String type;

  /** Trạng thái đọc — trường duy nhất có thể thay đổi sau khi khởi tạo. */
  private boolean read;

  private final LocalDateTime createdAt;

  /**
   * Tạo NotificationItem với đầy đủ thông tin.
   *
   * @param id ID từ DB (null cho thông báo client-only)
   * @param message nội dung thông báo (có thể chứa guillemet và dấu ngoặc vuông)
   * @param type loại thông báo (ví dụ: "BID_PLACED", "AUCTION_ENDED", "AUCTION_CANCELED")
   * @param read {@code true} nếu người dùng đã đọc
   * @param createdAt thời điểm tạo (từ DB hoặc {@code LocalDateTime.now()} cho client-only)
   */
  public NotificationItem(
      Long id, String message, String type, boolean read, LocalDateTime createdAt) {
    this.id = id;
    this.message = message;
    this.type = type;
    this.read = read;
    this.createdAt = createdAt;
  }

  /**
   * Tạo thông báo client-only từ sự kiện WebSocket — chưa persist lên server.
   *
   * <p>Thông báo này chỉ hiển thị cho người dùng trong phiên làm việc hiện tại. Khi ứng dụng khởi
   * động lại, chỉ những thông báo đã được persist trên server mới được tải lại.
   *
   * @param message nội dung thông báo real-time
   * @return NotificationItem với {@code id = null}, {@code read = false}, {@code createdAt = now}
   */
  public static NotificationItem clientOnly(String message) {
    return new NotificationItem(null, message, null, false, LocalDateTime.now());
  }

  /**
   * Trả về ID thông báo từ DB, hoặc {@code null} nếu là thông báo client-only.
   *
   * @return ID thông báo
   */
  public Long getId() {
    return id;
  }

  /** Trả về nội dung thông báo. */
  public String getMessage() {
    return message;
  }

  /**
   * Trả về loại thông báo (ví dụ: {@code "BID_PLACED"}, {@code "AUCTION_CANCELED"}).
   *
   * @return loại thông báo, có thể {@code null} cho thông báo client-only
   */
  public String getType() {
    return type;
  }

  /**
   * Kiểm tra thông báo đã được đọc chưa.
   *
   * @return {@code true} nếu đã đọc
   */
  public boolean isRead() {
    return read;
  }

  /**
   * Cập nhật trạng thái đọc của thông báo.
   *
   * <p>Được gọi khi store nhận xác nhận từ server ({@code PATCH /api/notifications/:id/read}).
   *
   * @param read {@code true} để đánh dấu đã đọc
   */
  public void setRead(boolean read) {
    this.read = read;
  }

  /** Trả về thời điểm tạo thông báo. */
  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
