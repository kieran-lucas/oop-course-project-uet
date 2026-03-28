package com.auction.model;

import java.time.LocalDateTime;

/**
 * Người bán — đăng sản phẩm lên hệ thống đấu giá.
 *
 * <p>Seller có quyền: tạo/sửa/xóa sản phẩm, tạo phiên đấu giá, xem kết quả.
 * Seller KHÔNG có quyền: tham gia đấu giá sản phẩm của chính mình.
 */
public class Seller extends User {

  public Seller() {}

  public Seller(String username, String passwordHash, String email) {
    super(username, passwordHash, email);
  }

  public Seller(Long id, String username, String passwordHash, String email,
      LocalDateTime createdAt) {
    super(id, username, passwordHash, email, createdAt);
  }

  @Override
  public String getRole() {
    return "SELLER";
  }
}
