package com.auction.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Test suite verifying the contract of {@link NotFoundException} — thrown when a requested resource
 * (user, item, auction, etc.) does not exist in the system, typically mapping to an HTTP 404
 * response at the API layer.
 *
 * <p><b>Contract under test:</b>
 *
 * <ul>
 *   <li>Both constructors (message-only and message + cause) propagate their arguments correctly.
 *   <li>The class sits in the right place in the exception hierarchy: {@code NotFoundException →
 *       AuctionException → RuntimeException}.
 *   <li>{@code toString()} includes both the class name and the message, which matters for log
 *       readability when the full stack trace is not printed.
 * </ul>
 *
 * <p>No external dependencies or DB connection required — all tests are pure unit tests that
 * instantiate the exception directly.
 */
class NotFoundExceptionTest {

  /**
   * Verifies that the single-argument constructor stores the message and makes it retrievable via
   * {@link Throwable#getMessage()}.
   */
  @Test
  void shouldCarryMessage() {
    NotFoundException ex = new NotFoundException("user 42 not found");
    assertEquals("user 42 not found", ex.getMessage());
  }

  /**
   * Verifies the two-argument constructor: the cause must be the exact object passed in (identity
   * check via {@code assertSame}, not just equality), and the message must be preserved
   * independently of the cause.
   *
   * <p>Cause chaining matters here because a {@link NotFoundException} can originate from a failed
   * DB lookup (e.g., connection loss) rather than a genuinely missing entity. Preserving the root
   * cause lets upper layers distinguish the two scenarios.
   */
  @Test
  void shouldChainCause() {
    Throwable root = new RuntimeException("DB down");
    NotFoundException ex = new NotFoundException("lookup failed", root);
    assertSame(root, ex.getCause());
    assertEquals("lookup failed", ex.getMessage());
  }

  /**
   * Verifies that {@link NotFoundException} is a subtype of {@link AuctionException}, allowing
   * callers to catch all domain exceptions with a single {@code catch (AuctionException e)} block
   * without needing to enumerate each specific type.
   */
  @Test
  void shouldBeAnAuctionException() {
    NotFoundException ex = new NotFoundException("x");
    assertTrue(ex instanceof AuctionException);
  }

  /**
   * Verifies that {@link NotFoundException} extends {@link RuntimeException}, meaning callers are
   * not forced to declare it in {@code throws} clauses. This is the intended design for domain
   * exceptions in this system.
   */
  @Test
  void shouldBeARuntimeException() {
    NotFoundException ex = new NotFoundException("x");
    assertTrue(ex instanceof RuntimeException);
  }

  /**
   * Verifies that {@code toString()} contains both the fully-qualified class name and the message.
   * This guards against accidental overrides of {@code toString()} that might drop either piece of
   * information, which would make log-only error reports harder to diagnose.
   */
  @Test
  void toStringShouldIncludeClassName() {
    NotFoundException ex = new NotFoundException("entity gone");
    assertTrue(ex.toString().contains("NotFoundException"));
    assertTrue(ex.toString().contains("entity gone"));
  }
}
