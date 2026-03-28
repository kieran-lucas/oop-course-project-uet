package com.auction.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Cấu hình auto-bid cho một user trong một phiên đấu giá.
 *
 * <p>Khi user bật auto-bid, họ đặt:
 * - maxBid: giá tối đa sẵn sàng trả (ví dụ: 10 triệu)
 * - increment: mỗi lần tự động bid thêm bao nhiêu (ví dụ: 100k)
 *
 * <p>Cơ chế hoạt động (xử lý bởi AutoBidStrategy):
 * 1. Có người bid thủ công 5 triệu
 * 2. Server duyệt danh sách AutoBidConfig của phiên đó
 * 3. User A có maxBid = 10tr, increment = 100k → tự động bid 5.1 triệu
 * 4. User B có maxBid = 6tr, increment = 200k → tự động bid 5.3 triệu
 * 5. User A → 5.4 triệu... cứ thế cho đến khi hết budget
 *
 * <p>registeredAt dùng để sắp xếp ưu tiên: ai đăng ký auto-bid trước
 * thì được xử lý trước (PriorityQueue sắp theo registeredAt).
 *
 * <p>isActive = false khi user tự tắt hoặc khi maxBid đã bị vượt qua.
 *
 * <p>UNIQUE(auction_id, bidder_id) trong database đảm bảo mỗi user
 * chỉ có 1 auto-bid config cho mỗi phiên.
 */
public class AutoBidConfig extends Entity {

  private Long auctionId;
  private Long bidderId;
  private BigDecimal maxBid;
  private BigDecimal increment;
  private boolean active;
  private LocalDateTime registeredAt; // thời điểm đăng ký auto-bid, dùng cho PriorityQueue

  public AutoBidConfig() {}

  public AutoBidConfig(Long auctionId, Long bidderId, BigDecimal maxBid, BigDecimal increment) {
    super();
    this.auctionId = auctionId;
    this.bidderId = bidderId;
    this.maxBid = maxBid;
    this.increment = increment;
    this.active = true;
    this.registeredAt = LocalDateTime.now();
  }

  public AutoBidConfig(Long id, Long auctionId, Long bidderId, BigDecimal maxBid,
      BigDecimal increment, boolean active, LocalDateTime registeredAt,
      LocalDateTime createdAt) {
    super(id, createdAt);
    this.auctionId = auctionId;
    this.bidderId = bidderId;
    this.maxBid = maxBid;
    this.increment = increment;
    this.active = active;
    this.registeredAt = registeredAt;
  }

  /** Kiểm tra auto-bid này còn đủ budget để bid ở mức giá hiện tại không. */
  public boolean canBidAt(BigDecimal currentPrice) {
    return active && currentPrice.add(increment).compareTo(maxBid) <= 0;
  }

  /** Tính giá bid tiếp theo. */
  public BigDecimal getNextBidAmount(BigDecimal currentPrice) {
    return currentPrice.add(increment);
  }

  // === Getters & Setters ===

  public Long getAuctionId() {
    return auctionId;
  }

  public void setAuctionId(Long auctionId) {
    this.auctionId = auctionId;
  }

  public Long getBidderId() {
    return bidderId;
  }

  public void setBidderId(Long bidderId) {
    this.bidderId = bidderId;
  }

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

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public LocalDateTime getRegisteredAt() {
    return registeredAt;
  }

  public void setRegisteredAt(LocalDateTime registeredAt) {
    this.registeredAt = registeredAt;
  }
}
