package com.auction.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.auction.dao.PasswordResetRequestDao;
import com.auction.dao.UserDao;
import com.auction.exception.DuplicateException;
import com.auction.exception.NotFoundException;
import com.auction.model.PasswordResetRecord;
import com.auction.model.User;
import java.util.List;

/**
 * Dịch vụ quản lý yêu cầu đặt lại mật khẩu qua Admin.
 *
 * <p>Luồng hoạt động:
 *
 * <ol>
 *   <li>User gọi {@link #requestReset(String)} với email đã đăng ký → tạo bản ghi PENDING.
 *   <li>Admin xem danh sách PENDING qua {@link #getPendingRequests()}.
 *   <li>Admin duyệt qua {@link #approveReset(Long)} → mật khẩu reset về {@code "123456"}.
 *   <li>Hoặc Admin từ chối qua {@link #rejectReset(Long)}.
 * </ol>
 */
public class PasswordResetService {

  private static final String DEFAULT_PASSWORD = "123456";

  private final UserDao userDao;
  private final PasswordResetRequestDao resetDao;

  public PasswordResetService(UserDao userDao, PasswordResetRequestDao resetDao) {
    this.userDao = userDao;
    this.resetDao = resetDao;
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
            .orElseThrow(
                () -> new NotFoundException("Không tìm thấy tài khoản với email: " + email));

    if (resetDao.hasPendingRequest(user.getId())) {
      throw new DuplicateException("Bạn đã có yêu cầu đang chờ Admin xét duyệt.");
    }

    resetDao.insert(new PasswordResetRecord(user.getId()));
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
   * Admin phê duyệt: đặt lại mật khẩu về {@code "123456"}, đổi status → APPROVED.
   *
   * @param requestId ID của yêu cầu
   * @throws NotFoundException nếu không tìm thấy yêu cầu hoặc tài khoản
   * @throws IllegalStateException nếu yêu cầu đã được xử lý rồi
   */
  public void approveReset(Long requestId) {
    PasswordResetRecord record =
        resetDao
            .findById(requestId)
            .orElseThrow(() -> new NotFoundException("Không tìm thấy yêu cầu: " + requestId));

    if (!"PENDING".equals(record.getStatus())) {
      throw new IllegalStateException("Yêu cầu này đã được xử lý rồi.");
    }

    User user =
        userDao
            .findById(record.getUserId())
            .orElseThrow(() -> new NotFoundException("User not found: " + record.getUserId()));

    user.setPasswordHash(BCrypt.withDefaults().hashToString(12, DEFAULT_PASSWORD.toCharArray()));
    userDao.update(user);
    resetDao.updateStatus(requestId, "APPROVED");
  }

  /**
   * Admin từ chối yêu cầu: đổi status → REJECTED, không thay đổi mật khẩu.
   *
   * @param requestId ID của yêu cầu
   * @throws NotFoundException nếu không tìm thấy yêu cầu
   * @throws IllegalStateException nếu yêu cầu đã được xử lý rồi
   */
  public void rejectReset(Long requestId) {
    PasswordResetRecord record =
        resetDao
            .findById(requestId)
            .orElseThrow(() -> new NotFoundException("Không tìm thấy yêu cầu: " + requestId));

    if (!"PENDING".equals(record.getStatus())) {
      throw new IllegalStateException("Yêu cầu này đã được xử lý rồi.");
    }

    resetDao.updateStatus(requestId, "REJECTED");
  }
}
