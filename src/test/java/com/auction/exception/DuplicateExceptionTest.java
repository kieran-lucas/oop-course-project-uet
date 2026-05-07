package com.auction.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DuplicateExceptionTest {

  @Test
  void shouldCarryMessage() {
    DuplicateException ex = new DuplicateException("email exists");
    assertEquals("email exists", ex.getMessage());
  }

  @Test
  void shouldChainCause() {
    Throwable root = new RuntimeException("constraint violation");
    DuplicateException ex = new DuplicateException("duplicate detected", root);
    assertSame(root, ex.getCause());
    assertEquals("duplicate detected", ex.getMessage());
  }

  @Test
  void shouldBeAnAuctionException() {
    DuplicateException ex = new DuplicateException("x");
    assertTrue(ex instanceof AuctionException);
  }

  @Test
  void shouldBeARuntimeException() {
    DuplicateException ex = new DuplicateException("x");
    assertTrue(ex instanceof RuntimeException);
  }

  @Test
  void toStringShouldIncludeClassName() {
    DuplicateException ex = new DuplicateException("conflict");
    assertTrue(ex.toString().contains("DuplicateException"));
    assertTrue(ex.toString().contains("conflict"));
  }
}
