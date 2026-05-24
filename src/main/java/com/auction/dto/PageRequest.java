package com.auction.dto;

/**
 * Value object đại diện cho yêu cầu phân trang — trang hiện tại và kích thước mỗi trang.
 *
 * <p>Trang được đánh số bắt đầu từ 0 (0-based). Phương thức {@link #offset()} tính ra giá trị
 * OFFSET dùng trực tiếp trong câu lệnh SQL LIMIT/OFFSET.
 *
 * <p>Ví dụ: trang 2, kích thước 20 → offset = 40 → bỏ qua 40 bản ghi đầu, lấy 20 bản ghi tiếp.
 *
 * @param page số trang, bắt đầu từ 0
 * @param size số bản ghi trên mỗi trang
 */
public record PageRequest(int page, int size) {

  /**
   * Tính giá trị OFFSET tương ứng để dùng trong câu lệnh SQL.
   *
   * @return {@code page * size} — số bản ghi cần bỏ qua
   */
  public int offset() {
    return page * size;
  }

  /**
   * Factory method tiện lợi để tạo PageRequest — rõ ràng hơn so với gọi constructor trực tiếp.
   *
   * @param page số trang (0-based)
   * @param size kích thước trang
   * @return instance PageRequest mới
   */
  public static PageRequest of(int page, int size) {
    return new PageRequest(page, size);
  }
}
