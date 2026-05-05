package com.auction.exception;

/**
 * Thrown when a user attempts an operation without sufficient permissions:
 *
 * <ul>
 *   <li>A {@code BIDDER} accessing endpoints reserved for {@code SELLER} or {@code ADMIN}</li>
 *   <li>A seller attempting to modify an auction owned by another seller</li>
 *   <li>An unauthenticated user accessing protected resources</li>
 *   <li>Token validation failure (expired, invalid signature)</li>
 * </ul>
 *
 * <p>Typical usage:
 * <pre>{@code
 * if (!item.getSellerId().equals(userId)) {
 *     throw new UnauthorizedException("You can only edit your own items");
 * }
 * }</pre>
 *
 * @see AuctionException
 */
public class UnauthorizedException extends AuctionException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new UnauthorizedException with the specified message.
     *
     * @param message description of the permission violation
     */
    public UnauthorizedException(String message) {
        super(message);
    }

    /**
     * Constructs a new UnauthorizedException with the specified message and cause.
     *
     * @param message description of the permission violation
     * @param cause   the underlying exception
     */
    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
