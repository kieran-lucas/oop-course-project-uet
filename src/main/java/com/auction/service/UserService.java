package com.auction.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.auction.config.JwtUtil;
import com.auction.dao.UserDao;
import com.auction.dto.LoginRequest;
import com.auction.dto.RegisterRequest;
import com.auction.exception.UserAlreadyExistsException;
import com.auction.exception.UserNotFoundException;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import com.auction.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Map;

public class UserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);

    private final UserDao userDao;

    public UserService(UserDao userDao) {
        this.userDao = userDao;
    }

    public Map<String, Object> login(LoginRequest request) {
        User user = userDao.findByUsername(request.getUsername())
            .orElseThrow(() -> new UserNotFoundException("Invalid username or password"));

        BCrypt.Result result = BCrypt.verifyer()
            .verify(request.getPassword().toCharArray(), user.getPasswordHash());

        if (!result.verified) {
            throw new UserNotFoundException("Invalid username or password");
        }

        String token = JwtUtil.generateToken(
            user.getUsername(),
            user.getRole(),
            user.getId()
        );

        LOGGER.info("User logged in: {} (role={})", user.getUsername(), user.getRole());

        return Map.of("token", token);
    }

    public Map<String, Object> register(RegisterRequest request) {
        if (userDao.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException(
                "Username '" + request.getUsername() + "' already exists");
        }

        String passwordHash = BCrypt.withDefaults()
            .hashToString(12, request.getPassword().toCharArray());

        User newUser;
        LocalDateTime now = LocalDateTime.now();

        if ("SELLER".equalsIgnoreCase(request.getRole())) {
            newUser = new Seller(null, request.getUsername(), passwordHash,
                request.getEmail(), now);
        } else {
            newUser = new Bidder(null, request.getUsername(), passwordHash,
                request.getEmail(), now);
        }

        User savedUser = userDao.insert(newUser);

        String token = JwtUtil.generateToken(
            savedUser.getUsername(),
            savedUser.getRole(),
            savedUser.getId()
        );

        LOGGER.info("New user registered: {} (role={})",
            savedUser.getUsername(), savedUser.getRole());

        return Map.of(
            "token", token,
            "role", savedUser.getRole()
        );
    }
}
