package com.auction.model;

import java.time.LocalDateTime;

/**
 * Người tham gia đấu giá
 *
 * <p>Bidder có quyền: xem phiên đấu giá, đặt giá, thiết lập auto-bid. Bidder KHÔNG có quyền: tạo
 * sản phẩm, tạo phiên đấu giá, quản lý hệ thống
 *
 * <p>Class này rất ngắn vì phần lớn logic nằm ở User (kế thừa). Đây chính là sức mạnh của
 * inheritance: Bidder không cần viết lại username, email, getter/setter — tất cả đã có từ User →
 * Entity
 */
public class Bidder extends User {

  /** Constructor mặc định */
  public Bidder() {}

  /** Constructor cho đăng ký mới */
  public Bidder(String username, String passwordHash, String email) {
    super(username, passwordHash, email);
  }

  /** Constructor từ database */
  public Bidder(
      Long id, String username, String passwordHash, String email, LocalDateTime createdAt) {
    super(id, username, passwordHash, email, createdAt);
  }

  /**
   * POLYMORPHISM: trả về "BIDDER". Map trực tiếp với cột role trong bảng users (CHECK constraint)
   * Factory/DAO dùng giá trị này để quyết định tạo class nào khi đọc từ DB
   */
  @Override
  public String getRole() {
    return "BIDDER";
  }
}
