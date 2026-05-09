package com.auction.model;

import java.time.LocalDateTime;

/**
 * Sản phẩm điện tử — có thêm thông tin thương hiệu
 *
 * <p>Ví dụ: iPhone 15 (brand = "Apple"). Field brand là thông tin riêng chỉ Electronics mới có —
 * Art không có brand, Vehicle không có brand (có year thay thế).
 *
 * <p>Đây minh họa tại sao cần kế thừa: nếu gộp tất cả vào 1 class Item với brand + artist + year,
 * thì Item "Mona Lisa" sẽ có brand = null, year = null — rất lãng phí và dễ nhầm lẫn
 */
public class Electronics extends Item {

  private String brand;

  public Electronics() {}

  public Electronics(String name, String description, Long sellerId, String brand) {
    super(name, description, sellerId);
    this.brand = brand;
  }

  public Electronics(
      Long id,
      String name,
      String description,
      Long sellerId,
      String brand,
      LocalDateTime createdAt) {
    super(id, name, description, sellerId, createdAt);
    this.brand = brand;
  }

  @Override
  public String getCategory() {
    return "ELECTRONICS";
  }

  public String getBrand() {
    return brand;
  }

  public void setBrand(String brand) {
    this.brand = brand;
  }
}
