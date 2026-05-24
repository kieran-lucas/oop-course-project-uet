package com.auction.pattern.state;

import static org.junit.jupiter.api.Assertions.*;

import com.auction.exception.AuctionClosedException;
import com.auction.exception.InvalidBidException;
import com.auction.model.Auction;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử {@link RunningState} — trạng thái phiên đang diễn ra và sẵn sàng nhận giá.
 *
 * <p>Hành động được cho phép: {@code placeBid()} (nếu hợp lệ), {@code close()}, {@code extend()}.
 * Hành động bị từ ch���i: {@code edit()}, {@code placeBid()} khi vi phạm quy tắc.
 */
@DisplayName("RunningState")
class RunningStateTest {

  private final RunningState state = new RunningState();
  private static final Long SELLER_ID = 99L;
  private static final Long BIDDER_ID = 7L;

  /** Tạo phiên đấu giá đang chạy (startTime trong quá khứ, endTime trong tương lai). */
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
  @DisplayName("placeBid() cập nhật currentPrice và leadingBidderId khi giá hợp lệ")
  void placeBidUpdatesAuction() {
    Auction auction = runningAuction();

    state.placeBid(auction, new BigDecimal("200000"), BIDDER_ID);

    assertEquals(0, new BigDecimal("200000").compareTo(auction.getCurrentPrice()));
    assertEquals(BIDDER_ID, auction.getLeadingBidderId());
  }

  @Test
  @DisplayName("placeBid() từ chối giá bằng giá hiện tại")
  void rejectsEqualPrice() {
    Auction auction = runningAuction();
    assertThrows(
        InvalidBidException.class,
        () -> state.placeBid(auction, auction.getCurrentPrice(), BIDDER_ID));
  }

  @Test
  @DisplayName("placeBid() từ chối giá thấp hơn giá hiện tại")
  void rejectsLowerPrice() {
    Auction auction = runningAuction();
    assertThrows(
        InvalidBidException.class,
        () -> state.placeBid(auction, new BigDecimal("50000"), BIDDER_ID));
  }

  @Test
  @DisplayName("placeBid() chặn seller tự đặt giá cho phiên của chính mình")
  void blocksSellerSelfBid() {
    Auction auction = runningAuction();
    InvalidBidException ex =
        assertThrows(
            InvalidBidException.class,
            () -> state.placeBid(auction, new BigDecimal("200000"), SELLER_ID));
    assertTrue(ex.getMessage().toLowerCase().contains("seller"));
  }

  @Test
  @DisplayName("placeBid() chặn người đang dẫn đầu tự đặt giá lại")
  void blocksCurrentLeader() {
    Auction auction = runningAuction();
    state.placeBid(auction, new BigDecimal("150000"), BIDDER_ID); // trở thành leader

    assertThrows(
        InvalidBidException.class,
        () -> state.placeBid(auction, new BigDecimal("200000"), BIDDER_ID));
  }

  @Test
  @DisplayName("close() thành công — admin hoặc scheduler có thể đóng phiên")
  void closeAllowed() {
    assertDoesNotThrow(() -> state.close(runningAuction()));
  }

  @Test
  @DisplayName("edit() từ chối — không được sửa phiên đang chạy")
  void editRejected() {
    AuctionClosedException ex =
        assertThrows(AuctionClosedException.class, () -> state.edit(runningAuction()));
    assertTrue(ex.getMessage().toLowerCase().contains("running"));
  }

  @Test
  @DisplayName("extend() đẩy endTime lên thêm số giây được chỉ định")
  void extendPushesEndTime() {
    Auction auction = runningAuction();
    LocalDateTime before = auction.getEndTime();

    state.extend(auction, 60L);

    assertEquals(before.plusSeconds(60L), auction.getEndTime());
  }
}
