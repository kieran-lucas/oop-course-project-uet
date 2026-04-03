package com.auction;

import com.auction.config.DatabaseConfig;
import com.auction.controller.AuthController;
import com.auction.dao.UserDao;
import com.auction.dto.ErrorResponse;
import com.auction.exception.InvalidBidException;
import com.auction.exception.UnauthorizedException;
import com.auction.exception.UserAlreadyExistsException;
import com.auction.exception.UserNotFoundException;
import com.auction.middleware.JwtMiddleware;
import com.auction.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;

public class App {

    public static void main(String[] args) {

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(objectMapper, true));
        });

        app.before("/api/*", JwtMiddleware::handle);

        app.exception(InvalidBidException.class, (e, ctx) -> {
            ctx.status(400).json(new ErrorResponse("INVALID_BID", e.getMessage()));
        });

        app.exception(UnauthorizedException.class, (e, ctx) -> {
            ctx.status(401).json(new ErrorResponse("UNAUTHORIZED", e.getMessage()));
        });

        app.exception(UserNotFoundException.class, (e, ctx) -> {
            ctx.status(401).json(new ErrorResponse("UNAUTHORIZED", e.getMessage()));
        });

        app.exception(UserAlreadyExistsException.class, (e, ctx) -> {
            ctx.status(409).json(new ErrorResponse("CONFLICT", e.getMessage()));
        });

        app.exception(Exception.class, (e, ctx) -> {
            ctx.status(500).json(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
        });

        var jdbi = DatabaseConfig.create();
        var userDao = new UserDao(jdbi);
        var userService = new UserService(userDao);
        var authController = new AuthController(userService);

        app.get("/api/health", ctx -> ctx.json(java.util.Map.of("status", "ok")));

        authController.register(app);

        app.start(8080);
    }
}
