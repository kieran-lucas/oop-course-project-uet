package com.auction.exception;

/**
 * Thrown when an operation is attempted on an auction that is not in the {@code RUNNING} state.
 *
 * <p>This includes auctions that are {@code OPEN} (not yet started),
 * {@code FINISHED} (already ended), {@code CANCELED}, or {@code PAID}.
 *
 * <p>Typical usage:
 * <pre>{@code
 * if (!"RUNNING".equals(auction.getStatus())) {
 *     throw new AuctionClosedException(
 *         "Cannot place bid on auction in state: " + auction.getStatus());
 * }
 * }</pre>
 *
 * @see AuctionException
 */
public class AuctionClosedException extends AuctionException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new AuctionClosedException with the specified message.
     *
     * @param message description of the auction state and the attempted operation
     */
    public AuctionClosedException(String message) {
        super(message);
    }

    /**
     * Constructs a new AuctionClosedException with the specified message and cause.
     *
     * @param message description of the auction state and the attempted operation
     * @param cause   the underlying exception
     */
    public AuctionClosedException(String message, Throwable cause) {
        super(message, cause);
    }
}
