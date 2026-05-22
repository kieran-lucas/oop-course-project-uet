package com.auction.pattern.state;

import static org.junit.jupiter.api.Assertions.*;

import com.auction.exception.AuctionClosedException;
import com.auction.model.Auction;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OpenState")
class OpenStateTest {

  private final OpenState state = new OpenState();

  /** Helper that creates a minimally-valid auction in the OPEN state. */
  private Auction openAuction() {
    Auction auction =
        new Auction(
            10L,
            new BigDecimal("100000"),
            LocalDateTime.now().plusMinutes(5),
            LocalDateTime.now().plusHours(1));
    auction.setId(1L);
    auction.setSellerId(99L);
    return auction;
  }

  @Test
  @DisplayName("placeBid() rejects with AuctionClosedException — auction not started")
  void placeBidRejected() {
    Auction auction = openAuction();
    AuctionClosedException ex =
        assertThrows(
            AuctionClosedException.class,
            () -> state.placeBid(auction, new BigDecimal("200000"), 7L));
    assertTrue(ex.getMessage().contains("chưa bắt đầu"));
  }

  @Test
  @DisplayName("close() succeeds — seller can cancel before start")
  void closeAllowed() {
    assertDoesNotThrow(() -> state.close(openAuction()));
  }

  @Test
  @DisplayName("edit() succeeds — seller can change details before start")
  void editAllowed() {
    assertDoesNotThrow(() -> state.edit(openAuction()));
  }

  @Test
  @DisplayName("extend() rejects with AuctionClosedException")
  void extendRejected() {
    AuctionClosedException ex =
        assertThrows(AuctionClosedException.class, () -> state.extend(openAuction(), 60L));
    assertTrue(ex.getMessage().contains("chưa bắt đầu"));
  }
}
