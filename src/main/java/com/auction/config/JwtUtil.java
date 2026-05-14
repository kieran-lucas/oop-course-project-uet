package com.auction.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class JwtUtil {

  private static final int MIN_SECRET_BYTES = 32;
  private static final String SECRET_KEY;

  static {
    SECRET_KEY = requireJwtSecret(System.getenv("JWT_SECRET"));
  }

  /** HMAC-256 symmetric signing algorithm; uses the same key to sign and verify. */
  private static final Algorithm ALGORITHM = Algorithm.HMAC256(SECRET_KEY);

  /**
   * Singleton verifier — initialized once at class load, reused for every request. Thread-safe:
   * JWT.require().build() produces an immutable JWTVerifier instance.
   */
  private static final JWTVerifier VERIFIER = JWT.require(ALGORITHM).build();

  public static void validateConfiguration() {
    // Class initialization validates JWT_SECRET before the app starts accepting requests.
  }

  static String requireJwtSecret(String secret) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException(
          "JWT_SECRET is required and must be at least 32 bytes long when encoded as UTF-8.");
    }

    if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
      throw new IllegalStateException(
          "JWT_SECRET must be at least 32 bytes long when encoded as UTF-8.");
    }

    return secret;
  }

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
