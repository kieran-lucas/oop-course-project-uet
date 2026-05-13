package com.auction.dto;

import com.auction.model.Auction;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO trả về thông tin phiên đấu giá cho client — phiên bản "an toàn" của Auction
 *
 * <p>Tại sao cần AuctionResponse thay vì trả thẳng Auction?
 *
 * <ul>
 *   <li>Auction chứa các field nội bộ mà client không cần biết
 *   <li>Có thể muốn thêm/bớt field tùy API mà không ảnh hưởng model
 *   <li>Tách biệt tầng data (model) và tầng presentation (DTO) — đúng nguyên tắc MVC
 *   <li>Trong tương lai có thể cần nhiều response format khác nhau cho cùng 1 entity
 * </ul>
 *
 * <p>AuctionResponse bổ sung thêm thông tin phái sinh mà client cần:
 *
 * <ul>
 *   <li>itemName — tên sản phẩm (thay vì chỉ itemId, client không cần query thêm)
 *   <li>leadingBidderUsername — tên người dẫn đầu (thay vì chỉ leadingBidderId)
 *   <li>remainingTimeMs — thời gian còn lại tính sẵn (client không cần tự tính)
 *   <li>itemBrand — hãng sản xuất (chỉ có khi category = ELECTRONICS)
 *   <li>itemArtist — nghệ sĩ (chỉ có khi category = ART)
 *   <li>itemYear — năm sản xuất (chỉ có khi category = VEHICLE)
 * </ul>
 *
 * <p>Method tĩnh fromAuction() giúp chuyển đổi Auction → AuctionResponse một cách tiện lợi, tránh
 * viết lại code mapping ở nhiều nơi
 */
public class AuctionResponse {

  private Long id;
  private Long itemId;
  private Long sellerId;
  private String itemName;
  private String itemCategory;
  private String itemDescription;
  private String itemBrand; // ELECTRONICS only
  private String itemArtist; // ART only
  private Integer itemYear; // VEHICLE only
  private BigDecimal startingPrice;
  private BigDecimal currentPrice;
  private Long leadingBidderId;
  private String leadingBidderUsername;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  private String status;
  private long remainingTimeMs;

  public AuctionResponse() {}

  /**
   * Factory method chuyển Auction model → AuctionResponse DTO
   *
   * <p>itemName và leadingBidderUsername cần query thêm từ DB, nên để Controller/Service set sau.
   * Method này chỉ map các field có sẵn trong Auction
   *
   * @param auction đối tượng Auction từ database
   * @return AuctionResponse chứa thông tin cơ bản, thiếu itemName và leadingBidderUsername
   */
  public static AuctionResponse fromAuction(Auction auction) {
    AuctionResponse response = new AuctionResponse();
    response.id = auction.getId();
    response.itemId = auction.getItemId();
    response.sellerId = auction.getSellerId();
    response.startingPrice = auction.getStartingPrice();
    response.currentPrice = auction.getCurrentPrice();
    response.leadingBidderId = auction.getLeadingBidderId();
    response.startTime = auction.getStartTime();
    response.endTime = auction.getEndTime();
    response.status = auction.getStatus().name();
    response.remainingTimeMs = auction.getRemainingTimeMs();
    return response;
  }

  // === Getters & Setters ===

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

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

  public String getItemName() {
    return itemName;
  }

  public void setItemName(String itemName) {
    this.itemName = itemName;
  }

  public String getItemCategory() {
    return itemCategory;
  }

  public void setItemCategory(String itemCategory) {
    this.itemCategory = itemCategory;
  }

  public String getItemDescription() {
    return itemDescription;
  }

  public void setItemDescription(String itemDescription) {
    this.itemDescription = itemDescription;
  }

  public String getItemBrand() {
    return itemBrand;
  }

  public void setItemBrand(String itemBrand) {
    this.itemBrand = itemBrand;
  }

  public String getItemArtist() {
    return itemArtist;
  }

  public void setItemArtist(String itemArtist) {
    this.itemArtist = itemArtist;
  }

  public Integer getItemYear() {
    return itemYear;
  }

  public void setItemYear(Integer itemYear) {
    this.itemYear = itemYear;
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

  public String getLeadingBidderUsername() {
    return leadingBidderUsername;
  }

  public void setLeadingBidderUsername(String leadingBidderUsername) {
    this.leadingBidderUsername = leadingBidderUsername;
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

  public long getRemainingTimeMs() {
    return remainingTimeMs;
  }

  public void setRemainingTimeMs(long remainingTimeMs) {
    this.remainingTimeMs = remainingTimeMs;
  }
}
