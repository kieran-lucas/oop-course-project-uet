package com.auction.dto;

import com.auction.model.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** DTO trả về thông tin user cho client — không bao gồm passwordHash. */
public class UserResponse {

  private Long id;
  private String username;
  private String email;
  private String role;
  private BigDecimal balance;
  private LocalDateTime createdAt;

  public UserResponse() {}

  /** Chuyển đổi từ User model sang UserResponse DTO. */
  public static UserResponse from(User user) {
    UserResponse r = new UserResponse();
    r.id = user.getId();
    r.username = user.getUsername();
    r.email = user.getEmail();
    r.role = user.getRole();
    r.balance = user.getBalance();
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

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
