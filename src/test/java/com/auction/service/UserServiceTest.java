package com.auction.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.auction.config.JwtUtil;
import com.auction.dao.UserDao;
import com.auction.dto.ChangePasswordRequest;
import com.auction.dto.LoginRequest;
import com.auction.dto.RegisterRequest;
import com.auction.exception.DuplicateException;
import com.auction.exception.NotFoundException;
import com.auction.exception.UnauthorizedException;
import com.auction.model.Bidder;
import com.auction.model.User;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserServiceTest {

  @Mock private UserDao userDao;

  @InjectMocks private UserService userService;

  private Bidder mockUser;
  private final String plainPassword = "pass123"; // Mật khẩu gốc

  @BeforeEach
  void setUp() {
    // TẠO MÃ BĂM THẬT: Điều này giúp hàm verifyer() của BCrypt không bị sập
    String realBcryptHash = BCrypt.withDefaults().hashToString(12, plainPassword.toCharArray());

    mockUser = new Bidder("nhomAnhDuc", realBcryptHash, "nad@gmail.com");
    mockUser.setId(1L);
  }

  // ============================================================
  // 1. TEST ĐĂNG KÝ (REGISTER)
  // ============================================================

  @Test
  @Order(1)
  @DisplayName("testRegisterSuccess() — Đăng ký thành công dùng RegisterRequest")
  void testRegisterSuccess() {
    when(userDao.insert(any(User.class)))
        .thenAnswer(
            invocation -> {
              User u = invocation.getArgument(0);
              u.setId(100L);
              return u;
            });

    RegisterRequest regReq = new RegisterRequest("newUser", "pass123", "nad@gmail.com", "BIDDER");
    User result = userService.register(regReq);

    assertNotNull(result.getId());
  }

  @Test
  @Order(2)
  @DisplayName("testRegisterDuplicateUsername() — Trùng tên -> Throw DuplicateException")
  void testRegisterDuplicateUsername() {
    when(userDao.insert(any(User.class)))
        .thenThrow(
            new org.jdbi.v3.core.statement.UnableToExecuteStatementException(
                "duplicate key value violates unique constraint \"users_username_key\""));

    RegisterRequest regReq =
        new RegisterRequest("nhomAnhDuc", "pass123", "nad@gmail.com", "BIDDER");

    assertThrows(
        DuplicateException.class,
        () -> {
          userService.register(regReq);
        });
  }

  @Test
  @Order(3)
  @DisplayName("testRegisterDuplicateEmail() — Trùng email -> Throw DuplicateException")
  void testRegisterDuplicateEmail() {
    when(userDao.existsByEmail("nad@gmail.com")).thenReturn(true);

    RegisterRequest regReq = new RegisterRequest("newUser", "pass123", "nad@gmail.com", "BIDDER");

    DuplicateException ex =
        assertThrows(DuplicateException.class, () -> userService.register(regReq));
    assertTrue(ex.getMessage().contains("Email"));
  }

  @Test
  @Order(4)
  @DisplayName("testRegisterDuplicateEmailFromDb() — DB unique email -> Throw DuplicateException")
  void testRegisterDuplicateEmailFromDb() {
    when(userDao.insert(any(User.class)))
        .thenThrow(
            new org.jdbi.v3.core.statement.UnableToExecuteStatementException(
                "duplicate key value violates unique constraint \"users_email_key\""));

    RegisterRequest regReq = new RegisterRequest("newUser", "pass123", "nad@gmail.com", "BIDDER");

    DuplicateException ex =
        assertThrows(DuplicateException.class, () -> userService.register(regReq));
    assertTrue(ex.getMessage().contains("Email"));
  }

  @Test
  @Order(5)
  @DisplayName("testRegisterGenericDbError() - Khong doi loi DB thuong thanh trung email")
  void testRegisterGenericDbError() {
    when(userDao.insert(any(User.class)))
        .thenThrow(
            new org.jdbi.v3.core.statement.UnableToExecuteStatementException(
                "ERROR: column \"balance\" does not exist in INSERT INTO users (email)"));

    RegisterRequest regReq = new RegisterRequest("newUser", "pass123", "new@gmail.com", "BIDDER");

    assertThrows(
        org.jdbi.v3.core.statement.UnableToExecuteStatementException.class,
        () -> userService.register(regReq));
  }

  // ============================================================
  // 2. TEST ĐĂNG NHẬP (LOGIN)
  // ============================================================

  @Test
  @Order(6)
  @DisplayName("testLoginSuccess() — nhomAnhDuc login -> Token")
  void testLoginSuccess() {
    when(userDao.findByUsername(any())).thenReturn(Optional.of(mockUser));

    // Truyền mật khẩu gốc vào để BCrypt tự kiểm tra
    LoginRequest loginReq = new LoginRequest("nhomAnhDuc", plainPassword);
    String token = userService.login(loginReq);

    assertNotNull(token);
    assertDoesNotThrow(() -> JwtUtil.verifyToken(token));
  }

  @Test
  @Order(7)
  @DisplayName("testLoginWrongPassword() — Sai mật khẩu -> UnauthorizedException")
  void testLoginWrongPassword() {
    when(userDao.findByUsername(any())).thenReturn(Optional.of(mockUser));

    // Truyền mật khẩu sai
    LoginRequest loginReq = new LoginRequest("nhomAnhDuc", "wrong_password_hihi");

    assertThrows(
        UnauthorizedException.class,
        () -> {
          userService.login(loginReq);
        });
  }

  @Test
  @Order(8)
  @DisplayName("testLoginUserNotFound() — Không tồn tại User -> NotFoundException")
  void testLoginUserNotFound() {
    when(userDao.findByUsername(any())).thenReturn(Optional.empty());

    LoginRequest loginReq = new LoginRequest("ghostUser", "any_pass");

    assertThrows(
        NotFoundException.class,
        () -> {
          userService.login(loginReq);
        });
  }

  @Test
  @Order(9)
  @DisplayName("testLoginInvalidStoredHash() — Hash lỗi trong DB -> UnauthorizedException")
  void testLoginInvalidStoredHash() {
    mockUser.setPasswordHash("hash");
    when(userDao.findByUsername(any())).thenReturn(Optional.of(mockUser));

    LoginRequest loginReq = new LoginRequest("nhomAnhDuc", plainPassword);

    assertThrows(UnauthorizedException.class, () -> userService.login(loginReq));
  }

  @Test
  @Order(10)
  @DisplayName("changePassword() rehashes password and increments tokenVersion")
  void changePasswordIncrementsTokenVersion() {
    mockUser.setTokenVersion(4);
    when(userDao.findById(1L)).thenReturn(Optional.of(mockUser));
    ChangePasswordRequest req = new ChangePasswordRequest();
    req.setCurrentPassword(plainPassword);
    req.setNewPassword("newPass123");

    userService.changePassword(1L, req);

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userDao).update(captor.capture());
    User updated = captor.getValue();
    assertEquals(5, updated.getTokenVersion());
    assertTrue(
        BCrypt.verifyer().verify("newPass123".toCharArray(), updated.getPasswordHash()).verified);
    assertFalse(
        BCrypt.verifyer().verify(plainPassword.toCharArray(), updated.getPasswordHash()).verified);
  }
}
