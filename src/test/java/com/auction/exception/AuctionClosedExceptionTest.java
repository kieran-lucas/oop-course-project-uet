package com.auction.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AuctionClosedExceptionTest {

  @Test
  void shouldCarryMessage() {
    AuctionClosedException ex = new AuctionClosedException("auction 1 closed");
    assertEquals("auction 1 closed", ex.getMessage());
  }

  @Test
  void shouldChainCause() {
    Throwable root = new RuntimeException("scheduler error");
    AuctionClosedException ex = new AuctionClosedException("close failed", root);
    assertSame(root, ex.getCause());
    assertEquals("close failed", ex.getMessage());
  }

  @Test
  void shouldBeAnAuctionException() {
    AuctionClosedException ex = new AuctionClosedException("x");
    assertTrue(ex instanceof AuctionException);
  }

  @Test
  void shouldBeARuntimeException() {
    AuctionClosedException ex = new AuctionClosedException("x");
    assertTrue(ex instanceof RuntimeException);
  }

  @Test
  void toStringShouldIncludeClassName() {
    AuctionClosedException ex = new AuctionClosedException("ended");
    assertTrue(ex.toString().contains("AuctionClosedException"));
    assertTrue(ex.toString().contains("ended"));
  }
}
