package com.auction.model;

import java.time.LocalDateTime;

/**
 * Lớp con của {@link Item} đại diện cho sản phẩm là tác phẩm nghệ thuật.
 *
 * <p>Trường đặc thù: {@code artist} — tên nghệ sĩ/tác giả sáng tác. Trường này tương ứng với cột
 * {@code artist} trong bảng {@code items} (NULL cho ELECTRONICS và VEHICLE).
 *
 * <p>Phương thức {@link #getCategory()} luôn trả về {@code "ART"} — đây là discriminator value được
 * {@link com.auction.dao.ItemDao.ItemMapper} dùng để phân biệt loại sản phẩm khi đọc từ DB.
 */
public class Art extends Item {

  /** Tên nghệ sĩ hoặc tác giả của tác phẩm. */
  private String artist;

  /** Constructor mặc định — dùng bởi framework (Jackson deserialization). */
  public Art() {}

  /**
   * Tạo tác phẩm nghệ thuật mới chưa được lưu vào DB (chưa có id và createdAt).
   *
   * @param name tên tác phẩm
   * @param description mô tả chi tiết
   * @param sellerId ID người bán sở hữu tác phẩm
   * @param artist tên nghệ sĩ/tác giả
   */
  public Art(String name, String description, Long sellerId, String artist) {
    super(name, description, sellerId);
    this.artist = artist;
  }

  /**
   * Tái tạo tác phẩm nghệ thuật từ một hàng trong DB (đã có id và createdAt). Dùng bởi {@link
   * com.auction.dao.ItemDao.ItemMapper}.
   *
   * @param id khóa chính từ DB
   * @param name tên tác phẩm
   * @param description mô tả chi tiết
   * @param sellerId ID người bán
   * @param status trạng thái hiện tại (AVAILABLE, IN_AUCTION, SOLD, REMOVED)
   * @param artist tên nghệ sĩ/tác giả
   * @param createdAt thời điểm tạo từ DB
   */
  public Art(
      Long id,
      String name,
      String description,
      Long sellerId,
      String status,
      String artist,
      LocalDateTime createdAt) {
    super(id, name, description, sellerId, status, createdAt);
    this.artist = artist;
  }

  /**
   * Trả về discriminator category của loại sản phẩm này.
   *
   * @return {@code "ART"} — hằng số dùng trong DB và Jackson polymorphism
   */
  @Override
  public String getCategory() {
    return "ART";
  }

  /** Lấy tên nghệ sĩ/tác giả của tác phẩm. */
  public String getArtist() {
    return artist;
  }

  /** Cập nhật tên nghệ sĩ/tác giả của tác phẩm. */
  public void setArtist(String artist) {
    this.artist = artist;
  }
}
