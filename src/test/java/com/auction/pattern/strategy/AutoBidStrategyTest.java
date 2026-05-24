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

/**
 * Kiểm thử {@link AutoBidStrategy} — chiến lược tự động đặt giá theo Strategy Pattern.
 *
 * <p>Hai nhóm test chính:
 *
 * <ul>
 *   <li>{@code executeAll()} — vòng lặp xử lý các config auto-bid sau khi có bid mới
 *   <li>{@code AutoBidConfig.canBidAt()/getNextBidAmount()} — logic tính to��n giá auto-bid
 * </ul>
 *
 * <p>Dùng Mockito để stub {@link AutoBidConfigDao} và {@link AutoBidStrategy.AutoBidExecutor} —
 * không cần kết nối DB hay service thực.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AutoBidStrategy — thực thi chuỗi auto-bid")
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
    return new AutoBidConfig(
        id,
        AUCTION_ID,
        bidderId,
        maxBid,
        increment,
        AutoBidStatus.ACTIVE,
        null,
        LocalDateTime.now().minusMinutes(5 + id),
        LocalDateTime.now().minusMinutes(10));
  }

  private BidTransaction fakeTx(Long bidderId, BigDecimal amount) {
    BidTransaction tx = new BidTransaction();
    tx.setBidderId(bidderId);
    tx.setAmount(amount);
    tx.setAuctionId(AUCTION_ID);
    return tx;
  }

  @Nested
  @DisplayName("executeAll()")
  class ExecuteAll {

    @Test
    @DisplayName("không làm gì khi không có config auto-bid nào active")
    void noOpWhenNoConfigs() {
      when(autoBidConfigDao.findActiveByAuctionId(AUCTION_ID)).thenReturn(List.of());

      strategy.executeAll(AUCTION_ID, new BigDecimal("1000000"), BIDDER_A, executor);

      verify(executor, never()).execute(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("đặt một auto-bid khi bidder duy nhất còn ngân sách")
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
    @DisplayName("bỏ qua bidder đang dẫn đầu hiện tại — không cần auto-bid thêm")
    void skipsCurrentLeader() {
      BigDecimal maxBid = new BigDecimal("3000000");
      BigDecimal increment = new BigDecimal("100000");
      BigDecimal startPrice = new BigDecimal("1000000");

      AutoBidConfig cfg = buildConfig(1L, BIDDER_A, maxBid, increment); // BIDDER_A đang dẫn đầu
      when(autoBidConfigDao.findActiveByAuctionId(AUCTION_ID)).thenReturn(List.of(cfg));
      when(autoBidConfigDao.findById(1L)).thenReturn(Optional.of(cfg));

      strategy.executeAll(AUCTION_ID, startPrice, BIDDER_A, executor);

      // BIDDER_A đang dẫn đầu — không ai cần auto-bid
      verify(executor, never()).execute(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("đánh dấu config EXHAUSTED khi ngân sách đã cạn")
    void marksExhaustedWhenBudgetExceeded() {
      BigDecimal maxBid = new BigDecimal("1000000"); // bằng đúng giá hiện tại
      BigDecimal increment = new BigDecimal("100000");
      BigDecimal startPrice = new BigDecimal("1000000"); // bid tiếp theo 1_100_000 > maxBid

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
    @DisplayName("xử lý graceful khi config đã bị hủy kích hoạt giữa chừng")
    void handlesDeactivatedConfigGracefully() {
      AutoBidConfig cfg =
          buildConfig(1L, BIDDER_B, new BigDecimal("3000000"), new BigDecimal("100000"));
      when(autoBidConfigDao.findActiveByAuctionId(AUCTION_ID)).thenReturn(List.of(cfg));
      // fetch mới trả về empty — config bị vô hiệu hóa giữa lần load ban đầu và lần này
      when(autoBidConfigDao.findById(1L)).thenReturn(Optional.empty());

      assertDoesNotThrow(
          () -> strategy.executeAll(AUCTION_ID, new BigDecimal("1000000"), BIDDER_A, executor));
      verify(executor, never()).execute(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("exception từ executor được nuốt — chuỗi tiếp tục")
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

  @Nested
  @DisplayName("AutoBidConfig.canBidAt() và getNextBidAmount()")
  class AutoBidConfigLogic {

    @Test
    @DisplayName("canBidAt trả về true khi currentPrice + increment <= maxBid")
    void canBidAtTrueWhenBudgetAvailable() {
      AutoBidConfig cfg =
          buildConfig(1L, BIDDER_A, new BigDecimal("2000000"), new BigDecimal("100000"));

      assertTrue(cfg.canBidAt(new BigDecimal("1000000")));
      assertTrue(cfg.canBidAt(new BigDecimal("1900000")));
    }

    @Test
    @DisplayName("canBidAt trả về false khi bid tiếp theo vượt quá maxBid")
    void canBidAtFalseWhenBudgetExceeded() {
      AutoBidConfig cfg =
          buildConfig(1L, BIDDER_A, new BigDecimal("1000000"), new BigDecimal("100000"));

      assertFalse(cfg.canBidAt(new BigDecimal("1000000"))); // 1_000_000 + 100_000 > 1_000_000
    }

    @Test
    @DisplayName("getNextBidAmount trả về currentPrice + increment")
    void getNextBidAmountIsCorrect() {
      AutoBidConfig cfg =
          buildConfig(1L, BIDDER_A, new BigDecimal("5000000"), new BigDecimal("200000"));

      BigDecimal next = cfg.getNextBidAmount(new BigDecimal("1000000"));

      assertEquals(0, new BigDecimal("1200000").compareTo(next));
    }

    @Test
    @DisplayName("canBidAt trả về false khi config không ở trạng thái ACTIVE")
    void canBidAtFalseWhenInactive() {
      AutoBidConfig cfg =
          buildConfig(1L, BIDDER_A, new BigDecimal("5000000"), new BigDecimal("100000"));
      cfg.setStatus(AutoBidStatus.STOPPED);

      assertFalse(cfg.canBidAt(new BigDecimal("1000000")));
    }
  }
}
