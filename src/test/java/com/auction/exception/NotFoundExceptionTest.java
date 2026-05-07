package com.auction.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NotFoundExceptionTest {

  @Test
  void shouldCarryMessage() {
    NotFoundException ex = new NotFoundException("user 42 not found");
    assertEquals("user 42 not found", ex.getMessage());
  }

  @Test
  void shouldChainCause() {
    Throwable root = new RuntimeException("DB down");
    NotFoundException ex = new NotFoundException("lookup failed", root);
    assertSame(root, ex.getCause());
    assertEquals("lookup failed", ex.getMessage());
  }

  @Test
  void shouldBeAnAuctionException() {
    NotFoundException ex = new NotFoundException("x");
    assertTrue(ex instanceof AuctionException);
  }

  @Test
  void shouldBeARuntimeException() {
    NotFoundException ex = new NotFoundException("x");
    assertTrue(ex instanceof RuntimeException);
  }

  @Test
  void toStringShouldIncludeClassName() {
    NotFoundException ex = new NotFoundException("entity gone");
    assertTrue(ex.toString().contains("NotFoundException"));
    assertTrue(ex.toString().contains("entity gone"));
  }
}
