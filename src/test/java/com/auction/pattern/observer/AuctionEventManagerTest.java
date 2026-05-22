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

@ExtendWith(MockitoExtension.class)
@DisplayName("AuctionEventManager — observer subscribe / notify")
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

  // ── subscribe / unsubscribe ───────────────────────────────

  @Nested
  @DisplayName("subscribe() and unsubscribe()")
  class SubscribeUnsubscribe {

    @Test
    @DisplayName("subscribed listener receives notifyBidUpdate")
    void subscribedListenerReceivesNotification() {
      manager.subscribe(AUCTION_ID, listener1);
      BidUpdateMessage msg = bidMsg();

      manager.notifyBidUpdate(AUCTION_ID, msg);

      verify(listener1).onBidUpdate(msg);
    }

    @Test
    @DisplayName("unsubscribed listener no longer receives events")
    void unsubscribedListenerReceivesNoEvents() {
      manager.subscribe(AUCTION_ID, listener1);
      manager.unsubscribe(AUCTION_ID, listener1);

      manager.notifyBidUpdate(AUCTION_ID, bidMsg());

      verify(listener1, never()).onBidUpdate(any());
    }

    @Test
    @DisplayName("multiple listeners all receive the event")
    void multipleListenersAllNotified() {
      manager.subscribe(AUCTION_ID, listener1);
      manager.subscribe(AUCTION_ID, listener2);
      BidUpdateMessage msg = bidMsg();

      manager.notifyBidUpdate(AUCTION_ID, msg);

      verify(listener1).onBidUpdate(msg);
      verify(listener2).onBidUpdate(msg);
    }

    @Test
    @DisplayName("unsubscribe on unknown auction ID does not throw")
    void unsubscribeUnknownAuctionIdDoesNotThrow() {
      assertDoesNotThrow(() -> manager.unsubscribe(999L, listener1));
    }

    @Test
    @DisplayName("listeners of different auctions are isolated")
    void listenersIsolatedByAuction() {
      manager.subscribe(AUCTION_ID, listener1);
      manager.subscribe(AUCTION_ID + 1, listener2);

      manager.notifyBidUpdate(AUCTION_ID, bidMsg());

      verify(listener1).onBidUpdate(any());
      verify(listener2, never()).onBidUpdate(any());
    }
  }

  // ── notify methods ────────────────────────────────────────

  @Nested
  @DisplayName("notify methods")
  class NotifyMethods {

    @BeforeEach
    void subscribe() {
      manager.subscribe(AUCTION_ID, listener1);
    }

    @Test
    @DisplayName("notifyBidUpdate calls onBidUpdate on all listeners")
    void notifyBidUpdate() {
      BidUpdateMessage msg = bidMsg();
      manager.notifyBidUpdate(AUCTION_ID, msg);
      verify(listener1).onBidUpdate(msg);
    }

    @Test
    @DisplayName("notifyTimeExtended calls onTimeExtended on all listeners")
    void notifyTimeExtended() {
      BidUpdateMessage msg = timeMsg();
      manager.notifyTimeExtended(AUCTION_ID, msg);
      verify(listener1).onTimeExtended(msg);
    }

    @Test
    @DisplayName("notifyAuctionEnd calls onAuctionEnd on all listeners")
    void notifyAuctionEnd() {
      BidUpdateMessage msg = endMsg();
      manager.notifyAuctionEnd(AUCTION_ID, msg);
      verify(listener1).onAuctionEnd(msg);
    }

    @Test
    @DisplayName("no-op when no listeners are subscribed for the auction")
    void noOpWhenNoListeners() {
      assertDoesNotThrow(() -> manager.notifyBidUpdate(999L, bidMsg()));
    }

    @Test
    @DisplayName("exception in one listener does not prevent others from being notified")
    void exceptionInOneListenerDoesNotBlockOthers() {
      manager.subscribe(AUCTION_ID, listener2);
      doThrow(new RuntimeException("simulated WS error")).when(listener1).onBidUpdate(any());

      BidUpdateMessage msg = bidMsg();
      // Must not propagate
      assertDoesNotThrow(() -> manager.notifyBidUpdate(AUCTION_ID, msg));

      // listener2 must still be called
      verify(listener2).onBidUpdate(msg);
    }
  }
}
