package com.auction.dto;

import com.auction.model.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** DTO trả về thông tin user cho client — không bao gồm passwordHash */
public class UserResponse {

  private Long id;
  private String username;
  private String email;
  private String role;

  /**
   * Tổng số dư đã nạp ({@code users.balance}). Đây là con số UI hiển thị cho người dùng — bid thành
   * công KHÔNG làm giảm trường này; tiền chỉ bị trừ thật khi auction kết thúc và winner thanh toán.
   */
  private BigDecimal balance;

  /**
   * Số dư khả dụng = {@code balance − reservedBalance}. Tiền bị "giữ chỗ" khi user đang dẫn đầu một
   * phiên không trừ vào {@link #balance}, mà chỉ làm giảm trường này. Server dùng availableBalance
   * để validate bid; client dùng để gợi ý lỗi sớm trước khi gọi API.
   */
  private BigDecimal availableBalance;

  private LocalDateTime createdAt;

  public UserResponse() {}

  /** Chuyển đổi từ User model sang UserResponse DTO */
  public static UserResponse from(User user) {
    UserResponse r = new UserResponse();
    r.id = user.getId();
    r.username = user.getUsername();
    r.email = user.getEmail();
    r.role = user.getRole();
    r.balance = user.getBalance();
    r.availableBalance = user.getAvailableBalance();
    r.createdAt = user.getCreatedAt();
    return r;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
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

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public BigDecimal getBalance() {
    return balance;
  }

  public void setBalance(BigDecimal balance) {
    this.balance = balance;
  }

  public BigDecimal getAvailableBalance() {
    return availableBalance;
  }

  public void setAvailableBalance(BigDecimal availableBalance) {
    this.availableBalance = availableBalance;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
