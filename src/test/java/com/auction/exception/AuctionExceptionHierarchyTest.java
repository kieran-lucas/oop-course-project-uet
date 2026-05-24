package com.auction.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử rằng mọi exception cụ thể trong domain đều kế thừa {@link AuctionException}, và các
 * constructor message + cause truyền đúng giá trị. Các lớp test riêng lẻ đã kiểm tra chi tiết từng
 * exception; lớp này tập trung vào hợp đồng cây kế thừa.
 */
@DisplayName("Cây kế thừa AuctionException")
class AuctionExceptionHierarchyTest {

  @Test
  @DisplayName("InvalidBidException là AuctionException và RuntimeException")
  void invalidBidExtendsAuctionException() {
    InvalidBidException ex = new InvalidBidException("low");
    assertInstanceOf(AuctionException.class, ex);
    assertInstanceOf(RuntimeException.class, ex);
    assertEquals("low", ex.getMessage());
  }

  @Test
  @DisplayName("AuctionClosedException mang đúng message")
  void auctionClosedCarriesMessage() {
    AuctionClosedException ex = new AuctionClosedException("closed");
    assertEquals("closed", ex.getMessage());
    assertInstanceOf(AuctionException.class, ex);
  }

  @Test
  @DisplayName("UnauthorizedException là AuctionException")
  void unauthorizedExtendsAuctionException() {
    assertInstanceOf(AuctionException.class, new UnauthorizedException("nope"));
  }

  @Test
  @DisplayName("NotFoundException là AuctionException")
  void notFoundExtendsAuctionException() {
    assertInstanceOf(AuctionException.class, new NotFoundException("missing"));
  }

  @Test
  @DisplayName("DuplicateException là AuctionException")
  void duplicateExtendsAuctionException() {
    assertInstanceOf(AuctionException.class, new DuplicateException("dup"));
  }

  @Test
  @DisplayName("một khối catch (AuctionException) bắt được mọi subtype")
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
