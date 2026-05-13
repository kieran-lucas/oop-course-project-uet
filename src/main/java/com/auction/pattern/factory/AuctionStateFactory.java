package com.auction.pattern.factory;

import com.auction.pattern.state.AuctionState;
import com.auction.pattern.state.AuctionStates;

/**
 * Factory for obtaining AuctionState instances by status string. Delegates to AuctionStates
 * singletons — no new allocations.
 */
public final class AuctionStateFactory {
  private AuctionStateFactory() {}

  public static AuctionState create(String status) {
    return switch (status.toUpperCase()) {
      case "OPEN" -> AuctionStates.OPEN;
      case "RUNNING", "SETTLING" -> AuctionStates.RUNNING;
      case "FINISHED" -> AuctionStates.FINISHED;
      case "PAID" -> AuctionStates.PAID;
      case "CANCELED" -> AuctionStates.CANCELED;
      default -> throw new IllegalArgumentException("Unknown status: " + status);
    };
  }
}
