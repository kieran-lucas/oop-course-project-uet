package com.auction.model;

import java.time.LocalDateTime;

/**
 * Tác phẩm nghệ thuật — có thêm thông tin nghệ sĩ.
 *
 * <p>Ví dụ: "Starry Night" (artist = "Van Gogh").
 * Thông tin nghệ sĩ quan trọng với Art nhưng vô nghĩa với Electronics.
 */
public class Art extends Item {

  private String artist;

  public Art() {}

  public Art(String name, String description, Long sellerId, String artist) {
    super(name, description, sellerId);
    this.artist = artist;
  }

  public Art(Long id, String name, String description, Long sellerId, String artist,
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
