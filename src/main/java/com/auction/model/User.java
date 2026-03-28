package com.auction.model;

import java.time.LocalDateTime;

/**
 * Lớp trừu tượng cho người dùng hệ thống.
 *
 * <p>Ba loại user: Bidder (đấu giá), Seller (bán), Admin (quản lý).
 * Phần chung (username, password, email) nằm ở đây.
 * Phần riêng (quyền hạn, hành vi) nằm ở subclass.
 *
 * <p>Đây là INHERITANCE: User kế thừa id + createdAt từ Entity,
 * rồi thêm username, passwordHash, email.
 */
public abstract class User extends Entity {

  private String username;
  private String passwordHash; // không bao giờ lưu password gốc, chỉ lưu hash (BCrypt)
  private String email;

  /** Constructor mặc định — cho framework/JDBI tạo object. */
  protected User() {}

  /** Constructor đầy đủ — dùng khi đăng ký user mới. */
  protected User(String username, String passwordHash, String email) {
    super(); // gọi Entity() → set createdAt = now
    this.username = username;
    this.passwordHash = passwordHash;
    this.email = email;
  }

  /** Constructor từ database — đã có id và createdAt. */
  protected User(Long id, String username, String passwordHash, String email,
      LocalDateTime createdAt) {
    super(id, createdAt);
    this.username = username;
    this.passwordHash = passwordHash;
    this.email = email;
  }

  /**
   * Trả về vai trò của user: "BIDDER", "SELLER", hoặc "ADMIN".
   *
   * <p>Đây là POLYMORPHISM: mỗi subclass override method này trả về giá trị khác nhau.
   * Khi bạn có List<User> users, gọi user.getRole() sẽ trả đúng vai trò
   * mà không cần instanceof.
   */
  public abstract String getRole();

  // === Getters & Setters ===

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  @Override
  public String toString() {
    return getRole() + "{username='" + username + "', email='" + email + "'}";
  }
}
