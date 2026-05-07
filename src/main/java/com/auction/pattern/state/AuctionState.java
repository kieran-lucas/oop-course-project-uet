package com.auction.pattern.state;

import com.auction.model.Auction;
import java.math.BigDecimal;

/**
 * Hợp đồng (contract) chung cho mọi trạng thái của phiên đấu giá.
 *
 * <h2>Design Pattern: State</h2>
 *
 * <p><b>Vấn đề cần giải quyết:</b> Một phiên đấu giá có thể đi qua năm trạng thái khác nhau —
 * {@code OPEN}, {@code RUNNING}, {@code FINISHED}, {@code PAID}, và {@code CANCELED}. Mỗi trạng
 * thái cho phép hoặc từ chối những hành động khác nhau. Nếu phân tán logic kiểm tra bằng các khối
 * {@code if/switch} trên trường {@code auction.getStatus()} ở khắp nơi trong code base, hệ thống sẽ
 * rất khó bảo trì, dễ bỏ sót case khi bổ sung trạng thái mới, và vi phạm nguyên tắc Open/Closed.
 *
 * <p><b>Giải pháp State Pattern:</b> Mỗi trạng thái được hiện thực hoá thành một class riêng biệt,
 * tự chịu trách nhiệm trả lời câu hỏi "hành động này có hợp lệ với tôi không?". Nhờ vậy, {@code
 * AuctionService} không cần biết phiên đang ở trạng thái nào — chỉ cần gọi {@code
 * state.placeBid(...)} và để chính state quyết định: thực hiện hành động, hoặc ném exception thích
 * hợp.
 *
 * <p><b>Sơ đồ chuyển trạng thái:</b>
 *
 * <pre>
 *                  startTime đến
 * [OPEN] ──────────────────────────► [RUNNING]
 *   │                                    │
 *   │ seller/admin cancel                │ endTime đến
 *   ▼                                    ▼
 * [CANCELED]                        [FINISHED]
 *                                        │
 *                                        │ thanh toán
 *                                        ▼
 *                                      [PAID]
 * </pre>
 *
 * <p><b>Tóm tắt hành vi của các implementation:</b>
 *
 * <ul>
 *   <li>{@link OpenState}: cho phép {@code edit} và {@code close}; từ chối {@code placeBid} và
 *       {@code extend}.
 *   <li>{@link RunningState}: cho phép {@code placeBid}, {@code extend} và {@code close}; từ chối
 *       {@code edit}.
 *   <li>{@link FinishedState}: chỉ cho phép {@code close} (để chuyển sang PAID); từ chối các hành
 *       động còn lại.
 *   <li>{@link PaidState}: từ chối tất cả — đây là trạng thái cuối tích cực.
 *   <li>{@link CanceledState}: từ chối tất cả — đây là trạng thái cuối tiêu cực.
 * </ul>
 *
 * <p><b>Đối tượng sử dụng interface:</b> {@code AuctionService.getState(auction)} sẽ resolve phiên
 * hiện tại sang đúng implementation; phần còn lại của hệ thống chỉ làm việc với interface này,
 * không cần biết class cụ thể.
 */
public interface AuctionState {

  /**
   * Đặt giá trong phiên đấu giá.
   *
   * <p>Chỉ có {@link RunningState} cho phép thao tác này. Mọi state khác sẽ ném {@link
   * com.auction.exception.AuctionClosedException} kèm thông điệp giải thích lý do.
   *
   * @param auction phiên đấu giá đang được xử lý
   * @param amount giá muốn đặt — bắt buộc phải lớn hơn {@code currentPrice}
   * @param bidderId ID của người đặt giá; không được trùng với {@code sellerId}
   * @throws com.auction.exception.AuctionClosedException nếu state hiện tại không cho phép đặt giá
   * @throws com.auction.exception.InvalidBidException nếu giá đặt vi phạm các ràng buộc nghiệp vụ
   */
  void placeBid(Auction auction, BigDecimal amount, Long bidderId);

  /**
   * Đóng phiên đấu giá để chuyển sang trạng thái cuối ({@code FINISHED} hoặc {@code CANCELED}).
   *
   * <p>{@link OpenState} và {@link RunningState} đều cho phép thao tác này. Các trạng thái cuối
   * ({@link PaidState}, {@link CanceledState}) ném exception, vì việc đóng thêm là vô nghĩa.
   *
   * @param auction phiên đấu giá cần đóng
   * @throws com.auction.exception.AuctionClosedException nếu phiên đã ở một trạng thái cuối
   */
  void close(Auction auction);

  /**
   * Chỉnh sửa thông tin của phiên đấu giá (giá khởi điểm, thời gian, mô tả...).
   *
   * <p>Chỉ {@link OpenState} cho phép. Khi phiên đã chuyển sang {@code RUNNING}, các thông tin này
   * không được phép thay đổi nữa, vì người tham gia đã ra quyết định dựa trên dữ liệu cũ — thay đổi
   * sẽ phá vỡ tính công bằng.
   *
   * @param auction phiên đấu giá cần chỉnh sửa
   * @throws com.auction.exception.AuctionClosedException nếu state hiện tại không cho phép chỉnh
   *     sửa
   */
  void edit(Auction auction);

  /**
   * Gia hạn thời gian kết thúc của phiên — cơ chế anti-sniping.
   *
   * <p>Chỉ {@link RunningState} cho phép. Phương thức này được {@code BidService} gọi tự động khi
   * phát hiện một lượt đặt giá rơi vào khoảng 30 giây cuối, nhằm gia hạn thêm thường là 60 giây để
   * các bidder khác kịp phản ứng.
   *
   * @param auction phiên đấu giá cần gia hạn
   * @param extraSeconds số giây gia hạn thêm (giá trị thường dùng là 60)
   * @throws com.auction.exception.AuctionClosedException nếu state hiện tại không cho phép gia hạn
   */
  void extend(Auction auction, long extraSeconds);
}
