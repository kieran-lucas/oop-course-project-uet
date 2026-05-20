package com.auction.model;

import java.time.LocalDateTime;

/** Item subtype for vehicles. */
public class Vehicle extends Item {

  private Integer year;

  public Vehicle() {}

  public Vehicle(String name, String description, Long sellerId, Integer year) {
    super(name, description, sellerId);
    this.year = year;
  }

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

  @Override
  public String getCategory() {
    return "VEHICLE";
  }

  public Integer getYear() {
    return year;
  }

  public void setYear(Integer year) {
    this.year = year;
  }
}
