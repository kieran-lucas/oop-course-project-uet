package com.auction.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.auction.config.JwtUtil;
import com.auction.dao.UserDao;
import com.auction.dto.ChangePasswordRequest;
import com.auction.dto.LoginRequest;
import com.auction.dto.RegisterRequest;
import com.auction.dto.UserResponse;
import com.auction.exception.DuplicateException;
import com.auction.exception.NotFoundException;
import com.auction.exception.UnauthorizedException;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import com.auction.model.User;
import java.math.BigDecimal;
import java.util.List;

/** Service xử lý logic nghiệp vụ cho User. */
public class UserService {
  private final UserDao userDao;

  public UserService(UserDao userDao) {
    this.userDao = userDao;
  }

  public User register(RegisterRequest req) {
    if (req.getUsername() == null || req.getUsername().trim().isEmpty()) {
      throw new IllegalArgumentException("Username không được để trống");
    }

    String emailRegex = "^[A-Za-z0-9+_.-]+@(.+)$";
    if (req.getEmail() == null || !req.getEmail().matches(emailRegex)) {
      throw new IllegalArgumentException("Định dạng email không hợp lệ.");
    }

    if (req.getPassword() == null || req.getPassword().length() < 6) {
      throw new IllegalArgumentException("Mật khẩu phải có ít nhất 6 ký tự.");
    }

    if (userDao.findByUsername(req.getUsername()).isPresent()) {
      throw new DuplicateException("Username '" + req.getUsername() + "' đã tồn tại!");
    }

    String hashedPassword = BCrypt.withDefaults().hashToString(12, req.getPassword().toCharArray());

    // Java 21 enhanced switch expression — Issue 7
    User newUser =
        switch (req.getRole().toUpperCase()) {
          case "BIDDER" -> new Bidder();
          case "SELLER" -> new Seller();
          default -> throw new IllegalArgumentException("Role không hợp lệ: " + req.getRole());
        };

    newUser.setUsername(req.getUsername());
    newUser.setPasswordHash(hashedPassword);
    newUser.setEmail(req.getEmail());

    return userDao.insert(newUser);
  }

  public String login(LoginRequest req) {
    User user =
        userDao
            .findByUsername(req.getUsername())
            .orElseThrow(() -> new NotFoundException("Không tìm thấy tài khoản với username này."));

    BCrypt.Result result =
        BCrypt.verifyer().verify(req.getPassword().toCharArray(), user.getPasswordHash());
    if (!result.verified) {
      throw new UnauthorizedException("Sai mật khẩu.");
    }

    return JwtUtil.createToken(user.getId(), user.getUsername(), user.getRole());
  }

  /**
   * Lấy role của user theo username — dùng sau đăng nhập để trả về cho client.
   *
   * @param username tên đăng nhập
   * @return role: "BIDDER", "SELLER", hoặc "ADMIN"
   * @throws NotFoundException nếu username không tồn tại
   */
  public String getRoleByUsername(String username) {
    return userDao
        .findByUsername(username)
        .map(User::getRole)
        .orElseThrow(() -> new NotFoundException("Không tìm thấy user: " + username));
  }

  /**
   * Lấy thông tin user theo ID.
   *
   * @param userId ID của user
   * @return UserResponse chứa thông tin user (không có passwordHash)
   * @throws NotFoundException nếu user không tồn tại
   */
  public UserResponse findById(Long userId) {
    User user =
        userDao
            .findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found: " + userId));
    return UserResponse.from(user);
  }

  /**
   * Đổi mật khẩu — xác minh mật khẩu cũ trước khi cập nhật.
   *
   * @param userId ID user từ JWT
   * @param req request chứa currentPassword và newPassword
   * @throws NotFoundException nếu user không tồn tại
   * @throws UnauthorizedException nếu mật khẩu hiện tại sai
   */
  public void changePassword(Long userId, ChangePasswordRequest req) {
    if (req.getNewPassword() == null || req.getNewPassword().length() < 6) {
      throw new IllegalArgumentException("Mật khẩu mới phải có ít nhất 6 ký tự.");
    }

    User user =
        userDao
            .findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found: " + userId));

    BCrypt.Result result =
        BCrypt.verifyer().verify(req.getCurrentPassword().toCharArray(), user.getPasswordHash());
    if (!result.verified) {
      throw new UnauthorizedException("Mật khẩu hiện tại không đúng.");
    }

    String newHash = BCrypt.withDefaults().hashToString(12, req.getNewPassword().toCharArray());
    user.setPasswordHash(newHash);
    userDao.update(user);
  }

  /**
   * Nạp tiền vào tài khoản.
   *
   * @param userId ID user từ JWT
   * @param amount số tiền nạp (phải > 0)
   * @throws NotFoundException nếu user không tồn tại
   * @throws IllegalArgumentException nếu số tiền <= 0
   */
  public void deposit(Long userId, BigDecimal amount) {
    if (amount == null || amount.signum() <= 0) {
      throw new IllegalArgumentException("Số tiền nạp phải lớn hơn 0.");
    }

    User user =
        userDao
            .findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found: " + userId));

    BigDecimal current = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
    user.setBalance(current.add(amount));
    userDao.update(user);
  }

  /**
   * Lấy tất cả user — dành cho Admin.
   *
   * @return danh sách UserResponse của toàn bộ người dùng
   */
  public List<UserResponse> getAll() {
    return userDao.findAll().stream().map(UserResponse::from).toList();
  }

  /**
   * Xóa user theo ID — chỉ Admin có quyền gọi.
   *
   * @param userId ID user cần xóa
   * @throws NotFoundException nếu user không tồn tại
   */
  public void delete(Long userId) {
    if (!userDao.findById(userId).isPresent()) {
      throw new NotFoundException("User not found: " + userId);
    }
    userDao.delete(userId);
  }
}
