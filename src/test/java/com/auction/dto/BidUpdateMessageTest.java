package com.auction.dto;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the static factory methods on {@link BidUpdateMessage}. Each factory should set
 * the correct {@code type} discriminator and only populate the fields relevant to that message
 * type.
 */
@DisplayName("BidUpdateMessage factories")
class BidUpdateMessageTest {

  @Test
  @DisplayName("bidUpdate() sets type=BID_UPDATE and all bid fields")
  void bidUpdateFactory() {
    LocalDateTime end = LocalDateTime.now().plusMinutes(10);
    BidUpdateMessage msg =
        BidUpdateMessage.bidUpdate(5L, new BigDecimal("200000"), 7L, "alice", end, true);

    assertEquals(BidUpdateMessage.TYPE_BID_UPDATE, msg.getType());
    assertEquals(5L, msg.getAuctionId());
    assertEquals(0, new BigDecimal("200000").compareTo(msg.getCurrentPrice()));
    assertEquals(7L, msg.getLeadingBidderId());
    assertEquals("alice", msg.getLeadingBidderUsername());
    assertEquals(end, msg.getEndTime());
    assertTrue(msg.isAutoBid());
    assertNotNull(msg.getTimestamp());
  }

  @Test
  @DisplayName("timeExtended() sets type=TIME_EXTENDED and new endTime only")
  void timeExtendedFactory() {
    LocalDateTime end = LocalDateTime.now().plusMinutes(2);
    BidUpdateMessage msg = BidUpdateMessage.timeExtended(5L, end);

    assertEquals(BidUpdateMessage.TYPE_TIME_EXTENDED, msg.getType());
    assertEquals(5L, msg.getAuctionId());
    assertEquals(end, msg.getEndTime());
    assertNull(msg.getCurrentPrice());
    assertNull(msg.getLeadingBidderId());
  }

  @Test
  @DisplayName("auctionEnded() sets type=AUCTION_ENDED with final price + winner")
  void auctionEndedFactory() {
    BidUpdateMessage msg = BidUpdateMessage.auctionEnded(5L, new BigDecimal("999000"), 7L, "alice");

    assertEquals(BidUpdateMessage.TYPE_AUCTION_ENDED, msg.getType());
    assertEquals(5L, msg.getAuctionId());
    assertEquals(0, new BigDecimal("999000").compareTo(msg.getCurrentPrice()));
    assertEquals(7L, msg.getLeadingBidderId());
    assertEquals("alice", msg.getLeadingBidderUsername());
  }

  @Test
  @DisplayName("auctionEnded() accepts a null winner for empty auctions")
  void auctionEndedNoWinner() {
    BidUpdateMessage msg = BidUpdateMessage.auctionEnded(5L, new BigDecimal("0"), null, null);

    assertEquals(BidUpdateMessage.TYPE_AUCTION_ENDED, msg.getType());
    assertNull(msg.getLeadingBidderId());
    assertNull(msg.getLeadingBidderUsername());
  }

  @Test
  @DisplayName("balanceUpdated() sets type=BALANCE_UPDATED and approval flag")
  void balanceUpdatedFactory() {
    BidUpdateMessage msg =
        BidUpdateMessage.balanceUpdated(
            42L, new BigDecimal("1500000"), new BigDecimal("500000"), true);

    assertEquals(BidUpdateMessage.TYPE_BALANCE_UPDATED, msg.getType());
    assertEquals(42L, msg.getAuctionId()); // doubles as userId — see Javadoc
    assertEquals(0, new BigDecimal("1500000").compareTo(msg.getNewBalance()));
    assertEquals(0, new BigDecimal("500000").compareTo(msg.getBalanceDelta()));
    assertTrue(msg.isApproved());
  }
}
