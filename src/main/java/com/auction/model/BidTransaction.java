package com.auction.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ghi lại mỗi lần đặt giá trong một phiên đấu giá
 *
 * <p>Mỗi khi ai đó bid thành công (giá hợp lệ, phiên đang mở), hệ thống tạo 1 BidTransaction ghi
 * lại: ai, phiên nào, giá bao nhiêu, lúc nào
 *
 * <p>BidTransaction không bao giờ bị sửa hay xóa — nó là lịch sử bất biến. Điều này quan trọng cho:
 * tính minh bạch đấu giá, bid history chart (trục X = thời gian, trục Y = giá lấy từ các
 * BidTransaction), và audit trail
 *
 * <p>Field isAutoBid đánh dấu bid này do hệ thống tự đặt (auto-bidding) hay do người dùng tự tay
 * đặt. Dùng cho hiển thị trên chart và thống kê
 */
public class BidTransaction extends Entity {

  private Long auctionId; // foreign key → bảng auctions
  private Long bidderId; // foreign key → bảng users
  private BigDecimal amount;
  private boolean autoBid;

  public BidTransaction() {}

  /** Constructor tạo bid mới — createdAt = now (thời điểm bid). */
  public BidTransaction(Long auctionId, Long bidderId, BigDecimal amount, boolean autoBid) {
    super();
    this.auctionId = auctionId;
    this.bidderId = bidderId;
    this.amount = amount;
    this.autoBid = autoBid;
  }

  /** Constructor đầy đủ từ database. */
  public BidTransaction(
      Long id,
      Long auctionId,
      Long bidderId,
      BigDecimal amount,
      boolean autoBid,
      LocalDateTime createdAt) {
    super(id, createdAt);
    this.auctionId = auctionId;
    this.bidderId = bidderId;
    this.amount = amount;
    this.autoBid = autoBid;
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

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public boolean isAutoBid() {
    return autoBid;
  }

  public void setAutoBid(boolean autoBid) {
    this.autoBid = autoBid;
  }

  @Override
  public String toString() {
    return "Bid{auction="
        + auctionId
        + ", bidder="
        + bidderId
        + ", amount="
        + amount
        + (autoBid ? " (auto)" : "")
        + "}";
  }
}
