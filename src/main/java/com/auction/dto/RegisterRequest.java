package com.auction.dto;

/**
 * DTO cho yêu cầu đăng ký tài khoản mới.
 *
 * <p>Luồng dữ liệu:
 * <ol>
 *   <li>Client gửi POST /api/auth/register với JSON:
 *       {"username":"alice", "password":"secret123", "email":"alice@mail.com", "role":"BIDDER"}</li>
 *   <li>Jackson parse JSON → RegisterRequest</li>
 *   <li>AuthController → UserService.register()</li>
 *   <li>UserService: validate → BCrypt.hash(password) → tạo đúng subclass (Bidder/Seller/Admin)
 *       → UserDao.insert()</li>
 * </ol>
 *
 * <p>Field role quyết định hệ thống tạo loại User nào:
 * <ul>
 *   <li>"BIDDER" → new Bidder(...) — tham gia đấu giá</li>
 *   <li>"SELLER" → new Seller(...) — đăng sản phẩm</li>
 *   <li>"ADMIN"  → new Admin(...)  — quản lý hệ thống</li>
 * </ul>
 *
 * <p>Server cần validate:
 * <ul>
 *   <li>username không trùng (DuplicateException nếu đã tồn tại)</li>
 *   <li>email không trùng</li>
 *   <li>password đủ mạnh (tùy chọn)</li>
 *   <li>role phải là 1 trong 3 giá trị hợp lệ</li>
 * </ul>
 */
public class RegisterRequest {

  private String username;
  private String password;
  private String email;
  private String role; // "BIDDER", "SELLER", hoặc "ADMIN"

  public RegisterRequest() {}

  public RegisterRequest(String username, String password, String email, String role) {
    this.username = username;
    this.password = password;
    this.email = email;
    this.role = role;
  }

  // === Getters & Setters ===

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
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
}
