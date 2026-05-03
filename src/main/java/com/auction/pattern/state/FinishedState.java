package com.auction.pattern.state;

import com.auction.exception.AuctionClosedException;
import com.auction.model.Auction;
import java.math.BigDecimal;

/**
 * Trạng thái FINISHED — phiên đã kết thúc, đang chờ thanh toán.
 *
 * <p>Đây là trạng thái trung gian giữa RUNNING và PAID. Phiên đã có người thắng
 * ({@code leadingBidderId}) nhưng giao dịch tài chính chưa hoàn tất.
 *
 * <h2>Hành động được phép / bị từ chối</h2>
 * <table border="1">
 *   <tr><th>Hành động</th><th>Kết quả</th></tr>
 *   <tr><td>placeBid()</td><td>❌ "Phiên đã kết thúc"</td></tr>
 *   <tr><td>edit()</td><td>❌ "Phiên đã kết thúc"</td></tr>
 *   <tr><td>extend()</td><td>❌ "Phiên đã kết thúc"</td></tr>
 *   <tr><td>close()</td><td>✅ Cho phép (chuyển sang PAID)</td></tr>
 * </table>
 *
 * <p><b>Chuyển trạng thái:</b> FINISHED → PAID (khi seller xác nhận thanh toán).
 */
public class FinishedState implements AuctionState {

  private static final String ERROR_MSG_TEMPLATE =
      "Phiên đấu giá #%d đã kết thúc. Không thể %s.";

  @Override
  public void placeBid(Auction auction, BigDecimal amount, Long bidderId) {
    throw new AuctionClosedException(
        String.format(ERROR_MSG_TEMPLATE, auction.getId(), "đặt giá")
    );
  }

  /**
   * {@inheritDoc}
   *
   * <p>Cho phép chuyển sang PAID sau khi thanh toán hoàn tất.
   */
  @Override
  public void close(Auction auction) {
    // FinishedState cho phép close → PAID. AuctionService sẽ set status "PAID"
  }

  @Override
  public void edit(Auction auction) {
    throw new AuctionClosedException(
        String.format(ERROR_MSG_TEMPLATE, auction.getId(), "chỉnh sửa")
    );
  }

  @Override
  public void extend(Auction auction, long extraSeconds) {
    throw new AuctionClosedException(
        String.format(ERROR_MSG_TEMPLATE, auction.getId(), "gia hạn")
    );
  }
}
