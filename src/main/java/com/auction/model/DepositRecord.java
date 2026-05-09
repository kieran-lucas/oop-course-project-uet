package com.auction.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Yêu cầu nạp tiền của Bidder — chờ Admin xác nhận trước khi cộng vào số dư*/
public class DepositRecord {

  private Long id;
  private Long userId;
  private String username;
  private BigDecimal amount;
  private String status; // PENDING, APPROVED, REJECTED
  private LocalDateTime createdAt;
  private LocalDateTime reviewedAt;

  public DepositRecord() {}

  public DepositRecord(Long userId, BigDecimal amount) {
    this.userId = userId;
    this.amount = amount;
    this.status = "PENDING";
    this.createdAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getReviewedAt() {
    return reviewedAt;
  }

  public void setReviewedAt(LocalDateTime reviewedAt) {
    this.reviewedAt = reviewedAt;
  }
}
