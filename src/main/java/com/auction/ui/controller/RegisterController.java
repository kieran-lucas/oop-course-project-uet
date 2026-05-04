package com.auction.ui.controller;

import com.auction.ui.util.Navigable;
import com.auction.ui.util.SceneManager;
import com.auction.util.RestClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller cho màn hình đăng ký tài khoản (register.fxml).
 *
 * <p><b>Mục đích:</b>
 * Xử lý luồng tạo tài khoản mới: validate dữ liệu phía client, gọi REST API
 * {@code POST /api/auth/register}, lưu JWT token vào {@link SceneManager},
 * và điều hướng đến danh sách phiên đấu giá sau khi đăng ký thành công.
 *
 * <p><b>Các phương thức chính:</b>
 * <ul>
 *   <li>{@link #handleRegister()} — Validate và gửi request đăng ký đến server.</li>
 *   <li>{@link #goToLogin()} — Quay lại màn hình đăng nhập.</li>
 * </ul>
 *
 * <p><b>Vị trí trong kiến trúc:</b>
 * RegisterController là điểm đầu vào của người dùng mới. Sau khi đăng ký thành công,
 * server trả về JWT token và người dùng được tự động đăng nhập vào hệ thống.
 */
public class RegisterController implements Navigable {

  private static final Logger LOGGER = LoggerFactory.getLogger(RegisterController.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @FXML private TextField usernameField;
  @FXML private TextField emailField;
  @FXML private PasswordField passwordField;
  @FXML private PasswordField confirmPasswordField;
  @FXML private ComboBox<String> roleCombo;
  @FXML private Label errorLabel;
  @FXML private Button registerButton;

  // ========== NAVIGABLE LIFECYCLE ==========

  /** Reset form khi navigate đến màn hình đăng ký. */
  @Override
  public void onNavigatedTo() {
    clearForm();
  }

  // ========== FXML ACTIONS ==========

  /**
   * Xử lý sự kiện nhấn nút "Đăng ký".
   *
   * <p>Validate phía client: username không trống, email hợp lệ, mật khẩu ≥ 6 ký tự,
   * hai mật khẩu khớp nhau, và role đã được chọn.
   * Sau đó gửi request trên luồng nền, lưu session nếu thành công.
   */
  @FXML
  public void handleRegister() {
    String username = usernameField.getText().trim();
    String email = emailField.getText().trim();
    String password = passwordField.getText();
    String confirmPassword = confirmPasswordField.getText();
    String role = roleCombo.getValue();

    // Validate phía client
    if (username.isEmpty()) {
      showError("Tên đăng nhập không được để trống.");
      return;
    }
    if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
      showError("Định dạng email không hợp lệ.");
      return;
    }
    if (password.length() < 6) {
      showError("Mật khẩu phải có ít nhất 6 ký tự.");
      return;
    }
    if (!password.equals(confirmPassword)) {
      showError("Hai mật khẩu không khớp nhau.");
      return;
    }
    if (role == null) {
      showError("Vui lòng chọn vai trò.");
      return;
    }

    registerButton.setDisable(true);
    hideError();

    // Gọi API trên luồng nền
    Thread.ofVirtual().start(() -> {
      try {
        Map<String, String> body = new HashMap<>();
        body.put("username", username);
        body.put("email", email);
        body.put("password", password);
        body.put("role", role);

        HttpResponse<String> response = RestClient.post("/api/auth/register", body);

        if (response.statusCode() == 201) {
          JsonNode json = MAPPER.readTree(response.body());
          String token = json.get("token").asText();
          String returnedRole = json.get("role").asText();
          String uname = json.get("username").asText();
          long userId = json.get("userId").asLong();

          Platform.runLater(() -> onRegisterSuccess(token, returnedRole, uname, userId));
        } else {
          JsonNode json = MAPPER.readTree(response.body());
          String msg = json.has("message") ? json.get("message").asText()
              : "Đăng ký thất bại. Tên đăng nhập có thể đã tồn tại.";
          Platform.runLater(() -> {
            showError(msg);
            registerButton.setDisable(false);
          });
        }
      } catch (Exception e) {
        LOGGER.error("Lỗi kết nối server khi đăng ký", e);
        Platform.runLater(() -> {
          showError("Không thể kết nối đến server. Vui lòng thử lại.");
          registerButton.setDisable(false);
        });
      }
    });
  }

  /** Quay lại màn hình đăng nhập. */
  @FXML
  public void goToLogin() {
    SceneManager.getInstance().navigateTo("login.fxml");
  }

  // ========== PRIVATE HELPERS ==========

  /** Lưu session và điều hướng sau khi đăng ký thành công. */
  private void onRegisterSuccess(String token, String role, String username, long userId) {
    SceneManager sm = SceneManager.getInstance();
    sm.setJwtToken(token);
    sm.setCurrentUsername(username);
    sm.setCurrentRole(role);
    sm.setCurrentUserId(userId);

    LOGGER.info("Đăng ký thành công: username={}, role={}", username, role);
    sm.navigateTo("auction-list.fxml");
  }

  private void showError(String message) {
    errorLabel.setText(message);
    errorLabel.setVisible(true);
  }

  private void hideError() {
    errorLabel.setVisible(false);
  }

  private void clearForm() {
    if (usernameField != null) {
        usernameField.clear();
    }
    if (emailField != null) {
        emailField.clear();
    }
    if (passwordField != null) {
        passwordField.clear();
    }
    if (confirmPasswordField != null) {
        confirmPasswordField.clear();
    }
    if (roleCombo != null) {
        roleCombo.setValue(null);
    }
    hideError();
    if (registerButton != null) {
        registerButton.setDisable(false);
    }
  }
}