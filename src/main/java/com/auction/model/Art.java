package com.auction.model;

import java.time.LocalDateTime;

/**
 * Đại diện cho một tác phẩm nghệ thuật trong hệ thống đấu giá
 *
 * <p>Lớp này mở rộng {@link Item} bằng cách bổ sung thông tin về nghệ sĩ — một thuộc tính đặc thù
 * chỉ có ý nghĩa đối với các sản phẩm thuộc danh mục nghệ thuật (ví dụ: bức "Starry Night" của Van
 * Gogh). Các danh mục khác như Electronics sẽ không sử dụng trường này
 */
public class Art extends Item {

  private String artist;

  /** Hàm khởi tạo mặc định, phục vụ cho việc deserialization*/
  public Art() {}

  /**
   * Khởi tạo một tác phẩm nghệ thuật mới (chưa có ID, chưa có thời điểm tạo)
   *
   * @param name tên tác phẩm
   * @param description mô tả tác phẩm
   * @param sellerId ID của người bán
   * @param artist tên nghệ sĩ
   */
  public Art(String name, String description, Long sellerId, String artist) {
    super(name, description, sellerId);
    this.artist = artist;
  }

  /**
   * Khởi tạo một tác phẩm nghệ thuật đầy đủ thông tin (thường dùng khi đọc từ database)
   *
   * @param id ID của tác phẩm
   * @param name tên tác phẩm
   * @param description mô tả tác phẩm
   * @param sellerId ID của người bán
   * @param artist tên nghệ sĩ
   * @param createdAt thời điểm tạo bản ghi
   */
  public Art(
      Long id,
      String name,
      String description,
      Long sellerId,
      String artist,
      LocalDateTime createdAt) {
    super(id, name, description, sellerId, createdAt);
    this.artist = artist;
  }

  @Override
  public String getCategory() {
    return "ART";
  }

  public String getArtist() {
    return artist;
  }

  public void setArtist(String artist) {
    this.artist = artist;
  }
}
