package com.auction.pattern.state;

import com.auction.model.Auction;
import java.math.BigDecimal;

/**
 * Interface định nghĩa hợp đồng cho tất cả trạng thái của phiên đấu giá.
 *
 * <h2>Design Pattern: State</h2>
 *
 * <p><b>Vấn đề cần giải quyết:</b> Phiên đấu giá có 5 trạng thái (OPEN, RUNNING, FINISHED,
 * PAID, CANCELED). Mỗi trạng thái cho phép/từ chối các hành động khác nhau. Nếu dùng
 * {@code if/switch} kiểm tra {@code auction.getStatus()} ở mọi nơi, code sẽ rất khó
 * bảo trì và dễ sót case.
 *
 * <p><b>Giải pháp State Pattern:</b> Mỗi trạng thái là một class riêng, tự quyết định
 * hành động nào được phép. {@code AuctionService} không cần if/else — chỉ cần gọi
 * {@code state.placeBid()}, state tự biết phải làm gì hoặc ném exception.
 *
 * <p><b>Sơ đồ trạng thái:</b>
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
 * <p><b>Mỗi state implement interface này khác nhau:</b>
 * <ul>
 *   <li>{@code OpenState}: cho phép edit, từ chối placeBid</li>
 *   <li>{@code RunningState}: cho phép placeBid + extend, từ chối edit</li>
 *   <li>{@code FinishedState}: từ chối mọi hành động trừ close (đã close rồi)</li>
 *   <li>{@code PaidState}: từ chối tất cả</li>
 *   <li>{@code CanceledState}: từ chối tất cả</li>
 * </ul>
 *
 * <p><b>Ai dùng interface này:</b>
 * {@code AuctionService.getState(auction)} → resolve sang đúng implementation
 * → gọi method trên interface → không cần biết state cụ thể.
 */
public interface AuctionState {

  /**
   * Đặt giá trong phiên đấu giá.
   *
   * <p>Chỉ {@code RunningState} cho phép. Các state khác ném
   * {@link com.auction.exception.AuctionClosedException}.
   *
   * @param auction  phiên đấu giá đang xử lý
   * @param amount   giá muốn đặt (phải > currentPrice)
   * @param bidderId ID của người đặt giá
   * @throws com.auction.exception.AuctionClosedException nếu state không cho phép bid
   * @throws com.auction.exception.InvalidBidException    nếu giá không hợp lệ
   */
  void placeBid(Auction auction, BigDecimal amount, Long bidderId);

  /**
   * Đóng phiên đấu giá (chuyển sang FINISHED hoặc CANCELED).
   *
   * <p>{@code OpenState} và {@code RunningState} cho phép. Các state cuối (PAID, CANCELED)
   * ném exception vì không thể đóng thêm.
   *
   * @param auction phiên đấu giá cần đóng
   * @throws com.auction.exception.AuctionClosedException nếu phiên đã ở trạng thái cuối
   */
  void close(Auction auction);

  /**
   * Chỉnh sửa thông tin phiên đấu giá (giá khởi điểm, thời gian, v.v.).
   *
   * <p>Chỉ {@code OpenState} cho phép. Khi phiên đã RUNNING, thông tin không thể thay đổi
   * vì người tham gia đã quyết định dựa trên thông tin cũ.
   *
   * @param auction phiên đấu giá cần chỉnh sửa
   * @throws com.auction.exception.AuctionClosedException nếu state không cho phép edit
   */
  void edit(Auction auction);

  /**
   * Gia hạn thời gian kết thúc phiên (anti-sniping).
   *
   * <p>Chỉ {@code RunningState} cho phép. Được gọi tự động bởi {@code BidService}
   * khi có bid trong 30 giây cuối — gia hạn thêm 60 giây.
   *
   * @param auction        phiên đấu giá cần gia hạn
   * @param extraSeconds   số giây gia hạn thêm (thường = 60)
   * @throws com.auction.exception.AuctionClosedException nếu state không cho phép gia hạn
   */
  void extend(Auction auction, long extraSeconds);
}
