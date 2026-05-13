package com.auction.pattern.state;

import com.auction.exception.AuctionClosedException;
import com.auction.exception.InvalidBidException;
import com.auction.model.Auction;
import java.math.BigDecimal;

/**
 * Trạng thái RUNNING — phiên đấu giá đang diễn ra và sẵn sàng nhận giá.
 *
 * <p>Đây là trạng thái "trung tâm" trong vòng đời của một phiên đấu giá. Tại đây, các bidder thực
 * sự cạnh tranh với nhau, cơ chế anti-sniping được kích hoạt, và mọi luật chơi nghiêm ngặt nhất
 * được áp dụng để đảm bảo công bằng.
 *
 * <h2>Hành động được phép / bị từ chối</h2>
 *
 * <table border="1">
 *   <caption>Quy tắc xử lý hành động trong RunningState</caption>
 *   <tr><th>Hành động</th><th>Kết quả</th><th>Lý do</th></tr>
 *   <tr>
 *     <td>{@code placeBid()}</td>
 *     <td>✅ Cho phép</td>
 *     <td>Đây là trạng thái duy nhất nhận giá đặt</td>
 *   </tr>
 *   <tr>
 *     <td>{@code extend()}</td>
 *     <td>✅ Cho phép</td>
 *     <td>Anti-sniping kích hoạt khi có bid sát giờ kết thúc</td>
 *   </tr>
 *   <tr>
 *     <td>{@code close()}</td>
 *     <td>✅ Cho phép</td>
 *     <td>Admin có thể chủ động kết thúc sớm khi cần thiết</td>
 *   </tr>
 *   <tr>
 *     <td>{@code edit()}</td>
 *     <td>❌ Từ chối</td>
 *     <td>Bidder đã quyết định dựa trên thông tin cũ — sửa giữa chừng là không công bằng</td>
 *   </tr>
 * </table>
 *
 * <p><b>Phạm vi validation trong {@code placeBid()}:</b> RunningState chỉ kiểm tra hai ràng buộc cơ
 * bản — giá phải cao hơn giá hiện tại và seller không được tự đặt giá. Các luật phức tạp hơn
 * (anti-sniping, kích hoạt auto-bid, quản lý concurrency) thuộc phạm vi của {@code BidService} và
 * được thực thi sau khi state đã chấp nhận thao tác.
 *
 * <p><b>Chuyển trạng thái:</b>
 *
 * <ul>
 *   <li>RUNNING → FINISHED: tự động khi đồng hồ chạm {@code endTime}; do {@code AuctionScheduler}
 *       kích hoạt.
 *   <li>RUNNING → CANCELED: khi admin force-close vì phát hiện vi phạm.
 * </ul>
 *
 * <p><b>Ví dụ luồng sử dụng trong {@code BidService}:</b>
 *
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
   * <p>Phương thức kiểm tra hai ràng buộc cốt lõi: giá đặt phải vượt giá hiện tại, và người đặt giá
   * không được trùng với seller. Nếu hợp lệ, giá mới và người dẫn đầu sẽ được cập nhật ngay trên
   * object {@link Auction} (chỉ trong bộ nhớ).
   *
   * <p>Trách nhiệm persist dữ liệu xuống DB và phát thông báo cho các observer thuộc về {@code
   * BidService} — được thực thi sau khi method này trả về thành công.
   *
   * @throws InvalidBidException nếu {@code amount} ≤ {@code currentPrice}, hoặc nếu {@code
   *     bidderId} trùng với {@code sellerId} của phiên
   */
  @Override
  public void placeBid(Auction auction, BigDecimal amount, Long bidderId) {
    // Ràng buộc 1: giá đặt phải cao hơn giá hiện tại của phiên
    if (amount.compareTo(auction.getCurrentPrice()) <= 0) {
      throw new InvalidBidException(
          "Giá đặt " + amount + " phải cao hơn giá hiện tại " + auction.getCurrentPrice());
    }

    // Ràng buộc 2: seller không được tự đặt giá cho phiên của chính mình
    if (bidderId.equals(auction.getSellerId())) {
      throw new InvalidBidException("Seller không thể đặt giá cho phiên của chính mình");
    }

    // Ràng buộc 3: bidder đang dẫn đầu không được tự đặt giá tiếp (tránh bid ảo, tăng giá ảo)
    if (bidderId.equals(auction.getLeadingBidderId())) {
      throw new InvalidBidException("Bạn đang là người dẫn đầu, không thể đặt giá tiếp.");
    }

    // Cập nhật in-memory — BidService sẽ chịu trách nhiệm persist & notify sau bước này
    auction.setCurrentPrice(amount);
    auction.setLeadingBidderId(bidderId);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Cho phép đóng phiên — có thể do admin chủ động kết thúc sớm, hoặc do scheduler kích hoạt khi
   * đến {@code endTime}. Method này không ném exception; việc cập nhật trường {@code status} thành
   * {@code "FINISHED"} hoặc {@code "CANCELED"} do {@code AuctionService} đảm nhiệm sau bước này.
   */
  @Override
  public void close(Auction auction) {
    // RunningState cho phép close — không ném exception.
    // AuctionService sẽ quyết định set status "FINISHED" hay "CANCELED" tuỳ ngữ cảnh.
  }

  /**
   * {@inheritDoc}
   *
   * <p>Từ chối tuyệt đối việc chỉnh sửa thông tin phiên trong lúc đang diễn ra, nhằm bảo vệ tính
   * công bằng cho các bidder đã ra quyết định dựa trên dữ liệu hiện hành.
   *
   * @throws AuctionClosedException luôn luôn ném, kèm theo gợi ý dừng phiên trước khi sửa
   */
  @Override
  public void edit(Auction auction) {
    throw new AuctionClosedException(
        "Không thể chỉnh sửa phiên #"
            + auction.getId()
            + " khi đang diễn ra. Dừng phiên trước khi chỉnh sửa.");
  }

  /**
   * {@inheritDoc}
   *
   * <p>Cập nhật {@code endTime} của phiên trong bộ nhớ. Method này được {@code BidService} gọi khi
   * phát hiện một lượt đặt giá rơi vào 30 giây cuối — cơ chế anti-sniping. Sau khi state cập nhật
   * thành công, {@code BidService} sẽ persist xuống DB và phát sự kiện {@code TIME_EXTENDED} qua
   * WebSocket cho các client.
   *
   * @param extraSeconds số giây gia hạn thêm (thường là 60)
   */
  @Override
  public void extend(Auction auction, long extraSeconds) {
    auction.setEndTime(auction.getEndTime().plusSeconds(extraSeconds));
  }
}
