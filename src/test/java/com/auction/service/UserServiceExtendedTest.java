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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserService — extended paths")
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
  @DisplayName("register() — input validation")
  class RegisterValidation {

    @Test
    @DisplayName("null username throws IllegalArgumentException")
    void nullUsernameThrows() {
      RegisterRequest req = new RegisterRequest(null, "pass123", "a@b.com", "BIDDER");
      assertThrows(java.lang.IllegalArgumentException.class, () -> service.register(req));
    }

    @Test
    @DisplayName("blank username throws IllegalArgumentException")
    void blankUsernameThrows() {
      RegisterRequest req = new RegisterRequest("  ", "pass123", "a@b.com", "BIDDER");
      assertThrows(java.lang.IllegalArgumentException.class, () -> service.register(req));
    }

    @Test
    @DisplayName("invalid email format throws IllegalArgumentException")
    void invalidEmailThrows() {
      RegisterRequest req = new RegisterRequest("alice", "pass123", "not-an-email", "BIDDER");
      assertThrows(java.lang.IllegalArgumentException.class, () -> service.register(req));
    }

    @Test
    @DisplayName("null email throws IllegalArgumentException")
    void nullEmailThrows() {
      RegisterRequest req = new RegisterRequest("alice", "pass123", null, "BIDDER");
      assertThrows(java.lang.IllegalArgumentException.class, () -> service.register(req));
    }

    @Test
    @DisplayName("password shorter than 6 chars throws IllegalArgumentException")
    void shortPasswordThrows() {
      RegisterRequest req = new RegisterRequest("alice", "abc", "a@b.com", "BIDDER");
      assertThrows(java.lang.IllegalArgumentException.class, () -> service.register(req));
    }

    @Test
    @DisplayName("null password throws IllegalArgumentException")
    void nullPasswordThrows() {
      RegisterRequest req = new RegisterRequest("alice", null, "a@b.com", "BIDDER");
      assertThrows(java.lang.IllegalArgumentException.class, () -> service.register(req));
    }

    @Test
    @DisplayName("null role throws IllegalArgumentException")
    void nullRoleThrows() {
      RegisterRequest req = new RegisterRequest("alice", "pass123", "a@b.com", null);
      assertThrows(java.lang.IllegalArgumentException.class, () -> service.register(req));
    }

    @Test
    @DisplayName("ADMIN role is rejected — cannot register as admin")
    void adminRoleRejected() {
      RegisterRequest req = new RegisterRequest("alice", "pass123", "a@b.com", "ADMIN");
      assertThrows(java.lang.IllegalArgumentException.class, () -> service.register(req));
    }

    @Test
    @DisplayName("SELLER role succeeds when no duplicates exist")
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
  @DisplayName("login() — input validation")
  class LoginValidation {

    @Test
    @DisplayName("null username throws IllegalArgumentException")
    void nullUsernameThrows() {
      assertThrows(
          java.lang.IllegalArgumentException.class,
          () -> service.login(new LoginRequest(null, "pass")));
    }

    @Test
    @DisplayName("blank username throws IllegalArgumentException")
    void blankUsernameThrows() {
      assertThrows(
          java.lang.IllegalArgumentException.class,
          () -> service.login(new LoginRequest("  ", "pass")));
    }

    @Test
    @DisplayName("null password throws IllegalArgumentException")
    void nullPasswordThrows() {
      assertThrows(
          java.lang.IllegalArgumentException.class,
          () -> service.login(new LoginRequest("alice", null)));
    }

    @Test
    @DisplayName("empty password throws IllegalArgumentException")
    void emptyPasswordThrows() {
      assertThrows(
          java.lang.IllegalArgumentException.class,
          () -> service.login(new LoginRequest("alice", "")));
    }
  }

  // ── changePassword() validation ───────────────────────────

  @Nested
  @DisplayName("changePassword() — validation")
  class ChangePasswordValidation {

    @Test
    @DisplayName("short new password throws IllegalArgumentException")
    void shortNewPasswordThrows() {
      ChangePasswordRequest req = new ChangePasswordRequest();
      req.setCurrentPassword("oldPass");
      req.setNewPassword("abc");

      assertThrows(java.lang.IllegalArgumentException.class, () -> service.changePassword(1L, req));
    }

    @Test
    @DisplayName("null new password throws IllegalArgumentException")
    void nullNewPasswordThrows() {
      ChangePasswordRequest req = new ChangePasswordRequest();
      req.setCurrentPassword("oldPass");
      req.setNewPassword(null);

      assertThrows(java.lang.IllegalArgumentException.class, () -> service.changePassword(1L, req));
    }

    @Test
    @DisplayName("user not found throws NotFoundException")
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
    @DisplayName("returns role for existing user")
    void returnsRoleForExistingUser() {
      Bidder user = buildBidder("alice", "hash");
      when(userDao.findByUsername("alice")).thenReturn(Optional.of(user));

      assertEquals("BIDDER", service.getRoleByUsername("alice"));
    }

    @Test
    @DisplayName("throws NotFoundException for unknown user")
    void throwsNotFoundForUnknownUser() {
      when(userDao.findByUsername("ghost")).thenReturn(Optional.empty());

      assertThrows(NotFoundException.class, () -> service.getRoleByUsername("ghost"));
    }

    @Test
    @DisplayName("normalises whitespace from username")
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
    @DisplayName("returns UserResponse for existing user")
    void returnsUserResponse() {
      Bidder user = buildBidder("alice", "hash");
      when(userDao.findById(1L)).thenReturn(Optional.of(user));

      UserResponse r = service.findById(1L);

      assertEquals("alice", r.getUsername());
    }

    @Test
    @DisplayName("throws NotFoundException for missing user")
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
    @DisplayName("creates PENDING deposit record for valid amount")
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
    @DisplayName("rejects zero amount")
    void rejectsZeroAmount() {
      assertThrows(
          java.lang.IllegalArgumentException.class,
          () -> service.requestDeposit(1L, BigDecimal.ZERO));
    }

    @Test
    @DisplayName("rejects negative amount")
    void rejectsNegativeAmount() {
      assertThrows(
          java.lang.IllegalArgumentException.class,
          () -> service.requestDeposit(1L, new BigDecimal("-100000")));
    }

    @Test
    @DisplayName("throws NotFoundException when user does not exist")
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
    @DisplayName("returns list from DAO")
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
    @DisplayName("returns all users as UserResponse DTOs")
    void returnsAllUsers() {
      Bidder u1 = buildBidder("alice", "hash1");
      Seller u2 = new Seller("bob", "hash2", "bob@ex.com");
      u2.setId(2L);
      when(userDao.findAll()).thenReturn(List.of(u1, u2));

      List<UserResponse> result = service.getAll();

      assertEquals(2, result.size());
    }

    @Test
    @DisplayName("returns empty list when no users exist")
    void returnsEmptyList() {
      when(userDao.findAll()).thenReturn(List.of());

      assertTrue(service.getAll().isEmpty());
    }
  }

  // ── delete() ─────────────────────────────────────────────

  @Nested
  @DisplayName("delete() — user not found")
  class DeleteEdgeCases {

    @Test
    @DisplayName("throws NotFoundException when user does not exist (for second not-found)")
    void throwsNotFoundWhenDeleteReturnsfalse() {
      Bidder user = buildBidder("alice", "hash");
      when(userDao.findById(1L)).thenReturn(Optional.of(user));
      when(userDao.hasDeleteBlockingReferences(1L)).thenReturn(false);
      when(userDao.delete(1L)).thenReturn(false); // row disappeared between check and delete

      assertThrows(NotFoundException.class, () -> service.delete(1L));
    }
  }
}
