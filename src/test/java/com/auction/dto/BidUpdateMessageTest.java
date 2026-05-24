package com.auction.dto;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử các static factory method của {@link BidUpdateMessage}.
 *
 * <p>Mỗi factory phải đặt đúng discriminator {@code type} và chỉ populate các trường liên quan đến
 * loại thông điệp đó.
 */
@DisplayName("BidUpdateMessage factories")
class BidUpdateMessageTest {

  @Test
  @DisplayName("bidUpdate() đặt type=BID_UPDATE v�� đầy đủ các trường bid")
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
  @DisplayName("timeExtended() đặt type=TIME_EXTENDED và chỉ có endTime mới")
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
  @DisplayName("auctionEnded() đặt type=AUCTION_ENDED với giá cuối và winner")
  void auctionEndedFactory() {
    BidUpdateMessage msg = BidUpdateMessage.auctionEnded(5L, new BigDecimal("999000"), 7L, "alice");

    assertEquals(BidUpdateMessage.TYPE_AUCTION_ENDED, msg.getType());
    assertEquals(5L, msg.getAuctionId());
    assertEquals(0, new BigDecimal("999000").compareTo(msg.getCurrentPrice()));
    assertEquals(7L, msg.getLeadingBidderId());
    assertEquals("alice", msg.getLeadingBidderUsername());
  }

  @Test
  @DisplayName("auctionEnded() chấp nhận winner null (phiên không có ai đặt giá)")
  void auctionEndedNoWinner() {
    BidUpdateMessage msg = BidUpdateMessage.auctionEnded(5L, new BigDecimal("0"), null, null);

    assertEquals(BidUpdateMessage.TYPE_AUCTION_ENDED, msg.getType());
    assertNull(msg.getLeadingBidderId());
    assertNull(msg.getLeadingBidderUsername());
  }

  @Test
  @DisplayName("balanceUpdated() đặt type=BALANCE_UPDATED và c�� approved")
  void balanceUpdatedFactory() {
    BidUpdateMessage msg =
        BidUpdateMessage.balanceUpdated(
            42L, new BigDecimal("1500000"), new BigDecimal("500000"), true);

    assertEquals(BidUpdateMessage.TYPE_BALANCE_UPDATED, msg.getType());
    assertEquals(42L, msg.getAuctionId()); // doubles as userId — xem Javadoc
    assertEquals(0, new BigDecimal("1500000").compareTo(msg.getNewBalance()));
    assertEquals(0, new BigDecimal("500000").compareTo(msg.getBalanceDelta()));
    assertTrue(msg.isApproved());
  }
}
