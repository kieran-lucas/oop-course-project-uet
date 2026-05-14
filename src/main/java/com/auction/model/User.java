package com.auction.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lớp trừu tượng đại diện cho một người dùng của hệ thống.
 *
 * <p>Có ba loại người dùng: {@code Bidder} (người tham gia đấu giá), {@code Seller} (người bán), và
 * {@code Admin} (người quản trị). Các thuộc tính dùng chung như {@code username}, {@code
 * passwordHash}, {@code email} được đặt tại lớp này; những khác biệt về quyền hạn và hành vi sẽ
 * được xử lý tại các lớp con tương ứng.
 *
 * <p>Lớp {@code User} kế thừa {@code id} và {@code createdAt} từ {@link Entity}, sau đó mở rộng
 * thêm các trường dành riêng cho người dùng — đây là INHERITANCE trong OOP
 */
public abstract class User extends Entity {

  private String username;

  @JsonIgnore
  private String passwordHash; // chỉ lưu giá trị đã hash (ví dụ: BCrypt), không lưu mật khẩu gốc

  private String email;
  private BigDecimal balance = BigDecimal.ZERO;
  private BigDecimal reservedBalance = BigDecimal.ZERO;
  private int tokenVersion;

  /** Constructor mặc định — phục vụ framework/JDBI khi tạo object */
  protected User() {}

  /**
   * Khởi tạo một người dùng mới khi đăng ký tài khoản
   *
   * <p>Việc gọi {@code super()} sẽ kích hoạt constructor của {@link Entity}, qua đó gán {@code
   * createdAt} bằng thời điểm hiện tại
   *
   * @param username tên đăng nhập
   * @param passwordHash mật khẩu đã hash
   * @param email địa chỉ email
   */
  protected User(String username, String passwordHash, String email) {
    super();
    this.username = username;
    this.passwordHash = passwordHash;
    this.email = email;
  }

  /**
   * Khởi tạo một người dùng từ bản ghi đã có trong DB
   *
   * @param id định danh người dùng
   * @param username tên đăng nhập
   * @param passwordHash mật khẩu đã hash
   * @param email địa chỉ email
   * @param createdAt thời điểm tài khoản được tạo
   */
  protected User(
      Long id, String username, String passwordHash, String email, LocalDateTime createdAt) {
    super(id, createdAt);
    this.username = username;
    this.passwordHash = passwordHash;
    this.email = email;
  }

  /**
   * Trả về vai trò của người dùng: {@code "BIDDER"}, {@code "SELLER"}, hoặc {@code "ADMIN"}.
   *
   * <p>POLYMORPHISM: mỗi lớp con override method này để trả về giá trị tương ứng. Nhờ đó, khi duyệt
   * một {@code List<User>} và gọi {@code user.getRole()}, ta luôn nhận được vai trò chính xác mà
   * không cần dùng {@code instanceof} hay ép kiểu.
   *
   * @return chuỗi đại diện cho vai trò của người dùng
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

  public BigDecimal getBalance() {
    return balance;
  }

  public void setBalance(BigDecimal balance) {
    this.balance = balance;
  }

  public BigDecimal getReservedBalance() {
    return reservedBalance;
  }

  public void setReservedBalance(BigDecimal reservedBalance) {
    this.reservedBalance = reservedBalance;
  }

  public int getTokenVersion() {
    return tokenVersion;
  }

  public void setTokenVersion(int tokenVersion) {
    this.tokenVersion = tokenVersion;
  }

  public BigDecimal getAvailableBalance() {
    BigDecimal total = balance != null ? balance : BigDecimal.ZERO;
    BigDecimal reserved = reservedBalance != null ? reservedBalance : BigDecimal.ZERO;
    return total.subtract(reserved);
  }

  @Override
  public String toString() {
    return getRole() + "{username='" + username + "', email='" + email + "'}";
  }
}
