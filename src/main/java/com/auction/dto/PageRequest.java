package com.auction.dto;

public record PageRequest(int page, int size) {
  public int offset() {
    return page * size;
  }

  public static PageRequest of(int page, int size) {
    return new PageRequest(page, size);
  }
}
