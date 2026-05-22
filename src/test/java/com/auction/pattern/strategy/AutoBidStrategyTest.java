package com.auction.pattern.strategy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.auction.dao.AutoBidConfigDao;
import com.auction.dao.UserDao;
import com.auction.model.AutoBidConfig;
import com.auction.model.AutoBidFailureReason;
import com.auction.model.AutoBidStatus;
import com.auction.model.BidTransaction;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AutoBidStrategy — auto-bid chain execution")
class AutoBidStrategyTest {

  @Mock private AutoBidConfigDao autoBidConfigDao;
  @Mock private UserDao userDao;
  @Mock private AutoBidStrategy.AutoBidExecutor executor;

  private AutoBidStrategy strategy;

  private static final Long AUCTION_ID = 1L;
  private static final Long BIDDER_A = 10L;
  private static final Long BIDDER_B = 20L;

  @BeforeEach
  void setUp() {
    strategy = new AutoBidStrategy(autoBidConfigDao, userDao);
  }

  private AutoBidConfig buildConfig(
      Long id, Long bidderId, BigDecimal maxBid, BigDecimal increment) {
    AutoBidConfig cfg =
        new AutoBidConfig(
            id,
            AUCTION_ID,
            bidderId,
            maxBid,
            increment,
            AutoBidStatus.ACTIVE,
            null,
            LocalDateTime.now().minusMinutes(5 + id),
            LocalDateTime.now().minusMinutes(10));
    return cfg;
  }

  private BidTransaction fakeTx(Long bidderId, BigDecimal amount) {
    BidTransaction tx = new BidTransaction();
    tx.setBidderId(bidderId);
    tx.setAmount(amount);
    tx.setAuctionId(AUCTION_ID);
    return tx;
  }

  // ── executeAll() ──────────────────────────────────────────

  @Nested
  @DisplayName("executeAll()")
  class ExecuteAll {

    @Test
    @DisplayName("does nothing when there are no active auto-bid configs")
    void noOpWhenNoConfigs() {
      when(autoBidConfigDao.findActiveByAuctionId(AUCTION_ID)).thenReturn(List.of());

      strategy.executeAll(AUCTION_ID, new BigDecimal("1000000"), BIDDER_A, executor);

      verify(executor, never()).execute(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("places one auto-bid when a single bidder still has budget")
    void placesOneBidForSingleBidderWithBudget() {
      BigDecimal maxBid = new BigDecimal("2000000");
      BigDecimal increment = new BigDecimal("100000");
      BigDecimal startPrice = new BigDecimal("1000000");
      BigDecimal expectedAmount = startPrice.add(increment); // 1_100_000

      AutoBidConfig cfg = buildConfig(1L, BIDDER_B, maxBid, increment);
      when(autoBidConfigDao.findActiveByAuctionId(AUCTION_ID)).thenReturn(List.of(cfg));
      when(autoBidConfigDao.findById(1L)).thenReturn(Optional.of(cfg));
      when(executor.execute(AUCTION_ID, BIDDER_B, expectedAmount))
          .thenReturn(fakeTx(BIDDER_B, expectedAmount));

      strategy.executeAll(AUCTION_ID, startPrice, BIDDER_A, executor);

      verify(executor).execute(AUCTION_ID, BIDDER_B, expectedAmount);
    }

    @Test
    @DisplayName("skips bidder who is already the current leader")
    void skipsCurrentLeader() {
      BigDecimal maxBid = new BigDecimal("3000000");
      BigDecimal increment = new BigDecimal("100000");
      BigDecimal startPrice = new BigDecimal("1000000");

      AutoBidConfig cfg = buildConfig(1L, BIDDER_A, maxBid, increment); // BIDDER_A is the leader
      when(autoBidConfigDao.findActiveByAuctionId(AUCTION_ID)).thenReturn(List.of(cfg));
      when(autoBidConfigDao.findById(1L)).thenReturn(Optional.of(cfg));

      strategy.executeAll(AUCTION_ID, startPrice, BIDDER_A, executor);

      // BIDDER_A is the leader — nobody else to auto-bid for
      verify(executor, never()).execute(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("marks config as EXHAUSTED when budget is exceeded")
    void marksExhaustedWhenBudgetExceeded() {
      BigDecimal maxBid = new BigDecimal("1000000"); // exactly at current price
      BigDecimal increment = new BigDecimal("100000");
      BigDecimal startPrice = new BigDecimal("1000000"); // next bid would be 1_100_000 > 1_000_000

      AutoBidConfig cfg = buildConfig(1L, BIDDER_B, maxBid, increment);
      when(autoBidConfigDao.findActiveByAuctionId(AUCTION_ID)).thenReturn(List.of(cfg));
      when(autoBidConfigDao.findById(1L)).thenReturn(Optional.of(cfg));

      strategy.executeAll(AUCTION_ID, startPrice, BIDDER_A, executor);

      verify(autoBidConfigDao)
          .update(
              argThat(
                  c ->
                      c.getStatus() == AutoBidStatus.EXHAUSTED
                          && c.getFailureReason() == AutoBidFailureReason.MAX_PRICE_TOO_LOW));
      verify(executor, never()).execute(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("handles null / deactivated config gracefully (fetched fresh from DB)")
    void handlesDeactivatedConfigGracefully() {
      AutoBidConfig cfg =
          buildConfig(1L, BIDDER_B, new BigDecimal("3000000"), new BigDecimal("100000"));
      when(autoBidConfigDao.findActiveByAuctionId(AUCTION_ID)).thenReturn(List.of(cfg));
      // fresh fetch returns empty — config was deactivated between the initial load and this call
      when(autoBidConfigDao.findById(1L)).thenReturn(Optional.empty());

      assertDoesNotThrow(
          () -> strategy.executeAll(AUCTION_ID, new BigDecimal("1000000"), BIDDER_A, executor));
      verify(executor, never()).execute(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("executor exception is swallowed and chain continues")
    void executorExceptionIsSwallowed() {
      BigDecimal maxBid = new BigDecimal("3000000");
      BigDecimal increment = new BigDecimal("100000");
      BigDecimal startPrice = new BigDecimal("1000000");

      AutoBidConfig cfg = buildConfig(1L, BIDDER_B, maxBid, increment);
      when(autoBidConfigDao.findActiveByAuctionId(AUCTION_ID)).thenReturn(List.of(cfg));
      when(autoBidConfigDao.findById(1L)).thenReturn(Optional.of(cfg));
      when(executor.execute(any(), any(), any())).thenThrow(new RuntimeException("bid rejected"));

      assertDoesNotThrow(() -> strategy.executeAll(AUCTION_ID, startPrice, BIDDER_A, executor));
    }
  }

  // ── AutoBidConfig.canBidAt() ──────────────────────────────

  @Nested
  @DisplayName("AutoBidConfig.canBidAt() and getNextBidAmount()")
  class AutoBidConfigLogic {

    @Test
    @DisplayName("canBidAt returns true when currentPrice + increment <= maxBid")
    void canBidAtTrueWhenBudgetAvailable() {
      AutoBidConfig cfg =
          buildConfig(1L, BIDDER_A, new BigDecimal("2000000"), new BigDecimal("100000"));

      assertTrue(cfg.canBidAt(new BigDecimal("1000000")));
      assertTrue(cfg.canBidAt(new BigDecimal("1900000")));
    }

    @Test
    @DisplayName("canBidAt returns false when next bid would exceed maxBid")
    void canBidAtFalseWhenBudgetExceeded() {
      AutoBidConfig cfg =
          buildConfig(1L, BIDDER_A, new BigDecimal("1000000"), new BigDecimal("100000"));

      assertFalse(cfg.canBidAt(new BigDecimal("1000000"))); // 1_000_000 + 100_000 > 1_000_000
    }

    @Test
    @DisplayName("getNextBidAmount returns currentPrice + increment")
    void getNextBidAmountIsCorrect() {
      AutoBidConfig cfg =
          buildConfig(1L, BIDDER_A, new BigDecimal("5000000"), new BigDecimal("200000"));

      BigDecimal next = cfg.getNextBidAmount(new BigDecimal("1000000"));

      assertEquals(0, new BigDecimal("1200000").compareTo(next));
    }

    @Test
    @DisplayName("canBidAt returns false when config is not ACTIVE")
    void canBidAtFalseWhenInactive() {
      AutoBidConfig cfg =
          buildConfig(1L, BIDDER_A, new BigDecimal("5000000"), new BigDecimal("100000"));
      cfg.setStatus(AutoBidStatus.STOPPED);

      assertFalse(cfg.canBidAt(new BigDecimal("1000000")));
    }
  }
}
