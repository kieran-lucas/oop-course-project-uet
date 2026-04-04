package com.auction.service;

import com.auction.config.JwtUtil;
import com.auction.dao.UserDao;
import com.auction.dto.LoginRequest;
import com.auction.dto.RegisterRequest;
import com.auction.model.Bidder;
import com.auction.model.User;
import com.auction.exception.DuplicateException;
import com.auction.exception.UnauthorizedException;
import com.auction.exception.NotFoundException;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // CHÌA KHÓA GIẢI QUYẾT LỖI Ở ĐÂY
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserServiceTest {

    @Mock
    private UserDao userDao;

    @InjectMocks
    private UserService userService;

    private Bidder mockUser;

    @BeforeEach
    void setUp() {
        mockUser = new Bidder("nhomAnhDuc", "hashed_password_123", "nad@gmail.com");
        mockUser.setId(1L);
    }

    // ============================================================
    // 1. TEST ĐĂNG KÝ (REGISTER)
    // ============================================================

    @Test
    @Order(1)
    @DisplayName("testRegisterSuccess() — Đăng ký thành công dùng RegisterRequest")
    void testRegisterSuccess() {
        // Dùng any() để bắt mọi trường hợp
        when(userDao.findByUsername(any())).thenReturn(Optional.empty());
        when(userDao.insert(any(User.class))).thenAnswer(invocation -> {
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
        when(userDao.findByUsername(any())).thenReturn(Optional.of(mockUser));

        RegisterRequest regReq = new RegisterRequest("nhomAnhDuc", "pass123", "nad@gmail.com", "BIDDER");

        assertThrows(DuplicateException.class, () -> {
            userService.register(regReq);
        });
    }

    // ============================================================
    // 2. TEST ĐĂNG NHẬP (LOGIN)
    // ============================================================

    @Test
    @Order(3)
    @DisplayName("testLoginSuccess() — nhomAnhDuc login -> Token")
    void testLoginSuccess() {
        when(userDao.findByUsername(any())).thenReturn(Optional.of(mockUser));
        
        LoginRequest loginReq = new LoginRequest("nhomAnhDuc", "hashed_password_123");
        String token = userService.login(loginReq);

        assertNotNull(token);
        assertDoesNotThrow(() -> JwtUtil.verifyToken(token));
    }

    @Test
    @Order(4)
    @DisplayName("testLoginWrongPassword() — Sai mật khẩu -> UnauthorizedException")
    void testLoginWrongPassword() {
        when(userDao.findByUsername(any())).thenReturn(Optional.of(mockUser));

        LoginRequest loginReq = new LoginRequest("nhomAnhDuc", "wrong_password");

        assertThrows(UnauthorizedException.class, () -> {
            userService.login(loginReq);
        });
    }

    @Test
    @Order(5)
    @DisplayName("testLoginUserNotFound() — Không tồn tại User -> NotFoundException")
    void testLoginUserNotFound() {
        when(userDao.findByUsername(any())).thenReturn(Optional.empty());

        LoginRequest loginReq = new LoginRequest("ghostUser", "any_pass");

        assertThrows(NotFoundException.class, () -> {
            userService.login(loginReq);
        });
    }
}