package com.auction.pattern.factory;

import com.auction.model.Admin;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import com.auction.model.User;

/**
 * Factory for instantiating User subclasses by role string. Centralises the BIDDER/SELLER/ADMIN
 * branching that was previously scattered.
 */
public final class UserFactory {
  private UserFactory() {}

  public static User create(String role) {
    return switch (role.toUpperCase()) {
      case "BIDDER" -> new Bidder();
      case "SELLER" -> new Seller();
      case "ADMIN" -> new Admin();
      default -> throw new IllegalArgumentException("Unknown role: " + role);
    };
  }
}
