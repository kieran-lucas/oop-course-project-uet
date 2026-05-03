package com.auction.pattern.strategy;

import com.auction.model.Auction;
import com.auction.model.BidTransaction;
import java.math.BigDecimal;

/**
 * Interface Strategy trong Strategy Pattern — định nghĩa thuật toán đặt giá.
 *
 * <p><b>Pattern được áp dụng: Strategy (Behavioral Pattern)</b>
 *
 * <p>Strategy Pattern cho phép định nghĩa nhiều thuật toán đặt giá khác nhau và
 * có thể hoán đổi chúng tại runtime. BidService không cần biết chi tiết cách thực hiện
 * — chỉ cần gọi {@code execute()} trên strategy hiện tại.
 *
 * <p><b>Các ConcreteStrategy:</b>
 * <ul>
 *   <li>{@link ManualBidStrategy} — Người dùng tự nhập giá và xác nhận.</li>
 *   <li>{@link AutoBidStrategy} — Hệ thống tự động đặt giá theo cấu hình của user.</li>
 * </ul>
 *
 * <p><b>Lý do chọn Strategy Pattern:</b>
 * Manual bid và Auto-bid có logic khác nhau nhưng cùng kết quả (cập nhật currentPrice,
 * tạo BidTransaction). Thay vì dùng if/else trong BidService, tách thành strategy riêng
 * giúp code rõ ràng, dễ test từng loại bid độc lập, và dễ thêm loại bid mới sau này.
 *
 * <p><b>Ví dụ sử dụng trong BidService:</b>
 * <pre>
 *   BidStrategy strategy = isAutoBid ? autoBidStrategy : manualBidStrategy;
 *   BidTransaction result = strategy.execute(auction, bidderId, amount, isAutoBid);
 * </pre>
 *
 * <p><b>Liên kết với các file khác:</b>
 * <ul>
 *   <li>{@link ManualBidStrategy} — ConcreteStrategy cho bid thủ công</li>
 *   <li>{@link AutoBidStrategy} — ConcreteStrategy cho auto-bid</li>
 *   <li>{@link com.auction.service.BidService} — Context sử dụng strategy</li>
 *   <li>{@link com.auction.model.Auction} — đối tượng được cập nhật sau mỗi bid</li>
 * </ul>
 */
public interface BidStrategy {

  /**
   * Thực thi thuật toán đặt giá.
   *
   * <p>Implementation cụ thể sẽ:
   * <ol>
   *   <li>Validate: giá phải cao hơn currentPrice, bidderId không phải sellerId, v.v.</li>
   *   <li>Cập nhật auction: {@code auction.setCurrentPrice(amount)},
   *       {@code auction.setLeadingBidderId(bidderId)}</li>
   *   <li>Tạo và trả về {@link BidTransaction} mới (chưa lưu DB — BidService tự lưu)</li>
   * </ol>
   *
   * @param auction   phiên đấu giá đang diễn ra (sẽ được cập nhật in-place)
   * @param bidderId  ID người đặt giá
   * @param amount    số tiền đặt giá
   * @param isAutoBid {@code true} nếu đây là auto-bid, {@code false} nếu manual
   * @return BidTransaction mới chứa thông tin bid vừa thực hiện
   * @throws com.auction.exception.InvalidBidException nếu giá không hợp lệ
   */
  BidTransaction execute(Auction auction, Long bidderId, BigDecimal amount, boolean isAutoBid);
}
