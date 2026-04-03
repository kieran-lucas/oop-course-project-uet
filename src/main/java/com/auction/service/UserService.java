package com.auction.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.auction.config.JwtUtil;
import com.auction.dao.UserDao;
import com.auction.dto.LoginRequest;
import com.auction.dto.RegisterRequest;
import com.auction.exception.DuplicateException;
import com.auction.exception.NotFoundException;
import com.auction.exception.UnauthorizedException;
import com.auction.model.Admin;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import com.auction.model.User;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);
    private static final Set<String> VALID_ROLES = Set.of("BIDDER", "SELLER", "ADMIN");

    private final UserDao userDao;

    public UserService(UserDao userDao) {
        this.userDao = userDao;
    }

    public Map<String, Object> login(LoginRequest request) {
        // NotFoundException khi không tìm thấy user
        User user = userDao.findByUsername(request.getUsername())
            .orElseThrow(() -> new NotFoundException(
                "User not found: " + request.getUsername()));

        BCrypt.Result result = BCrypt.verifyer()
            .verify(request.getPassword().toCharArray(), user.getPasswordHash());

        // UnauthorizedException khi sai password
        if (!result.verified) {
            throw new UnauthorizedException("Invalid password");
        }

        String token = JwtUtil.createToken(
            user.getId(),
            user.getUsername(),
            user.getRole()
        );

        LOGGER.info("User logged in: {} (role={})", user.getUsername(), user.getRole());
        return Map.of("token", token);
    }

    public Map<String, Object> register(RegisterRequest request) {
        // Validate input
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new IllegalArgumentException("Username must not be empty");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        if (request.getEmail() == null
            || !request.getEmail().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("Invalid email format");
        }
        if (request.getRole() == null
            || !VALID_ROLES.contains(request.getRole().toUpperCase())) {
            throw new IllegalArgumentException("Invalid role. Must be one of: " + VALID_ROLES);
        }

        // DuplicateException khi username đã tồn tại
        if (userDao.existsByUsername(request.getUsername())) {
            throw new DuplicateException(
                "Username '" + request.getUsername() + "' already exists");
        }

        String passwordHash = BCrypt.withDefaults()
            .hashToString(12, request.getPassword().toCharArray());

        LocalDateTime now = LocalDateTime.now();
        String role = request.getRole().toUpperCase();

        User newUser = switch (role) {
            case "SELLER" -> new Seller(null, request.getUsername(), passwordHash,
                request.getEmail(), now);
            case "ADMIN" -> new Admin(null, request.getUsername(), passwordHash,
                request.getEmail(), now);
            default -> new Bidder(null, request.getUsername(), passwordHash,
                request.getEmail(), now);
        };

        User savedUser = userDao.insert(newUser);
        
        String token = JwtUtil.createToken(
            savedUser.getId(),
            savedUser.getUsername(),
            savedUser.getRole()
        );

        LOGGER.info("New user registered: {} (role={})",
            savedUser.getUsername(), savedUser.getRole());

        return Map.of(
            "token", token,
            "role", savedUser.getRole()
        );
    }
}
