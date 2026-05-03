package com.auction.pattern.state;

import com.auction.exception.AuctionClosedException;
import com.auction.model.Auction;
import java.math.BigDecimal;

/**
 * Trạng thái OPEN — phiên đã được tạo nhưng chưa bắt đầu.
 *
 * <h2>Hành động được phép / bị từ chối</h2>
 * <table border="1">
 *   <tr><th>Hành động</th><th>Kết quả</th><th>Lý do</th></tr>
 *   <tr><td>edit()</td><td>✅ Cho phép</td><td>Phiên chưa có người tham gia, seller còn điều chỉnh được</td></tr>
 *   <tr><td>close()</td><td>✅ Cho phép (cancel)</td><td>Seller/Admin có thể hủy trước khi bắt đầu</td></tr>
 *   <tr><td>placeBid()</td><td>❌ Từ chối</td><td>Phiên chưa bắt đầu</td></tr>
 *   <tr><td>extend()</td><td>❌ Từ chối</td><td>Chưa RUNNING nên không cần gia hạn</td></tr>
 * </table>
 *
 * <p><b>Chuyển trạng thái:</b>
 * <ul>
 *   <li>OPEN → RUNNING: tự động khi đến {@code startTime} (do {@code AuctionScheduler} xử lý)</li>
 *   <li>OPEN → CANCELED: seller/admin hủy trước khi bắt đầu</li>
 * </ul>
 *
 * <p><b>Ví dụ luồng sử dụng trong AuctionService:</b>
 * <pre>{@code
 * AuctionState state = getState(auction); // → new OpenState()
 * state.edit(auction);   // OK: seller sửa giá khởi điểm
 * state.placeBid(...);   // THROW: "Phiên chưa bắt đầu"
 * }</pre>
 */
public class OpenState implements AuctionState {

  /**
   * {@inheritDoc}
   *
   * <p>Từ chối đặt giá vì phiên chưa bắt đầu. Người dùng cần chờ đến {@code startTime}.
   *
   * @throws AuctionClosedException luôn luôn ném, với message giải thích phiên chưa mở
   */
  @Override
  public void placeBid(Auction auction, BigDecimal amount, Long bidderId) {
    throw new AuctionClosedException(
        "Phiên đấu giá #" + auction.getId() + " chưa bắt đầu. "
            + "Thời gian bắt đầu: " + auction.getStartTime()
    );
  }

  /**
   * {@inheritDoc}
   *
   * <p>Cho phép đóng/hủy phiên khi còn ở OPEN. Phương thức này chỉ validate state,
   * việc cập nhật status thực tế do {@code AuctionService} thực hiện sau khi gọi method này.
   *
   * @param auction phiên cần đóng — không throw exception
   */
  @Override
  public void close(Auction auction) {
    // OpenState cho phép close (cancel) — không ném exception
    // AuctionService sẽ set auction.setStatus("CANCELED")
  }

  /**
   * {@inheritDoc}
   *
   * <p>Cho phép chỉnh sửa thông tin phiên vì chưa có người tham gia.
   * Seller có thể thay đổi giá khởi điểm, thời gian, v.v.
   *
   * @param auction phiên cần chỉnh sửa — không throw exception
   */
  @Override
  public void edit(Auction auction) {
    // OpenState cho phép edit — không ném exception
    // AuctionService sẽ thực hiện cập nhật các trường cụ thể
  }

  /**
   * {@inheritDoc}
   *
   * <p>Từ chối gia hạn vì phiên chưa RUNNING. Anti-sniping chỉ áp dụng khi đang diễn ra.
   *
   * @throws AuctionClosedException luôn ném, phiên chưa bắt đầu nên không cần gia hạn
   */
  @Override
  public void extend(Auction auction, long extraSeconds) {
    throw new AuctionClosedException(
        "Không thể gia hạn phiên #" + auction.getId() + " vì phiên chưa bắt đầu"
    );
  }
}
