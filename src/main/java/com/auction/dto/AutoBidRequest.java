package com.auction.dto;

import java.math.BigDecimal;

/**
 * DTO cho yêu cầu thiết lập đấu giá tự động (auto-bid).
 *
 * <p>Auto-bid cho phép người dùng "đặt trước" chiến lược đấu giá: hệ thống sẽ tự động trả giá thay
 * họ mỗi khi có người khác bid, cho đến khi đạt giá tối đa.
 *
 * <p>Ví dụ thực tế:
 *
 * <ul>
 *   <li>User đặt: maxBid = 10,000,000đ, increment = 100,000đ
 *   <li>Giá hiện tại: 5,000,000đ
 *   <li>Đối thủ bid 5,500,000đ → hệ thống tự động bid 5,600,000đ cho user
 *   <li>Đối thủ bid 6,000,000đ → hệ thống tự động bid 6,100,000đ
 *   <li>Cứ thế cho đến khi giá vượt 10,000,000đ → auto-bid dừng
 * </ul>
 *
 * <p>Luồng dữ liệu:
 *
 * <ol>
 *   <li>Client gửi POST /api/auctions/5/auto-bid với JSON: {"maxBid": 10000000, "increment":
 *       100000}
 *   <li>BidController → tạo AutoBidConfig → lưu DB
 *   <li>Khi có bid mới, AutoBidStrategy duyệt PriorityQueue các AutoBidConfig (sort theo
 *       registeredAt) → ai đủ budget → tự động bid
 * </ol>
 *
 * <p>Validate phía server:
 *
 * <ul>
 *   <li>maxBid &gt; currentPrice (không có ý nghĩa nếu max thấp hơn giá hiện tại)
 *   <li>increment &gt; 0 (bước giá phải dương)
 *   <li>Mỗi user chỉ có 1 auto-bid config cho mỗi phiên (UNIQUE constraint)
 * </ul>
 */
public class AutoBidRequest {

  private BigDecimal maxBid;
  private BigDecimal increment;

  public AutoBidRequest() {}

  public AutoBidRequest(BigDecimal maxBid, BigDecimal increment) {
    this.maxBid = maxBid;
    this.increment = increment;
  }

  // === Getters & Setters ===

  public BigDecimal getMaxBid() {
    return maxBid;
  }

  public void setMaxBid(BigDecimal maxBid) {
    this.maxBid = maxBid;
  }

  public BigDecimal getIncrement() {
    return increment;
  }

  public void setIncrement(BigDecimal increment) {
    this.increment = increment;
  }
}
