package com.auction.model;

import java.time.LocalDateTime;

/** Yêu cầu đặt lại mật khẩu — chờ Admin phê duyệt trước khi reset về mật khẩu mặc định */
public class PasswordResetRecord {

  private Long id;
  private Long userId;
  private String username;
  private String email;
  private String status; // PENDING, APPROVED, REJECTED
  private LocalDateTime createdAt;
  private LocalDateTime reviewedAt;

  public PasswordResetRecord() {}

  public PasswordResetRecord(Long userId) {
    this.userId = userId;
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

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
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
