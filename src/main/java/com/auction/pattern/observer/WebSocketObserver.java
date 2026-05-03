package com.auction.pattern.observer;

import com.auction.controller.AuctionWebSocketHandler;
import com.auction.dto.BidUpdateMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ConcreteObserver trong Observer Pattern — gửi sự kiện đấu giá đến client qua WebSocket.
 *
 * <p><b>Pattern được áp dụng: Observer (Behavioral Pattern)</b>
 *
 * <p>WebSocketObserver là cầu nối giữa tầng Business Logic (BidService, AuctionScheduler)
 * và tầng Transport (WebSocket). Khi AuctionEventManager phát sự kiện:
 * <ol>
 *   <li>WebSocketObserver.onBidUpdate() / onTimeExtended() / onAuctionEnd() được gọi</li>
 *   <li>Observer gọi {@code AuctionWebSocketHandler.broadcast()} để gửi JSON đến client</li>
 *   <li>Tất cả client đang xem phiên nhận được message và cập nhật UI tức thì</li>
 * </ol>
 *
 * <p><b>Mỗi phiên đấu giá có 1 WebSocketObserver riêng.</b> Observer được tạo khi phiên
 * bắt đầu có client kết nối (hoặc khi server khởi động) và được đăng ký vào EventManager.
 * Observer biết mình thuộc phiên nào qua {@code auctionId}.
 *
 * <p><b>Liên kết với các file khác:</b>
 * <ul>
 *   <li>{@link AuctionEventListener} — interface mà class này implement</li>
 *   <li>{@link AuctionEventManager} — Subject gọi các method của class này</li>
 *   <li>{@link AuctionWebSocketHandler} — thực sự gửi message đến client qua WS</li>
 *   <li>{@link com.auction.App} — khởi tạo WebSocketObserver và đăng ký vào EventManager</li>
 * </ul>
 */
public class WebSocketObserver implements AuctionEventListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketObserver.class);

  /** Handler WebSocket thực sự gửi message đến client. */
  private final AuctionWebSocketHandler handler;

  /** ID phiên đấu giá mà observer này theo dõi. */
  private final Long auctionId;

  /**
   * Khởi tạo WebSocketObserver cho 1 phiên đấu giá cụ thể.
   *
   * @param handler   handler WebSocket dùng để broadcast message đến client
   * @param auctionId ID phiên đấu giá observer này theo dõi
   */
  public WebSocketObserver(AuctionWebSocketHandler handler, Long auctionId) {
    this.handler = handler;
    this.auctionId = auctionId;
  }

  /**
   * Xử lý sự kiện có bid mới — broadcast BID_UPDATE đến tất cả client đang xem phiên.
   *
   * <p>Client nhận message sẽ cập nhật: giá hiện tại, tên người dẫn đầu, Bid History Chart.
   *
   * @param msg tin nhắn BID_UPDATE chứa giá mới và người dẫn đầu
   */
  @Override
  public void onBidUpdate(BidUpdateMessage msg) {
    try {
      handler.broadcast(auctionId, msg);
      LOGGER.debug("Đã broadcast BID_UPDATE cho phiên #{}", auctionId);
    } catch (Exception e) {
      LOGGER.error("Lỗi broadcast BID_UPDATE cho phiên #{}: {}", auctionId, e.getMessage());
    }
  }

  /**
   * Xử lý sự kiện gia hạn thời gian (anti-sniping) — broadcast TIME_EXTENDED đến client.
   *
   * <p>Client nhận message sẽ cập nhật countdown timer với endTime mới.
   *
   * @param msg tin nhắn TIME_EXTENDED chứa endTime mới
   */
  @Override
  public void onTimeExtended(BidUpdateMessage msg) {
    try {
      handler.broadcast(auctionId, msg);
      LOGGER.debug("Đã broadcast TIME_EXTENDED cho phiên #{}", auctionId);
    } catch (Exception e) {
      LOGGER.error("Lỗi broadcast TIME_EXTENDED cho phiên #{}: {}", auctionId, e.getMessage());
    }
  }

  /**
   * Xử lý sự kiện phiên kết thúc — broadcast AUCTION_ENDED đến client.
   *
   * <p>Client nhận message sẽ disable nút bid và hiển thị người thắng cuộc.
   *
   * @param msg tin nhắn AUCTION_ENDED chứa thông tin người thắng
   */
  @Override
  public void onAuctionEnd(BidUpdateMessage msg) {
    try {
      handler.broadcast(auctionId, msg);
      LOGGER.debug("Đã broadcast AUCTION_ENDED cho phiên #{}", auctionId);
    } catch (Exception e) {
      LOGGER.error("Lỗi broadcast AUCTION_ENDED cho phiên #{}: {}", auctionId, e.getMessage());
    }
  }

  public Long getAuctionId() {
    return auctionId;
  }
}
