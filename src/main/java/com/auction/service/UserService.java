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
import com.auction.model.Admin;
import com.auction.model.DepositRecord;
import com.auction.model.User;
import com.auction.pattern.factory.UserFactory;
import java.math.BigDecimal;
import java.util.List;

/**
 * Service xử lý toàn bộ logic nghiệp vụ liên quan đến người dùng: đăng ký, đăng nhập, đổi mật khẩu,
 * nạp tiền và quản trị tài khoản.
 *
 * <p>Lớp này là tầng trung gian giữa Controller và DAO — không truy cập database trực tiếp mà uỷ
 * quyền cho {@link UserDao} và {@link DepositRequestDao}.
 */
public class UserService {
  private final UserDao userDao;
  private final DepositRequestDao depositRequestDao;
  private final org.jdbi.v3.core.Jdbi jdbi;

  public UserService(
      UserDao userDao, DepositRequestDao depositRequestDao, org.jdbi.v3.core.Jdbi jdbi) {
    this.userDao = userDao;
    this.depositRequestDao = depositRequestDao;
    this.jdbi = jdbi;
  }

  /**
   * Đăng ký tài khoản mới.
   *
   * <p>Thứ tự validate: username → email → password → kiểm tra trùng username. Mật khẩu được hash
   * bằng BCrypt (cost factor = 12) trước khi lưu vào DB, tuyệt đối không lưu plaintext.
   *
   * @param req thông tin đăng ký gồm username, email, password, role
   * @return {@link User} vừa được tạo (có id từ DB)
   * @throws IllegalArgumentException nếu username/email/password không hợp lệ hoặc role không được
   *     hỗ trợ
   * @throws DuplicateException nếu username đã tồn tại trong hệ thống
   */
  public User register(RegisterRequest req) {
    String username = req.getUsername() != null ? req.getUsername().trim() : null;
    String email = req.getEmail() != null ? req.getEmail().trim() : null;
    String role = req.getRole() != null ? req.getRole().trim() : null;

    if (username == null || username.isEmpty()) {
      throw new IllegalArgumentException("Username không được để trống");
    }

    // Regex cơ bản: kiểm tra có ký tự @ và phần domain phía sau
    String emailRegex = "^[A-Za-z0-9+_.-]+@(.+)$";
    if (email == null || !email.matches(emailRegex)) {
      throw new IllegalArgumentException("Định dạng email không hợp lệ.");
    }

    if (req.getPassword() == null || req.getPassword().length() < 6) {
      throw new IllegalArgumentException("Mật khẩu phải có ít nhất 6 ký tự.");
    }
    if (role == null || role.isEmpty()) {
      throw new IllegalArgumentException("Role không hợp lệ: " + req.getRole());
    }

    if (userDao.existsByUsername(username)) {
      throw new DuplicateException("Username '" + username + "' đã tồn tại!");
    }
    if (userDao.existsByEmail(email)) {
      throw new DuplicateException("Email '" + email + "' đã tồn tại!");
    }

    // BCrypt với cost factor 12: đủ chậm để chống brute-force, không quá nặng cho server
    String hashedPassword = BCrypt.withDefaults().hashToString(12, req.getPassword().toCharArray());

    User newUser;
    try {
      newUser = UserFactory.create(role);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Role không hợp lệ: " + role);
    }
    if (newUser instanceof Admin) {
      throw new IllegalArgumentException("Role không hợp lệ: " + role);
    }

    newUser.setUsername(username);
    newUser.setPasswordHash(hashedPassword);
    newUser.setEmail(email);

    try {
      return userDao.insert(newUser);
    } catch (org.jdbi.v3.core.statement.UnableToExecuteStatementException e) {
      String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
      if (msg.contains("users_email_key") || msg.contains("email")) {
        throw new DuplicateException("Email '" + email + "' đã tồn tại!");
      }
      if (msg.contains("users_username_key")
          || msg.contains("unique")
          || msg.contains("duplicate")) {
        throw new DuplicateException("Username '" + username + "' đã tồn tại!");
      }
      throw e;
    }
  }

  /**
   * Đăng nhập và trả về JWT token nếu thông tin hợp lệ.
   *
   * <p>BCrypt tự động so sánh hash — không cần hash lại password rồi so sánh thủ công.
   *
   * @param req thông tin đăng nhập gồm username và password
   * @return JWT token dạng String, dùng cho các request tiếp theo qua header Authorization
   * @throws NotFoundException nếu username không tồn tại
   * @throws UnauthorizedException nếu password không khớp
   */
  public String login(LoginRequest req) {
    String username = req.getUsername() != null ? req.getUsername().trim() : null;
    if (username == null || username.isEmpty()) {
      throw new IllegalArgumentException("Username không được để trống");
    }
    if (req.getPassword() == null || req.getPassword().isEmpty()) {
      throw new IllegalArgumentException("Mật khẩu không được để trống");
    }

    User user =
        userDao
            .findByUsername(username)
            .orElseThrow(() -> new NotFoundException("Không tìm thấy tài khoản với username này."));

    BCrypt.Result result;
    try {
      result = BCrypt.verifyer().verify(req.getPassword().toCharArray(), user.getPasswordHash());
    } catch (RuntimeException e) {
      throw new UnauthorizedException("Sai mật khẩu.");
    }
    if (!result.verified) {
      throw new UnauthorizedException("Sai mật khẩu.");
    }

    // Nhúng userId, username, role vào payload của JWT để middleware đọc sau này
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
    String normalizedUsername = username != null ? username.trim() : null;
    return userDao
        .findByUsername(normalizedUsername)
        .map(User::getRole)
        .orElseThrow(
            () -> new NotFoundException("Không tìm thấy người dùng: " + normalizedUsername));
  }

  /**
   * Lấy thông tin user theo ID.
   *
   * @param userId ID của user
   * @return {@link UserResponse} chứa thông tin công khai (không bao gồm passwordHash)
   * @throws NotFoundException nếu user không tồn tại
   */
  public UserResponse findById(Long userId) {
    User user =
        userDao
            .findById(userId)
            .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng: " + userId));
    return UserResponse.from(user);
  }

  /**
   * Đổi mật khẩu — xác minh mật khẩu cũ trước khi cập nhật.
   *
   * @param userId ID người dùng lấy từ JWT
   * @param req request chứa {@code currentPassword} và {@code newPassword}
   * @throws NotFoundException nếu user không tồn tại
   * @throws IllegalArgumentException nếu mật khẩu mới không đủ độ dài
   * @throws UnauthorizedException nếu mật khẩu hiện tại không đúng
   */
  public void changePassword(Long userId, ChangePasswordRequest req) {
    if (req.getNewPassword() == null || req.getNewPassword().length() < 6) {
      throw new IllegalArgumentException("Mật khẩu mới phải có ít nhất 6 ký tự.");
    }

    User user =
        userDao
            .findById(userId)
            .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng: " + userId));

    // Xác minh mật khẩu cũ — bắt buộc để chống tấn công chiếm quyền khi token bị lộ
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
   * Gửi yêu cầu nạp tiền — tạo bản ghi với trạng thái PENDING, chờ Admin xác nhận.
   *
   * <p>Tiền chưa được cộng vào số dư ngay — chỉ cộng sau khi Admin phê duyệt qua {@link
   * #approveDeposit(Long)}.
   *
   * @param userId ID người dùng lấy từ JWT
   * @param amount số tiền muốn nạp (phải lớn hơn 0)
   * @return {@link DepositRecord} vừa tạo với trạng thái PENDING
   * @throws IllegalArgumentException nếu amount không hợp lệ
   * @throws NotFoundException nếu user không tồn tại
   */
  public DepositRecord requestDeposit(Long userId, BigDecimal amount) {
    if (amount == null || amount.signum() <= 0) {
      throw new IllegalArgumentException("Số tiền nạp phải lớn hơn 0.");
    }
    // Kiểm tra user tồn tại trước khi tạo bản ghi deposit
    userDao
        .findById(userId)
        .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng: " + userId));
    return depositRequestDao.insert(new DepositRecord(userId, amount));
  }

  /**
   * Lấy danh sách yêu cầu nạp tiền đang chờ duyệt — dành cho Admin.
   *
   * @return danh sách {@link DepositRecord} có trạng thái PENDING
   */
  public List<DepositRecord> getPendingDeposits() {
    return depositRequestDao.findByStatus("PENDING");
  }

  /**
   * Admin phê duyệt yêu cầu nạp tiền: cộng tiền vào số dư user và chuyển trạng thái → APPROVED.
   *
   * <p>Sử dụng transaction để đảm bảo tính nguyên tử giữa việc cộng tiền và cập nhật trạng thái.
   * Khóa row user để tránh race condition nếu có nhiều yêu cầu nạp tiền được duyệt cùng lúc.
   *
   * @param requestId ID của yêu cầu nạp tiền
   * @return {@link UserResponse} phản ánh số dư sau khi được cộng thêm
   * @throws NotFoundException nếu yêu cầu hoặc user không tồn tại
   * @throws IllegalStateException nếu yêu cầu đã được xử lý trước đó (không còn PENDING)
   */
  public UserResponse approveDeposit(Long requestId) {
    return jdbi.inTransaction(
        handle -> {
          DepositRecord record =
              depositRequestDao
                  .findByIdForUpdate(handle, requestId)
                  .orElseThrow(
                      () -> new NotFoundException("Không tìm thấy yêu cầu nạp tiền: " + requestId));

          if (!"PENDING".equals(record.getStatus())) {
            throw new IllegalStateException("Yêu cầu này đã được xử lý rồi.");
          }

          // Khóa row user để cập nhật balance an toàn
          User user = userDao.findByIdForUpdate(handle, record.getUserId());

          BigDecimal current = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
          user.setBalance(current.add(record.getAmount()));

          // Cập nhật cả 2 trong cùng transaction
          handle
              .createUpdate("UPDATE users SET balance = :balance WHERE id = :id")
              .bind("balance", user.getBalance())
              .bind("id", user.getId())
              .execute();

          depositRequestDao.transitionStatusInTransaction(handle, requestId, "PENDING", "APPROVED");

          return UserResponse.from(user);
        });
  }

  /**
   * Admin từ chối yêu cầu nạp tiền: chuyển trạng thái → REJECTED, không cộng tiền.
   *
   * @param requestId ID của yêu cầu nạp tiền
   * @return userId của người gửi yêu cầu — dùng để gửi thông báo qua WebSocket
   * @throws NotFoundException nếu yêu cầu không tồn tại
   * @throws IllegalStateException nếu yêu cầu đã được xử lý trước đó
   */
  public Long rejectDeposit(Long requestId) {
    return jdbi.inTransaction(
        handle -> {
          DepositRecord record =
              depositRequestDao
                  .findByIdForUpdate(handle, requestId)
                  .orElseThrow(
                      () -> new NotFoundException("Không tìm thấy yêu cầu nạp tiền: " + requestId));

          if (!"PENDING".equals(record.getStatus())) {
            throw new IllegalStateException("Yêu cầu này đã được xử lý rồi.");
          }
          depositRequestDao.transitionStatusInTransaction(handle, requestId, "PENDING", "REJECTED");
          return record.getUserId();
        });
  }

  /**
   * Lấy danh sách toàn bộ người dùng — chỉ dành cho Admin.
   *
   * @return danh sách {@link UserResponse} của tất cả tài khoản trong hệ thống
   */
  public List<UserResponse> getAll() {
    return userDao.findAll().stream().map(UserResponse::from).toList();
  }

  /**
   * Xóa tài khoản người dùng theo ID — chỉ Admin có quyền gọi.
   *
   * @param userId ID người dùng cần xóa
   * @throws NotFoundException nếu người dùng không tồn tại
   */
  public void delete(Long userId) {
    userDao
        .findById(userId)
        .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng: " + userId));
    userDao.delete(userId);
  }
}
