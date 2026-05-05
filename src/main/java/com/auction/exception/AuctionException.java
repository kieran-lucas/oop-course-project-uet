package com.auction.exception;

/**
 * Base class for all custom exceptions in the auction domain.
 *
 * <p>All domain-specific exceptions ({@link InvalidBidException},
 * {@link AuctionClosedException}, {@link NotFoundException},
 * {@link DuplicateException}, {@link UnauthorizedException}) extend this class
 * to allow callers to catch all auction-related exceptions with a single catch block:
 *
 * <pre>{@code
 * try {
 *     auctionService.placeBid(bid);
 * } catch (AuctionException e) {
 *     // handles all custom exceptions in com.auction.exception
 *     log.error("Auction operation failed: {}", e.getMessage(), e);
 *     return errorResponse(e);
 * }
 * }</pre>
 *
 * <p>This class is {@code abstract} — instantiate one of its concrete subclasses instead.
 *
 * <p>Extending {@link RuntimeException} (rather than checked {@link Exception}) is intentional:
 * auction errors typically indicate violated business rules or programming errors that callers
 * cannot reasonably recover from at every call site, so forcing {@code throws} declarations
 * everywhere would add noise without value.
 */
public abstract class AuctionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new AuctionException with the specified detail message.
     *
     * @param message the detail message; should describe the business rule violated
     */
    protected AuctionException(String message) {
        super(message);
    }

    /**
     * Constructs a new AuctionException with the specified detail message and cause.
     *
     * @param message the detail message; should describe the business rule violated
     * @param cause   the underlying exception that triggered this one (e.g., SQLException)
     */
    protected AuctionException(String message, Throwable cause) {
        super(message, cause);
    }
    /**
     * Returns a string representation suitable for logging:
     * {@code [SimpleClassName] message}.
     *
     * @return formatted exception representation
     */
    @Override
    public String toString() {
        return "[" + getClass().getSimpleName() + "] "
            + (getMessage() == null ? "" : getMessage());
    }
}
