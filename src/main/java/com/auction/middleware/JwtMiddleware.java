package com.auction.middleware;

import com.auction.config.JwtUtil;
import com.auction.exception.UnauthorizedException;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.javalin.http.Context;

public class JwtMiddleware {

    public static void handle(Context ctx) {
        String path = ctx.path();
        String method = ctx.method().name(); // "GET", "POST", "PUT", "DELETE"

        // 1. Public routes - Bỏ qua hoàn toàn xác thực
        if (path.equals("/api/auth/login")
            || path.equals("/api/auth/register")
            || path.equals("/api/health")) {
            return;
        }

        // 2. GET /api/items và /api/auctions là public (ai cũng xem được)
        // Nhưng thao tác POST/PUT/DELETE trên các route này vẫn cần token
        if ("GET".equals(method)
            && (path.startsWith("/api/items") || path.startsWith("/api/auctions"))) {

            // Nếu có token thì vẫn parse (để biết ai đang xem, tiện cho việc phân quyền UI sau này)
            // Nếu không có token thì cho qua (khách vãng lai/anonymous)
            String authHeader = ctx.header("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    String token = authHeader.substring(7);
                    DecodedJWT jwt = JwtUtil.verifyToken(token);

                    ctx.attribute("userId", jwt.getClaim("userId").asLong());
                    ctx.attribute("username", jwt.getClaim("username").asString());
                    ctx.attribute("role", jwt.getClaim("role").asString());
                } catch (Exception e) {
                    // Token không hợp lệ hoặc hết hạn nhưng vì là GET public → vẫn cho qua
                }
            }
            return;
        }

        // 3. Các route còn lại: BẮT BUỘC phải có token
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
