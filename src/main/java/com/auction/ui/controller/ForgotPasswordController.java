package com.auction.ui.controller;

import com.auction.ui.util.Navigable;
import com.auction.ui.util.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * Controller cho màn hình quên mật khẩu (forgot-password.fxml).
 *
 * <p><b>Mục đích:</b>
 * Cho phép người dùng chưa đăng nhập yêu cầu đặt lại mật khẩu qua email.
 * Trong phiên bản hiện tại, hiển thị hướng dẫn liên hệ admin vì hệ thống
 * chưa tích hợp email service.
 *
 * <p><b>Vị trí trong kiến trúc:</b>
 * Được điều hướng đến từ login.fxml qua Hyperlink "Quên mật khẩu?".
 */
public class ForgotPasswordController implements Navigable {

  @FXML private TextField emailField;
  @FXML private Label statusLabel;
  @FXML private Button submitButton;

  @Override
  public void onNavigatedTo() {
    if (emailField != null) emailField.clear();
    hideStatus();
  }

  /**
   * Xử lý yêu cầu đặt lại mật khẩu.
   * Hiển thị thông báo hướng dẫn (tính năng email chưa triển khai).
   */
  @FXML
  public void handleSubmit() {
    String email = emailField.getText().trim();
    if (email.isEmpty() || !email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
      showStatus("Vui lòng nhập địa chỉ email hợp lệ.", true);
      return;
    }
    showStatus(
        "Yêu cầu đã được ghi nhận. Vui lòng liên hệ admin để đặt lại mật khẩu cho email: "
            + email,
        false);
  }

  @FXML
  public void goBack() {
    SceneManager.getInstance().navigateTo("login.fxml");
  }

  private void showStatus(String msg, boolean isError) {
    statusLabel.setText(msg);
    statusLabel.setStyle(isError ? "-fx-text-fill: #e53935;" : "-fx-text-fill: #43a047;");
    statusLabel.setVisible(true);
  }

  private void hideStatus() {
    if (statusLabel != null) statusLabel.setVisible(false);
  }
}
