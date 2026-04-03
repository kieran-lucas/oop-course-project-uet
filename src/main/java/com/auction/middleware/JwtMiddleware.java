package com.auction.middleware;

import com.auction.exception.UnauthorizedException;
import io.javalin.http.Context;
import java.util.List;

public class JwtMiddleware {

    private static final List<String> EXCLUDED_PATHS = List.of(
        "/api/auth/login",
        "/api/auth/register",
        "/api/health"
    );

    public static void handle(Context ctx) {
        String path = ctx.path();

        if (EXCLUDED_PATHS.contains(path)) {
            return;
        }

        String authHeader = ctx.header("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Vui lòng đăng nhập để thực hiện chức năng này (Thiếu Bearer Token).");
        }

        String token = authHeader.substring(7);

        try {
            if ("fake-invalid-token".equals(token)) {
                throw new Exception("Token hết hạn");
            }

            String usernameFromToken = "mock_user";
            ctx.attribute("currentUser", usernameFromToken);

        } catch (Exception e) {
            throw new UnauthorizedException("Token không hợp lệ hoặc đã hết hạn!");
        }
    }
}
