package com.auction.pattern.state;

import static org.junit.jupiter.api.Assertions.*;

import com.auction.exception.AuctionClosedException;
import com.auction.model.Auction;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử bốn trạng thái từ chối mọi hành động: {@link FinishedState}, {@link SettlingState},
 * {@link PaidState}, {@link CanceledState}.
 *
 * <p>Nhóm vào một lớp test duy nhất vì hành vi của chúng có cùng cấu trúc — chỉ cần xác nhận đúng
 * loại exception được ném. Ngoại lệ: {@link FinishedState#close(Auction)} cho phép để chuyển sang
 * PAID.
 */
@DisplayName("Trạng thái cuối và khóa (Terminal & locked AuctionStates)")
class TerminalStatesTest {

  /** Tạo phiên đấu giá đã hết giờ (startTime và endTime đều trong quá khứ). */
  private static Auction auction() {
    Auction auction =
        new Auction(
            10L,
            new BigDecimal("100000"),
            LocalDateTime.now().minusHours(1),
            LocalDateTime.now().minusMinutes(1));
    auction.setId(42L);
    auction.setSellerId(99L);
    return auction;
  }

  /** Helper xác nhận tất cả hành động ngoại trừ close() đều bị từ chối. */
  private static void assertAllRejected(AuctionState state) {
    Auction a = auction();
    assertThrows(
        AuctionClosedException.class, () -> state.placeBid(a, new BigDecimal("200000"), 7L));
    assertThrows(AuctionClosedException.class, () -> state.edit(a));
    assertThrows(AuctionClosedException.class, () -> state.extend(a, 60L));
  }

  @Nested
  @DisplayName("FinishedState")
  class Finished {
    private final FinishedState state = new FinishedState();

    @Test
    @DisplayName("từ chối placeBid/edit/extend — phiên đã kết thúc")
    void rejectsMostActions() {
      Auction a = auction();
      assertThrows(
          AuctionClosedException.class, () -> state.placeBid(a, new BigDecimal("200000"), 7L));
      assertThrows(AuctionClosedException.class, () -> state.edit(a));
      assertThrows(AuctionClosedException.class, () -> state.extend(a, 60L));
    }

    @Test
    @DisplayName("close() thành công — cho phép chuyển sang PAID")
    void closeAllowed() {
      assertDoesNotThrow(() -> state.close(auction()));
    }
  }

  @Nested
  @DisplayName("SettlingState")
  class Settling {
    private final SettlingState state = new SettlingState();

    @Test
    @DisplayName("từ chối mọi hành động trong lúc settlement đang chạy")
    void rejectsAll() {
      Auction a = auction();
      assertThrows(
          AuctionClosedException.class, () -> state.placeBid(a, new BigDecimal("200000"), 7L));
      assertThrows(AuctionClosedException.class, () -> state.close(a));
      assertThrows(AuctionClosedException.class, () -> state.edit(a));
      assertThrows(AuctionClosedException.class, () -> state.extend(a, 60L));
    }
  }

  @Nested
  @DisplayName("PaidState (trạng thái cuối tích cực)")
  class Paid {
    private final PaidState state = new PaidState();

    @Test
    @DisplayName("từ chối mọi hành động — phiên đã thanh toán và đóng băng vĩnh viễn")
    void rejectsAll() {
      Auction a = auction();
      assertThrows(
          AuctionClosedException.class, () -> state.placeBid(a, new BigDecimal("200000"), 7L));
      assertThrows(AuctionClosedException.class, () -> state.close(a));
      assertThrows(AuctionClosedException.class, () -> state.edit(a));
      assertThrows(AuctionClosedException.class, () -> state.extend(a, 60L));
    }
  }

  @Nested
  @DisplayName("CanceledState (trạng thái cuối tiêu cực)")
  class Canceled {
    private final CanceledState state = new CanceledState();

    @Test
    @DisplayName("từ chối mọi hành động — phiên đã bị hủy và đóng băng vĩnh viễn")
    void rejectsAll() {
      Auction a = auction();
      assertThrows(
          AuctionClosedException.class, () -> state.placeBid(a, new BigDecimal("200000"), 7L));
      assertThrows(AuctionClosedException.class, () -> state.close(a));
      assertThrows(AuctionClosedException.class, () -> state.edit(a));
      assertThrows(AuctionClosedException.class, () -> state.extend(a, 60L));
    }
  }
}
