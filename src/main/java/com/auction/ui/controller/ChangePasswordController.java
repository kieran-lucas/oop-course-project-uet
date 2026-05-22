package com.auction.ui.controller;

import com.auction.ui.util.Navigable;
import com.auction.ui.util.SceneManager;
import com.auction.util.RestClient;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller cho màn hình đổi mật khẩu (change-password.fxml).
 *
 * <p><b>Mục đích:</b> Cho phép người dùng đã đăng nhập thay đổi mật khẩu bằng cách nhập mật khẩu
 * hiện tại và mật khẩu mới. Gửi request đến {@code PUT /api/users/me/password}.
 *
 * <p><b>Vị trí trong kiến trúc:</b> Được điều hướng đến từ màn hình Profile. Yêu cầu người dùng đã
 * xác thực (JWT token trong SceneManager).
 */
public class ChangePasswordController implements Navigable {

  private static final Logger LOGGER = LoggerFactory.getLogger(ChangePasswordController.class);

  @FXML private PasswordField currentPasswordField;
  @FXML private PasswordField newPasswordField;
  @FXML private PasswordField confirmPasswordField;
  @FXML private Label statusLabel;
  @FXML private Button changeButton;

  @Override
  public void onNavigatedTo() {
    clearForm();
  }

  /** Xử lý đổi mật khẩu — validate rồi gửi PUT request. */
  @FXML
  public void handleChangePassword() {
    String current = currentPasswordField.getText();
    String newPass = newPasswordField.getText();
    String confirm = confirmPasswordField.getText();

    if (current.isEmpty() || newPass.isEmpty()) {
      showStatus("Please fill in every field.", true);
      return;
    }
    if (newPass.length() < 6) {
      showStatus("The new password must be at least 6 characters.", true);
      return;
    }
    if (!newPass.equals(confirm)) {
      showStatus("The two new passwords don't match.", true);
      return;
    }

    changeButton.setDisable(true);
    hideStatus();

    Map<String, String> body = new HashMap<>();
    body.put("currentPassword", current);
    body.put("newPassword", newPass);

    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> response = RestClient.put("/api/users/me/password", body);
                Platform.runLater(
                    () -> {
                      int code = response.statusCode();
                      if (code == 200 || code == 204) {
                        showStatus("Password changed. Signing you out in 3 seconds...", false);
                        new Timeline(
                                new KeyFrame(
                                    Duration.seconds(3), ev -> SceneManager.getInstance().logout()))
                            .play();
                      } else {
                        showStatus(
                            "Password change failed. Your current password is incorrect.", true);
                        changeButton.setDisable(false);
                      }
                    });
              } catch (Exception e) {
                LOGGER.error("Lỗi đổi mật khẩu", e);
                Platform.runLater(
                    () -> {
                      showStatus("Unable to reach the server.", true);
                      changeButton.setDisable(false);
                    });
              }
            });
  }

  /** Quay lại màn hình hồ sơ cá nhân mà không lưu thay đổi. */
  @FXML
  public void goBack() {
    SceneManager.getInstance().navigateBack("profile.fxml");
  }

  /**
   * Hiển thị thông báo kết quả trên statusLabel.
   *
   * @param msg nội dung thông báo
   * @param isError {@code true} để hiển thị màu đỏ (lỗi), {@code false} cho màu xanh (thành công)
   */
  private void showStatus(String msg, boolean isError) {
    statusLabel.setText(msg);
    statusLabel.setStyle("");
    statusLabel.getStyleClass().setAll(isError ? "error-label" : "status-label");
    statusLabel.setVisible(true);
    statusLabel.setManaged(true);
  }

  /** Ẩn statusLabel và giải phóng layout space. */
  private void hideStatus() {
    statusLabel.setVisible(false);
    statusLabel.setManaged(false);
  }

  /**
   * Xóa trắng toàn bộ form và reset trạng thái nút. Được gọi trong {@link #onNavigatedTo()} để đảm
   * bảo form sạch mỗi lần điều hướng đến màn hình này.
   */
  private void clearForm() {
    if (currentPasswordField != null) {
      currentPasswordField.clear();
    }
    if (newPasswordField != null) {
      newPasswordField.clear();
    }
    if (confirmPasswordField != null) {
      confirmPasswordField.clear();
    }
    hideStatus();
    if (changeButton != null) {
      changeButton.setDisable(false);
    }
  }
}
