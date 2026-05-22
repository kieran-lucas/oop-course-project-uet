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
 * Covers the four states whose contract is "reject every action": FinishedState, SettlingState,
 * PaidState, CanceledState. Grouped into a single test class because each state's behaviour is
 * identical in shape and we only need to verify the right exception type is thrown.
 */
@DisplayName("Terminal & locked AuctionStates")
class TerminalStatesTest {

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
    @DisplayName("rejects bid/edit/extend")
    void rejectsMostActions() {
      Auction a = auction();
      assertThrows(
          AuctionClosedException.class, () -> state.placeBid(a, new BigDecimal("200000"), 7L));
      assertThrows(AuctionClosedException.class, () -> state.edit(a));
      assertThrows(AuctionClosedException.class, () -> state.extend(a, 60L));
    }

    @Test
    @DisplayName("close() succeeds — allowed for PAID transition")
    void closeAllowed() {
      assertDoesNotThrow(() -> state.close(auction()));
    }
  }

  @Nested
  @DisplayName("SettlingState")
  class Settling {
    private final SettlingState state = new SettlingState();

    @Test
    @DisplayName("rejects every external action while settlement runs")
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
  @DisplayName("PaidState (terminal positive)")
  class Paid {
    private final PaidState state = new PaidState();

    @Test
    @DisplayName("rejects every action — auction is closed permanently")
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
  @DisplayName("CanceledState (terminal negative)")
  class Canceled {
    private final CanceledState state = new CanceledState();

    @Test
    @DisplayName("rejects every action")
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
