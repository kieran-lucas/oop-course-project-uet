package com.auction.controller;

import com.auction.dto.LoginRequest;
import com.auction.dto.RegisterRequest;
import com.auction.service.UserService;
import io.javalin.Javalin;

public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    public void register(Javalin app) {
        app.post("/api/auth/login", ctx -> {
            LoginRequest request = ctx.bodyAsClass(LoginRequest.class);
            var response = userService.login(request);
            ctx.json(response);
        });

        app.post("/api/auth/register", ctx -> {
            RegisterRequest request = ctx.bodyAsClass(RegisterRequest.class);
            var response = userService.register(request);
            ctx.status(201).json(response);
        });
    }
}