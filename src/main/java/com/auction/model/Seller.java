package com.auction.model;

import java.time.LocalDateTime;

/**
 * Người bán — người dùng có quyền đăng sản phẩm lên hệ thống đấu giá
 *
 * <p>Seller được phép: tạo, chỉnh sửa, xóa sản phẩm của bản thân; tạo phiên đấu giá; xem kết quả
 * của các phiên đấu giá liên quan đến mình
 *
 * <p>Seller KHÔNG được phép: tham gia đấu giá sản phẩm do chính mình đăng — quy tắc này tránh xung
 * đột lợi ích và ngăn chặn hành vi tự đẩy giá
 */
public class Seller extends User {

  /** Constructor mặc định — phục vụ framework/JDBI khi tạo object */
  public Seller() {}

  /**
   * Khởi tạo một Seller mới khi đăng ký tài khoản
   *
   * @param username tên đăng nhập
   * @param passwordHash mật khẩu đã được hash (không lưu plain text)
   * @param email địa chỉ email
   */
  public Seller(String username, String passwordHash, String email) {
    super(username, passwordHash, email);
  }

  /**
   * Khởi tạo một Seller từ bản ghi đã tồn tại trong DB.
   *
   * @param id định danh người dùng
   * @param username tên đăng nhập
   * @param passwordHash mật khẩu đã hash
   * @param email địa chỉ email
   * @param createdAt thời điểm tài khoản được tạo
   */
  public Seller(
      Long id, String username, String passwordHash, String email, LocalDateTime createdAt) {
    super(id, username, passwordHash, email, createdAt);
  }

  @Override
  public String getRole() {
    return "SELLER";
  }
}
