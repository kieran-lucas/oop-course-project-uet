package com.auction.model;

import java.time.LocalDateTime;

/** Item subtype for artwork. */
public class Art extends Item {

  private String artist;

  public Art() {}

  public Art(String name, String description, Long sellerId, String artist) {
    super(name, description, sellerId);
    this.artist = artist;
  }

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
