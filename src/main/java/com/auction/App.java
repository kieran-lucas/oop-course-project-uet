package com.auction;

import com.auction.config.DatabaseConfig;
import com.auction.controller.AuthController;
import com.auction.dao.UserDao;
import com.auction.dto.ErrorResponse;
import com.auction.exception.*;
import com.auction.middleware.JwtMiddleware;
import com.auction.service.UserService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import org.jdbi.v3.core.Jdbi;

public class App {

    public static void main(String[] args) {

        // 1. Cấu hình Jackson (Rất quan trọng để parse LocalDateTime chuẩn ISO-8601)
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 2. Khởi tạo Database, DAO và Service (Dependency Injection thủ công)
        Jdbi jdbi = DatabaseConfig.create();
        UserDao userDao = new UserDao(jdbi);
        UserService userService = new UserService(userDao);
        AuthController authController = new AuthController(userService);

        // 3. Tạo ứng dụng Javalin và cấu hình
        Javalin app = Javalin.create(config -> {
            // Cấu hình Jackson làm JSON Mapper mặc định
            config.jsonMapper(new JavalinJackson(mapper));
            config.http.defaultContentType = "application/json";

            // Cho phép CORS nếu Client (JavaFX WebEngine hoặc Web Frontend) gọi khác port
            config.bundledPlugins.enableCors(cors -> cors.addRule(it -> it.anyHost()));
        });

        // 4. Đăng ký Middleware xác thực JWT cho tất cả API (trừ /api/auth/ đã bỏ qua trong Middleware)
        app.before("/api/*", JwtMiddleware::handle);

        // 5. Đăng ký 5 Exception Handlers chuyển Exception thành JSON ErrorResponse
        app.exception(InvalidBidException.class, (e, ctx) -> {
            ctx.status(400).json(ErrorResponse.of("INVALID_BID", e.getMessage()));
        });

        app.exception(AuctionClosedException.class, (e, ctx) -> {
            ctx.status(400).json(ErrorResponse.of("AUCTION_CLOSED", e.getMessage()));
        });

        app.exception(UnauthorizedException.class, (e, ctx) -> {
            ctx.status(401).json(ErrorResponse.of("UNAUTHORIZED", e.getMessage()));
        });

        app.exception(NotFoundException.class, (e, ctx) -> {
            ctx.status(404).json(ErrorResponse.of("NOT_FOUND", e.getMessage()));
        });

        app.exception(DuplicateException.class, (e, ctx) -> {
            ctx.status(409).json(ErrorResponse.of("DUPLICATE", e.getMessage()));
        });

        // Catch-all cho các lỗi Server không lường trước
        app.exception(Exception.class, (e, ctx) -> {
            e.printStackTrace(); // Log ra console để dev debug
            ctx.status(500).json(ErrorResponse.of("INTERNAL_ERROR", "Lỗi hệ thống, vui lòng thử lại sau."));
        });

        // 6. Đăng ký các Routes

        // Endpoint Health Check
        app.get("/api/health", ctx -> ctx.json(java.util.Map.of("status", "ok")));

        // Đăng ký routes của Auth
        authController.register(app);

        // 7. Khởi động server
        app.start(8080);
        System.out.println("🚀 Server started on http://localhost:8080");
    }
}
