package com.auction.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO cho yêu cầu tạo phiên đấu giá mới.
 *
 * <p>Chỉ Seller mới có quyền tạo phiên đấu giá (kiểm tra role từ JWT). Seller chọn sản phẩm của
 * mình, đặt giá khởi điểm, và lên lịch thời gian bắt đầu/kết thúc.
 *
 * <p>Ví dụ JSON:
 *
 * <pre>
 * {
 *   "itemId": 42,
 *   "startingPrice": 1000000,
 *   "startTime": "2026-04-01T09:00:00",
 *   "endTime": "2026-04-01T21:00:00"
 * }
 * </pre>
 *
 * <p>Validate phía server:
 *
 * <ul>
 *   <li>itemId phải tồn tại trong bảng items (NotFoundException nếu không)
 *   <li>Item phải thuộc về seller đang request (không được tạo phiên cho item người khác)
 *   <li>startingPrice &gt; 0
 *   <li>endTime &gt; startTime (phiên phải có thời lượng dương)
 *   <li>startTime có thể ở tương lai (phiên lên lịch trước) hoặc hiện tại (bắt đầu ngay)
 * </ul>
 *
 * <p>Khi tạo thành công, Auction mới có status = "OPEN". AuctionScheduler sẽ tự động chuyển sang
 * "RUNNING" khi đến startTime, và "FINISHED" khi đến endTime.
 */
public class CreateAuctionRequest {

  private Long itemId;
  private BigDecimal startingPrice;
  private LocalDateTime startTime;
  private LocalDateTime endTime;

  public CreateAuctionRequest() {}

  public CreateAuctionRequest(
      Long itemId, BigDecimal startingPrice, LocalDateTime startTime, LocalDateTime endTime) {
    this.itemId = itemId;
    this.startingPrice = startingPrice;
    this.startTime = startTime;
    this.endTime = endTime;
  }

  // === Getters & Setters ===

  public Long getItemId() {
    return itemId;
  }

  public void setItemId(Long itemId) {
    this.itemId = itemId;
  }

  public BigDecimal getStartingPrice() {
    return startingPrice;
  }

  public void setStartingPrice(BigDecimal startingPrice) {
    this.startingPrice = startingPrice;
  }

  public LocalDateTime getStartTime() {
    return startTime;
  }

  public void setStartTime(LocalDateTime startTime) {
    this.startTime = startTime;
  }

  public LocalDateTime getEndTime() {
    return endTime;
  }

  public void setEndTime(LocalDateTime endTime) {
    this.endTime = endTime;
  }
}
