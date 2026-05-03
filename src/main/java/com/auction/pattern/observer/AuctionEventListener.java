package com.auction.pattern.observer;

import com.auction.dto.BidUpdateMessage;

/**
 * Interface Observer trong Observer Pattern — định nghĩa hợp đồng lắng nghe sự kiện đấu giá.
 *
 * <p><b>Pattern được áp dụng: Observer (Behavioral Pattern)</b>
 *
 * <p>Trong Observer Pattern:
 * <ul>
 *   <li><b>Subject (Publisher):</b> {@link AuctionEventManager} — quản lý danh sách listeners
 *       và phát sự kiện khi có bid mới, gia hạn thời gian, phiên kết thúc.</li>
 *   <li><b>Observer (Subscriber):</b> Interface này — định nghĩa các callback mà Subject gọi.</li>
 *   <li><b>ConcreteObserver:</b> {@link WebSocketObserver} — implements interface này, gửi
 *       {@link BidUpdateMessage} qua WebSocket đến tất cả client đang xem phiên.</li>
 * </ul>
 *
 * <p><b>Lý do chọn Observer Pattern:</b>
 * BidService cần thông báo cho nhiều client đang xem cùng 1 phiên mỗi khi có bid mới.
 * Nếu BidService giữ tham chiếu trực tiếp đến WebSocket handler, sẽ tạo coupling chặt.
 * Observer Pattern tách rời BidService (không biết ai đang lắng nghe) khỏi
 * WebSocket layer (không biết bid logic), giúp dễ test và mở rộng.
 *
 * <p><b>Ví dụ luồng sự kiện:</b>
 * <pre>
 *   User A bid 500,000đ
 *   → BidService.placeBid() thành công
 *   → AuctionEventManager.notifyBidUpdate(auctionId, msg)
 *   → AuctionEventListener.onBidUpdate(msg)  ← interface này được gọi
 *   → WebSocketObserver gửi JSON đến tất cả client đang xem phiên
 * </pre>
 *
 * <p><b>Liên kết với các file khác:</b>
 * <ul>
 *   <li>{@link AuctionEventManager} — Subject, gọi các method của interface này</li>
 *   <li>{@link WebSocketObserver} — ConcreteObserver, implements interface này</li>
 *   <li>{@link com.auction.service.BidService} — gọi EventManager sau mỗi bid thành công</li>
 *   <li>{@link BidUpdateMessage} — DTO chứa thông tin sự kiện gửi qua WebSocket</li>
 * </ul>
 */
public interface AuctionEventListener {

  /**
   * Được gọi khi có bid mới thành công trong phiên đấu giá.
   *
   * <p>Client nhận sự kiện này sẽ cập nhật:
   * <ul>
   *   <li>Giá hiện tại (currentPrice)</li>
   *   <li>Tên người dẫn đầu (leadingBidderUsername)</li>
   *   <li>Thêm data point vào Bid History Chart</li>
   * </ul>
   *
   * @param msg tin nhắn chứa thông tin bid mới (type = "BID_UPDATE")
   */
  void onBidUpdate(BidUpdateMessage msg);

  /**
   * Được gọi khi anti-sniping gia hạn thời gian phiên (bid trong 30 giây cuối).
   *
   * <p>Client nhận sự kiện này sẽ cập nhật countdown timer với endTime mới.
   *
   * @param msg tin nhắn chứa endTime mới (type = "TIME_EXTENDED")
   */
  void onTimeExtended(BidUpdateMessage msg);

  /**
   * Được gọi khi phiên đấu giá kết thúc (hết giờ hoặc bị hủy).
   *
   * <p>Client nhận sự kiện này sẽ:
   * <ul>
   *   <li>Disable nút "Đặt giá"</li>
   *   <li>Hiển thị "Người thắng: [username]" và giá cuối cùng</li>
   * </ul>
   *
   * @param msg tin nhắn chứa thông tin người thắng (type = "AUCTION_ENDED")
   */
  void onAuctionEnd(BidUpdateMessage msg);
}
