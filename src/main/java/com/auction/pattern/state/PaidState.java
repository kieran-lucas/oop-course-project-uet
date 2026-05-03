package com.auction.pattern.state;

import com.auction.exception.AuctionClosedException;
import com.auction.model.Auction;
import java.math.BigDecimal;

/**
 * Trạng thái PAID — phiên đã hoàn tất, thanh toán đã được xác nhận.
 *
 * <p>Đây là trạng thái cuối cùng tích cực. Mọi hành động đều bị từ chối vì
 * giao dịch đã hoàn tất và không thể thay đổi — bảo toàn tính toàn vẹn
 * của lịch sử giao dịch.
 *
 * <h2>Hành động được phép / bị từ chối</h2>
 * <p>Tất cả hành động đều từ chối với message "Phiên đã được thanh toán".
 */
public class PaidState implements AuctionState {

  private static final String ERROR_MSG_TEMPLATE =
      "Phiên đấu giá #%d đã được thanh toán và không thể thay đổi.";

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
