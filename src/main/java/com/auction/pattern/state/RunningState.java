package com.auction.pattern.state;

import com.auction.exception.AuctionClosedException;
import com.auction.exception.InvalidBidException;
import com.auction.model.Auction;
import java.math.BigDecimal;

/**
 * Trạng thái RUNNING — phiên đang diễn ra, nhận đặt giá.
 *
 * <h2>Hành động được phép / bị từ chối</h2>
 * <table border="1">
 *   <tr><th>Hành động</th><th>Kết quả</th><th>Lý do</th></tr>
 *   <tr><td>placeBid()</td><td>✅ Cho phép</td><td>Phiên đang nhận giá</td></tr>
 *   <tr><td>extend()</td><td>✅ Cho phép</td><td>Anti-sniping gia hạn khi bid cuối giờ</td></tr>
 *   <tr><td>close()</td><td>✅ Cho phép</td><td>Admin có thể kết thúc sớm</td></tr>
 *   <tr><td>edit()</td><td>❌ Từ chối</td><td>Không công bằng khi đang có người tham gia</td></tr>
 * </table>
 *
 * <p><b>Validation trong placeBid():</b> RunningState kiểm tra các điều kiện cơ bản.
 * Validation phức tạp hơn (anti-sniping, auto-bid trigger) do {@code BidService} xử lý
 * sau khi state đã cho phép.
 *
 * <p><b>Chuyển trạng thái:</b>
 * <ul>
 *   <li>RUNNING → FINISHED: tự động khi đến {@code endTime} (do {@code AuctionScheduler})</li>
 *   <li>RUNNING → CANCELED: admin force-close phiên vi phạm</li>
 * </ul>
 *
 * <p><b>Ví dụ luồng sử dụng trong BidService:</b>
 * <pre>{@code
 * AuctionState state = auctionService.getState(auction); // → new RunningState()
 * state.placeBid(auction, amount, bidderId);  // OK nếu amount > currentPrice
 * state.edit(auction);                        // THROW: "Không thể sửa khi đang diễn ra"
 * }</pre>
 */
public class RunningState implements AuctionState {

  /**
   * {@inheritDoc}
   *
   * <p>Validate giá đặt và kiểm tra seller không tự bid. Nếu hợp lệ, cập nhật giá trên
   * auction object (in-memory). {@code BidService} chịu trách nhiệm persist xuống DB
   * và notify observers sau khi method này trả về thành công.
   *
   * @throws InvalidBidException nếu amount ≤ currentPrice hoặc seller tự bid
   */
  @Override
  public void placeBid(Auction auction, BigDecimal amount, Long bidderId) {
    // Validate: giá phải cao hơn giá hiện tại
    if (amount.compareTo(auction.getCurrentPrice()) <= 0) {
      throw new InvalidBidException(
          "Giá đặt " + amount + " phải cao hơn giá hiện tại " + auction.getCurrentPrice()
      );
    }

    // Validate: seller không được tự bid phiên của mình
    if (bidderId.equals(auction.getSellerId())) {
      throw new InvalidBidException("Seller không thể đặt giá cho phiên của chính mình");
    }

    // Cập nhật in-memory — BidService sẽ persist và notify sau
    auction.setCurrentPrice(amount);
    auction.setLeadingBidderId(bidderId);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Cho phép admin/scheduler đóng phiên sớm hoặc khi đến endTime.
   * Không ném exception.
   */
  @Override
  public void close(Auction auction) {
    // RunningState cho phép close — AuctionService sẽ set status "FINISHED" hoặc "CANCELED"
  }

  /**
   * {@inheritDoc}
   *
   * <p>Từ chối chỉnh sửa khi phiên đang diễn ra để đảm bảo công bằng cho người tham gia.
   *
   * @throws AuctionClosedException luôn ném với message giải thích lý do
   */
  @Override
  public void edit(Auction auction) {
    throw new AuctionClosedException(
        "Không thể chỉnh sửa phiên #" + auction.getId()
            + " khi đang diễn ra. Dừng phiên trước khi chỉnh sửa."
    );
  }

  /**
   * {@inheritDoc}
   *
   * <p>Gia hạn thời gian kết thúc. Được gọi bởi {@code BidService} khi phát hiện
   * bid trong 30 giây cuối (anti-sniping). Cập nhật endTime in-memory;
   * {@code BidService} persist và broadcast {@code TIME_EXTENDED} qua WebSocket.
   *
   * @param extraSeconds số giây gia hạn (thường là 60)
   */
  @Override
  public void extend(Auction auction, long extraSeconds) {
    auction.setEndTime(auction.getEndTime().plusSeconds(extraSeconds));
  }
}
