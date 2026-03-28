package com.auction.model;

import java.time.LocalDateTime;

/**
 * Lớp trừu tượng cho sản phẩm đấu giá.
 *
 * <p>Ba loại sản phẩm: Electronics, Art, Vehicle.
 * Phần chung (tên, mô tả, ai bán) nằm ở đây.
 * Phần riêng (brand cho electronics, artist cho art, year cho vehicle) ở subclass.
 *
 * <p>Tương tự User, Item cũng có abstract method getCategory()
 * để polymorphism quyết định loại sản phẩm.
 */
public abstract class Item extends Entity {

  private String name;
  private String description;
  private Long sellerId; // foreign key → bảng users

  protected Item() {}

  protected Item(String name, String description, Long sellerId) {
    super();
    this.name = name;
    this.description = description;
    this.sellerId = sellerId;
  }

  protected Item(Long id, String name, String description, Long sellerId,
      LocalDateTime createdAt) {
    super(id, createdAt);
    this.name = name;
    this.description = description;
    this.sellerId = sellerId;
  }

  /**
   * Trả về loại sản phẩm: "ELECTRONICS", "ART", hoặc "VEHICLE".
   *
   * <p>POLYMORPHISM: mỗi subclass override → ItemFactory dùng giá trị này
   * để quyết định tạo class nào. Database cột category lưu giá trị này.
   */
  public abstract String getCategory();

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

  @Override
  public String toString() {
    return getCategory() + "{name='" + name + "', seller=" + sellerId + "}";
  }
}
