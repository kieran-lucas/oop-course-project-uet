package com.auction.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Test suite verifying the contract of {@link InvalidBidException} — thrown when a bid fails
 * business-rule validation, for example when the bid amount is below the current price, below the
 * required increment, or negative.
 *
 * <p><b>Contract under test:</b>
 *
 * <ul>
 *   <li>Both constructors (message-only and message + cause) propagate their arguments correctly.
 *   <li>The class sits in the right place in the exception hierarchy: {@code InvalidBidException →
 *       AuctionException → RuntimeException}.
 *   <li>{@code toString()} includes both the class name and the message, which matters for log
 *       readability when the full stack trace is not printed.
 * </ul>
 *
 * <p>No external dependencies or DB connection required — all tests are pure unit tests that
 * instantiate the exception directly.
 */
class InvalidBidExceptionTest {

  /**
   * Verifies that the single-argument constructor stores the message and makes it retrievable via
   * {@link Throwable#getMessage()}.
   */
  @Test
  void shouldCarryMessage() {
    InvalidBidException ex = new InvalidBidException("bid too low");
    assertEquals("bid too low", ex.getMessage());
  }

  /**
   * Verifies the two-argument constructor: the cause must be the exact object passed in (identity
   * check via {@code assertSame}, not just equality), and the message must be preserved
   * independently of the cause.
   *
   * <p>Cause chaining is useful when {@link InvalidBidException} wraps a lower-level validation
   * exception (e.g., {@link IllegalArgumentException} from an input parser), preserving the
   * original context for debugging.
   */
  @Test
  void shouldChainCause() {
    Throwable root = new IllegalArgumentException("negative amount");
    InvalidBidException ex = new InvalidBidException("validation failed", root);
    assertSame(root, ex.getCause());
    assertEquals("validation failed", ex.getMessage());
  }

  /**
   * Verifies that {@link InvalidBidException} is a subtype of {@link AuctionException}, allowing
   * callers to catch all domain exceptions with a single {@code catch (AuctionException e)} block
   * without needing to enumerate each specific type.
   */
  @Test
  void shouldBeAnAuctionException() {
    InvalidBidException ex = new InvalidBidException("x");
    assertTrue(ex instanceof AuctionException);
  }

  /**
   * Verifies that {@link InvalidBidException} extends {@link RuntimeException}, meaning callers are
   * not forced to declare it in {@code throws} clauses. This is the intended design for domain
   * exceptions in this system.
   */
  @Test
  void shouldBeARuntimeException() {
    InvalidBidException ex = new InvalidBidException("x");
    assertTrue(ex instanceof RuntimeException);
  }

  /**
   * Verifies that {@code toString()} contains both the fully-qualified class name and the message.
   * This guards against accidental overrides of {@code toString()} that might drop either piece of
   * information, which would make log-only error reports harder to diagnose.
   */
  @Test
  void toStringShouldIncludeClassName() {
    InvalidBidException ex = new InvalidBidException("invalid");
    assertTrue(ex.toString().contains("InvalidBidException"));
    assertTrue(ex.toString().contains("invalid"));
  }
}
