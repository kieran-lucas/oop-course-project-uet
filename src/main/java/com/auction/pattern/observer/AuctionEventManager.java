package com.auction.pattern.observer;

import com.auction.dto.BidUpdateMessage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Subject (Publisher) trong Observer Pattern — quản lý danh sách listener và phát sự kiện đấu giá.
 *
 * <p><b>Pattern được áp dụng: Observer (Behavioral Pattern)</b>
 *
 * <p>AuctionEventManager đóng vai trò "trung tâm thông báo" của hệ thống:
 *
 * <ul>
 *   <li>Mỗi phiên đấu giá (auctionId) có một danh sách {@link AuctionEventListener} riêng.
 *   <li>Khi có sự kiện (bid mới, gia hạn, kết thúc), method {@code notify*} được gọi → tất cả
 *       listener đang theo dõi phiên đó đều nhận được thông báo.
 *   <li>Listener có thể subscribe/unsubscribe bất cứ lúc nào (khi client vào/rời màn hình).
 * </ul>
 *
 * <p><b>Cấu trúc dữ liệu:</b> {@code Map<Long, List<AuctionEventListener>>} — key là auctionId,
 * value là danh sách listener. Dùng {@code ConcurrentHashMap} và {@code synchronized} để an toàn
 * với multi-thread (nhiều bid xảy ra đồng thời từ nhiều user).
 *
 * <p><b>Liên kết với các file khác:</b>
 *
 * <ul>
 *   <li>{@link AuctionEventListener} — interface Observer mà class này gọi
 *   <li>{@link WebSocketObserver} — ConcreteObserver, subscribe vào manager khi client kết nối WS
 *   <li>{@link com.auction.service.BidService} — gọi {@code notifyBidUpdate()} sau mỗi bid
 *   <li>{@link com.auction.service.AuctionScheduler} — gọi {@code notifyAuctionEnd()} khi hết giờ
 * </ul>
 */
public class AuctionEventManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuctionEventManager.class);

  /**
   * Map auctionId → danh sách listener đang theo dõi phiên đó.
   *
   * <p>ConcurrentHashMap để đảm bảo thread-safe khi nhiều bid xảy ra đồng thời. List bên trong được
   * synchronized trong mỗi method để tránh ConcurrentModificationException.
   */
  private final Map<Long, List<AuctionEventListener>> listeners = new ConcurrentHashMap<>();

  // ── Subscribe / Unsubscribe ──────────────────────────────

  /**
   * Đăng ký listener để nhận thông báo sự kiện của một phiên đấu giá.
   *
   * <p>Được gọi khi client kết nối WebSocket đến phiên đấu giá: {@code subscribe(auctionId, new
   * WebSocketObserver(wsHandler, auctionId))}
   *
   * @param auctionId ID phiên đấu giá cần theo dõi
   * @param listener listener sẽ nhận thông báo (thường là WebSocketObserver)
   */
  public void subscribe(Long auctionId, AuctionEventListener listener) {
    listeners.computeIfAbsent(auctionId, k -> new CopyOnWriteArrayList<>()).add(listener);
    LOGGER.debug(
        "Listener đã subscribe phiên #{}: {}", auctionId, listener.getClass().getSimpleName());
  }

  /**
   * Hủy đăng ký listener khỏi phiên đấu giá.
   *
   * <p>Được gọi khi client ngắt kết nối WebSocket hoặc rời màn hình auction-detail. Nếu không
   * unsubscribe, listener lỗi thời sẽ gây memory leak và lỗi gửi WS message.
   *
   * @param auctionId ID phiên đấu giá
   * @param listener listener cần hủy đăng ký
   */
  public void unsubscribe(Long auctionId, AuctionEventListener listener) {
    List<AuctionEventListener> list = listeners.get(auctionId);
    if (list != null) {
      list.remove(listener);
      LOGGER.debug("Listener đã unsubscribe phiên #{}", auctionId);
    }
  }

  // ── Notify methods ───────────────────────────────────────

  /**
   * Phát sự kiện BID_UPDATE — có người đặt giá mới thành công.
   *
   * <p>Gọi sau khi BidService.placeBid() lưu bid vào database thành công. Tất cả client đang xem
   * phiên sẽ nhận được message và cập nhật UI tức thì.
   *
   * @param auctionId ID phiên đấu giá
   * @param msg tin nhắn chứa giá mới, người dẫn đầu (type = "BID_UPDATE")
   */
  public void notifyBidUpdate(Long auctionId, BidUpdateMessage msg) {
    notifyAll(auctionId, listener -> listener.onBidUpdate(msg), "BID_UPDATE");
  }

  /**
   * Phát sự kiện TIME_EXTENDED — anti-sniping gia hạn thời gian phiên.
   *
   * <p>Gọi trong BidService.placeBid() khi bid xảy ra trong 30 giây cuối. Client cập nhật countdown
   * timer với endTime mới.
   *
   * @param auctionId ID phiên đấu giá
   * @param msg tin nhắn chứa endTime mới (type = "TIME_EXTENDED")
   */
  public void notifyTimeExtended(Long auctionId, BidUpdateMessage msg) {
    notifyAll(auctionId, listener -> listener.onTimeExtended(msg), "TIME_EXTENDED");
  }

  /**
   * Phát sự kiện AUCTION_ENDED — phiên đấu giá kết thúc.
   *
   * <p>Gọi bởi AuctionScheduler khi phiên hết giờ. Client disable nút bid và hiển thị người thắng
   * cuộc.
   *
   * @param auctionId ID phiên đấu giá vừa kết thúc
   * @param msg tin nhắn chứa thông tin người thắng (type = "AUCTION_ENDED")
   */
  public void notifyAuctionEnd(Long auctionId, BidUpdateMessage msg) {
    notifyAll(auctionId, listener -> listener.onAuctionEnd(msg), "AUCTION_ENDED");
  }

  // ── Internal helper ──────────────────────────────────────

  /**
   * Gọi một hành động cho tất cả listener của phiên đấu giá.
   *
   * <p>Dùng snapshot của list để tránh ConcurrentModificationException khi listener unsubscribe
   * trong quá trình iterate. Mỗi listener được gọi trong try-catch riêng để lỗi của 1 listener
   * không ảnh hưởng đến những listener khác.
   *
   * @param auctionId ID phiên đấu giá
   * @param action hành động cần thực thi trên mỗi listener
   * @param eventType tên sự kiện (chỉ dùng để log)
   */
  private void notifyAll(
      Long auctionId, java.util.function.Consumer<AuctionEventListener> action, String eventType) {
    List<AuctionEventListener> list = listeners.get(auctionId);
    if (list == null || list.isEmpty()) {
      return;
    }

    for (AuctionEventListener listener : list) {
      try {
        action.accept(listener);
      } catch (Exception e) {
        LOGGER.error(
            "Lỗi khi notify listener {} sự kiện {} cho phiên #{}: {}",
            listener.getClass().getSimpleName(),
            eventType,
            auctionId,
            e.getMessage());
      }
    }

    LOGGER.debug(
        "Đã notify {} listener(s) sự kiện {} cho phiên #{}", list.size(), eventType, auctionId);
  }
}
