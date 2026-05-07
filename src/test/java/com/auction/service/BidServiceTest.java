package com.auction.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.auction.dao.AuctionDao;
import com.auction.dao.AutoBidConfigDao;
import com.auction.dao.BidTransactionDao;
import com.auction.dto.BidUpdateMessage;
import com.auction.exception.AuctionClosedException;
import com.auction.exception.InvalidBidException;
import com.auction.exception.NotFoundException;
import com.auction.model.Auction;
import com.auction.model.BidTransaction;
import com.auction.pattern.observer.AuctionEventManager;
import com.auction.pattern.state.CanceledState;
import com.auction.pattern.state.FinishedState;
import com.auction.pattern.state.OpenState;
import com.auction.pattern.state.PaidState;
import com.auction.pattern.state.RunningState;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Test toàn diện cho BidService — trọng tâm của tuần 3.
 *
 * <p><b>BidService là class phức tạp nhất trong project</b> vì nó kết hợp nhiều thành phần:
 *
 * <ul>
 *   <li>State pattern — kiểm tra trạng thái phiên trước khi cho bid
 *   <li>Strategy pattern — AutoBidStrategy chain
 *   <li>Observer pattern — notify tất cả client qua WebSocket sau mỗi bid
 *   <li>Concurrency — SELECT FOR UPDATE trong DB transaction
 *   <li>Anti-sniping — gia hạn phiên nếu bid trong 30 giây cuối
 * </ul>
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
  @Mock private Jdbi jdbi;
  @Mock private AuctionService auctionService;
  @Mock private Handle mockHandle;

  // ── System Under Test ────────────────────────────────────
  @InjectMocks private BidService bidService;

  // ── Constant test data ───────────────────────────────────
  private static final Long AUCTION_ID = 5L;
  private static final Long SELLER_ID = 1L;
  private static final Long BIDDER_ID = 2L;
  private static final Long BIDDER_B_ID = 3L;
  private static final BigDecimal STARTING = new BigDecimal("1000000"); // 1 triệu

  // ── Helper ───────────────────────────────────────────────

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

  private Auction runningAuction() {
    return buildAuction("RUNNING", 3600);
  }

  /**
   * Global setup: stub jdbi.inTransaction() to execute the callback with mockHandle, and
   * auctionService.getState() to return real State instances.
   */
  @BeforeEach
  @SuppressWarnings("unchecked")
  void globalSetup() throws Exception {
    doAnswer(
            invocation -> {
              org.jdbi.v3.core.HandleCallback<Object, Exception> callback =
                  invocation.getArgument(0);
              return callback.withHandle(mockHandle);
            })
        .when(jdbi)
        .inTransaction(any());

    doAnswer(
            invocation -> {
              Auction a = invocation.getArgument(0);
              return switch (a.getStatus()) {
                case "OPEN" -> new OpenState();
                case "RUNNING" -> new RunningState();
                case "FINISHED" -> new FinishedState();
                case "PAID" -> new PaidState();
                case "CANCELED" -> new CanceledState();
                default -> throw new IllegalStateException("Unknown: " + a.getStatus());
              };
            })
        .when(auctionService)
        .getState(any());
  }

  // ═══════════════════════════════════════════════════════════
  // Nhóm 1: Bid thành công
  // ═══════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Bid thành công — trường hợp bình thường")
  class BidSuccess {

    @BeforeEach
    void setupMocks() {
      when(auctionDao.findByIdForUpdate(mockHandle, AUCTION_ID)).thenReturn(runningAuction());
      doNothing().when(auctionDao).updateInTransaction(eq(mockHandle), any(Auction.class));
      when(bidTransactionDao.insert(eq(mockHandle), any(BidTransaction.class)))
          .thenAnswer(inv -> inv.getArgument(1));
    }

    @Test
    @DisplayName("Bid thành công → currentPrice cập nhật bằng giá bid")
    void testBidSuccessCurrentPriceUpdated() {
      BigDecimal bidAmount = new BigDecimal("2000000");

      bidService.placeBid(AUCTION_ID, BIDDER_ID, bidAmount, false);

      ArgumentCaptor<Auction> captor = ArgumentCaptor.forClass(Auction.class);
      verify(auctionDao).updateInTransaction(eq(mockHandle), captor.capture());

      assertEquals(
          0,
          bidAmount.compareTo(captor.getValue().getCurrentPrice()),
          "currentPrice phải cập nhật thành giá bid mới");
    }

    @Test
    @DisplayName("Bid thành công → leadingBidderId là người vừa bid")
    void testBidSuccessLeadingBidderUpdated() {
      bidService.placeBid(AUCTION_ID, BIDDER_ID, new BigDecimal("2000000"), false);

      ArgumentCaptor<Auction> captor = ArgumentCaptor.forClass(Auction.class);
      verify(auctionDao).updateInTransaction(eq(mockHandle), captor.capture());

      assertEquals(
          BIDDER_ID,
          captor.getValue().getLeadingBidderId(),
          "leadingBidderId phải là người vừa thắng bid");
    }

    @Test
    @DisplayName("Bid thành công → BidTransaction được lưu vào DB")
    void testBidSuccessTransactionSaved() {
      bidService.placeBid(AUCTION_ID, BIDDER_ID, new BigDecimal("2000000"), false);

      verify(bidTransactionDao, times(1)).insert(eq(mockHandle), any(BidTransaction.class));
    }

    @Test
    @DisplayName("Bid thành công → BidTransaction ghi đúng bidderId và amount")
    void testBidSuccessTransactionCorrectData() {
      BigDecimal bidAmount = new BigDecimal("5000000");
      bidService.placeBid(AUCTION_ID, BIDDER_ID, bidAmount, false);

      ArgumentCaptor<BidTransaction> captor = ArgumentCaptor.forClass(BidTransaction.class);
      verify(bidTransactionDao).insert(eq(mockHandle), captor.capture());

      BidTransaction txn = captor.getValue();
      assertAll(
          "BidTransaction data",
          () -> assertEquals(AUCTION_ID, txn.getAuctionId()),
          () -> assertEquals(BIDDER_ID, txn.getBidderId()),
          () -> assertEquals(0, bidAmount.compareTo(txn.getAmount())),
          () -> assertFalse(txn.isAutoBid(), "Manual bid không phải auto-bid"));
    }

    @Test
    @DisplayName("Bid thành công → Observer nhận BID_UPDATE notification")
    void testBidSuccessObserverNotified() {
      bidService.placeBid(AUCTION_ID, BIDDER_ID, new BigDecimal("2000000"), false);

      verify(eventManager, times(1)).notifyBidUpdate(eq(AUCTION_ID), any(BidUpdateMessage.class));
    }

    @Test
    @DisplayName("Bid thành công → auctionDao.updateInTransaction() được gọi đúng 1 lần")
    void testBidSuccessAuctionUpdatedOnce() {
      bidService.placeBid(AUCTION_ID, BIDDER_ID, new BigDecimal("2000000"), false);

      verify(auctionDao, times(1)).updateInTransaction(eq(mockHandle), any(Auction.class));
    }

    @Test
    @DisplayName("Bid bằng đúng startingPrice → không hợp lệ (phải bid CAO HƠN)")
    void testBidEqualToStartingPriceValid() {
      BigDecimal equalBid = STARTING;

      assertThrows(
          InvalidBidException.class,
          () -> bidService.placeBid(AUCTION_ID, BIDDER_ID, equalBid, false),
          "Bid bằng giá hiện tại không hợp lệ — phải bid CAO HƠN");
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
      when(auctionDao.findByIdForUpdate(mockHandle, AUCTION_ID)).thenReturn(runningAuction());
    }

    @Test
    @DisplayName("Bid thấp hơn giá hiện tại → InvalidBidException")
    void testBidTooLowThrowsInvalidBidException() {
      BigDecimal lowBid = new BigDecimal("500000");

      assertThrows(
          InvalidBidException.class,
          () -> bidService.placeBid(AUCTION_ID, BIDDER_ID, lowBid, false),
          "Bid thấp hơn currentPrice phải throw InvalidBidException");
    }

    @Test
    @DisplayName("Bid thấp hơn → DB không bị update")
    void testBidTooLowNoDatabaseUpdate() {
      try {
        bidService.placeBid(AUCTION_ID, BIDDER_ID, new BigDecimal("500000"), false);
      } catch (InvalidBidException ignored) {
      }

      verify(auctionDao, never()).updateInTransaction(any(), any());
      verify(bidTransactionDao, never()).insert(any(), any());
    }

    @Test
    @DisplayName("Bid thấp hơn → Observer không được notify")
    void testBidTooLowObserverNotNotified() {
      try {
        bidService.placeBid(AUCTION_ID, BIDDER_ID, new BigDecimal("500000"), false);
      } catch (InvalidBidException ignored) {
      }

      verify(eventManager, never()).notifyBidUpdate(anyLong(), any());
    }

    @Test
    @DisplayName("Bid giá âm → InvalidBidException")
    void testBidNegativeAmountThrowsInvalidBidException() {
      assertThrows(
          InvalidBidException.class,
          () -> bidService.placeBid(AUCTION_ID, BIDDER_ID, new BigDecimal("-100000"), false),
          "Giá âm phải throw InvalidBidException");
    }

    @Test
    @DisplayName("Bid giá null → NullPointerException hoặc InvalidBidException")
    void testBidNullAmountThrowsException() {
      assertThrows(
          RuntimeException.class,
          () -> bidService.placeBid(AUCTION_ID, BIDDER_ID, null, false),
          "Giá null phải throw exception");
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
      when(auctionDao.findByIdForUpdate(mockHandle, AUCTION_ID))
          .thenReturn(buildAuction("FINISHED", -3600));

      assertThrows(
          AuctionClosedException.class,
          () -> bidService.placeBid(AUCTION_ID, BIDDER_ID, new BigDecimal("2000000"), false),
          "Bid khi FINISHED phải throw AuctionClosedException");
    }

    @Test
    @DisplayName("Bid khi OPEN → AuctionClosedException với message 'chưa bắt đầu'")
    void testBidWhenOpenThrowsAuctionClosedException() {
      when(auctionDao.findByIdForUpdate(mockHandle, AUCTION_ID))
          .thenReturn(buildAuction("OPEN", 3600));

      AuctionClosedException ex =
          assertThrows(
              AuctionClosedException.class,
              () -> bidService.placeBid(AUCTION_ID, BIDDER_ID, new BigDecimal("2000000"), false));

      String msg = ex.getMessage().toLowerCase();
      assertTrue(
          msg.contains("chưa") || msg.contains("open") || msg.contains("start"),
          "Message phải nói rõ phiên chưa bắt đầu, hiện: " + ex.getMessage());
    }

    @Test
    @DisplayName("Bid khi CANCELED → AuctionClosedException")
    void testBidWhenCanceledThrowsAuctionClosedException() {
      when(auctionDao.findByIdForUpdate(mockHandle, AUCTION_ID))
          .thenReturn(buildAuction("CANCELED", 0));

      assertThrows(
          AuctionClosedException.class,
          () -> bidService.placeBid(AUCTION_ID, BIDDER_ID, new BigDecimal("2000000"), false));
    }

    @Test
    @DisplayName("Bid sai trạng thái → DB không bị update")
    void testBidWrongStateNoDatabaseSideEffects() {
      when(auctionDao.findByIdForUpdate(mockHandle, AUCTION_ID))
          .thenReturn(buildAuction("FINISHED", 0));

      try {
        bidService.placeBid(AUCTION_ID, BIDDER_ID, new BigDecimal("2000000"), false);
      } catch (AuctionClosedException ignored) {
      }

      verify(auctionDao, never()).updateInTransaction(any(), any());
      verify(bidTransactionDao, never()).insert(any(), any());
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
      when(auctionDao.findByIdForUpdate(mockHandle, AUCTION_ID)).thenReturn(runningAuction());
    }

    @Test
    @DisplayName("Seller bid phiên của mình → InvalidBidException")
    void testBidOwnAuctionThrowsInvalidBidException() {
      InvalidBidException ex =
          assertThrows(
              InvalidBidException.class,
              () -> bidService.placeBid(AUCTION_ID, SELLER_ID, new BigDecimal("2000000"), false),
              "Seller không thể bid phiên của chính mình");

      assertNotNull(ex.getMessage(), "Exception phải có message giải thích lý do");
    }

    @Test
    @DisplayName("Seller bid của mình → message đề cập 'sản phẩm của mình'")
    void testBidOwnAuctionMeaningfulMessage() {
      InvalidBidException ex =
          assertThrows(
              InvalidBidException.class,
              () -> bidService.placeBid(AUCTION_ID, SELLER_ID, new BigDecimal("2000000"), false));

      String msg = ex.getMessage().toLowerCase();
      assertTrue(
          msg.contains("mình") || msg.contains("own") || msg.contains("seller"),
          "Message phải giải thích vì sao seller không được bid, hiện: " + ex.getMessage());
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
      doNothing().when(auctionDao).updateInTransaction(eq(mockHandle), any(Auction.class));
      when(bidTransactionDao.insert(eq(mockHandle), any(BidTransaction.class)))
          .thenAnswer(inv -> inv.getArgument(1));
    }

    @Test
    @DisplayName("Bid khi còn 20 giây → endTime được gia hạn thêm 60 giây")
    void testAntiSnipingEndTimeExtended() {
      Auction auction = buildAuction("RUNNING", 20);
      LocalDateTime originalEndTime = auction.getEndTime();
      when(auctionDao.findByIdForUpdate(mockHandle, AUCTION_ID)).thenReturn(auction);

      bidService.placeBid(AUCTION_ID, BIDDER_ID, new BigDecimal("2000000"), false);

      ArgumentCaptor<Auction> captor = ArgumentCaptor.forClass(Auction.class);
      verify(auctionDao, atLeastOnce()).updateInTransaction(eq(mockHandle), captor.capture());

      Auction updatedAuction = captor.getAllValues().get(captor.getAllValues().size() - 1);

      assertTrue(
          updatedAuction.getEndTime().isAfter(originalEndTime),
          "endTime phải được gia hạn khi bid trong 30 giây cuối");

      long extendedSeconds =
          java.time.Duration.between(originalEndTime, updatedAuction.getEndTime()).getSeconds();
      assertTrue(
          extendedSeconds >= 50,
          "Phải gia hạn ít nhất 50 giây, thực tế gia hạn: " + extendedSeconds + "s");
    }

    @Test
    @DisplayName("Bid khi còn 20 giây → broadcast TIME_EXTENDED cho client")
    void testAntiSnipingTimeExtendedBroadcast() {
      when(auctionDao.findByIdForUpdate(mockHandle, AUCTION_ID))
          .thenReturn(buildAuction("RUNNING", 20));

      bidService.placeBid(AUCTION_ID, BIDDER_ID, new BigDecimal("2000000"), false);

      verify(eventManager, times(1))
          .notifyTimeExtended(eq(AUCTION_ID), any(BidUpdateMessage.class));
    }

    @Test
    @DisplayName("Bid khi còn 60 giây → endTime KHÔNG thay đổi")
    void testAntiSnipingNotTriggeredWhen60SecRemaining() {
      when(auctionDao.findByIdForUpdate(mockHandle, AUCTION_ID))
          .thenReturn(buildAuction("RUNNING", 60));

      bidService.placeBid(AUCTION_ID, BIDDER_ID, new BigDecimal("2000000"), false);

      verify(eventManager, never()).notifyTimeExtended(anyLong(), any(BidUpdateMessage.class));
    }

    @Test
    @DisplayName("Bid khi còn đúng 29 giây → trigger anti-sniping (boundary)")
    void testAntiSnipingExactBoundary30Seconds() {
      when(auctionDao.findByIdForUpdate(mockHandle, AUCTION_ID))
          .thenReturn(buildAuction("RUNNING", 29));

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
      when(auctionDao.findByIdForUpdate(mockHandle, AUCTION_ID)).thenReturn(runningAuction());
      doNothing().when(auctionDao).updateInTransaction(eq(mockHandle), any(Auction.class));
      when(bidTransactionDao.insert(eq(mockHandle), any(BidTransaction.class)))
          .thenAnswer(inv -> inv.getArgument(1));
    }

    @Test
    @DisplayName("Manual bid → BidTransaction.autoBid = false")
    void testManualBidAutoBidFlagFalse() {
      bidService.placeBid(AUCTION_ID, BIDDER_ID, new BigDecimal("2000000"), false);

      ArgumentCaptor<BidTransaction> captor = ArgumentCaptor.forClass(BidTransaction.class);
      verify(bidTransactionDao).insert(eq(mockHandle), captor.capture());

      assertFalse(
          captor.getValue().isAutoBid(), "Manual bid phải có autoBid = false trong BidTransaction");
    }

    @Test
    @DisplayName("Auto bid → BidTransaction.autoBid = true")
    void testAutoBidAutoBidFlagTrue() {
      bidService.placeBid(AUCTION_ID, BIDDER_B_ID, new BigDecimal("2000000"), true);

      ArgumentCaptor<BidTransaction> captor = ArgumentCaptor.forClass(BidTransaction.class);
      verify(bidTransactionDao).insert(eq(mockHandle), captor.capture());

      assertTrue(
          captor.getValue().isAutoBid(), "Auto bid phải có autoBid = true trong BidTransaction");
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
      when(auctionDao.findByIdForUpdate(any(Handle.class), eq(999L)))
          .thenThrow(new NotFoundException("Auction not found with id: 999"));

      assertThrows(
          NotFoundException.class,
          () -> bidService.placeBid(999L, BIDDER_ID, new BigDecimal("2000000"), false),
          "Bid phiên không tồn tại → phải throw NotFoundException");
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
      when(auctionDao.findByIdForUpdate(mockHandle, AUCTION_ID)).thenReturn(runningAuction());
      doNothing().when(auctionDao).updateInTransaction(eq(mockHandle), any(Auction.class));
      when(bidTransactionDao.insert(eq(mockHandle), any(BidTransaction.class)))
          .thenAnswer(inv -> inv.getArgument(1));

      BigDecimal bidAmount = new BigDecimal("3500000");
      bidService.placeBid(AUCTION_ID, BIDDER_ID, bidAmount, false);

      ArgumentCaptor<BidUpdateMessage> msgCaptor = ArgumentCaptor.forClass(BidUpdateMessage.class);
      verify(eventManager).notifyBidUpdate(eq(AUCTION_ID), msgCaptor.capture());

      BidUpdateMessage msg = msgCaptor.getValue();
      assertAll(
          "BID_UPDATE message fields",
          () -> assertEquals(AUCTION_ID, msg.getAuctionId(), "auctionId phải khớp"),
          () ->
              assertEquals(
                  0,
                  bidAmount.compareTo(msg.getCurrentPrice()),
                  "currentPrice phải là giá bid mới"),
          () ->
              assertEquals(
                  BIDDER_ID, msg.getLeadingBidderId(), "leadingBidderId phải là người vừa bid"),
          () -> assertEquals("BID_UPDATE", msg.getType(), "type phải là 'BID_UPDATE'"));
    }
  }
}
