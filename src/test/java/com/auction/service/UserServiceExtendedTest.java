package com.auction.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.auction.dao.DepositRequestDao;
import com.auction.dao.UserDao;
import com.auction.dto.ChangePasswordRequest;
import com.auction.dto.LoginRequest;
import com.auction.dto.RegisterRequest;
import com.auction.dto.UserResponse;
import com.auction.exception.NotFoundException;
import com.auction.model.Bidder;
import com.auction.model.DepositRecord;
import com.auction.model.Seller;
import com.auction.model.User;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit test bổ sung cho {@link UserService} — kiểm tra validation đầu vào và các luồng mở rộng.
 *
 * <p>Kiểm tra validation trong register() (null/blank username, email không hợp lệ, mật khẩu ngắn,
 * role ADMIN bị từ chối), login() (null/blank input), changePassword() (mật khẩu mới không hợp lệ),
 * và các phương thức tra cứu: getRoleByUsername(), findById(), requestDeposit(), getAll(),
 * delete().
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserService — validation đầu vào và các trường hợp mở rộng")
class UserServiceExtendedTest {

  @Mock private UserDao userDao;
  @Mock private DepositRequestDao depositRequestDao;
  @Mock private Jdbi jdbi;

  private UserService service;

  @BeforeEach
  void setUp() {
    service = new UserService(userDao, depositRequestDao, jdbi);
  }

  private Bidder buildBidder(String username, String hash) {
    Bidder u = new Bidder(username, hash, username + "@example.com");
    u.setId(1L);
    return u;
  }

  // ── register() validation edge cases ─────────────────────

  @Nested
  @DisplayName("register() — kiểm tra đầu vào")
  class RegisterValidation {

    @Test
    @DisplayName("username null — ném IllegalArgumentException")
    void nullUsernameThrows() {
      RegisterRequest req = new RegisterRequest(null, "pass123", "a@b.com", "BIDDER");
      assertThrows(java.lang.IllegalArgumentException.class, () -> service.register(req));
    }

    @Test
    @DisplayName("username rỗng — ném IllegalArgumentException")
    void blankUsernameThrows() {
      RegisterRequest req = new RegisterRequest("  ", "pass123", "a@b.com", "BIDDER");
      assertThrows(java.lang.IllegalArgumentException.class, () -> service.register(req));
    }

    @Test
    @DisplayName("email không đúng định dạng — ném IllegalArgumentException")
    void invalidEmailThrows() {
      RegisterRequest req = new RegisterRequest("alice", "pass123", "not-an-email", "BIDDER");
      assertThrows(java.lang.IllegalArgumentException.class, () -> service.register(req));
    }

    @Test
    @DisplayName("email null — ném IllegalArgumentException")
    void nullEmailThrows() {
      RegisterRequest req = new RegisterRequest("alice", "pass123", null, "BIDDER");
      assertThrows(java.lang.IllegalArgumentException.class, () -> service.register(req));
    }

    @Test
    @DisplayName("mật khẩu ngắn hơn 6 ký tự — ném IllegalArgumentException")
    void shortPasswordThrows() {
      RegisterRequest req = new RegisterRequest("alice", "abc", "a@b.com", "BIDDER");
      assertThrows(java.lang.IllegalArgumentException.class, () -> service.register(req));
    }

    @Test
    @DisplayName("mật khẩu null — ném IllegalArgumentException")
    void nullPasswordThrows() {
      RegisterRequest req = new RegisterRequest("alice", null, "a@b.com", "BIDDER");
      assertThrows(java.lang.IllegalArgumentException.class, () -> service.register(req));
    }

    @Test
    @DisplayName("role null — ném IllegalArgumentException")
    void nullRoleThrows() {
      RegisterRequest req = new RegisterRequest("alice", "pass123", "a@b.com", null);
      assertThrows(java.lang.IllegalArgumentException.class, () -> service.register(req));
    }

    @Test
    @DisplayName("role ADMIN bị từ chối — không được đăng ký làm admin")
    void adminRoleRejected() {
      RegisterRequest req = new RegisterRequest("alice", "pass123", "a@b.com", "ADMIN");
      assertThrows(java.lang.IllegalArgumentException.class, () -> service.register(req));
    }

    @Test
    @DisplayName("role SELLER thành công khi không trùng lặp")
    void sellerRoleSucceeds() {
      when(userDao.insert(any(User.class)))
          .thenAnswer(
              inv -> {
                User u = inv.getArgument(0);
                u.setId(2L);
                return u;
              });

      RegisterRequest req = new RegisterRequest("seller1", "pass123", "seller@ex.com", "SELLER");
      User result = service.register(req);

      assertEquals("SELLER", result.getRole());
    }
  }

  // ── login() validation ────────────────────────────────────

  @Nested
  @DisplayName("login() — kiểm tra đầu vào")
  class LoginValidation {

    @Test
    @DisplayName("username null — ném IllegalArgumentException")
    void nullUsernameThrows() {
      assertThrows(
          java.lang.IllegalArgumentException.class,
          () -> service.login(new LoginRequest(null, "pass")));
    }

    @Test
    @DisplayName("username rỗng — ném IllegalArgumentException")
    void blankUsernameThrows() {
      assertThrows(
          java.lang.IllegalArgumentException.class,
          () -> service.login(new LoginRequest("  ", "pass")));
    }

    @Test
    @DisplayName("mật khẩu null — ném IllegalArgumentException")
    void nullPasswordThrows() {
      assertThrows(
          java.lang.IllegalArgumentException.class,
          () -> service.login(new LoginRequest("alice", null)));
    }

    @Test
    @DisplayName("mật khẩu rỗng — ném IllegalArgumentException")
    void emptyPasswordThrows() {
      assertThrows(
          java.lang.IllegalArgumentException.class,
          () -> service.login(new LoginRequest("alice", "")));
    }
  }

  // ── changePassword() validation ───────────────────────────

  @Nested
  @DisplayName("changePassword() — kiểm tra đầu vào")
  class ChangePasswordValidation {

    @Test
    @DisplayName("mật khẩu mới quá ngắn — ném IllegalArgumentException")
    void shortNewPasswordThrows() {
      ChangePasswordRequest req = new ChangePasswordRequest();
      req.setCurrentPassword("oldPass");
      req.setNewPassword("abc");

      assertThrows(java.lang.IllegalArgumentException.class, () -> service.changePassword(1L, req));
    }

    @Test
    @DisplayName("mật khẩu mới null — ném IllegalArgumentException")
    void nullNewPasswordThrows() {
      ChangePasswordRequest req = new ChangePasswordRequest();
      req.setCurrentPassword("oldPass");
      req.setNewPassword(null);

      assertThrows(java.lang.IllegalArgumentException.class, () -> service.changePassword(1L, req));
    }

    @Test
    @DisplayName("user không tồn tại — ném NotFoundException")
    void userNotFoundThrows() {
      when(userDao.findById(99L)).thenReturn(Optional.empty());
      ChangePasswordRequest req = new ChangePasswordRequest();
      req.setCurrentPassword("old");
      req.setNewPassword("newPass123");

      assertThrows(NotFoundException.class, () -> service.changePassword(99L, req));
    }
  }

  // ── getRoleByUsername() ───────────────────────────────────

  @Nested
  @DisplayName("getRoleByUsername()")
  class GetRoleByUsername {

    @Test
    @DisplayName("trả về role cho user tồn tại")
    void returnsRoleForExistingUser() {
      Bidder user = buildBidder("alice", "hash");
      when(userDao.findByUsername("alice")).thenReturn(Optional.of(user));

      assertEquals("BIDDER", service.getRoleByUsername("alice"));
    }

    @Test
    @DisplayName("ném NotFoundException cho user không tồn tại")
    void throwsNotFoundForUnknownUser() {
      when(userDao.findByUsername("ghost")).thenReturn(Optional.empty());

      assertThrows(NotFoundException.class, () -> service.getRoleByUsername("ghost"));
    }

    @Test
    @DisplayName("chuẩn hóa khoảng trắng trong username")
    void normalisesUsername() {
      Bidder user = buildBidder("alice", "hash");
      when(userDao.findByUsername("alice")).thenReturn(Optional.of(user));

      assertEquals("BIDDER", service.getRoleByUsername("  alice  "));
    }
  }

  // ── findById() ────────────────────────────────────────────

  @Nested
  @DisplayName("findById()")
  class FindById {

    @Test
    @DisplayName("trả về UserResponse cho user tồn tại")
    void returnsUserResponse() {
      Bidder user = buildBidder("alice", "hash");
      when(userDao.findById(1L)).thenReturn(Optional.of(user));

      UserResponse r = service.findById(1L);

      assertEquals("alice", r.getUsername());
    }

    @Test
    @DisplayName("ném NotFoundException khi user không tồn tại")
    void throwsNotFoundForMissingUser() {
      when(userDao.findById(99L)).thenReturn(Optional.empty());

      assertThrows(NotFoundException.class, () -> service.findById(99L));
    }
  }

  // ── requestDeposit() ──────────────────────────────────────

  @Nested
  @DisplayName("requestDeposit()")
  class RequestDeposit {

    @Test
    @DisplayName("tạo bản ghi PENDING cho số tiền hợp lệ")
    void createsPendingDepositRecord() {
      Bidder user = buildBidder("alice", "hash");
      when(userDao.findById(1L)).thenReturn(Optional.of(user));
      DepositRecord expectedRecord = new DepositRecord(1L, new BigDecimal("500000"));
      when(depositRequestDao.insert(any())).thenReturn(expectedRecord);

      DepositRecord result = service.requestDeposit(1L, new BigDecimal("500000"));

      assertNotNull(result);
      verify(depositRequestDao).insert(any());
    }

    @Test
    @DisplayName("từ chối số tiền bằng 0")
    void rejectsZeroAmount() {
      assertThrows(
          java.lang.IllegalArgumentException.class,
          () -> service.requestDeposit(1L, BigDecimal.ZERO));
    }

    @Test
    @DisplayName("từ chối số tiền âm")
    void rejectsNegativeAmount() {
      assertThrows(
          java.lang.IllegalArgumentException.class,
          () -> service.requestDeposit(1L, new BigDecimal("-100000")));
    }

    @Test
    @DisplayName("ném NotFoundException khi user không tồn tại")
    void throwsNotFoundForMissingUser() {
      when(userDao.findById(99L)).thenReturn(Optional.empty());

      assertThrows(
          NotFoundException.class, () -> service.requestDeposit(99L, new BigDecimal("100000")));
    }
  }

  // ── getPendingDeposits() ──────────────────────────────────

  @Nested
  @DisplayName("getPendingDeposits()")
  class GetPendingDeposits {

    @Test
    @DisplayName("trả về danh sách từ DAO")
    void returnsListFromDao() {
      DepositRecord r1 = new DepositRecord(1L, new BigDecimal("100000"));
      when(depositRequestDao.findByStatus("PENDING")).thenReturn(List.of(r1));

      assertEquals(1, service.getPendingDeposits().size());
    }
  }

  // ── getAll() ──────────────────────────────────────────────

  @Nested
  @DisplayName("getAll()")
  class GetAll {

    @Test
    @DisplayName("trả về tất cả user dưới dạng UserResponse DTO")
    void returnsAllUsers() {
      Bidder u1 = buildBidder("alice", "hash1");
      Seller u2 = new Seller("bob", "hash2", "bob@ex.com");
      u2.setId(2L);
      when(userDao.findAll()).thenReturn(List.of(u1, u2));

      List<UserResponse> result = service.getAll();

      assertEquals(2, result.size());
    }

    @Test
    @DisplayName("trả về danh sách rỗng khi không có user")
    void returnsEmptyList() {
      when(userDao.findAll()).thenReturn(List.of());

      assertTrue(service.getAll().isEmpty());
    }
  }

  // ── delete() ─────────────────────────────────────────────

  @Nested
  @DisplayName("delete() — user không tồn tại")
  class DeleteEdgeCases {

    @Test
    @DisplayName("ném NotFoundException khi user biến mất giữa kiểm tra và xóa")
    void throwsNotFoundWhenDeleteReturnsfalse() {
      Bidder user = buildBidder("alice", "hash");
      when(userDao.findById(1L)).thenReturn(Optional.of(user));
      when(userDao.hasDeleteBlockingReferences(1L)).thenReturn(false);
      when(userDao.delete(1L)).thenReturn(false); // row disappeared between check and delete

      assertThrows(NotFoundException.class, () -> service.delete(1L));
    }
  }
}
