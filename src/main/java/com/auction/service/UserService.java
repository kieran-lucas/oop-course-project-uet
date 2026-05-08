package com.auction.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.auction.config.JwtUtil;
import com.auction.dao.DepositRequestDao;
import com.auction.dao.UserDao;
import com.auction.dto.ChangePasswordRequest;
import com.auction.dto.LoginRequest;
import com.auction.dto.RegisterRequest;
import com.auction.dto.UserResponse;
import com.auction.exception.DuplicateException;
import com.auction.exception.NotFoundException;
import com.auction.exception.UnauthorizedException;
import com.auction.model.Bidder;
import com.auction.model.DepositRecord;
import com.auction.model.Seller;
import com.auction.model.User;
import java.math.BigDecimal;
import java.util.List;

/** Service xử lý logic nghiệp vụ cho User. */
public class UserService {
  private final UserDao userDao;
  private final DepositRequestDao depositRequestDao;

  public UserService(UserDao userDao, DepositRequestDao depositRequestDao) {
    this.userDao = userDao;
    this.depositRequestDao = depositRequestDao;
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
   * Gửi yêu cầu nạp tiền — tạo bản ghi PENDING, chờ Admin xác nhận.
   *
   * @param userId ID user từ JWT
   * @param amount số tiền muốn nạp (phải > 0)
   * @return DepositRecord vừa tạo (status = PENDING)
   */
  public DepositRecord requestDeposit(Long userId, BigDecimal amount) {
    if (amount == null || amount.signum() <= 0) {
      throw new IllegalArgumentException("Số tiền nạp phải lớn hơn 0.");
    }
    userDao
        .findById(userId)
        .orElseThrow(() -> new NotFoundException("User not found: " + userId));
    return depositRequestDao.insert(new DepositRecord(userId, amount));
  }

  /**
   * Lấy danh sách yêu cầu nạp tiền đang chờ duyệt — dành cho Admin.
   *
   * @return danh sách DepositRecord có status = PENDING
   */
  public List<DepositRecord> getPendingDeposits() {
    return depositRequestDao.findByStatus("PENDING");
  }

  /**
   * Admin phê duyệt yêu cầu nạp tiền: cộng tiền vào số dư user, đổi status → APPROVED.
   *
   * @param requestId ID của deposit request
   * @return UserResponse sau khi cập nhật số dư
   */
  public UserResponse approveDeposit(Long requestId) {
    DepositRecord record =
        depositRequestDao
            .findById(requestId)
            .orElseThrow(() -> new NotFoundException("Deposit request not found: " + requestId));

    if (!"PENDING".equals(record.getStatus())) {
      throw new IllegalStateException("Yêu cầu này đã được xử lý rồi.");
    }

    User user =
        userDao
            .findById(record.getUserId())
            .orElseThrow(() -> new NotFoundException("User not found: " + record.getUserId()));

    BigDecimal current = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
    user.setBalance(current.add(record.getAmount()));
    userDao.update(user);
    depositRequestDao.updateStatus(requestId, "APPROVED");
    return UserResponse.from(user);
  }

  /**
   * Admin từ chối yêu cầu nạp tiền: đổi status → REJECTED, không cộng tiền.
   *
   * @param requestId ID của deposit request
   */
  public void rejectDeposit(Long requestId) {
    DepositRecord record =
        depositRequestDao
            .findById(requestId)
            .orElseThrow(() -> new NotFoundException("Deposit request not found: " + requestId));

    if (!"PENDING".equals(record.getStatus())) {
      throw new IllegalStateException("Yêu cầu này đã được xử lý rồi.");
    }
    depositRequestDao.updateStatus(requestId, "REJECTED");
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
