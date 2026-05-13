package com.auction.controller;

import com.auction.config.JwtUtil;
import com.auction.dto.ForgotPasswordRequest;
import com.auction.dto.LoginRequest;
import com.auction.dto.RegisterRequest;
import com.auction.model.User;
import com.auction.service.PasswordResetService;
import com.auction.service.UserService;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller xử lý các endpoint xác thực người dùng: đăng ký, đăng nhập, quên mật khẩu.
 *
 * <p>Các endpoint không yêu cầu JWT token (khai báo public trong {@code JwtMiddleware}):
 *
 * <ul>
 *   <li>{@code POST /api/auth/register} — Tạo tài khoản mới, trả về JWT token.
 *   <li>{@code POST /api/auth/login} — Đăng nhập, trả về JWT token.
 *   <li>{@code POST /api/auth/forgot-password} — Gửi yêu cầu đặt lại mật khẩu cho Admin duyệt.
 * </ul>
 *
 * @see com.auction.service.UserService
 * @see com.auction.config.JwtUtil
 */
public class AuthController {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

  private AuthController() {}

  /**
   * Đăng ký route đăng ký và đăng nhập vào Javalin instance.
   *
   * @param app Javalin instance
   * @param userService service xử lý đăng ký, đăng nhập
   */
  public static void register(Javalin app, UserService userService) {
    app.post("/api/auth/register", ctx -> handleRegister(ctx, userService));
    app.post("/api/auth/login", ctx -> handleLogin(ctx, userService));
    LOGGER.info("Đã đăng ký AuthController: POST /api/auth/register, POST /api/auth/login");
  }

  /**
   * Đăng ký route quên mật khẩu — user gửi yêu cầu, Admin duyệt.
   *
   * <ul>
   *   <li>{@code POST /api/auth/forgot-password} — Tạo yêu cầu PENDING, Admin sẽ xét duyệt.
   * </ul>
   *
   * @param app Javalin instance
   * @param resetService service quản lý yêu cầu đặt lại mật khẩu
   */
  public static void registerPasswordReset(Javalin app, PasswordResetService resetService) {
    app.post("/api/auth/forgot-password", ctx -> handleForgotPassword(ctx, resetService));
    LOGGER.info("Đã đăng ký: POST /api/auth/forgot-password");
  }

  private static void handleRegister(Context ctx, UserService userService) {
    RegisterRequest request = ctx.bodyAsClass(RegisterRequest.class);
    User newUser = userService.register(request);
    String token = JwtUtil.createToken(newUser.getId(), newUser.getUsername(), newUser.getRole());

    LOGGER.info(
        "Đăng ký thành công: username={}, role={}", newUser.getUsername(), newUser.getRole());

    ctx.status(201)
        .json(
            Map.of(
                "token", token,
                "role", newUser.getRole(),
                "username", newUser.getUsername(),
                "userId", newUser.getId()));
  }

  private static void handleLogin(Context ctx, UserService userService) {
    LoginRequest request = ctx.bodyAsClass(LoginRequest.class);
    String token = userService.login(request);

    var decoded = JwtUtil.verifyToken(token);
    String username = decoded.getClaim("username").asString();
    String role = userService.getRoleByUsername(username);
    long userId = decoded.getClaim("userId").asLong();

    LOGGER.info("Đăng nhập thành công: username={}", username);

    ctx.status(200)
        .json(
            Map.of(
                "token", token,
                "role", role,
                "username", username,
                "userId", userId));
  }

  /**
   * Xử lý yêu cầu quên mật khẩu — tạo bản ghi PENDING cho Admin xét duyệt.
   *
   * <p>Request body: {@code {"email": "user@example.com"}}
   *
   * @param ctx Javalin context
   * @param service PasswordResetService
   */
  private static void handleForgotPassword(Context ctx, PasswordResetService service) {
    ForgotPasswordRequest req = ctx.bodyAsClass(ForgotPasswordRequest.class);
    if (req.getEmail() == null || req.getEmail().trim().isEmpty()) {
      ctx.status(400).json(Map.of("message", "Email không được để trống."));
      return;
    }
    service.requestReset(req.getEmail().trim());
    ctx.status(200)
        .json(
            Map.of(
                "message",
                "Yêu cầu đã được gửi. Admin sẽ xét duyệt và cấp mật khẩu tạm thời nếu được duyệt."));
  }
}
