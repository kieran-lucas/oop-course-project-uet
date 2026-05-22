package com.auction.pattern.state;

import static org.junit.jupiter.api.Assertions.*;

import com.auction.exception.AuctionClosedException;
import com.auction.exception.InvalidBidException;
import com.auction.model.Auction;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RunningState")
class RunningStateTest {

  private final RunningState state = new RunningState();
  private static final Long SELLER_ID = 99L;
  private static final Long BIDDER_ID = 7L;

  private Auction runningAuction() {
    Auction auction =
        new Auction(
            10L,
            new BigDecimal("100000"),
            LocalDateTime.now().minusMinutes(5),
            LocalDateTime.now().plusMinutes(10));
    auction.setId(1L);
    auction.setSellerId(SELLER_ID);
    return auction;
  }

  @Test
  @DisplayName("placeBid() updates currentPrice and leadingBidderId when valid")
  void placeBidUpdatesAuction() {
    Auction auction = runningAuction();

    state.placeBid(auction, new BigDecimal("200000"), BIDDER_ID);

    assertEquals(0, new BigDecimal("200000").compareTo(auction.getCurrentPrice()));
    assertEquals(BIDDER_ID, auction.getLeadingBidderId());
  }

  @Test
  @DisplayName("placeBid() rejects amount equal to current price")
  void rejectsEqualPrice() {
    Auction auction = runningAuction();
    assertThrows(
        InvalidBidException.class,
        () -> state.placeBid(auction, auction.getCurrentPrice(), BIDDER_ID));
  }

  @Test
  @DisplayName("placeBid() rejects amount lower than current price")
  void rejectsLowerPrice() {
    Auction auction = runningAuction();
    assertThrows(
        InvalidBidException.class,
        () -> state.placeBid(auction, new BigDecimal("50000"), BIDDER_ID));
  }

  @Test
  @DisplayName("placeBid() blocks the seller from bidding on own auction")
  void blocksSellerSelfBid() {
    Auction auction = runningAuction();
    InvalidBidException ex =
        assertThrows(
            InvalidBidException.class,
            () -> state.placeBid(auction, new BigDecimal("200000"), SELLER_ID));
    assertTrue(ex.getMessage().toLowerCase().contains("seller"));
  }

  @Test
  @DisplayName("placeBid() blocks the current leader from bidding again")
  void blocksCurrentLeader() {
    Auction auction = runningAuction();
    state.placeBid(auction, new BigDecimal("150000"), BIDDER_ID); // becomes leader

    assertThrows(
        InvalidBidException.class,
        () -> state.placeBid(auction, new BigDecimal("200000"), BIDDER_ID));
  }

  @Test
  @DisplayName("close() succeeds")
  void closeAllowed() {
    assertDoesNotThrow(() -> state.close(runningAuction()));
  }

  @Test
  @DisplayName("edit() rejects — cannot modify a running auction")
  void editRejected() {
    AuctionClosedException ex =
        assertThrows(AuctionClosedException.class, () -> state.edit(runningAuction()));
    assertTrue(ex.getMessage().contains("đang diễn ra"));
  }

  @Test
  @DisplayName("extend() pushes endTime forward by the requested seconds")
  void extendPushesEndTime() {
    Auction auction = runningAuction();
    LocalDateTime before = auction.getEndTime();

    state.extend(auction, 60L);

    assertEquals(before.plusSeconds(60L), auction.getEndTime());
  }
}
