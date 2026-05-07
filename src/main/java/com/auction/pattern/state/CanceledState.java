package com.auction.pattern.state;

import com.auction.exception.AuctionClosedException;
import com.auction.model.Auction;
import java.math.BigDecimal;

/**
 * Trạng thái CANCELED — phiên đấu giá đã bị hủy bỏ.
 *
 * <p>Một phiên có thể bị chuyển sang trạng thái này từ {@code OPEN} (khi seller hoặc admin hủy
 * trước giờ bắt đầu) hoặc từ {@code RUNNING} (khi admin force-close vì vi phạm quy định). Đây là
 * một trong hai trạng thái <em>cuối cùng</em> của phiên — không có đường ra khỏi đây, mọi thao tác
 * đều bị chặn.
 *
 * <p><b>Phân biệt với {@link FinishedState}:</b>
 *
 * <ul>
 *   <li>{@code FINISHED}: phiên kết thúc tự nhiên đúng hạn, có người thắng cuộc và chuẩn bị bước
 *       sang giai đoạn thanh toán.
 *   <li>{@code CANCELED}: phiên bị một bên có thẩm quyền (seller/admin) chấm dứt giữa chừng; không
 *       xác định người thắng và không phát sinh giao dịch tài chính.
 * </ul>
 *
 * <h2>Hành động được phép / bị từ chối</h2>
 *
 * <p>Tất cả các hành động đều bị từ chối với cùng một message thống nhất, nhằm cho người gọi biết
 * rõ phiên này đã đóng vĩnh viễn.
 */
public class CanceledState implements AuctionState {

  /** Mẫu thông điệp lỗi dùng chung cho mọi method — đảm bảo phản hồi nhất quán. */
  private static final String ERROR_MSG_TEMPLATE =
      "Phiên đấu giá #%d đã bị hủy và không thể thực hiện thêm thao tác nào.";

  /**
   * {@inheritDoc}
   *
   * @throws AuctionClosedException luôn luôn ném — phiên đã bị hủy, không nhận giá
   */
  @Override
  public void placeBid(Auction auction, BigDecimal amount, Long bidderId) {
    throw new AuctionClosedException(String.format(ERROR_MSG_TEMPLATE, auction.getId()));
  }

  /**
   * {@inheritDoc}
   *
   * @throws AuctionClosedException luôn luôn ném — không thể đóng một phiên đã đóng
   */
  @Override
  public void close(Auction auction) {
    throw new AuctionClosedException(String.format(ERROR_MSG_TEMPLATE, auction.getId()));
  }

  /**
   * {@inheritDoc}
   *
   * @throws AuctionClosedException luôn luôn ném — không cho phép chỉnh sửa phiên đã hủy
   */
  @Override
  public void edit(Auction auction) {
    throw new AuctionClosedException(String.format(ERROR_MSG_TEMPLATE, auction.getId()));
  }

  /**
   * {@inheritDoc}
   *
   * @throws AuctionClosedException luôn luôn ném — phiên đã hủy không cần gia hạn
   */
  @Override
  public void extend(Auction auction, long extraSeconds) {
    throw new AuctionClosedException(String.format(ERROR_MSG_TEMPLATE, auction.getId()));
  }
}
