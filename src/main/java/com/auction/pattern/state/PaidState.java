package com.auction.pattern.state;

import com.auction.exception.AuctionClosedException;
import com.auction.model.Auction;
import java.math.BigDecimal;

/**
 * Trạng thái PAID — phiên đấu giá đã hoàn tất và đã được thanh toán.
 *
 * <p>Đây là trạng thái <em>cuối cùng tích cực</em> của vòng đời phiên đấu giá: người thắng cuộc đã
 * thanh toán cho seller, hệ thống coi như giao dịch khép kín. Để bảo toàn tính toàn vẹn của lịch sử
 * giao dịch, mọi hành động làm thay đổi trạng thái phiên đều bị từ chối.
 *
 * <p><b>Phân biệt với {@link CanceledState}:</b> cả hai đều là trạng thái cuối, nhưng PAID phản ánh
 * một phiên kết thúc thành công, trong khi CANCELED phản ánh một phiên bị chấm dứt bất thường.
 *
 * <h2>Hành động được phép / bị từ chối</h2>
 *
 * <p>Mọi hành động đều bị từ chối với cùng một thông điệp thống nhất, làm rõ rằng phiên đã được
 * thanh toán và đóng băng vĩnh viễn.
 */
public class PaidState implements AuctionState {

  /** Mẫu thông điệp lỗi dùng chung cho mọi method bị từ chối. */
  private static final String ERROR_MSG_TEMPLATE =
      "Phiên đấu giá #%d đã được thanh toán và không thể thay đổi.";

  /**
   * {@inheritDoc}
   *
   * @throws AuctionClosedException luôn luôn ném — phiên đã chốt, không nhận thêm giá
   */
  @Override
  public void placeBid(Auction auction, BigDecimal amount, Long bidderId) {
    throw new AuctionClosedException(String.format(ERROR_MSG_TEMPLATE, auction.getId()));
  }

  /**
   * {@inheritDoc}
   *
   * @throws AuctionClosedException luôn luôn ném — không thể đóng một phiên đã hoàn tất
   */
  @Override
  public void close(Auction auction) {
    throw new AuctionClosedException(String.format(ERROR_MSG_TEMPLATE, auction.getId()));
  }

  /**
   * {@inheritDoc}
   *
   * @throws AuctionClosedException luôn luôn ném — bảo toàn tính toàn vẹn của lịch sử
   */
  @Override
  public void edit(Auction auction) {
    throw new AuctionClosedException(String.format(ERROR_MSG_TEMPLATE, auction.getId()));
  }

  /**
   * {@inheritDoc}
   *
   * @throws AuctionClosedException luôn luôn ném — phiên đã đóng, không có gì để gia hạn
   */
  @Override
  public void extend(Auction auction, long extraSeconds) {
    throw new AuctionClosedException(String.format(ERROR_MSG_TEMPLATE, auction.getId()));
  }
}
