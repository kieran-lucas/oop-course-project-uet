package com.auction.model;

/** Trạng thái vòng đời của một cấu hình auto-bid. */
public enum AutoBidStatus {
  ACTIVE,
  STOPPED,
  EXHAUSTED,
  FAILED;

  public static AutoBidStatus from(String value) {
    if (value == null || value.isBlank()) {
      return ACTIVE;
    }
    return AutoBidStatus.valueOf(value.trim().toUpperCase());
  }
}
