package com.auction.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;

import java.util.Date;

/**
 * Tiện ích xử lý JWT (JSON Web Token) cho hệ thống đấu giá.
 * Sử dụng thư viện com.auth0:java-jwt.
 */
public class JwtUtil {

    private final String secret;
    private final long expirationMs;
    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    // Constructor 1: Dùng cho hệ thống chạy thật
    public JwtUtil() {
        this.secret = "chuoi_ky_tu_bi_mat_cua_nhom_auction_123456789";
        this.expirationMs = 3600000L; // 1 giờ
        this.algorithm = Algorithm.HMAC256(this.secret);
        this.verifier = JWT.require(this.algorithm).build();
    }

    // Constructor 2: Dùng để chạy Unit Test
    public JwtUtil(String secret, long expirationMs) {
        this.secret = secret;
        this.expirationMs = expirationMs;
        this.algorithm = Algorithm.HMAC256(this.secret);
        this.verifier = JWT.require(this.algorithm).build();
    }

    /**
     * Sinh ra Token dựa trên userId và role
     */
    public String generateToken(String userId, String role) {
        return JWT.create()
                .withSubject(userId) // Subject thường dùng lưu ID định danh
                .withClaim("role", role) // Custom claim cho role
                .withExpiresAt(new Date(System.currentTimeMillis() + this.expirationMs))
                .sign(this.algorithm);
    }

    /**
     * Xác thực Token có hợp lệ và còn hạn không
     */
    public void verifyToken(String token) {
        try {
            verifier.verify(token);
        } catch (JWTVerificationException e) {
            // Ném ra RuntimeException để test case (assertThrows) bắt được
            throw new RuntimeException("Token không hợp lệ hoặc đã hết hạn: " + e.getMessage());
        }
    }

    /**
     * Trích xuất UserId từ Token
     */
    public String extractUserId(String token) {
        DecodedJWT jwt = verifier.verify(token);
        return jwt.getSubject();
    }

    /**
     * Trích xuất Role (Vai trò) từ Token
     */
    public String extractRole(String token) {
        DecodedJWT jwt = verifier.verify(token);
        return jwt.getClaim("role").asString();
    }
}
