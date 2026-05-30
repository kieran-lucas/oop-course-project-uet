package com.auction.pattern.state;

import com.auction.exception.AuctionClosedException;
import com.auction.model.Auction;
import java.math.BigDecimal;

/**
 * Trạng thái SETTLING — phiên đang được khóa tạm thời để chốt kết quả và xử lý thanh toán.
 *
 * <p>SETTLING là trạng thái trung gian ngắn ngủi giữa {@link RunningState} và {@link
 * FinishedState}. Khi {@code AuctionScheduler} phát hiện một phiên đã hết giờ, nó chuyển phiên sang
 * SETTLING trước khi thực hiện các bước settlement (xác định người thắng, chuyển tiền, cập nhật
 * item status). Cơ chế này tránh race condition trong môi trường multi-thread: nếu nhiều thread
 * cùng phát hiện phiên hết giờ, chỉ thread nào chuyển được phiên sang SETTLING thành công mới tiếp
 * tục xử lý.
 *
 * <h2>Hành động được phép / bị từ chối</h2>
 *
 * <p>Mọi hành động đều bị từ chối trong SETTLING để đảm bảo trạng thái phiên không thay đổi trong
 * lúc hệ thống đang tính toán kết quả và thực hiện giao dịch tài chính.
 *
 * <table border="1">
 *   <caption>Quy tắc xử lý hành động trong SettlingState</caption>
 *   <tr><th>Hành động</th><th>Kết quả</th><th>Lý do</th></tr>
 *   <tr><td>{@code placeBid()}</td><td>❌ Từ chối</td><td>Phiên đã đóng nhận giá</td></tr>
 *   <tr><td>{@code close()}</td><td>❌ Từ chối</td><td>Settlement tự quản lý việc chuyển trạng thái</td></tr>
 *   <tr><td>{@code edit()}</td><td>❌ Từ chối</td><td>Winner/price đã chốt, không được thay đổi</td></tr>
 *   <tr><td>{@code extend()}</td><td>❌ Từ chối</td><td>Phiên đã hết giờ, không còn ý nghĩa gia hạn</td></tr>
 * </table>
 *
 * <p><b>Chuyển trạng thái:</b> SETTLING → FINISHED (do settlement process hoàn tất thành công) hoặc
 * SETTLING → CANCELED (nếu settlement gặp lỗi không thể phục hồi).
 */
public class SettlingState implements AuctionState {

  /** Mẫu thông điệp lỗi dùng chung; tham số thứ hai là tên hành động bị chặn. */
  private static final String ERROR_MSG_TEMPLATE = "Auction #%d is being settled. Cannot %s.";

  /**
   * {@inheritDoc}
   *
   * @throws AuctionClosedException luôn luôn ném — phiên đã đóng nhận giá, đang ở giai đoạn chốt
   */
  @Override
  public void placeBid(Auction auction, BigDecimal amount, Long bidderId) {
    throw new AuctionClosedException(
        String.format(ERROR_MSG_TEMPLATE, auction.getId(), "place a bid"));
  }

  /**
   * {@inheritDoc}
   *
   * @throws AuctionClosedException luôn luôn ném — settlement tự quản lý chuyển trạng thái nội bộ
   */
  @Override
  public void close(Auction auction) {
    throw new AuctionClosedException(
        String.format(ERROR_MSG_TEMPLATE, auction.getId(), "close the auction"));
  }

  /**
   * {@inheritDoc}
   *
   * @throws AuctionClosedException luôn luôn ném — winner/price đã được chốt, không cho phép sửa
   */
  @Override
  public void edit(Auction auction) {
    throw new AuctionClosedException(String.format(ERROR_MSG_TEMPLATE, auction.getId(), "edit"));
  }

  /**
   * {@inheritDoc}
   *
   * @throws AuctionClosedException luôn luôn ném — phiên đã hết giờ, gia hạn không còn ý nghĩa
   */
  @Override
  public void extend(Auction auction, long extraSeconds) {
    throw new AuctionClosedException(String.format(ERROR_MSG_TEMPLATE, auction.getId(), "extend"));
  }
}
