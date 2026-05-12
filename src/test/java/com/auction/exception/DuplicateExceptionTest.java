package com.auction.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Test suite verifying the contract of {@link DuplicateException} — thrown when an operation
 * attempts to create a resource that already exists, such as registering a username or email
 * that violates a UNIQUE constraint in the database.
 *
 * <p><b>Contract under test:</b>
 * <ul>
 *   <li>Both constructors (message-only and message + cause) propagate their arguments correctly.</li>
 *   <li>The class sits in the right place in the exception hierarchy:
 *       {@code DuplicateException → AuctionException → RuntimeException}.</li>
 *   <li>{@code toString()} includes both the class name and the message, which matters for
 *       log readability when the full stack trace is not printed.</li>
 * </ul>
 *
 * <p>No external dependencies or DB connection required — all tests are pure unit tests
 * that instantiate the exception directly.
 */
class DuplicateExceptionTest {

  /**
   * Verifies that the single-argument constructor stores the message and makes it
   * retrievable via {@link Throwable#getMessage()}.
   */
  @Test
  void shouldCarryMessage() {
    DuplicateException ex = new DuplicateException("email exists");
    assertEquals("email exists", ex.getMessage());
  }

  /**
   * Verifies the two-argument constructor: the cause must be the exact object passed in
   * (identity check via {@code assertSame}, not just equality), and the message must be
   * preserved independently of the cause.
   *
   * <p>Cause chaining is important here because {@link DuplicateException} is often raised
   * in response to a low-level JDBC constraint violation — wrapping the original exception
   * allows upper layers to inspect the root cause if needed.
   */
  @Test
  void shouldChainCause() {
    Throwable root = new RuntimeException("constraint violation");
    DuplicateException ex = new DuplicateException("duplicate detected", root);
    assertSame(root, ex.getCause());
    assertEquals("duplicate detected", ex.getMessage());
  }

  /**
   * Verifies that {@link DuplicateException} is a subtype of {@link AuctionException},
   * allowing callers to catch all domain exceptions with a single {@code catch (AuctionException e)}
   * block without needing to enumerate each specific type.
   */
  @Test
  void shouldBeAnAuctionException() {
    DuplicateException ex = new DuplicateException("x");
    assertTrue(ex instanceof AuctionException);
  }

  /**
   * Verifies that {@link DuplicateException} extends {@link RuntimeException}, meaning
   * callers are not forced to declare it in {@code throws} clauses. This is the intended
   * design for domain exceptions in this system.
   */
  @Test
  void shouldBeARuntimeException() {
    DuplicateException ex = new DuplicateException("x");
    assertTrue(ex instanceof RuntimeException);
  }

  /**
   * Verifies that {@code toString()} contains both the fully-qualified class name and the
   * message. This guards against accidental overrides of {@code toString()} that might drop
   * either piece of information, which would make log-only error reports harder to diagnose.
   */
  @Test
  void toStringShouldIncludeClassName() {
    DuplicateException ex = new DuplicateException("conflict");
    assertTrue(ex.toString().contains("DuplicateException"));
    assertTrue(ex.toString().contains("conflict"));
  }
}
