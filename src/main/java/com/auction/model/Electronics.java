package com.auction.model;

import java.time.LocalDateTime;

/**
 * Lớp con của {@link Item} đại diện cho sản phẩm thuộc danh mục điện tử.
 *
 * <p>Trường đặc thù: {@code brand} — thương hiệu/hãng sản xuất (ví dụ: Apple, Samsung, Dell).
 * Trường này tương ứng với cột {@code brand} trong bảng {@code items} (NULL cho ART và VEHICLE).
 *
 * <p>Phương thức {@link #getCategory()} luôn trả về {@code "ELECTRONICS"} — đây là discriminator
 * value được {@link com.auction.dao.ItemDao.ItemMapper} và Jackson dùng để phân biệt loại sản phẩm.
 */
public class Electronics extends Item {

  /** Thương hiệu/hãng sản xuất của sản phẩm điện tử. */
  private String brand;

  /** Constructor mặc định — dùng bởi framework (Jackson deserialization). */
  public Electronics() {}

  /**
   * Tạo sản phẩm điện tử mới chưa được lưu vào DB (chưa có id và createdAt).
   *
   * @param name tên sản phẩm
   * @param description mô tả chi tiết
   * @param sellerId ID người bán sở hữu sản phẩm
   * @param brand thương hiệu/hãng sản xuất
   */
  public Electronics(String name, String description, Long sellerId, String brand) {
    super(name, description, sellerId);
    this.brand = brand;
  }

  /**
   * Tái tạo sản phẩm điện tử từ một hàng trong DB (đã có id và createdAt). Dùng bởi {@link
   * com.auction.dao.ItemDao.ItemMapper}.
   *
   * @param id khóa chính từ DB
   * @param name tên sản phẩm
   * @param description mô tả chi tiết
   * @param sellerId ID người bán
   * @param status trạng thái hiện tại (AVAILABLE, IN_AUCTION, SOLD, REMOVED)
   * @param brand thương hiệu/hãng sản xuất
   * @param createdAt thời điểm tạo từ DB
   */
  public Electronics(
      Long id,
      String name,
      String description,
      Long sellerId,
      String status,
      String brand,
      LocalDateTime createdAt) {
    super(id, name, description, sellerId, status, createdAt);
    this.brand = brand;
  }

  /**
   * Trả về discriminator category của loại sản phẩm này.
   *
   * @return {@code "ELECTRONICS"} — hằng số dùng trong DB và Jackson polymorphism
   */
  @Override
  public String getCategory() {
    return "ELECTRONICS";
  }

  /** Lấy thương hiệu/hãng sản xuất của sản phẩm. */
  public String getBrand() {
    return brand;
  }

  /** Cập nhật thương hiệu/hãng sản xuất của sản phẩm. */
  public void setBrand(String brand) {
    this.brand = brand;
  }
}
