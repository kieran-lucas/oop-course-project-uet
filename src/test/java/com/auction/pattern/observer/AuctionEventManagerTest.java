package com.auction.pattern.observer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.auction.dto.BidUpdateMessage;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Kiểm thử {@link AuctionEventManager} — quản lý đăng ký và thông báo sự kiện đấu giá theo Observer
 * Pattern.
 *
 * <p>Hai nhóm test:
 *
 * <ul>
 *   <li>{@code subscribe()/unsubscribe()} — đăng ký, hủy đăng ký, cách ly theo auctionId
 *   <li>Các phương thức {@code notify*()} — gửi đúng loại sự kiện đến đúng listener
 * </ul>
 *
 * <p>Dùng Mockito để mock {@link AuctionEventListener} — không cần WebSocket thực.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuctionEventManager — đăng ký và thông báo observer")
class AuctionEventManagerTest {

  private AuctionEventManager manager;

  @Mock private AuctionEventListener listener1;
  @Mock private AuctionEventListener listener2;

  private static final Long AUCTION_ID = 5L;

  @BeforeEach
  void setUp() {
    manager = new AuctionEventManager();
  }

  private BidUpdateMessage bidMsg() {
    return BidUpdateMessage.bidUpdate(
        AUCTION_ID,
        new BigDecimal("2000000"),
        99L,
        "alice",
        java.time.LocalDateTime.now().plusHours(1),
        false);
  }

  private BidUpdateMessage timeMsg() {
    return BidUpdateMessage.timeExtended(AUCTION_ID, java.time.LocalDateTime.now().plusHours(1));
  }

  private BidUpdateMessage endMsg() {
    return BidUpdateMessage.auctionEnded(AUCTION_ID, new BigDecimal("2000000"), 99L, "alice");
  }

  @Nested
  @DisplayName("subscribe() và unsubscribe()")
  class SubscribeUnsubscribe {

    @Test
    @DisplayName("listener đã đăng ký nhận được notifyBidUpdate")
    void subscribedListenerReceivesNotification() {
      manager.subscribe(AUCTION_ID, listener1);
      BidUpdateMessage msg = bidMsg();

      manager.notifyBidUpdate(AUCTION_ID, msg);

      verify(listener1).onBidUpdate(msg);
    }

    @Test
    @DisplayName("listener sau khi hủy đăng ký không còn nhận sự kiện")
    void unsubscribedListenerReceivesNoEvents() {
      manager.subscribe(AUCTION_ID, listener1);
      manager.unsubscribe(AUCTION_ID, listener1);

      manager.notifyBidUpdate(AUCTION_ID, bidMsg());

      verify(listener1, never()).onBidUpdate(any());
    }

    @Test
    @DisplayName("nhiều listener cùng nhận sự kiện")
    void multipleListenersAllNotified() {
      manager.subscribe(AUCTION_ID, listener1);
      manager.subscribe(AUCTION_ID, listener2);
      BidUpdateMessage msg = bidMsg();

      manager.notifyBidUpdate(AUCTION_ID, msg);

      verify(listener1).onBidUpdate(msg);
      verify(listener2).onBidUpdate(msg);
    }

    @Test
    @DisplayName("unsubscribe với auctionId kh��ng tồn tại không ném exception")
    void unsubscribeUnknownAuctionIdDoesNotThrow() {
      assertDoesNotThrow(() -> manager.unsubscribe(999L, listener1));
    }

    @Test
    @DisplayName("listener của các phiên khác nhau được cách ly — không nhận sự kiện chéo")
    void listenersIsolatedByAuction() {
      manager.subscribe(AUCTION_ID, listener1);
      manager.subscribe(AUCTION_ID + 1, listener2);

      manager.notifyBidUpdate(AUCTION_ID, bidMsg());

      verify(listener1).onBidUpdate(any());
      verify(listener2, never()).onBidUpdate(any());
    }
  }

  @Nested
  @DisplayName("các phương thức notify*()")
  class NotifyMethods {

    @BeforeEach
    void subscribe() {
      manager.subscribe(AUCTION_ID, listener1);
    }

    @Test
    @DisplayName("notifyBidUpdate gọi onBidUpdate trên tất cả listener")
    void notifyBidUpdate() {
      BidUpdateMessage msg = bidMsg();
      manager.notifyBidUpdate(AUCTION_ID, msg);
      verify(listener1).onBidUpdate(msg);
    }

    @Test
    @DisplayName("notifyTimeExtended gọi onTimeExtended trên tất cả listener")
    void notifyTimeExtended() {
      BidUpdateMessage msg = timeMsg();
      manager.notifyTimeExtended(AUCTION_ID, msg);
      verify(listener1).onTimeExtended(msg);
    }

    @Test
    @DisplayName("notifyAuctionEnd gọi onAuctionEnd trên tất cả listener")
    void notifyAuctionEnd() {
      BidUpdateMessage msg = endMsg();
      manager.notifyAuctionEnd(AUCTION_ID, msg);
      verify(listener1).onAuctionEnd(msg);
    }

    @Test
    @DisplayName("không làm gì khi không có listener nào đăng ký cho phiên")
    void noOpWhenNoListeners() {
      assertDoesNotThrow(() -> manager.notifyBidUpdate(999L, bidMsg()));
    }

    @Test
    @DisplayName("exception trong một listener không ngăn các listener khác nhận sự kiện")
    void exceptionInOneListenerDoesNotBlockOthers() {
      manager.subscribe(AUCTION_ID, listener2);
      doThrow(new RuntimeException("simulated WS error")).when(listener1).onBidUpdate(any());

      BidUpdateMessage msg = bidMsg();
      // Không được ném exception ra ngoài
      assertDoesNotThrow(() -> manager.notifyBidUpdate(AUCTION_ID, msg));

      // listener2 vẫn phải được gọi
      verify(listener2).onBidUpdate(msg);
    }
  }
}
