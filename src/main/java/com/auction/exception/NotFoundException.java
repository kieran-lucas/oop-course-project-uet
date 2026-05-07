package com.auction.exception;

/**
 * Thrown when a requested entity (User, Item, Auction, Bid, ...) cannot be found in the underlying
 * data source.
 *
 * <p>Typical usage:
 *
 * <pre>{@code
 * Auction auction = auctionDao.findById(id)
 *     .orElseThrow(() -> new NotFoundException("Auction not found with id: " + id));
 * }</pre>
 *
 * @see AuctionException
 */
public class NotFoundException extends AuctionException {

  private static final long serialVersionUID = 1L;

  /**
   * Constructs a new NotFoundException with the specified message.
   *
   * @param message description of which entity was not found
   */
  public NotFoundException(String message) {
    super(message);
  }

  /**
   * Constructs a new NotFoundException with the specified message and cause.
   *
   * @param message description of which entity was not found
   * @param cause the underlying exception (e.g., SQLException)
   */
  public NotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
