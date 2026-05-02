package com.auction.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.auction.config.JwtUtil;
import com.auction.dao.UserDao;
import com.auction.dto.LoginRequest;
import com.auction.dto.RegisterRequest;
import com.auction.exception.DuplicateException;
import com.auction.exception.NotFoundException;
import com.auction.exception.UnauthorizedException;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import com.auction.model.User;

public class UserService {
  private final UserDao userDao;

  public UserService(UserDao userDao) {
    this.userDao = userDao;
  }

  public User register(RegisterRequest req) {
    // 1. Validate cơ bản (Có thể dùng thư viện validator hoặc viết tay nhanh)
    if (req.getUsername() == null || req.getUsername().trim().isEmpty()) {
      throw new IllegalArgumentException("Username không được để trống");
    }

    String emailRegex = "^[A-Za-z0-9+_.-]+@(.+)$";
    if (req.getEmail() == null || !req.getEmail().matches(emailRegex)) {
      throw new IllegalArgumentException("Định dạng email không hợp lệ.");
    }

    // Kiểm tra Password
    if (req.getPassword() == null || req.getPassword().length() < 6) {
      throw new IllegalArgumentException("Mật khẩu phải có ít nhất 6 ký tự.");
    }

    // 2. Check trùng username (Đã sửa lại để kiểm tra Optional đúng cách)
    if (userDao.findByUsername(req.getUsername()).isPresent()) {
      throw new DuplicateException("Username '" + req.getUsername() + "' đã tồn tại!");
    }

    // 3. Hash password
    String hashedPassword = BCrypt.withDefaults().hashToString(12, req.getPassword().toCharArray());

    // 4. Khởi tạo đối tượng theo Role (Đa hình - Polymorphism)
    User newUser;
    switch (req.getRole().toUpperCase()) {
      case "BIDDER":
        newUser = new Bidder();
        break;
      case "SELLER":
        newUser = new Seller();
        break;
      // ADMIN thường không đăng ký qua API ngoài nên để default ở đây
      default:
        throw new IllegalArgumentException("Role không hợp lệ: " + req.getRole());
    }

    newUser.setUsername(req.getUsername());
    newUser.setPasswordHash(hashedPassword);
    newUser.setEmail(req.getEmail());

    // 5. Lưu xuống DB và trả về
    return userDao.insert(newUser);
  }

  public String login(LoginRequest req) {
    // 1. Tìm user
    User user =
        userDao
            .findByUsername(req.getUsername())
            .orElseThrow(() -> new NotFoundException("Không tìm thấy tài khoản với username này."));

    // 2. Xác thực mật khẩu
    BCrypt.Result result =
        BCrypt.verifyer().verify(req.getPassword().toCharArray(), user.getPasswordHash());
    if (!result.verified) {
      throw new UnauthorizedException("Sai mật khẩu.");
    }

    // 3. Trả về Token
    return JwtUtil.createToken(user.getId(), user.getUsername(), user.getRole());
  }
}
