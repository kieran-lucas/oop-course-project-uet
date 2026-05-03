package com.auction.pattern.strategy;

import com.auction.exception.InvalidBidException;
import com.auction.model.Auction;
import com.auction.model.BidTransaction;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ConcreteStrategy cho đặt giá thủ công — validate và cập nhật auction theo bid của người dùng.
 *
 * <p><b>Pattern được áp dụng: Strategy (Behavioral Pattern)</b>
 *
 * <p>ManualBidStrategy xử lý trường hợp người dùng tự nhập giá và bấm "Đặt giá".
 * Các validation quan trọng:
 * <ul>
 *   <li><b>Giá phải cao hơn giá hiện tại:</b> không cho phép bid bằng hoặc thấp hơn.</li>
 *   <li><b>Seller không được bid sản phẩm của mình:</b> tránh conflict of interest
 *       (seller tự đẩy giá lên để gian lận).</li>
 *   <li><b>Giá phải dương:</b> không cho phép giá âm hoặc bằng 0.</li>
 * </ul>
 *
 * <p><b>Lưu ý:</b> Method {@code execute()} chỉ cập nhật đối tượng {@code auction} trong
 * bộ nhớ và trả về {@code BidTransaction} mới. BidService chịu trách nhiệm lưu vào database
 * và thông báo qua Observer. Điều này đảm bảo Single Responsibility.
 *
 * <p><b>Liên kết với các file khác:</b>
 * <ul>
 *   <li>{@link BidStrategy} — interface mà class này implements</li>
 *   <li>{@link com.auction.service.BidService} — sử dụng strategy này cho bid thủ công</li>
 *   <li>{@link com.auction.model.Auction} — model được cập nhật in-place</li>
 *   <li>{@link InvalidBidException} — thrown khi bid không hợp lệ</li>
 * </ul>
 */
public class ManualBidStrategy implements BidStrategy {

  private static final Logger LOGGER = LoggerFactory.getLogger(ManualBidStrategy.class);

  /**
   * Validate và thực thi bid thủ công.
   *
   * <p>Thứ tự validate:
   * <ol>
   *   <li>Giá không null và dương</li>
   *   <li>Người bid không phải seller của phiên</li>
   *   <li>Giá phải cao hơn currentPrice (strict: > chứ không phải >=)</li>
   * </ol>
   *
   * @param auction   phiên đấu giá đang RUNNING (BidService đã kiểm tra trạng thái)
   * @param bidderId  ID người đặt giá
   * @param amount    số tiền muốn bid
   * @param isAutoBid false cho manual bid (dùng thêm flag để tạo BidTransaction đúng)
   * @return BidTransaction ghi lại thông tin bid (chưa lưu DB)
   * @throws InvalidBidException nếu giá không hợp lệ hoặc seller tự bid
   */
  @Override
  public BidTransaction execute(Auction auction, Long bidderId, BigDecimal amount,
      boolean isAutoBid) {
    // 1. Validate giá
    if (amount == null || amount.signum() <= 0) {
      throw new InvalidBidException("Giá bid phải lớn hơn 0");
    }

    // 2. Seller không được bid sản phẩm của mình
    if (auction.getSellerId() != null && auction.getSellerId().equals(bidderId)) {
      throw new InvalidBidException("Không thể bid sản phẩm của mình");
    }

    // 3. Giá phải cao hơn giá hiện tại (strict greater than)
    if (amount.compareTo(auction.getCurrentPrice()) <= 0) {
      throw new InvalidBidException(
          "Giá bid phải cao hơn giá hiện tại: "
              + auction.getCurrentPrice()
              + ". Giá bạn nhập: " + amount);
    }

    // 4. Cập nhật auction trong bộ nhớ
    auction.setCurrentPrice(amount);
    auction.setLeadingBidderId(bidderId);

    LOGGER.debug("Manual bid: auction={}, bidder={}, amount={}", auction.getId(), bidderId, amount);

    // 5. Trả về BidTransaction (chưa lưu DB — BidService tự lưu)
    return new BidTransaction(auction.getId(), bidderId, amount, isAutoBid);
  }
}
