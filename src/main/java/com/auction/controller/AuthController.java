package com.auction.controller;

import com.auction.config.JwtUtil;
import com.auction.dto.LoginRequest;
import com.auction.dto.RegisterRequest;
import com.auction.model.User;
import com.auction.service.UserService;
import io.javalin.Javalin;
import java.util.Map;

public class AuthController {

  private final UserService userService;

  public AuthController(UserService userService) {
    this.userService = userService;
  }

  public void register(Javalin app) {

    // 1. Endpoint Đăng nhập
    app.post(
        "/api/auth/login",
        ctx -> {
          LoginRequest request = ctx.bodyAsClass(LoginRequest.class);

          String token = userService.login(request);

          ctx.json(Map.of("token", token));
        });

    // 2. Endpoint Đăng ký
    app.post(
        "/api/auth/register",
        ctx -> {
          // Parse JSON từ Client thành RegisterRequest
          RegisterRequest request = ctx.bodyAsClass(RegisterRequest.class);

          User newUser = userService.register(request);

          String token =
              JwtUtil.createToken(newUser.getId(), newUser.getUsername(), newUser.getRole());

          ctx.status(201).json(Map.of("token", token, "role", newUser.getRole()));
        });
  }
}
