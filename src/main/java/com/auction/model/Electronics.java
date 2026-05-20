package com.auction.model;

import java.time.LocalDateTime;

/** Item subtype for electronics. */
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
      String status,
      String brand,
      LocalDateTime createdAt) {
    super(id, name, description, sellerId, status, createdAt);
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
