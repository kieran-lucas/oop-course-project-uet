package com.auction.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Test suite verifying the contract of {@link AuctionClosedException} — thrown when a bid is placed
 * on an auction that has already ended or been closed by the scheduler.
 *
 * <p><b>Contract under test:</b>
 *
 * <ul>
 *   <li>Both constructors (message-only and message + cause) propagate their arguments correctly.
 *   <li>The class sits in the right place in the exception hierarchy: {@code AuctionClosedException
 *       → AuctionException → RuntimeException}.
 *   <li>{@code toString()} includes both the class name and the message, which matters for log
 *       readability when the full stack trace is not printed.
 * </ul>
 *
 * <p>No external dependencies or DB connection required — all tests are pure unit tests that
 * instantiate the exception directly.
 */
class AuctionClosedExceptionTest {

  /**
   * Verifies that the single-argument constructor stores the message and makes it retrievable via
   * {@link Throwable#getMessage()}.
   */
  @Test
  void shouldCarryMessage() {
    AuctionClosedException ex = new AuctionClosedException("auction 1 closed");
    assertEquals("auction 1 closed", ex.getMessage());
  }

  /**
   * Verifies the two-argument constructor: the cause must be the exact object passed in (identity
   * check via {@code assertSame}, not just equality), and the message must be preserved
   * independently of the cause.
   *
   * <p>Cause chaining is critical for debugging — without it, the original scheduler error would be
   * silently swallowed when {@code AuctionClosedException} is caught upstream.
   */
  @Test
  void shouldChainCause() {
    Throwable root = new RuntimeException("scheduler error");
    AuctionClosedException ex = new AuctionClosedException("close failed", root);
    assertSame(root, ex.getCause());
    assertEquals("close failed", ex.getMessage());
  }

  /**
   * Verifies that {@link AuctionClosedException} is a subtype of {@link AuctionException}, allowing
   * callers to catch all domain exceptions with a single {@code catch (AuctionException e)} block
   * without needing to enumerate each specific type.
   */
  @Test
  void shouldBeAnAuctionException() {
    AuctionClosedException ex = new AuctionClosedException("x");
    assertTrue(ex instanceof AuctionException);
  }

  /**
   * Verifies that {@link AuctionClosedException} extends {@link RuntimeException}, meaning callers
   * are not forced to declare it in {@code throws} clauses. This is the intended design for domain
   * exceptions in this system.
   */
  @Test
  void shouldBeARuntimeException() {
    AuctionClosedException ex = new AuctionClosedException("x");
    assertTrue(ex instanceof RuntimeException);
  }

  /**
   * Verifies that {@code toString()} contains both the fully-qualified class name and the message.
   * This guards against accidental overrides of {@code toString()} that might drop either piece of
   * information, which would make log-only error reports harder to diagnose.
   */
  @Test
  void toStringShouldIncludeClassName() {
    AuctionClosedException ex = new AuctionClosedException("ended");
    assertTrue(ex.toString().contains("AuctionClosedException"));
    assertTrue(ex.toString().contains("ended"));
  }
}
