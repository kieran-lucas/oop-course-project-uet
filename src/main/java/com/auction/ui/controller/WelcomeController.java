package com.auction.ui.controller;

import com.auction.ui.util.SceneManager;
import javafx.fxml.FXML;

/**
 * Controller cho màn hình chào mừng (welcome.fxml).
 *
 * <p><b>Mục đích:</b>
 * Là màn hình đầu tiên khi ứng dụng khởi động. Cho phép người dùng chọn
 * vai trò (ADMIN, BIDDER, SELLER) để điều hướng đến màn hình đăng nhập.
 *
 * <p><b>Các phương thức chính:</b>
 * <ul>
 *   <li>{@link #goToLoginAsAdmin()} — Chuyển sang login.fxml với hint role ADMIN.</li>
 *   <li>{@link #goToLoginAsBidder()} — Chuyển sang login.fxml với hint role BIDDER.</li>
 *   <li>{@link #goToLoginAsSeller()} — Chuyển sang login.fxml với hint role SELLER.</li>
 * </ul>
 *
 * <p><b>Vị trí trong kiến trúc:</b>
 * WelcomeController là điểm khởi đầu của luồng điều hướng client.
 * Sau khi chọn role, người dùng được chuyển sang LoginController để đăng nhập.
 */
public class WelcomeController {

  /**
   * Xử lý nút ADMIN — chuyển sang màn hình đăng nhập, truyền hint "ADMIN".
   */
  @FXML
  public void goToLoginAsAdmin() {
    SceneManager.getInstance().navigateTo("login.fxml", "ADMIN");
  }

  /**
   * Xử lý nút BIDDER — chuyển sang màn hình đăng nhập, truyền hint "BIDDER".
   */
  @FXML
  public void goToLoginAsBidder() {
    SceneManager.getInstance().navigateTo("login.fxml", "BIDDER");
  }

  /**
   * Xử lý nút SELLER — chuyển sang màn hình đăng nhập, truyền hint "SELLER".
   */
  @FXML
  public void goToLoginAsSeller() {
    SceneManager.getInstance().navigateTo("login.fxml", "SELLER");
  }
}
