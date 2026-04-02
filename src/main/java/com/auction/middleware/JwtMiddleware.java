package com.auction.middleware;

import com.auction.config.JwtUtil;
import com.auction.exception.UnauthorizedException;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.javalin.http.Context;

public class JwtMiddleware {

  public static void handle(Context ctx) {
    // Bỏ qua xác thực cho các đường dẫn đăng nhập/đăng ký
    if (ctx.path().startsWith("/api/auth/")) {
      return;
    }

    String authHeader = ctx.header("Authorization");

    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new UnauthorizedException("Thiếu hoặc sai định dạng token. Vui lòng đăng nhập.");
    }

    try {
      // Cắt bỏ chữ "Bearer " để lấy đúng chuỗi JWT
      String token = authHeader.substring(7);

      // Xác thực token
      DecodedJWT jwt = JwtUtil.verifyToken(token);

      // Lưu thông tin người dùng vào context của Javalin để các Controller khác sử dụng
      ctx.attribute("userId", jwt.getClaim("userId").asLong());
      ctx.attribute("username", jwt.getClaim("username").asString());
      ctx.attribute("role", jwt.getClaim("role").asString());

    } catch (Exception e) {
      // Lỗi chữ ký, hết hạn, v.v.
      throw new UnauthorizedException("Token không hợp lệ hoặc đã hết hạn: " + e.getMessage());
    }
  }
}
