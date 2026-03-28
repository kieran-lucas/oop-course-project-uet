package com.auction.dto;

import java.math.BigDecimal;

/**
 * DTO cho yêu cầu đặt giá thủ công (manual bid).
 *
 * <p>Luồng dữ liệu khi user nhấn "Đặt giá 500,000đ":
 * <ol>
 *   <li>Client gửi POST /api/auctions/5/bid với JSON: {"amount": 500000}
 *       và header Authorization: Bearer &lt;JWT&gt;</li>
 *   <li>JWT Middleware verify token → biết userId và role</li>
 *   <li>BidController nhận BidRequest + userId từ JWT</li>
 *   <li>BidService.placeBid():
 *       <ul>
 *         <li>synchronized(auction) — tránh race condition</li>
 *         <li>State pattern: RunningState cho phép bid</li>
 *         <li>Validate: amount &gt; currentPrice</li>
 *         <li>Update auction → save DB → notify observers</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <p>Lưu ý: auctionId KHÔNG nằm trong JSON body mà lấy từ URL path parameter
 * (/api/auctions/{id}/bid). Điều này theo đúng REST convention: resource identifier nằm ở URL.
 *
 * <p>amount dùng BigDecimal vì tiền tệ cần chính xác tuyệt đối — xem giải thích trong
 * Auction.java.
 */
public class BidRequest {

  private BigDecimal amount;

  public BidRequest() {}

  public BidRequest(BigDecimal amount) {
    this.amount = amount;
  }

  // === Getters & Setters ===

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }
}
