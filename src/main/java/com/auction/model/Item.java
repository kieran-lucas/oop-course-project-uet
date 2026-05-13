package com.auction.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

/**
 * Lớp đại diện cho một sản phẩm có thể tham gia đấu giá.
 *
 * <p>Đã được làm phẳng (flattened) từ cấu trúc kế thừa cũ để đơn giản hóa việc quản lý và lưu trữ.
 * Thay vì có các lớp con Electronics, Art, Vehicle, tất cả thông tin giờ đây nằm trong class Item.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Item extends Entity {

  private String name;
  private String description;
  private Long sellerId; // khóa ngoại tham chiếu đến bảng users
  private String category; // "ELECTRONICS", "ART", "VEHICLE"

  // Các trường đặc thù (có thể null tùy vào category)
  private String brand; // dùng cho ELECTRONICS
  private String artist; // dùng cho ART
  private Integer year; // dùng cho VEHICLE

  /** Constructor mặc định — phục vụ framework/JDBI khi tạo object */
  public Item() {
    super();
  }

  /** Khởi tạo một sản phẩm mới chưa được lưu vào DB */
  public Item(String name, String description, Long sellerId, String category) {
    super();
    this.name = name;
    this.description = description;
    this.sellerId = sellerId;
    this.category = category;
  }

  /** Khởi tạo một sản phẩm từ bản ghi đã tồn tại trong DB */
  public Item(
      Long id,
      String name,
      String description,
      Long sellerId,
      String category,
      LocalDateTime createdAt) {
    super(id, createdAt);
    this.name = name;
    this.description = description;
    this.sellerId = sellerId;
    this.category = category;
  }

  // === Getters & Setters ===

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Long getSellerId() {
    return sellerId;
  }

  public void setSellerId(Long sellerId) {
    this.sellerId = sellerId;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getBrand() {
    return brand;
  }

  public void setBrand(String brand) {
    this.brand = brand;
  }

  public String getArtist() {
    return artist;
  }

  public void setArtist(String artist) {
    this.artist = artist;
  }

  public Integer getYear() {
    return year;
  }

  public void setYear(Integer year) {
    this.year = year;
  }

  @Override
  public String toString() {
    return "Item{name='" + name + "', category='" + category + "', seller=" + sellerId + "}";
  }
}
