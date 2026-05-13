package com.auction.model;

/** Type-safe replacement for stringly-typed auction status. */
public enum AuctionStatus {
  OPEN,
  RUNNING,
  SETTLING,
  FINISHED,
  PAID,
  CANCELED;

  /** Parse from a DB column string — case-insensitive. */
  public static AuctionStatus from(String s) {
    return valueOf(s.toUpperCase());
  }
}
