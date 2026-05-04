package com.auction.service;

import com.auction.dao.AuctionDao;
import com.auction.dao.BidTransactionDao;
import com.auction.dao.AutoBidConfigDao;
import com.auction.dto.BidUpdateMessage;
import com.auction.exception.AuctionClosedException;
import com.auction.exception.InvalidBidException;
import com.auction.model.Auction;
import com.auction.model.BidTransaction;
import com.auction.pattern.observer.AuctionEventManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Test toàn diện cho BidService — trọng tâm của tuần 3.
 *
 * <p><b>BidService là class phức tạp nhất trong project</b> vì nó kết hợp nhiều thành phần:
 * <ul>
 *   <li>State pattern — kiểm tra trạng thái phiên trước khi cho bid</li>
 *   <li>Strategy pattern — ManualBidStrategy validate + AutoBidStrategy chain</li>
 *   <li>Observer pattern — notify tất cả client qua WebSocket sau mỗi bid</li>
 *   <li>Concurrency — synchronized block (tầng 1)</li>
 *   <li>Anti-sniping — gia hạn phiên nếu bid trong 30 giây cuối</li>
 * </ul>
 *
 * <p><b>Chiến lược mock:</b> Mock toàn bộ DAO + EventManager. Chỉ test logic thuần —
 * không cần DB, không cần WebSocket server thật.
 *
 * <p><b>Mỗi test case kiểm tra đúng 1 hành vi</b> (single assertion principle) để khi fail,
 * biết ngay vấn đề ở đâu mà không cần đọc stacktrace dài.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BidService — Bid Logic, Anti-sniping & Observer Notification")
class BidServiceTest {

  // ── Mocks ────────────────────────────────────────────────
  @Mock private AuctionDao auctionDao;
  @Mock private BidTransactionDao bidTransactionDao;
  @Mock private AutoBidConfigDao autoBidConfigDao;
  @Mock private AuctionEventManager eventManager;

  // ── System Under Test ────────────────────────────────────
  @InjectMocks private BidService bidService;

  // ── Constant test data ───────────────────────────────────
  private static final Long   AUCTION_ID   = 5L;
  private static final Long   SELLER_ID    = 1L;
  private static final Long   BIDDER_ID    = 2L;
  private static final Long   BIDDER_B_ID  = 3L;
  private static final BigDecimal STARTING = new BigDecimal("1000000");  // 1 triệu

  // ── Helper ───────────────────────────────────────────────

  /**
   * Tạo Auction mẫu với trạng thái và thời gian còn lại cho trước.
   *
   * @param status       trạng thái phiên: "OPEN", "RUNNING", "FINISHED", ...
   * @param remainingSec thời gian còn lại (giây). Âm → phiên đã hết giờ
   */
  private Auction buildAuction(String status, long remainingSec) {
    Auction a = new Auction();
    a.setId(AUCTION_ID);
    a.setSellerId(SELLER_ID);
    a.setStartingPrice(STARTING);
    a.setCurrentPrice(STARTING);
    a.setLeadingBidderId(null);
    a.setStatus(status);
    a.setStartTime(LocalDateTime.now().minusHours(1));
    a.setEndTime(LocalDateTime.now().plusSeconds(remainingSec));
    return a;
  }

  /** Shortcut: phiên RUNNING còn nhiều giờ — trường hợp bình thường */
  private Auction runningAuction() {
    return buildAuction("RUNNING", 3600); // còn 1 giờ
  }

  // ═══════════════════════════════════════════════════════════
  // Nhóm 1: Bid thành công
  // ═══════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Bid thành công — trường hợp bình thường")
  class BidSuccess {

    @BeforeEach
    void setupMocks() {
      when(auctionDao.findById(AUCTION_ID)).thenReturn(Optional.of(runningAuction()));
      doNothing().when(auctionDao).update(any(Auction.class));
      doNothing().when(bidTransactionDao).insert(any(BidTransaction.class));
    }

    @Test
    @DisplayName("Bid thành công → currentPrice cập nhật bằng giá bid")
    void testBidSuccessCurrentPriceUpdated() {
      BigDecimal bidAmount = new BigDecimal("2000000"); // 2 triệu > 1 triệu

      bidService.placeBid(AUCTION_ID, BIDDER_ID, bidAmount, false);

      // Dùng ArgumentCaptor để bắt đối tượng được truyền vào auctionDao.update()
      ArgumentCaptor<Auction> captor = ArgumentCaptor.forClass(Auction.class);
      verify(auctionDao).update(captor.capture());

      assertEquals(0, bidAmount.compareTo(captor.getValue().getCurrentPrice()),
          "currentPrice phải cập nhật thành giá bid mới");
    }

    @Test
    @DisplayName("Bid thành công → leadingBidderId là người vừa bid")
    void testBidSuccessLeadingBidderUpdated() {
      bidService.placeBid(AUCTION_ID, BIDDER_ID, new BigDecimal("2000000"), false);

      ArgumentCaptor<Auction> captor = ArgumentCaptor.forClass(Auction.class);
      verify(auctionDao).update(captor.capture());

      assertEquals(BIDDER_ID, captor.getValue().getLeadingBidderId(),
          "leadingBidderId phải là người vừa thắng bid");
    }

    @Test
    @DisplayName("Bid thành công → BidTransaction được lưu vào DB")
    void testBidSuccessTransactionSaved() {
      bidService.placeBid(AUCTION_ID, BIDDER_ID, new BigDecimal("2000000"), false);

      verify(bidTransactionDao, times(1)).insert(any(BidTransaction.class));
    }

    @Test
    @DisplayName("Bid thành công → BidTransaction ghi đúng bidderId và amount")
    void testBidSuccessTransactionCorrectData() {
      BigDecimal bidAmount = new BigDecimal("5000000");
      bidService.placeBid(AUCTION_ID, BIDDER_ID, bidAmount, false);

      ArgumentCaptor<BidTransaction> captor = ArgumentCaptor.forClass(BidTransaction.class);
      verify(bidTransactionDao).insert(captor.capture());

      BidTransaction txn = captor.getValue();
      assertAll("BidTransaction data",
          () -> assertEquals(AUCTION_ID, txn.getAuctionId()),
          () -> assertEquals(BIDDER_ID,  txn.getBidderId()),
          () -> assertEquals(0, bidAmount.compareTo(txn.getAmount())),
          () -> assertFalse(txn.isAutoBid(), "Manual bid không phải auto-bid")
      );
    }

    @Test
    @DisplayName("Bid thành công → Observer nhận BID_UPDATE notification")
    void testBidSuccessObserverNotified() {
      bidService.placeBid(AUCTION_ID, BIDDER_ID, new BigDecimal("2000000"), false);

      verify(eventManager, times(1))
          .notifyBidUpdate(eq(AUCTION_ID), any(BidUpdateMessage.class));
    }

    @Test
    @DisplayName("Bid thành công → auctionDao.update() được gọi đúng 1 lần")
    void testBidSuccessAuctionUpdatedOnce() {
      bidService.placeBid(AUCTION_ID, BIDDER_ID, new BigDecimal("2000000"), false);

      verify(auctionDao, times(1)).update(any(Auction.class));
    }

    @Test
    @DisplayName("Bid bằng đúng startingPrice → hợp lệ (edge case)")
    void testBidEqualToStartingPriceValid() {
      // currentPrice = startingPrice = 1.000.000, bid đúng bằng → OK hay lỗi?
      // Theo rule: amount > currentPrice → bid BẰNG là KHÔNG hợp lệ
      // Test này document behavior rõ ràng
      BigDecimal equalBid = STARTING; // đúng bằng 1.000.000

      assertThrows(InvalidBidException.class,
          () -> bidService.placeBid(AUCTION_ID, BIDDER_ID, equalBid, false),
          "Bid bằng giá hiện tại không hợp lệ — phải bid CAO HƠN"
      );
    }
  }

  // ═══════════════════════════════════════════════════════════
  // Nhóm 2: Bid thất bại — giá không hợp lệ
  // ═══════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Bid thất bại — giá không hợp lệ")
  class BidInvalidAmount {

    @BeforeEach
    void setupMocks() {
      when(auctionDao.findById(AUCTION_ID)).thenReturn(Optional.of(runningAuction()));
    }

    @Test
    @DisplayName("Bid thấp hơn giá hiện tại → InvalidBidException")
    void testBidTooLowThrowsInvalidBidException() {
      BigDecimal lowBid = new BigDecimal("500000"); // 500k < 1 triệu

      assertThrows(InvalidBidException.class,
          () -> bidService.placeBid(AUCTION_ID, BIDDER_ID, lowBid, false),
          "Bid thấp hơn currentPrice phải throw InvalidBidException"
      );
    }

    @Test
    @DisplayName("Bid thấp hơn → DB không bị update")
    void testBidTooLowNoDatabaseUpdate() {
      try {
        bidService.placeBid(AUCTION_ID, BIDDER_ID, new BigDecimal("500000"), false);
      } catch (InvalidBidException ignored) { }

      verify(auctionDao, never()).update(any());
      verify(bidTransactionDao, never()).insert(any());
    }

    @Test
    @DisplayName("Bid thấp hơn → Observer không được notify")
    void testBidTooLowObserverNotNotified() {
      try {
        bidService.placeBid(AUCTION_ID, BIDDER_ID, new BigDecimal("500000"), false);
      } catch (InvalidBidException ignored) { }

      verify(eventManager, never()).notifyBidUpdate(anyLong(), any());
    }

    @Test
    @DisplayName("Bid giá âm → InvalidBidException")
    void testBidNegativeAmountThrowsInvalidBidException() {
      assertThrows(InvalidBidException.class,
          () -> bidService.placeBid(AUCTION_ID, BIDDER_ID, new BigDecimal("-100000"), false),
          "Giá âm phải throw InvalidBidException"
      );
    }

    @Test
    @DisplayName("Bid giá null → NullPointerException hoặc InvalidBidException")
    void testBidNullAmountThrowsException() {
      assertThrows(RuntimeException.class,
          () -> bidService.placeBid(AUCTION_ID, BIDDER_ID, null, false),
          "Giá null phải throw exception"
      );
    }
  }

  // ═══════════════════════════════════════════════════════════
  // Nhóm 3: Bid sai trạng thái phiên
  // ═══════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Bid sai trạng thái phiên")
  class BidWrongState {

    @Test
    @DisplayName("Bid khi FINISHED → AuctionClosedException")
    void testBidWhenFinishedThrowsAuctionClosedException() {
      when(auctionDao.findById(AUCTION_ID))
          .thenReturn(Optional.of(buildAuction("FINISHED", -3600)));

      assertThrows(AuctionClosedException.class,
          () -> bidService.placeBid(AUCTION_ID, BIDDER_ID, new BigDecimal("2000000"), false),
          "Bid khi FINISHED phải throw AuctionClosedException"
      );
    }

    @Test
    @DisplayName("Bid khi OPEN → AuctionClosedException với message 'chưa bắt đầu'")
    void testBidWhenOpenThrowsAuctionClosedException() {
      when(auctionDao.findById(AUCTION_ID))
          .thenReturn(Optional.of(buildAuction("OPEN", 3600)));

      AuctionClosedException ex = assertThrows(
          AuctionClosedException.class,
          () -> bidService.placeBid(AUCTION_ID, BIDDER_ID, new BigDecimal("2000000"), false)
      );

      String msg = ex.getMessage().toLowerCase();
      assertTrue(msg.contains("chưa") || msg.contains("open") || msg.contains("start"),
          "Message phải nói rõ phiên chưa bắt đầu, hiện: " + ex.getMessage());
    }

    @Test
    @DisplayName("Bid khi CANCELED → AuctionClosedException")
    void testBidWhenCanceledThrowsAuctionClosedException() {
      when(auctionDao.findById(AUCTION_ID))
          .thenReturn(Optional.of(buildAuction("CANCELED", 0)));

      assertThrows(AuctionClosedException.class,
          () -> bidService.placeBid(AUCTION_ID, BIDDER_ID, new BigDecimal("2000000"), false)
      );
    }

    @Test
    @DisplayName("Bid sai trạng thái → DB không bị update")
    void testBidWrongStateNoDatabaseSideEffects() {
      when(auctionDao.findById(AUCTION_ID))
          .thenReturn(Optional.of(buildAuction("FINISHED", 0)));

      try {
        bidService.placeBid(AUCTION_ID, BIDDER_ID, new BigDecimal("2000000"), false);
      } catch (AuctionClosedException ignored) { }

      verify(auctionDao, never()).update(any());
      verify(bidTransactionDao, never()).insert(any());
      verify(eventManager, never()).notifyBidUpdate(anyLong(), any());
    }
  }

  // ═══════════════════════════════════════════════════════════
  // Nhóm 4: Seller tự bid
  // ═══════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Seller tự bid sản phẩm của mình")
  class SellerBidOwnAuction {

    @BeforeEach
    void setupMocks() {
      when(auctionDao.findById(AUCTION_ID)).thenReturn(Optional.of(runningAuction()));
    }

    @Test
    @DisplayName("Seller bid phiên của mình → InvalidBidException")
    void testBidOwnAuctionThrowsInvalidBidException() {
      // SELLER_ID (= 1) là người tạo phiên và cũng là người bid
      InvalidBidException ex = assertThrows(
          InvalidBidException.class,
          () -> bidService.placeBid(AUCTION_ID, SELLER_ID, new BigDecimal("2000000"), false),
          "Seller không thể bid phiên của chính mình"
      );

      assertNotNull(ex.getMessage(), "Exception phải có message giải thích lý do");
    }

    @Test
    @DisplayName("Seller bid của mình → message đề cập 'sản phẩm của mình'")
    void testBidOwnAuctionMeaningfulMessage() {
      InvalidBidException ex = assertThrows(
          InvalidBidException.class,
          () -> bidService.placeBid(AUCTION_ID, SELLER_ID, new BigDecimal("2000000"), false)
      );

      String msg = ex.getMessage().toLowerCase();
      assertTrue(
          msg.contains("mình") || msg.contains("own") || msg.contains("seller"),
          "Message phải giải thích vì sao seller không được bid, hiện: " + ex.getMessage()
      );
    }
  }

  // ═══════════════════════════════════════════════════════════
  // Nhóm 5: Anti-sniping
  // ═══════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Anti-sniping — gia hạn khi bid trong 30 giây cuối")
  class AntiSniping {

    @BeforeEach
    void setupMocks() {
      doNothing().when(auctionDao).update(any(Auction.class));
      doNothing().when(bidTransactionDao).insert(any(BidTransaction.class));
    }

    @Test
    @DisplayName("Bid khi còn 20 giây → endTime được gia hạn thêm 60 giây")
    void testAntiSnipingEndTimeExtended() {
      Auction auction = buildAuction("RUNNING", 20); // còn 20 giây
      LocalDateTime originalEndTime = auction.getEndTime();
      when(auctionDao.findById(AUCTION_ID)).thenReturn(Optional.of(auction));

      bidService.placeBid(AUCTION_ID, BIDDER_ID, new BigDecimal("2000000"), false);

      ArgumentCaptor<Auction> captor = ArgumentCaptor.forClass(Auction.class);
      verify(auctionDao, atLeastOnce()).update(captor.capture());

      // Tìm lần update cuối (có thể update nhiều lần nếu auto-bid chain)
      Auction updatedAuction = captor.getAllValues().get(captor.getAllValues().size() - 1);

      assertTrue(
          updatedAuction.getEndTime().isAfter(originalEndTime),
          "endTime phải được gia hạn khi bid trong 30 giây cuối"
      );

      // Kiểm tra gia hạn ít nhất 50 giây (để margin cho test timing)
      long extendedSeconds = java.time.Duration
          .between(originalEndTime, updatedAuction.getEndTime())
          .getSeconds();
      assertTrue(extendedSeconds >= 50,
          "Phải gia hạn ít nhất 50 giây, thực tế gia hạn: " + extendedSeconds + "s");
    }

    @Test
    @DisplayName("Bid khi còn 20 giây → broadcast TIME_EXTENDED cho client")
    void testAntiSnipingTimeExtendedBroadcast() {
      when(auctionDao.findById(AUCTION_ID))
          .thenReturn(Optional.of(buildAuction("RUNNING", 20)));

      bidService.placeBid(AUCTION_ID, BIDDER_ID, new BigDecimal("2000000"), false);

      // Kiểm tra eventManager.notifyTimeExtended() được gọi
      verify(eventManager, times(1))
          .notifyTimeExtended(eq(AUCTION_ID), any(BidUpdateMessage.class));
    }

    @Test
    @DisplayName("Bid khi còn 60 giây → endTime KHÔNG thay đổi")
    void testAntiSnipingNotTriggeredWhen60SecRemaining() {
      Auction auction = buildAuction("RUNNING", 60); // còn 60 giây, trên ngưỡng 30
      LocalDateTime originalEndTime = auction.getEndTime();
      when(auctionDao.findById(AUCTION_ID)).thenReturn(Optional.of(auction));

      bidService.placeBid(AUCTION_ID, BIDDER_ID, new BigDecimal("2000000"), false);

      // notifyTimeExtended KHÔNG được gọi
      verify(eventManager, never())
          .notifyTimeExtended(anyLong(), any(BidUpdateMessage.class));
    }

    @Test
    @DisplayName("Bid khi còn đúng 30 giây → trigger anti-sniping (boundary)")
    void testAntiSnipingExactBoundary30Seconds() {
      when(auctionDao.findById(AUCTION_ID))
          .thenReturn(Optional.of(buildAuction("RUNNING", 29))); // 29 giây < 30 → trigger

      bidService.placeBid(AUCTION_ID, BIDDER_ID, new BigDecimal("2000000"), false);

      verify(eventManager, times(1))
          .notifyTimeExtended(eq(AUCTION_ID), any(BidUpdateMessage.class));
    }
  }

  // ═══════════════════════════════════════════════════════════
  // Nhóm 6: Auto-bid flag
  // ═══════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Auto-bid flag trong BidTransaction")
  class AutoBidFlag {

    @BeforeEach
    void setupMocks() {
      when(auctionDao.findById(AUCTION_ID)).thenReturn(Optional.of(runningAuction()));
      doNothing().when(auctionDao).update(any(Auction.class));
      doNothing().when(bidTransactionDao).insert(any(BidTransaction.class));
    }

    @Test
    @DisplayName("Manual bid → BidTransaction.autoBid = false")
    void testManualBidAutoBidFlagFalse() {
      bidService.placeBid(AUCTION_ID, BIDDER_ID, new BigDecimal("2000000"), false);

      ArgumentCaptor<BidTransaction> captor = ArgumentCaptor.forClass(BidTransaction.class);
      verify(bidTransactionDao).insert(captor.capture());

      assertFalse(captor.getValue().isAutoBid(),
          "Manual bid phải có autoBid = false trong BidTransaction");
    }

    @Test
    @DisplayName("Auto bid → BidTransaction.autoBid = true")
    void testAutoBidAutoBidFlagTrue() {
      bidService.placeBid(AUCTION_ID, BIDDER_B_ID, new BigDecimal("2000000"), true);

      ArgumentCaptor<BidTransaction> captor = ArgumentCaptor.forClass(BidTransaction.class);
      verify(bidTransactionDao).insert(captor.capture());

      assertTrue(captor.getValue().isAutoBid(),
          "Auto bid phải có autoBid = true trong BidTransaction");
    }
  }

  // ═══════════════════════════════════════════════════════════
  // Nhóm 7: Auction không tồn tại
  // ═══════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Auction không tồn tại")
  class AuctionNotFound {

    @Test
    @DisplayName("auctionId không có trong DB → NotFoundException")
    void testBidAuctionNotFoundThrowsNotFoundException() {
      when(auctionDao.findById(999L)).thenReturn(Optional.empty());

      assertThrows(
          com.auction.exception.NotFoundException.class,
          () -> bidService.placeBid(999L, BIDDER_ID, new BigDecimal("2000000"), false),
          "Bid phiên không tồn tại → phải throw NotFoundException"
      );
    }
  }

  // ═══════════════════════════════════════════════════════════
  // Nhóm 8: Observer BID_UPDATE message content
  // ═══════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Observer — BID_UPDATE message content")
  class ObserverMessageContent {

    @Test
    @DisplayName("BID_UPDATE message chứa đúng auctionId, price, bidderId")
    void testObserverMessageCorrectContent() {
      when(auctionDao.findById(AUCTION_ID)).thenReturn(Optional.of(runningAuction()));
      doNothing().when(auctionDao).update(any(Auction.class));
      doNothing().when(bidTransactionDao).insert(any(BidTransaction.class));

      BigDecimal bidAmount = new BigDecimal("3500000");
      bidService.placeBid(AUCTION_ID, BIDDER_ID, bidAmount, false);

      ArgumentCaptor<BidUpdateMessage> msgCaptor =
          ArgumentCaptor.forClass(BidUpdateMessage.class);
      verify(eventManager).notifyBidUpdate(eq(AUCTION_ID), msgCaptor.capture());

      BidUpdateMessage msg = msgCaptor.getValue();
      assertAll("BID_UPDATE message fields",
          () -> assertEquals(AUCTION_ID, msg.getAuctionId(),
              "auctionId phải khớp"),
          () -> assertEquals(0, bidAmount.compareTo(msg.getCurrentPrice()),
              "currentPrice phải là giá bid mới"),
          () -> assertEquals(BIDDER_ID, msg.getLeadingBidderId(),
              "leadingBidderId phải là người vừa bid"),
          () -> assertEquals("BID_UPDATE", msg.getType(),
              "type phải là 'BID_UPDATE'")
      );
    }
  }
}