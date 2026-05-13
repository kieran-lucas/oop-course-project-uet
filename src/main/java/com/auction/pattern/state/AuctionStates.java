package com.auction.pattern.state;

/**
 * Singleton instances for all AuctionState implementations. Thread-safe because all State objects
 * are immutable and stateless.
 */
public final class AuctionStates {
  private AuctionStates() {}

  public static final AuctionState OPEN = new OpenState();
  public static final AuctionState RUNNING = new RunningState();
  public static final AuctionState FINISHED = new FinishedState();
  public static final AuctionState PAID = new PaidState();
  public static final AuctionState CANCELED = new CanceledState();
}
