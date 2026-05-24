package com.auction.model;

/**
 * Enum an toàn kiểu (type-safe) đại diện cho trạng thái của phiên đấu giá, thay thế cho chuỗi ký tự
 * thuần (stringly-typed status).
 *
 * <p>Vòng đời trạng thái chuẩn:
 *
 * <pre>
 *   OPEN → RUNNING → SETTLING → FINISHED → PAID
 *                           ↘ CANCELED
 * </pre>
 *
 * <ul>
 *   <li>{@code OPEN} — phiên đã được tạo nhưng chưa đến giờ bắt đầu.
 *   <li>{@code RUNNING} — phiên đang diễn ra, chấp nhận bid mới.
 *   <li>{@code SETTLING} — khóa tạm thời trong quá trình xử lý kết quả (tránh race condition).
 *   <li>{@code FINISHED} — phiên đã kết thúc, đang chờ thanh toán.
 *   <li>{@code PAID} — phiên đã được thanh toán hoàn tất (trạng thái cuối tích cực).
 *   <li>{@code CANCELED} — phiên bị hủy bởi seller hoặc admin (trạng thái cuối tiêu cực).
 * </ul>
 */
public enum AuctionStatus {
  OPEN,
  RUNNING,
  SETTLING,
  FINISHED,
  PAID,
  CANCELED;

  /**
   * Phân tích chuỗi từ cột DB về enum — không phân biệt hoa thường.
   *
   * <p>Dùng bởi {@link com.auction.dao.AuctionDao.AuctionMapper} khi đọc cột {@code status} từ
   * ResultSet. Nên gọi {@code s.trim()} trước nếu dữ liệu có thể chứa khoảng trắng.
   *
   * @param s chuỗi trạng thái từ DB (ví dụ: "OPEN", "running", "Finished")
   * @return giá trị enum tương ứng
   * @throws IllegalArgumentException nếu {@code s} không khớp bất kỳ giá trị nào
   */
  public static AuctionStatus from(String s) {
    return valueOf(s.toUpperCase());
  }
}
