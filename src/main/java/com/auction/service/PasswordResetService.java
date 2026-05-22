package com.auction.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.auction.dao.PasswordResetRequestDao;
import com.auction.dao.UserDao;
import com.auction.exception.DuplicateException;
import com.auction.exception.NotFoundException;
import com.auction.model.PasswordResetRecord;
import com.auction.model.User;
import java.security.SecureRandom;
import java.util.List;
import org.jdbi.v3.core.Jdbi;

/**
 * Dịch vụ quản lý yêu cầu đặt lại mật khẩu qua Admin.
 *
 * <p>Luồng hoạt động:
 *
 * <ol>
 *   <li>User gọi {@link #requestReset(String)} với email đã đăng ký → tạo bản ghi PENDING.
 *   <li>Admin xem danh sách PENDING qua {@link #getPendingRequests()}.
 *   <li>Admin duyệt qua {@link #approveReset(Long)} → tạo mật khẩu mới 6 ký tự (a-z, 0-9).
 *   <li>Hoặc Admin từ chối qua {@link #rejectReset(Long)}.
 * </ol>
 */
public class PasswordResetService {

  private static final String RESET_PASSWORD_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
  private static final int RESET_PASSWORD_LENGTH = 6;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final UserDao userDao;
  private final PasswordResetRequestDao resetDao;
  private final Jdbi jdbi;

  public PasswordResetService(UserDao userDao, PasswordResetRequestDao resetDao, Jdbi jdbi) {
    this.userDao = userDao;
    this.resetDao = resetDao;
    this.jdbi = jdbi;
  }

  private static String generateTempPassword() {
    StringBuilder password = new StringBuilder(RESET_PASSWORD_LENGTH);
    for (int i = 0; i < RESET_PASSWORD_LENGTH; i++) {
      int index = SECURE_RANDOM.nextInt(RESET_PASSWORD_CHARS.length());
      password.append(RESET_PASSWORD_CHARS.charAt(index));
    }
    return password.toString();
  }

  /**
   * Tạo yêu cầu đặt lại mật khẩu cho tài khoản có email tương ứng.
   *
   * @param email email đã đăng ký
   * @throws NotFoundException nếu không tìm thấy tài khoản
   * @throws DuplicateException nếu user đã có yêu cầu đang chờ xét duyệt
   */
  public void requestReset(String email) {
    User user =
        userDao
            .findByEmail(email.toLowerCase().trim())
            .orElseThrow(() -> new NotFoundException("No account found with email: " + email));

    if (resetDao.hasPendingRequest(user.getId())) {
      throw new DuplicateException("You already have a pending request awaiting Admin review.");
    }

    try {
      resetDao.insert(new PasswordResetRecord(user.getId()));
    } catch (org.jdbi.v3.core.statement.UnableToExecuteStatementException e) {
      throw new DuplicateException("You already have a pending request awaiting Admin review.");
    }
  }

  /**
   * Lấy danh sách yêu cầu đang chờ duyệt — dành cho Admin.
   *
   * @return danh sách PasswordResetRecord có status = PENDING
   */
  public List<PasswordResetRecord> getPendingRequests() {
    return resetDao.findByStatus("PENDING");
  }

  /**
   * Admin phê duyệt: tạo mật khẩu mới 6 ký tự (a-z, 0-9), đổi status → APPROVED.
   *
   * @param requestId ID của yêu cầu
   * @throws NotFoundException nếu không tìm thấy yêu cầu hoặc tài khoản
   * @throws IllegalStateException nếu yêu cầu đã được xử lý rồi
   */
  public String approveReset(Long requestId) {
    return jdbi.inTransaction(
        handle -> {
          PasswordResetRecord record =
              resetDao
                  .findByIdForUpdate(handle, requestId)
                  .orElseThrow(() -> new NotFoundException("Request not found: " + requestId));
          if (!"PENDING".equals(record.getStatus())) {
            throw new IllegalStateException("This request has already been processed.");
          }
          String resetPassword = generateTempPassword();
          String hash = BCrypt.withDefaults().hashToString(12, resetPassword.toCharArray());
          handle
              .createUpdate("UPDATE users SET password_hash = :h WHERE id = :id")
              .bind("h", hash)
              .bind("id", record.getUserId())
              .execute();
          resetDao.transitionStatusInTransaction(handle, requestId, "PENDING", "APPROVED");
          return resetPassword;
        });
  }

  /**
   * Admin từ chối yêu cầu: đổi status → REJECTED, không thay đổi mật khẩu.
   *
   * @param requestId ID của yêu cầu
   * @throws NotFoundException nếu không tìm thấy yêu cầu
   * @throws IllegalStateException nếu yêu cầu đã được xử lý rồi
   */
  public void rejectReset(Long requestId) {
    jdbi.useTransaction(
        handle -> {
          PasswordResetRecord record =
              resetDao
                  .findByIdForUpdate(handle, requestId)
                  .orElseThrow(() -> new NotFoundException("Request not found: " + requestId));

          if (!"PENDING".equals(record.getStatus())) {
            throw new IllegalStateException("This request has already been processed.");
          }

          resetDao.transitionStatusInTransaction(handle, requestId, "PENDING", "REJECTED");
        });
  }
}
