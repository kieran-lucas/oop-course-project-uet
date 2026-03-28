package com.auction.dto;

/**
 * DTO cho yêu cầu đăng nhập — chứa thông tin client gửi lên khi nhấn "Đăng nhập".
 *
 * <p>Luồng dữ liệu:
 *
 * <ol>
 *   <li>Client gửi POST /api/auth/login với JSON: {"username":"alice", "password":"secret123"}
 *   <li>Jackson tự động parse JSON → LoginRequest object
 *   <li>AuthController nhận LoginRequest → chuyển cho UserService.login()
 *   <li>UserService kiểm tra password bằng BCrypt → nếu đúng, trả JWT token
 * </ol>
 *
 * <p>Lưu ý: password ở đây là password gốc (plaintext) do người dùng nhập. Server sẽ dùng BCrypt để
 * so sánh với passwordHash trong database — KHÔNG BAO GIỜ lưu plaintext vào DB.
 *
 * <p>DTO (Data Transfer Object) chỉ chứa dữ liệu thuần túy, không có business logic. Nó là "phong
 * bì" chuyển dữ liệu giữa client và server qua HTTP.
 */
public class LoginRequest {

  private String username;
  private String password;

  /** Constructor mặc định — bắt buộc để Jackson parse JSON → object. */
  public LoginRequest() {}

  public LoginRequest(String username, String password) {
    this.username = username;
    this.password = password;
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
}
