package com.auction.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class JwtUtil {

  private static final String SECRET_KEY;

  static {
    String envSecret = System.getenv("JWT_SECRET");
    SECRET_KEY = (envSecret != null && !envSecret.isBlank()) ? envSecret : "auction-secret-key-dev";
  }

  private static final Algorithm ALGORITHM =
      Algorithm.HMAC256(
          SECRET_KEY); // Đây là thuật toán mã hóa đối xứng. Nghĩa là hệ thống dùng chung 1 cái
  // SECRET_KEY vừa để khóa (tạo token) vừa để mở khóa (xác minh token).

  private static final JWTVerifier VERIFIER =
      JWT.require(ALGORITHM)
          .build(); // Đây là cái "Máy quét thẻ". Thầy để nó là static final (biến tĩnh hằng số) để

  // máy tính chỉ khởi tạo nó đúng 1 lần duy nhất khi chạy server, giúp tiết kiệm
  // bộ nhớ và tăng tốc độ xử lý khi có hàng nghìn người dùng cùng truy cập.

  public static String createToken(Long userId, String username, String role) {
    return JWT.create()
        .withClaim("userId", userId)
        .withClaim("username", username)
        .withClaim("role", role)
        .withExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
        .sign(ALGORITHM);
  }

  public static DecodedJWT verifyToken(String token) {
    // Hàm này sẽ tự động quăng lỗi của thư viện JWT nếu token hết hạn hoặc sai chữ ký
    return VERIFIER.verify(token);
  }
}
