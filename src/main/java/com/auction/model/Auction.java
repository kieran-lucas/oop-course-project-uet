package com.auction.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Phiên đấu giá — đối tượng trung tâm của toàn bộ hệ thống
 *
 * <p>Một Auction gắn với 1 Item, có giá khởi điểm, giá hiện tại, thời gian bắt đầu/kết thúc, và
 * trạng thái
 *
 * <p>Lưu ý sử dụng BigDecimal thay vì double cho tiền tệ. double có lỗi floating point: 0.1 + 0.2 =
 * 0.30000000000000004. Trong đấu giá, sai 1 đồng cũng không chấp nhận được. BigDecimal tính chính
 * xác: new BigDecimal("0.1").add(new BigDecimal("0.2")) = 0.3 đúng
 *
 * <p>Trạng thái phiên (status) liên kết với State pattern:
 *
 * <ul>
 *   <li>OPEN: vừa tạo, Seller còn có thể sửa thông tin
 *   <li>RUNNING: đang diễn ra, Bidder có thể đặt giá
 *   <li>FINISHED: hết giờ, xác định người thắng
 *   <li>PAID: người thắng đã thanh toán
 *   <li>CANCELED: phiên bị hủy
 * </ul>
 *
 * <p>Các trạng thái này map trực tiếp với CHECK constraint trong bảng auctions và với các class
 * trong pattern/state/
 */
public class Auction extends Entity {

  private Long itemId; // foreign key → bảng items
  private Long sellerId; // lấy từ bảng items (JOIN) — dùng để kiểm tra seller tự bid
  private BigDecimal startingPrice;
  private BigDecimal currentPrice;
  private Long leadingBidderId; // foreign key → bảng users (ai đang dẫn đầu)
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  private String status; // OPEN, RUNNING, FINISHED, PAID, CANCELED

  /**
   * Thời điểm cập nhật gần nhất (giá mới, trạng thái mới, gia hạn...).
   *
   * <p>[FIX #6] Trước đây cột updated_at được SELECT từ database nhưng AuctionMapper không đọc →
   * mất thông tin. Giờ field này được mapper set sau khi tạo Auction object. Dùng để hiển thị "Cập
   * nhật lần cuối: X phút trước" trên UI.
   */
  private LocalDateTime updatedAt;

  public Auction() {}

  /** Constructor tạo phiên mới — status mặc định OPEN, currentPrice = startingPrice. */
  public Auction(
      Long itemId, BigDecimal startingPrice, LocalDateTime startTime, LocalDateTime endTime) {
    super();
    this.itemId = itemId;
    this.startingPrice = startingPrice;
    this.currentPrice = startingPrice; // ban đầu chưa ai bid → giá = giá khởi điểm
    this.leadingBidderId = null; // chưa có ai dẫn đầu
    this.startTime = startTime;
    this.endTime = endTime;
    this.status = "OPEN";
    this.updatedAt = this.getCreatedAt(); // mới tạo → updatedAt = createdAt
  }

  /** Constructor đầy đủ từ database */
  public Auction(
      Long id,
      Long itemId,
      BigDecimal startingPrice,
      BigDecimal currentPrice,
      Long leadingBidderId,
      LocalDateTime startTime,
      LocalDateTime endTime,
      String status,
      LocalDateTime createdAt) {
    super(id, createdAt);
    this.itemId = itemId;
    this.startingPrice = startingPrice;
    this.currentPrice = currentPrice;
    this.leadingBidderId = leadingBidderId;
    this.startTime = startTime;
    this.endTime = endTime;
    this.status = status;
  }

  // === Business methods ===
  // Những method này sẽ được State pattern gọi,
  // nhưng logic check nằm trong AuctionState, không phải ở đây

  /** Kiểm tra phiên đã hết giờ chưa */
  public boolean isExpired() {
    return LocalDateTime.now().isAfter(endTime);
  }

  /** Kiểm tra phiên đang trong thời gian đấu giá. */
  public boolean isActive() {
    return "RUNNING".equals(status) && !isExpired();
  }

  /**
   * Tính thời gian còn lại (milliseconds). Dùng cho anti-sniping: nếu remaining &lt; 30000ms → gia
   * hạn.
   */
  public long getRemainingTimeMs() {
    return java.time.Duration.between(LocalDateTime.now(), endTime).toMillis();
  }

  // === Getters & Setters ===

  public Long getItemId() {
    return itemId;
  }

  public void setItemId(Long itemId) {
    this.itemId = itemId;
  }

  public Long getSellerId() {
    return sellerId;
  }

  public void setSellerId(Long sellerId) {
    this.sellerId = sellerId;
  }

  public BigDecimal getStartingPrice() {
    return startingPrice;
  }

  public void setStartingPrice(BigDecimal startingPrice) {
    this.startingPrice = startingPrice;
  }

  public BigDecimal getCurrentPrice() {
    return currentPrice;
  }

  public void setCurrentPrice(BigDecimal currentPrice) {
    this.currentPrice = currentPrice;
  }

  public Long getLeadingBidderId() {
    return leadingBidderId;
  }

  public void setLeadingBidderId(Long leadingBidderId) {
    this.leadingBidderId = leadingBidderId;
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

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  /** Lấy thời điểm cập nhật gần nhất. */
  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  /** Đặt thời điểm cập nhật gần nhất (AuctionMapper gọi sau khi đọc từ database). */
  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  @Override
  public String toString() {
    return "Auction{id="
        + getId()
        + ", item="
        + itemId
        + ", price="
        + currentPrice
        + ", status="
        + status
        + "}";
  }
}
