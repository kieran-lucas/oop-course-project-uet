package com.auction.ui.controller;

import com.auction.ui.util.Navigable;
import com.auction.ui.util.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

/**
 * Controller cho màn hình hồ sơ cá nhân (profile.fxml).
 *
 * <p><b>Mục đích:</b>
 * Hiển thị thông tin tài khoản của người dùng đang đăng nhập (username, role).
 * Cung cấp điều hướng đến các chức năng: đổi mật khẩu, nạp tiền (BIDDER), đăng xuất.
 *
 * <p><b>Vị trí trong kiến trúc:</b>
 * Màn hình phụ trợ có thể điều hướng đến từ thanh điều hướng của auction-list.
 * Lấy thông tin từ {@link SceneManager} session state (không cần gọi thêm API).
 */
public class ProfileController implements Navigable {

  @FXML private Label usernameLabel;
  @FXML private Label roleLabel;
  @FXML private Button depositButton;

  /** Cập nhật thông tin từ session khi vào màn hình. */
  @Override
  public void onNavigatedTo() {
    SceneManager sm = SceneManager.getInstance();

    if (usernameLabel != null) {
      usernameLabel.setText(sm.getCurrentUsername() != null ? sm.getCurrentUsername() : "—");
    }

    String role = sm.getCurrentRole();
    if (roleLabel != null) {
      roleLabel.setText(role != null ? role : "—");
    }

    // Chỉ BIDDER có thể nạp tiền
    boolean isBidder = "BIDDER".equals(role);
    if (depositButton != null) {
      depositButton.setVisible(isBidder);
      depositButton.setManaged(isBidder);
    }
  }

  @FXML
  public void goToChangePassword() {
    SceneManager.getInstance().navigateTo("change-password.fxml");
  }

  @FXML
  public void goToDeposit() {
    SceneManager.getInstance().navigateTo("deposit.fxml");
  }

  @FXML
  public void handleLogout() {
    SceneManager.getInstance().logout();
  }

  @FXML
  public void goBack() {
    SceneManager.getInstance().navigateTo("auction-list.fxml");
  }
}
