package com.auction.pattern.state;

import com.auction.exception.AuctionClosedException;
import com.auction.model.Auction;
import java.math.BigDecimal;

/**
 * Trạng thái OPEN — phiên đấu giá đã được tạo nhưng chưa đến giờ bắt đầu.
 *
 * <p>Đây là trạng thái khởi đầu của vòng đời phiên đấu giá. Trong giai đoạn này, seller vẫn còn
 * quyền điều chỉnh thông tin (giá khởi điểm, thời gian, mô tả...) hoặc hủy bỏ hoàn toàn nếu đổi ý.
 * Mọi hành vi liên quan đến quá trình đấu giá thật sự (đặt giá, gia hạn) đều chưa được phép vì chưa
 * có người tham gia.
 *
 * <h2>Hành động được phép / bị từ chối</h2>
 *
 * <table border="1">
 *   <caption>Quy tắc xử lý hành động trong OpenState</caption>
 *   <tr><th>Hành động</th><th>Kết quả</th><th>Lý do</th></tr>
 *   <tr>
 *     <td>{@code edit()}</td>
 *     <td>✅ Cho phép</td>
 *     <td>Chưa có người tham gia — seller có thể tự do điều chỉnh</td>
 *   </tr>
 *   <tr>
 *     <td>{@code close()}</td>
 *     <td>✅ Cho phép (cancel)</td>
 *     <td>Seller/Admin có quyền hủy trước giờ bắt đầu</td>
 *   </tr>
 *   <tr>
 *     <td>{@code placeBid()}</td>
 *     <td>❌ Từ chối</td>
 *     <td>Phiên chưa chính thức bắt đầu</td>
 *   </tr>
 *   <tr>
 *     <td>{@code extend()}</td>
 *     <td>❌ Từ chối</td>
 *     <td>Anti-sniping chỉ có ý nghĩa khi phiên đang chạy</td>
 *   </tr>
 * </table>
 *
 * <p><b>Chuyển trạng thái:</b>
 *
 * <ul>
 *   <li>OPEN → RUNNING: tự động khi đồng hồ chạm {@code startTime}, do {@code AuctionScheduler}
 *       kích hoạt.
 *   <li>OPEN → CANCELED: khi seller hoặc admin chủ động hủy phiên.
 * </ul>
 *
 * <p><b>Ví dụ luồng sử dụng trong {@code AuctionService}:</b>
 *
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
   * <p>Từ chối mọi yêu cầu đặt giá cho đến khi phiên chính thức bắt đầu. Người dùng cần chờ tới
   * {@code startTime} của phiên.
   *
   * @throws AuctionClosedException luôn luôn ném, kèm theo thời điểm phiên dự kiến mở
   */
  @Override
  public void placeBid(Auction auction, BigDecimal amount, Long bidderId) {
    throw new AuctionClosedException(
        "Phiên đấu giá #"
            + auction.getId()
            + " chưa bắt đầu. "
            + "Thời gian bắt đầu: "
            + auction.getStartTime());
  }

  /**
   * {@inheritDoc}
   *
   * <p>Cho phép đóng (hủy) phiên khi còn ở trạng thái OPEN. Method này chỉ kiểm tra tính hợp lệ về
   * mặt trạng thái; việc cập nhật trường {@code status} thực sự do {@code AuctionService} thực hiện
   * sau khi method trả về.
   *
   * @param auction phiên cần đóng — không ném exception
   */
  @Override
  public void close(Auction auction) {
    // OpenState cho phép close (cancel) — không ném exception.
    // AuctionService sẽ thực hiện auction.setStatus("CANCELED") sau bước này.
  }

  /**
   * {@inheritDoc}
   *
   * <p>Cho phép chỉnh sửa các trường thông tin của phiên vì chưa có người tham gia. Seller có thể
   * thay đổi giá khởi điểm, thời gian bắt đầu/kết thúc, mô tả...
   *
   * @param auction phiên cần chỉnh sửa — không ném exception
   */
  @Override
  public void edit(Auction auction) {
    // OpenState cho phép edit — không ném exception.
    // AuctionService sẽ thực hiện cập nhật các trường cụ thể sau bước này.
  }

  /**
   * {@inheritDoc}
   *
   * <p>Từ chối gia hạn vì phiên chưa diễn ra. Cơ chế anti-sniping chỉ áp dụng khi phiên đang ở
   * {@link RunningState}.
   *
   * @throws AuctionClosedException luôn luôn ném — không có endTime nào để gia hạn
   */
  @Override
  public void extend(Auction auction, long extraSeconds) {
    throw new AuctionClosedException(
        "Không thể gia hạn phiên #" + auction.getId() + " vì phiên chưa bắt đầu");
  }
}
