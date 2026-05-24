package com.auction.pattern.observer;

import com.auction.controller.AuctionWebSocketHandler;
import com.auction.dto.BidUpdateMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ConcreteObserver trong Observer Pattern — gửi sự kiện đấu giá đến client qua WebSocket.
 *
 * <p>Mỗi phiên đấu giá đang chạy ({@code RUNNING}) sẽ có một {@code WebSocketObserver} được đăng ký
 * vào {@link com.auction.pattern.observer.AuctionEventManager}. Khi có sự kiện xảy ra (bid mới, gia
 * hạn thời gian, kết thúc phiên), {@code AuctionEventManager} gọi các phương thức tương ứng trên
 * tất cả observer đã đăng ký; observer này chuyển tiếp thông điệp xuống {@link
 * AuctionWebSocketHandler} để broadcast đến mọi client đang theo dõi phiên.
 *
 * <p>{@code auctionId} được lưu trong observer để chỉ broadcast đến đúng phòng WebSocket của phiên
 * đó — tránh làm nhiễu client đang theo dõi phiên khác.
 *
 * <p><b>Vòng đời:</b> Observer được tạo khi phiên bắt đầu và hủy đăng ký khi phiên kết thúc hoặc bị
 * hủy bỏ, tránh rò rỉ bộ nhớ (listener leak).
 */
public class WebSocketObserver implements AuctionEventListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketObserver.class);

  /** Handler WebSocket dùng để gửi thông điệp đến client theo auctionId. */
  private final AuctionWebSocketHandler handler;

  /** ID phiên đấu giá mà observer này theo dõi — dùng để định tuyến broadcast. */
  private final Long auctionId;

  /**
   * Tạo observer cho một phiên đấu giá cụ thể.
   *
   * @param handler WebSocket handler có khả năng broadcast đến nhiều client cùng lúc
   * @param auctionId ID phiên đấu giá cần theo dõi
   */
  public WebSocketObserver(AuctionWebSocketHandler handler, Long auctionId) {
    this.handler = handler;
    this.auctionId = auctionId;
  }

  /**
   * Gửi thông báo có giá mới đến tất cả client đang xem phiên.
   *
   * <p>Được {@code AuctionEventManager} gọi mỗi khi có lượt đặt giá hợp lệ. Thông điệp chứa giá
   * mới, ID người đặt và thời điểm đặt để client cập nhật giao diện real-time.
   *
   * @param msg thông điệp chứa thông tin giá đặt mới
   */
  @Override
  public void onBidUpdate(BidUpdateMessage msg) {
    handler.broadcast(auctionId, msg);
  }

  /**
   * Gửi thông báo gia hạn thời gian đến tất cả client đang xem phiên.
   *
   * <p>Được gọi khi cơ chế anti-sniping kích hoạt — phát hiện bid đặt trong 30 giây cuối và gia hạn
   * thêm 60 giây. Client cần cập nhật đồng hồ đếm ngược theo {@code endTime} mới trong thông điệp.
   *
   * @param msg thông điệp chứa {@code endTime} mới sau khi gia hạn
   */
  @Override
  public void onTimeExtended(BidUpdateMessage msg) {
    handler.broadcast(auctionId, msg);
  }

  /**
   * Gửi thông báo kết thúc phiên đến tất cả client đang xem phiên.
   *
   * <p>Được gọi khi phiên chuyển sang trạng thái {@code FINISHED} hoặc {@code CANCELED}. Client
   * nhận được thông điệp này sẽ hiển thị kết quả cuối cùng và ngừng nhận bid.
   *
   * @param msg thông điệp chứa kết quả phiên (winner, giá thắng, hoặc lý do hủy)
   */
  @Override
  public void onAuctionEnd(BidUpdateMessage msg) {
    handler.broadcast(auctionId, msg);
  }

  /**
   * Trả về ID phiên đấu giá mà observer này được gắn vào.
   *
   * <p>Dùng bởi {@code AuctionEventManager} khi cần hủy đăng ký observer theo ID phiên.
   *
   * @return ID phiên đấu giá
   */
  public Long getAuctionId() {
    return auctionId;
  }
}
