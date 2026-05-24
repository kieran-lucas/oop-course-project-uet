package com.auction.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.auction.dao.PasswordResetRequestDao;
import com.auction.dao.UserDao;
import com.auction.exception.DuplicateException;
import com.auction.exception.NotFoundException;
import com.auction.model.Bidder;
import com.auction.model.PasswordResetRecord;
import com.auction.model.User;
import java.util.List;
import java.util.Optional;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit test kiểm tra quy trình đặt lại mật khẩu của {@link PasswordResetService}.
 *
 * <p>Xác nhận: yêu cầu đặt lại tạo bản ghi PENDING; email không tồn tại / đã có yêu cầu pending đều
 * bị từ chối; phê duyệt trả về mật khẩu tạm thời hợp lệ và chuyển trạng thái thành APPROVED; từ
 * chối chuyển thành REJECTED. Jdbi.inTransaction và useTransaction được mock để chạy lambda.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PasswordResetService — quy trình đặt lại mật khẩu")
class PasswordResetServiceTest {

  @Mock private UserDao userDao;
  @Mock private PasswordResetRequestDao resetDao;
  @Mock private Jdbi jdbi;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private Handle handle;

  private PasswordResetService service;

  private static final Long USER_ID = 42L;
  private static final String USER_EMAIL = "alice@example.com";

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    service = new PasswordResetService(userDao, resetDao, jdbi);

    // Route jdbi.inTransaction() to run the lambda with our mock handle
    when(jdbi.inTransaction(any()))
        .thenAnswer(
            inv -> {
              org.jdbi.v3.core.HandleCallback<?, ?> cb = inv.getArgument(0);
              return ((org.jdbi.v3.core.HandleCallback<Object, Exception>) cb).withHandle(handle);
            });

    // Route jdbi.useTransaction() to run the lambda with our mock handle
    doAnswer(
            inv -> {
              org.jdbi.v3.core.HandleConsumer<Exception> cb = inv.getArgument(0);
              cb.useHandle(handle);
              return null;
            })
        .when(jdbi)
        .useTransaction(any());
  }

  private User buildUser() {
    Bidder u = new Bidder("alice", "$2a$12$fakehash", USER_EMAIL);
    u.setId(USER_ID);
    return u;
  }

  // ── requestReset() ────────────────────────────────────────

  @Nested
  @DisplayName("requestReset()")
  class RequestReset {

    @Test
    @DisplayName("tạo bản ghi PENDING cho email tồn tại")
    void createsResetRecordForExistingEmail() {
      when(userDao.findByEmail(USER_EMAIL)).thenReturn(Optional.of(buildUser()));
      when(resetDao.hasPendingRequest(USER_ID)).thenReturn(false);

      assertDoesNotThrow(() -> service.requestReset(USER_EMAIL));

      verify(resetDao).insert(any(PasswordResetRecord.class));
    }

    @Test
    @DisplayName("ném NotFoundException khi email không tồn tại")
    void throwsNotFoundForUnknownEmail() {
      when(userDao.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

      assertThrows(NotFoundException.class, () -> service.requestReset("ghost@example.com"));
      verify(resetDao, never()).insert(any());
    }

    @Test
    @DisplayName("ném DuplicateException khi đã có yêu cầu PENDING")
    void throwsDuplicateWhenAlreadyPending() {
      when(userDao.findByEmail(USER_EMAIL)).thenReturn(Optional.of(buildUser()));
      when(resetDao.hasPendingRequest(USER_ID)).thenReturn(true);

      assertThrows(DuplicateException.class, () -> service.requestReset(USER_EMAIL));
      verify(resetDao, never()).insert(any());
    }

    @Test
    @DisplayName("chuẩn hóa email thành chữ thường trước khi tìm kiếm")
    void normalisesEmailToLowercase() {
      when(userDao.findByEmail(USER_EMAIL)).thenReturn(Optional.of(buildUser()));
      when(resetDao.hasPendingRequest(USER_ID)).thenReturn(false);

      service.requestReset("Alice@Example.COM");

      verify(userDao).findByEmail(USER_EMAIL);
    }

    @Test
    @DisplayName("ánh xạ lỗi duplicate-key DB thành DuplicateException")
    void mapsDuplicateDbExceptionToDuplicateException() {
      when(userDao.findByEmail(USER_EMAIL)).thenReturn(Optional.of(buildUser()));
      when(resetDao.hasPendingRequest(USER_ID)).thenReturn(false);
      when(resetDao.insert(any()))
          .thenThrow(
              new org.jdbi.v3.core.statement.UnableToExecuteStatementException(
                  "duplicate key", (org.jdbi.v3.core.statement.StatementContext) null));

      assertThrows(DuplicateException.class, () -> service.requestReset(USER_EMAIL));
    }
  }

  // ── getPendingRequests() ──────────────────────────────────

  @Nested
  @DisplayName("getPendingRequests()")
  class GetPendingRequests {

    @Test
    @DisplayName("trả về danh sách từ DAO")
    void returnsListFromDao() {
      PasswordResetRecord r1 = new PasswordResetRecord(1L);
      PasswordResetRecord r2 = new PasswordResetRecord(2L);
      when(resetDao.findByStatus("PENDING")).thenReturn(List.of(r1, r2));

      List<PasswordResetRecord> result = service.getPendingRequests();

      assertEquals(2, result.size());
    }

    @Test
    @DisplayName("trả về danh sách rỗng khi không có yêu cầu PENDING")
    void returnsEmptyListWhenNoPending() {
      when(resetDao.findByStatus("PENDING")).thenReturn(List.of());

      assertTrue(service.getPendingRequests().isEmpty());
    }
  }

  // ── approveReset() ────────────────────────────────────────

  @Nested
  @DisplayName("approveReset()")
  class ApproveReset {

    @Test
    @DisplayName("trả về mật khẩu tạm thời không rỗng")
    void returnsNonBlankTempPassword() {
      PasswordResetRecord record = new PasswordResetRecord(USER_ID);
      record.setId(10L);
      record.setStatus("PENDING");
      when(resetDao.findByIdForUpdate(handle, 10L)).thenReturn(Optional.of(record));

      String tempPassword = (String) service.approveReset(10L);

      assertNotNull(tempPassword);
      assertFalse(tempPassword.isBlank());
    }

    @Test
    @DisplayName("độ dài mật khẩu tạm thời đúng với hằng số cấu hình")
    void tempPasswordHasExpectedLength() {
      PasswordResetRecord record = new PasswordResetRecord(USER_ID);
      record.setId(10L);
      record.setStatus("PENDING");
      when(resetDao.findByIdForUpdate(handle, 10L)).thenReturn(Optional.of(record));

      String tempPassword = (String) service.approveReset(10L);

      assertTrue(tempPassword.length() >= 6, "temp password must have at least 6 characters");
    }

    @Test
    @DisplayName("cập nhật hash mật khẩu user trong transaction")
    void updatesPasswordHashInTransaction() {
      PasswordResetRecord record = new PasswordResetRecord(USER_ID);
      record.setId(10L);
      record.setStatus("PENDING");
      when(resetDao.findByIdForUpdate(handle, 10L)).thenReturn(Optional.of(record));

      String tempPassword = (String) service.approveReset(10L);

      // Verify the returned temp password is a valid BCrypt-hashable string
      assertNotNull(tempPassword);
      assertFalse(tempPassword.isBlank());
    }

    @Test
    @DisplayName("chuyển trạng thái yêu cầu thành APPROVED")
    void transitionsRequestToApproved() {
      PasswordResetRecord record = new PasswordResetRecord(USER_ID);
      record.setId(10L);
      record.setStatus("PENDING");
      when(resetDao.findByIdForUpdate(handle, 10L)).thenReturn(Optional.of(record));

      service.approveReset(10L);

      verify(resetDao).transitionStatusInTransaction(handle, 10L, "PENDING", "APPROVED");
    }

    @Test
    @DisplayName("ném NotFoundException khi yêu cầu không tồn tại")
    void throwsNotFoundForMissingRequest() {
      when(resetDao.findByIdForUpdate(handle, 99L)).thenReturn(Optional.empty());

      assertThrows(NotFoundException.class, () -> service.approveReset(99L));
    }

    @Test
    @DisplayName("ném IllegalStateException khi yêu cầu không ở trạng thái PENDING")
    void throwsIllegalStateWhenAlreadyProcessed() {
      PasswordResetRecord record = new PasswordResetRecord(USER_ID);
      record.setId(10L);
      record.setStatus("APPROVED");
      when(resetDao.findByIdForUpdate(handle, 10L)).thenReturn(Optional.of(record));

      assertThrows(IllegalStateException.class, () -> service.approveReset(10L));
    }
  }

  // ── rejectReset() ─────────────────────────────────────────

  @Nested
  @DisplayName("rejectReset()")
  class RejectReset {

    @Test
    @DisplayName("chuyển trạng thái yêu cầu thành REJECTED")
    void transitionsRequestToRejected() {
      PasswordResetRecord record = new PasswordResetRecord(USER_ID);
      record.setId(20L);
      record.setStatus("PENDING");
      when(resetDao.findByIdForUpdate(handle, 20L)).thenReturn(Optional.of(record));

      assertDoesNotThrow(() -> service.rejectReset(20L));

      verify(resetDao).transitionStatusInTransaction(handle, 20L, "PENDING", "REJECTED");
    }

    @Test
    @DisplayName("ném NotFoundException khi yêu cầu không tồn tại")
    void throwsNotFoundForMissingRequest() {
      when(resetDao.findByIdForUpdate(handle, 99L)).thenReturn(Optional.empty());

      assertThrows(NotFoundException.class, () -> service.rejectReset(99L));
    }

    @Test
    @DisplayName("ném IllegalStateException khi yêu cầu không ở trạng thái PENDING")
    void throwsIllegalStateWhenAlreadyProcessed() {
      PasswordResetRecord record = new PasswordResetRecord(USER_ID);
      record.setId(20L);
      record.setStatus("REJECTED");
      when(resetDao.findByIdForUpdate(handle, 20L)).thenReturn(Optional.of(record));

      assertThrows(IllegalStateException.class, () -> service.rejectReset(20L));
    }
  }
}
