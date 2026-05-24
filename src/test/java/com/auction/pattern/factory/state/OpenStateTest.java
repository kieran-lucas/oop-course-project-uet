package com.auction.pattern.state;

import static org.junit.jupiter.api.Assertions.*;

import com.auction.exception.AuctionClosedException;
import com.auction.model.Auction;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử {@link OpenState} — trạng thái phiên đã tạo nhưng chưa đến giờ bắt đầu.
 *
 * <p>Hành động được cho phép: {@code edit()}, {@code close()} (hủy trước giờ bắt đầu). Hành động bị
 * từ chối: {@code placeBid()}, {@code extend()}.
 */
@DisplayName("OpenState")
class OpenStateTest {

  private final OpenState state = new OpenState();

  /** Tạo phiên đấu giá tối giản ở trạng thái OPEN (startTime trong tương lai). */
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
  @DisplayName("placeBid() từ chối bằng AuctionClosedException — phiên chưa bắt đầu")
  void placeBidRejected() {
    Auction auction = openAuction();
    AuctionClosedException ex =
        assertThrows(
            AuctionClosedException.class,
            () -> state.placeBid(auction, new BigDecimal("200000"), 7L));
    assertTrue(ex.getMessage().contains("chưa bắt đầu"));
  }

  @Test
  @DisplayName("close() thành công — seller có thể hủy trước giờ bắt đầu")
  void closeAllowed() {
    assertDoesNotThrow(() -> state.close(openAuction()));
  }

  @Test
  @DisplayName("edit() thành công — seller có thể sửa thông tin trước giờ bắt đầu")
  void editAllowed() {
    assertDoesNotThrow(() -> state.edit(openAuction()));
  }

  @Test
  @DisplayName("extend() từ chối bằng AuctionClosedException")
  void extendRejected() {
    AuctionClosedException ex =
        assertThrows(AuctionClosedException.class, () -> state.extend(openAuction(), 60L));
    assertTrue(ex.getMessage().contains("chưa bắt đầu"));
  }
}
