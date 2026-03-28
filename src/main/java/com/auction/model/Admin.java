package com.auction.model;

import java.time.LocalDateTime;

/**
 * Quản trị viên hệ thống.
 *
 * <p>Admin có quyền: quản lý tất cả user, xóa phiên đấu giá vi phạm,
 * xem thống kê hệ thống. Admin KHÔNG tham gia đấu giá.
 */
public class Admin extends User {

  public Admin() {}

  public Admin(String username, String passwordHash, String email) {
    super(username, passwordHash, email);
  }

  public Admin(Long id, String username, String passwordHash, String email,
      LocalDateTime createdAt) {
    super(id, username, passwordHash, email, createdAt);
  }

  @Override
  public String getRole() {
    return "ADMIN";
  }
}
