package com.auction.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.Date;

public class JwtUtil {

    private static final String SECRET = "your-secret-key-change-in-production";
    private static final Algorithm ALGORITHM = Algorithm.HMAC256(SECRET);
    private static final long EXPIRATION_MS = 24 * 60 * 60 * 1000;
    private static final JWTVerifier VERIFIER = JWT.require(ALGORITHM).build();

    public static String generateToken(String username, String role, Long userId) {
        return JWT.create()
            .withSubject(username)
            .withClaim("role", role)
            .withClaim("userId", userId)
            .withIssuedAt(new Date())
            .withExpiresAt(new Date(System.currentTimeMillis() + EXPIRATION_MS))
            .sign(ALGORITHM);
    }

    public static DecodedJWT validateToken(String token) throws JWTVerificationException {
        return VERIFIER.verify(token);
    }
}
