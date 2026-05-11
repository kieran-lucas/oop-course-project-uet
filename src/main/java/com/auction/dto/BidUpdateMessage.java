package com.auction.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO cho tin nhắn WebSocket server push về client — thông báo realtime khi có sự kiện trong phiên
 * đấu giá
 *
 * <p>Đây KHÔNG phải REST response — đây là message gửi qua WebSocket connection đang mở. Mỗi khi có
 * sự kiện (bid mới, gia hạn thời gian, phiên kết thúc), server serialize BidUpdateMessage thành
 * JSON rồi gửi cho TẤT CẢ client đang xem phiên đó (Observer pattern)
 *
 * <p>Các loại message (phân biệt bằng field type):
 *
 * <ul>
 *   <li><b>BID_UPDATE</b> — có người đặt giá mới thành công. Client cập nhật: giá hiện tại, người
 *       dẫn đầu, thêm data point vào Bid History Chart
 *   <li><b>TIME_EXTENDED</b> — anti-sniping kích hoạt: có bid trong 30 giây cuối → phiên gia hạn
 *       thêm 60 giây. Client cập nhật: countdown timer với endTime mới
 *   <li><b>AUCTION_ENDED</b> — phiên đấu giá kết thúc. Client hiển thị: người thắng cuộc, giá cuối
 *       cùng, disable nút "Đặt giá"
 *   <li><b>AUTO_BID_TRIGGERED</b> — hệ thống tự động đặt giá (auto-bid). Client hiển thị: thông báo
 *       "Auto-bid đã đặt giá X cho bạn" (nếu là chính user đó)
 *   <li><b>BALANCE_UPDATED</b> — Admin duyệt/từ chối nạp tiền. Client cập nhật số dư và hiện thông
 *       báo.
 * </ul>
 *
 * <p>Ví dụ JSON gửi qua WebSocket:
 *
 * <pre>
 * {
 *   "type": "BID_UPDATE",
 *   "auctionId": 5,
 *   "currentPrice": 500000,
 *   "leadingBidderId": 12,
 *   "leadingBidderUsername": "alice",
 *   "endTime": "2026-04-01T21:01:00",
 *   "timestamp": "2026-04-01T20:59:52",
 *   "autoBid": false
 * }
 * </pre>
 *
 * <p>Client JavaFX nhận message → parse JSON → gọi Platform.runLater() để cập nhật UI trên JavaFX
 * Application Thread (bắt buộc — JavaFX không cho phép update UI từ thread khác)
 */
public class BidUpdateMessage {

  /** Hằng số cho các loại message — tránh viết sai chuỗi ở nhiều nơi */
  public static final String TYPE_BID_UPDATE = "BID_UPDATE";

  public static final String TYPE_TIME_EXTENDED = "TIME_EXTENDED";
  public static final String TYPE_AUCTION_ENDED = "AUCTION_ENDED";
  public static final String TYPE_AUTO_BID_TRIGGERED = "AUTO_BID_TRIGGERED";

  /**
   * Loại message thông báo biến động số dư — gửi qua kênh WebSocket riêng của user ({@code
   * /ws/user/{id}}) khi Admin phê duyệt hoặc từ chối yêu cầu nạp tiền.
   */
  public static final String TYPE_BALANCE_UPDATED = "BALANCE_UPDATED";

  private String type;
  private Long auctionId;
  private BigDecimal currentPrice;
  private Long leadingBidderId;
  private String leadingBidderUsername;
  private LocalDateTime endTime;
  private LocalDateTime timestamp;
  private boolean autoBid;

  /** Số dư mới sau khi deposit được duyệt — chỉ có trong message BALANCE_UPDATED. */
  private BigDecimal newBalance;

  /**
   * Trạng thái duyệt/từ chối — chỉ có trong message BALANCE_UPDATED. Field riêng, không tái dụng
   * autoBid nữa để tránh nhầm lẫn.
   */
  private boolean approved;

  public BidUpdateMessage() {}

  /**
   * Factory method tạo message BID_UPDATE — loại phổ biến nhất, gửi mỗi khi có bid thành công
   *
   * @param auctionId ID phiên đấu giá
   * @param price giá mới sau bid
   * @param bidderId ID người vừa bid
   * @param username tên người vừa bid (hiển thị trên UI)
   * @param endTime thời gian kết thúc (có thể đã thay đổi nếu anti-sniping)
   * @param isAutoBid true nếu bid này do auto-bidding, false nếu thủ công
   * @return BidUpdateMessage sẵn sàng serialize thành JSON và gửi qua WebSocket
   */
  public static BidUpdateMessage bidUpdate(
      Long auctionId,
      BigDecimal price,
      Long bidderId,
      String username,
      LocalDateTime endTime,
      boolean isAutoBid) {
    BidUpdateMessage msg = new BidUpdateMessage();
    msg.type = TYPE_BID_UPDATE;
    msg.auctionId = auctionId;
    msg.currentPrice = price;
    msg.leadingBidderId = bidderId;
    msg.leadingBidderUsername = username;
    msg.endTime = endTime;
    msg.timestamp = LocalDateTime.now();
    msg.autoBid = isAutoBid;
    return msg;
  }

  /**
   * Factory method tạo message TIME_EXTENDED — gửi khi anti-sniping gia hạn thời gian
   *
   * @param auctionId ID phiên đấu giá
   * @param newEndTime thời gian kết thúc mới (đã cộng thêm 60 giây)
   * @return BidUpdateMessage loại TIME_EXTENDED
   */
  public static BidUpdateMessage timeExtended(Long auctionId, LocalDateTime newEndTime) {
    BidUpdateMessage msg = new BidUpdateMessage();
    msg.type = TYPE_TIME_EXTENDED;
    msg.auctionId = auctionId;
    msg.endTime = newEndTime;
    msg.timestamp = LocalDateTime.now();
    return msg;
  }

  /**
   * Factory method tạo message AUCTION_ENDED — gửi khi phiên kết thúc
   *
   * @param auctionId ID phiên đấu giá
   * @param finalPrice giá cuối cùng
   * @param winnerId ID người thắng (null nếu không ai bid)
   * @param winnerName tên người thắng (null nếu không ai bid)
   * @return BidUpdateMessage loại AUCTION_ENDED
   */
  public static BidUpdateMessage auctionEnded(
      Long auctionId, BigDecimal finalPrice, Long winnerId, String winnerName) {
    BidUpdateMessage msg = new BidUpdateMessage();
    msg.type = TYPE_AUCTION_ENDED;
    msg.auctionId = auctionId;
    msg.currentPrice = finalPrice;
    msg.leadingBidderId = winnerId;
    msg.leadingBidderUsername = winnerName;
    msg.timestamp = LocalDateTime.now();
    return msg;
  }

  /**
   * Factory method tạo message BALANCE_UPDATED — gửi qua /ws/user/{id} khi Admin duyệt nạp tiền.
   *
   * <p>Dùng field {@code approved} riêng thay vì tái dụng {@code autoBid} để tránh nhầm lẫn ngữ
   * nghĩa và đọc sai giá trị.
   *
   * @param userId ID user được cộng tiền
   * @param newBalance số dư mới sau khi cộng
   * @param approved true = duyệt (cộng tiền), false = từ chối
   * @return BidUpdateMessage loại BALANCE_UPDATED
   */
  public static BidUpdateMessage balanceUpdated(
      Long userId, BigDecimal newBalance, boolean approved) {
    BidUpdateMessage msg = new BidUpdateMessage();
    msg.type = TYPE_BALANCE_UPDATED;
    msg.auctionId = userId; // tái dùng field auctionId để chứa userId — client phân biệt qua type
    msg.newBalance = newBalance;
    msg.approved = approved; // FIX Bug 2: dùng field approved riêng, không tái dụng autoBid nữa
    msg.timestamp = LocalDateTime.now();
    return msg;
  }

  // === Getters & Setters ===

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Long getAuctionId() {
    return auctionId;
  }

  public void setAuctionId(Long auctionId) {
    this.auctionId = auctionId;
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

  public String getLeadingBidderUsername() {
    return leadingBidderUsername;
  }

  public void setLeadingBidderUsername(String leadingBidderUsername) {
    this.leadingBidderUsername = leadingBidderUsername;
  }

  public LocalDateTime getEndTime() {
    return endTime;
  }

  public void setEndTime(LocalDateTime endTime) {
    this.endTime = endTime;
  }

  public LocalDateTime getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(LocalDateTime timestamp) {
    this.timestamp = timestamp;
  }

  public boolean isAutoBid() {
    return autoBid;
  }

  public void setAutoBid(boolean autoBid) {
    this.autoBid = autoBid;
  }

  public BigDecimal getNewBalance() {
    return newBalance;
  }

  public void setNewBalance(BigDecimal newBalance) {
    this.newBalance = newBalance;
  }

  /** Trạng thái duyệt — chỉ có ý nghĩa với message BALANCE_UPDATED. */
  public boolean isApproved() {
    return approved;
  }

  public void setApproved(boolean approved) {
    this.approved = approved;
  }
}
