package com.auction.model;

import java.time.LocalDateTime;

/**
 * Phương tiện — có thêm thông tin năm sản xuất.
 *
 * <p>Ví dụ: "Toyota Camry 2022" (year = 2022). Năm sản xuất ảnh hưởng lớn đến giá trị xe nhưng
 * không áp dụng cho Art hay Electronics.
 */
public class Vehicle extends Item {

  private int year;

  public Vehicle() {}

  public Vehicle(String name, String description, Long sellerId, int year) {
    super(name, description, sellerId);
    this.year = year;
  }

  public Vehicle(
      Long id, String name, String description, Long sellerId, int year, LocalDateTime createdAt) {
    super(id, name, description, sellerId, createdAt);
    this.year = year;
  }

  @Override
  public String getCategory() {
    return "VEHICLE";
  }

  public int getYear() {
    return year;
  }

  public void setYear(int year) {
    this.year = year;
  }
}
