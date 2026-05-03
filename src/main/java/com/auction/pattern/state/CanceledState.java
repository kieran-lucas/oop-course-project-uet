package com.auction.pattern.state;

import com.auction.exception.AuctionClosedException;
import com.auction.model.Auction;
import java.math.BigDecimal;

/**
 * Trạng thái CANCELED — phiên bị hủy (từ OPEN hoặc RUNNING).
 *
 * <p>Phiên có thể bị hủy bởi Seller (khi OPEN) hoặc Admin (bất kỳ lúc nào).
 * Đây là trạng thái cuối cùng tiêu cực — mọi hành động đều bị từ chối.
 *
 * <p><b>Khác với FINISHED:</b>
 * <ul>
 *   <li>FINISHED: phiên diễn ra bình thường và kết thúc đúng hạn, có người thắng</li>
 *   <li>CANCELED: phiên bị dừng giữa chừng bởi con người, không có người thắng</li>
 * </ul>
 *
 * <h2>Hành động được phép / bị từ chối</h2>
 * <p>Tất cả hành động đều từ chối với message "Phiên đã bị hủy".
 */
public class CanceledState implements AuctionState {

  private static final String ERROR_MSG_TEMPLATE =
      "Phiên đấu giá #%d đã bị hủy và không thể thực hiện thêm thao tác nào.";

  @Override
  public void placeBid(Auction auction, BigDecimal amount, Long bidderId) {
    throw new AuctionClosedException(String.format(ERROR_MSG_TEMPLATE, auction.getId()));
  }

  @Override
  public void close(Auction auction) {
    throw new AuctionClosedException(String.format(ERROR_MSG_TEMPLATE, auction.getId()));
  }

  @Override
  public void edit(Auction auction) {
    throw new AuctionClosedException(String.format(ERROR_MSG_TEMPLATE, auction.getId()));
  }

  @Override
  public void extend(Auction auction, long extraSeconds) {
    throw new AuctionClosedException(String.format(ERROR_MSG_TEMPLATE, auction.getId()));
  }
}
