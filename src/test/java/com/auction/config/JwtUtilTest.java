package com.auction.config;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cho JwtUtil - Kiểm tra việc tạo và xác thực Token.
 * Unit test thuần túy, không cần Database hay Mock.
 */
// sap xep thu tu test
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) 
class JwtUtilTest {

    private JwtUtil jwtUtil;
    private final String TEST_SECRET = "super_secret_key_for_testing_auction_project_only";

    @BeforeEach
    void setUp() {
        // Khởi tạo JwtUtil với thời gian sống 1 giờ (3600000ms)
        jwtUtil = new JwtUtil(TEST_SECRET, 3600000L); 
    }

    @Test
    @Order(1)
    @DisplayName("Nên tạo token thành công và parse đúng UserId, Role")
    void testCreateAndVerify() {
        String userId = "100";
        String role = "BIDDER";

        String token = jwtUtil.generateToken(userId, role);
        
        assertNotNull(token);
        assertEquals(userId, jwtUtil.extractUserId(token));
        assertEquals(role, jwtUtil.extractRole(token));
    }

    @Test
    @Order(2)
    @DisplayName("Nên throw Exception khi Token hết hạn (Expired)")
    void testExpiredToken() {
        // Tạo một JwtUtil với thời gian sống = 0ms (hết hạn ngay lập tức)
        JwtUtil expiredJwtUtil = new JwtUtil(TEST_SECRET, 0L);
        String token = expiredJwtUtil.generateToken("100", "BIDDER");

        // Thay RuntimeException bằng Exception thực tế, vd: ExpiredJwtException
        assertThrows(RuntimeException.class, () -> {
            expiredJwtUtil.verifyToken(token);
        });
    }

    @Test
    @Order(3)
    @DisplayName("Nên throw Exception khi Token không hợp lệ (Garbage)")
    void testInvalidToken() {
        String garbageToken = "ey.invalid.garbage.token";

        assertThrows(RuntimeException.class, () -> {
            jwtUtil.verifyToken(garbageToken);
        });
    }
}