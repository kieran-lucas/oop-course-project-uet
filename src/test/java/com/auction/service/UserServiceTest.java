package com.auction.service;

import com.auction.config.JwtUtil;
import com.auction.dao.UserDao;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserServiceTest {

    @Mock
    private UserDao userDao;

    @InjectMocks
    private UserService userService;

    private Bidder mockUser;

    @BeforeEach
    void setUp() {
        // Khởi tạo User mẫu với tên nhomAnhDuc và email nad@gmail.com
        mockUser = new Bidder("nhomAnhDuc", "hashed_password_123", "nad@gmail.com");
        mockUser.setId(1L);
    }

    // ============================================================
    // 1. TEST ĐĂNG KÝ (REGISTER)
    // ============================================================

    @Test
    @Order(1)
    @DisplayName("testRegisterSuccess() — Đăng ký thành công, verify ID và Role")
    void testRegisterSuccess() {
        // Giả lập: Tên mới hoàn toàn, không bị trùng
        when(userDao.findByUsername("newUser")).thenReturn(Optional.empty());
        
        // Giả lập: DB lưu thành công và cấp ID 100
        when(userDao.insert(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(100L); 
            return u;
        });

        User result = userService.register("newUser", "pass123", "nad@gmail.com", "BIDDER");

        assertNotNull(result.getId());
        assertEquals("BIDDER", result.getRole());
        verify(userDao, times(1)).insert(any(User.class));
    }

    @Test
    @Order(2)
    @DisplayName("testRegisterDuplicateUsername() — Trùng tên nhomAnhDuc -> Throw DuplicateException")
    void testRegisterDuplicateUsername() {
        // Giả lập: Đã tồn tại user nhomAnhDuc trong hệ thống
        when(userDao.findByUsername("nhomAnhDuc")).thenReturn(Optional.of(mockUser));

        // Kiểm tra đúng loại lỗi DuplicateException
        assertThrows(DuplicateException.class, () -> {
            userService.register("nhomAnhDuc", "pass123", "nad@gmail.com", "BIDDER");
        });

        verify(userDao, never()).insert(any(User.class));
    }

    // ============================================================
    // 2. TEST ĐĂNG NHẬP (LOGIN)
    // ============================================================

    @Test
    @Order(3)
    @DisplayName("testLoginSuccess() — nhomAnhDuc đăng nhập đúng -> Trả về Token")
    void testLoginSuccess() {
        when(userDao.findByUsername("nhomAnhDuc")).thenReturn(Optional.of(mockUser));
        
        // Giả sử pass đúng là hashed_password_123
        String token = userService.login("nhomAnhDuc", "hashed_password_123");

        assertNotNull(token);
        // Dùng JwtUtil của nhóm trưởng để verify token vừa tạo
        assertDoesNotThrow(() -> JwtUtil.verifyToken(token));
    }

    @Test
    @Order(4)
    @DisplayName("testLoginWrongPassword() — Sai mật khẩu -> Throw UnauthorizedException")
    void testLoginWrongPassword() {
        when(userDao.findByUsername("nhomAnhDuc")).thenReturn(Optional.of(mockUser));

        // Kiểm tra đúng loại lỗi UnauthorizedException
        assertThrows(UnauthorizedException.class, () -> {
            userService.login("nhomAnhDuc", "wrong_password");
        });
    }

    @Test
    @Order(5)
    @DisplayName("testLoginUserNotFound() — User không tồn tại -> Throw NotFoundException")
    void testLoginUserNotFound() {
        when(userDao.findByUsername("ghostUser")).thenReturn(Optional.empty());

        // Kiểm tra đúng loại lỗi NotFoundException
        assertThrows(NotFoundException.class, () -> {
            userService.login("ghostUser", "any_pass");
        });
    }
}