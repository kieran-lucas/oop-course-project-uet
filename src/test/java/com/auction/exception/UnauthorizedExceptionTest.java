package com.auction.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Test suite verifying the contract of {@link UnauthorizedException} — thrown when a caller lacks
 * the required permissions to perform an action, such as bidding under another user's identity,
 * modifying an item they do not own, or presenting an expired/invalid JWT token. Typically maps to
 * an HTTP 401 or 403 response at the API layer.
 *
 * <p><b>Contract under test:</b>
 *
 * <ul>
 *   <li>Both constructors (message-only and message + cause) propagate their arguments correctly.
 *   <li>The class sits in the right place in the exception hierarchy: {@code UnauthorizedException
 *       → AuctionException → RuntimeException}.
 *   <li>{@code toString()} includes both the class name and the message, which matters for log
 *       readability when the full stack trace is not printed.
 * </ul>
 *
 * <p>No external dependencies or DB connection required — all tests are pure unit tests that
 * instantiate the exception directly.
 */
class UnauthorizedExceptionTest {

  /**
   * Verifies that the single-argument constructor stores the message and makes it retrievable via
   * {@link Throwable#getMessage()}.
   */
  @Test
  void shouldCarryMessage() {
    UnauthorizedException ex = new UnauthorizedException("forbidden");
    assertEquals("forbidden", ex.getMessage());
  }

  /**
   * Verifies the two-argument constructor: the cause must be the exact object passed in (identity
   * check via {@code assertSame}, not just equality), and the message must be preserved
   * independently of the cause.
   *
   * <p>Cause chaining is particularly relevant here because {@link UnauthorizedException} is often
   * raised in response to a JWT verification failure (e.g., {@link SecurityException} from the
   * token library). Wrapping the original exception preserves the authentication context for
   * security auditing.
   */
  @Test
  void shouldChainCause() {
    Throwable root = new SecurityException("token expired");
    UnauthorizedException ex = new UnauthorizedException("auth failed", root);
    assertSame(root, ex.getCause());
    assertEquals("auth failed", ex.getMessage());
  }

  /**
   * Verifies that {@link UnauthorizedException} is a subtype of {@link AuctionException}, allowing
   * callers to catch all domain exceptions with a single {@code catch (AuctionException e)} block
   * without needing to enumerate each specific type.
   */
  @Test
  void shouldBeAnAuctionException() {
    UnauthorizedException ex = new UnauthorizedException("x");
    assertTrue(ex instanceof AuctionException);
  }

  /**
   * Verifies that {@link UnauthorizedException} extends {@link RuntimeException}, meaning callers
   * are not forced to declare it in {@code throws} clauses. This is the intended design for domain
   * exceptions in this system.
   */
  @Test
  void shouldBeARuntimeException() {
    UnauthorizedException ex = new UnauthorizedException("x");
    assertTrue(ex instanceof RuntimeException);
  }

  /**
   * Verifies that {@code toString()} contains both the fully-qualified class name and the message.
   * This guards against accidental overrides of {@code toString()} that might drop either piece of
   * information, which would make log-only error reports harder to diagnose.
   */
  @Test
  void toStringShouldIncludeClassName() {
    UnauthorizedException ex = new UnauthorizedException("denied");
    assertTrue(ex.toString().contains("UnauthorizedException"));
    assertTrue(ex.toString().contains("denied"));
  }
}
