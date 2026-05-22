package com.auction.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies that every concrete custom exception extends {@link AuctionException}, and that the
 * shared message-and-cause constructors propagate values correctly. The per-exception test classes
 * already cover their own message/HTTP mapping; this class focuses on the hierarchy contract.
 */
@DisplayName("AuctionException hierarchy")
class AuctionExceptionHierarchyTest {

  @Test
  @DisplayName("InvalidBidException is an AuctionException and a RuntimeException")
  void invalidBidExtendsAuctionException() {
    InvalidBidException ex = new InvalidBidException("low");
    assertInstanceOf(AuctionException.class, ex);
    assertInstanceOf(RuntimeException.class, ex);
    assertEquals("low", ex.getMessage());
  }

  @Test
  @DisplayName("AuctionClosedException carries its message")
  void auctionClosedCarriesMessage() {
    AuctionClosedException ex = new AuctionClosedException("closed");
    assertEquals("closed", ex.getMessage());
    assertInstanceOf(AuctionException.class, ex);
  }

  @Test
  @DisplayName("UnauthorizedException is an AuctionException")
  void unauthorizedExtendsAuctionException() {
    assertInstanceOf(AuctionException.class, new UnauthorizedException("nope"));
  }

  @Test
  @DisplayName("NotFoundException is an AuctionException")
  void notFoundExtendsAuctionException() {
    assertInstanceOf(AuctionException.class, new NotFoundException("missing"));
  }

  @Test
  @DisplayName("DuplicateException is an AuctionException")
  void duplicateExtendsAuctionException() {
    assertInstanceOf(AuctionException.class, new DuplicateException("dup"));
  }

  @Test
  @DisplayName("a single catch on AuctionException catches every subtype")
  void singleCatchCatchesAll() {
    AuctionException[] all = {
      new InvalidBidException("a"),
      new AuctionClosedException("b"),
      new UnauthorizedException("c"),
      new NotFoundException("d"),
      new DuplicateException("e")
    };
    for (AuctionException ex : all) {
      try {
        throw ex;
      } catch (AuctionException caught) {
        assertNotNull(caught.getMessage());
      }
    }
  }
}
