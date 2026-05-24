package com.auction.model;

import java.time.LocalDateTime;

/**
 * Lớp con của {@link Item} đại diện cho sản phẩm là phương tiện giao thông.
 *
 * <p>Trường đặc thù: {@code year} — năm sản xuất của phương tiện (ví dụ: 2020, 2023). Trường này
 * tương ứng với cột {@code year} trong bảng {@code items} (NULL cho ART và ELECTRONICS, cũng có thể
 * NULL cho Vehicle nếu người bán không biết năm sản xuất).
 *
 * <p>Phương thức {@link #getCategory()} luôn trả về {@code "VEHICLE"} — đây là discriminator value
 * được {@link com.auction.dao.ItemDao.ItemMapper} và Jackson dùng để phân biệt loại sản phẩm.
 */
public class Vehicle extends Item {

  /**
   * Năm sản xuất của phương tiện. Dùng {@code Integer} thay vì {@code int} để hỗ trợ giá trị null
   * khi không rõ năm sản xuất.
   */
  private Integer year;

  /** Constructor mặc định — dùng bởi framework (Jackson deserialization). */
  public Vehicle() {}

  /**
   * Tạo phương tiện mới chưa được lưu vào DB (chưa có id và createdAt).
   *
   * @param name tên phương tiện (ví dụ: "Toyota Camry 2023")
   * @param description mô tả chi tiết
   * @param sellerId ID người bán sở hữu phương tiện
   * @param year năm sản xuất (có thể null)
   */
  public Vehicle(String name, String description, Long sellerId, Integer year) {
    super(name, description, sellerId);
    this.year = year;
  }

  /**
   * Tái tạo phương tiện từ một hàng trong DB (đã có id và createdAt). Dùng bởi {@link
   * com.auction.dao.ItemDao.ItemMapper}.
   *
   * @param id khóa chính từ DB
   * @param name tên phương tiện
   * @param description mô tả chi tiết
   * @param sellerId ID người bán
   * @param status trạng thái hiện tại (AVAILABLE, IN_AUCTION, SOLD, REMOVED)
   * @param year năm sản xuất — null nếu DB lưu NULL
   * @param createdAt thời điểm tạo từ DB
   */
  public Vehicle(
      Long id,
      String name,
      String description,
      Long sellerId,
      String status,
      Integer year,
      LocalDateTime createdAt) {
    super(id, name, description, sellerId, status, createdAt);
    this.year = year;
  }

  /**
   * Trả về discriminator category của loại sản phẩm này.
   *
   * @return {@code "VEHICLE"} — hằng số dùng trong DB và Jackson polymorphism
   */
  @Override
  public String getCategory() {
    return "VEHICLE";
  }

  /** Lấy năm sản xuất của phương tiện (có thể null). */
  public Integer getYear() {
    return year;
  }

  /** Cập nhật năm sản xuất của phương tiện. */
  public void setYear(Integer year) {
    this.year = year;
  }
}
