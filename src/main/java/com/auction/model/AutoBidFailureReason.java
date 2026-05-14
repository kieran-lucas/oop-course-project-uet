package com.auction.model;

/** Lý do auto-bid bị dừng hoặc thất bại. */
public enum AutoBidFailureReason {
  MAX_PRICE_TOO_LOW,
  INSUFFICIENT_BALANCE,
  AUCTION_NOT_RUNNING,
  BIDDER_ALREADY_HIGHEST,
  ACTIVE_AUTOBID_EXISTS;

  public static AutoBidFailureReason from(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return AutoBidFailureReason.valueOf(value.trim().toUpperCase());
  }
}
