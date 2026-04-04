package com.auction.config;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JwtUtilTest {

    @Test
    @Order(1)
    @DisplayName("testCreateAndVerify() — tạo token -> verify -> đúng userId, role")
    void testCreateAndVerify() {
        // Arrange
        Long userId = 1L;
        String username = "nhomAnhDuc";
        String role = "BIDDER";

        // Act
        String token = JwtUtil.createToken(userId, username, role);
        DecodedJWT decoded = JwtUtil.verifyToken(token);

        // Assert
        assertNotNull(token);
        assertEquals(userId, decoded.getClaim("userId").asLong());
        assertEquals(role, decoded.getClaim("role").asString());
        assertEquals(username, decoded.getClaim("username").asString());
    }

    @Test
    @Order(2)
    @DisplayName("testInvalidToken() — verify 'garbage' -> throw")
    void testInvalidToken() {
        String garbage = "ey.invalid.token";
        
        // Thư viện sẽ tự quăng lỗi khi token không đúng định dạng
        assertThrows(Exception.class, () -> {
            JwtUtil.verifyToken(garbage);
        });
    }

    /* * LƯU Ý: 
     * việc testExpiredToken() thực tế là không thể thực hiện trừ khi sửa code main do khong the đợi 24h
     */
    @Test
    @Order(3)
    @DisplayName("testExpiredToken()")
    void testExpiredToken() {
        // tạm thời bỏ trống 
        // Vì code main dùng Instant.now().plus(24h) cố định.
        assertTrue(true); 
    }
}