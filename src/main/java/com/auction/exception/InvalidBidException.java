package com.auction.exception;

/**
 * Thrown when a bid violates business rules:
 *
 * <ul>
 *   <li>Amount is non-positive (≤ 0)
 *   <li>Amount is lower than or equal to current price
 *   <li>Bidder is the seller of the item (self-bidding)
 *   <li>Bid increment is below the minimum allowed
 * </ul>
 *
 * <p>Typical usage:
 *
 * <pre>{@code
 * if (amount.compareTo(auction.getCurrentPrice()) <= 0) {
 *     throw new InvalidBidException(
 *         "Bid " + amount + " must exceed current price " + auction.getCurrentPrice());
 * }
 * }</pre>
 *
 * @see AuctionException
 */
public class InvalidBidException extends AuctionException {

  private static final long serialVersionUID = 1L;

  /**
   * Constructs a new InvalidBidException with the specified message.
   *
   * @param message description of why the bid is invalid
   */
  public InvalidBidException(String message) {
    super(message);
  }

  /**
   * Constructs a new InvalidBidException with the specified message and cause.
   *
   * @param message description of why the bid is invalid
   * @param cause the underlying exception
   */
  public InvalidBidException(String message, Throwable cause) {
    super(message, cause);
  }
}
