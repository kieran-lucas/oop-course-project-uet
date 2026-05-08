package com.auction.ui.controller;

import com.auction.ui.util.Navigable;
import com.auction.ui.util.SceneManager;
import com.auction.util.RestClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpResponse;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller cho màn hình đăng nhập (login.fxml).
 *
 * <p><b>Mục đích:</b> Xử lý luồng đăng nhập người dùng: nhận username/password, gọi REST API {@code
 * POST /api/auth/login}, lưu JWT token vào {@link SceneManager}, và điều hướng đến màn hình phù hợp
 * theo role.
 *
 * <p><b>Các phương thức chính:</b>
 *
 * <ul>
 *   <li>{@link #handleLogin()} — Gọi API đăng nhập, lưu session, điều hướng.
 *   <li>{@link #goToRegister()} — Chuyển sang màn hình đăng ký.
 *   <li>{@link #goToForgotPassword()} — Chuyển sang màn hình quên mật khẩu.
 *   <li>{@link #onDataReceived(Object)} — Nhận role hint từ WelcomeController.
 * </ul>
 *
 * <p><b>Vị trí trong kiến trúc:</b> LoginController nhận dữ liệu từ WelcomeController (role hint),
 * giao tiếp với server qua {@link RestClient}, và lưu trạng thái session vào {@link SceneManager}
 * (JWT token, username, role, userId).
 */
public class LoginController implements Navigable {

  private static final Logger LOGGER = LoggerFactory.getLogger(LoginController.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @FXML private TextField usernameField;
  @FXML private PasswordField passwordField;
  @FXML private Label errorLabel;
  @FXML private Label roleHintLabel;
  @FXML private Button loginButton;

  private String expectedRole;

  // ========== NAVIGABLE LIFECYCLE ==========

  /**
   * Nhận role hint từ WelcomeController (ví dụ: "BIDDER", "SELLER", "ADMIN"). Cập nhật subtitle
   * label để gợi ý người dùng.
   */
  @Override
  public void onDataReceived(Object data) {
    if (data instanceof String role) {
      expectedRole = role;
      if (roleHintLabel != null) {
        String label =
            switch (role) {
              case "ADMIN" -> "Đăng nhập với vai trò: Quản trị viên";
              case "SELLER" -> "Đăng nhập với vai trò: Người bán";
              case "BIDDER" -> "Đăng nhập với vai trò: Người đặt giá";
              default -> "Đăng nhập với vai trò: " + role;
            };
        roleHintLabel.setText(label);
      }
    }
  }

  @Override
  public void onNavigatedFrom() {
    expectedRole = null;
  }

  /** Reset form khi navigate trở lại màn hình này. */
  @Override
  public void onNavigatedTo() {
    clearForm();
  }

  // ========== FXML ACTIONS ==========

  /**
   * Xử lý sự kiện nhấn nút "Đăng nhập".
   *
   * <p>Luồng:
   *
   * <ol>
   *   <li>Validate input cơ bản.
   *   <li>Gọi {@code POST /api/auth/login} trên luồng nền.
   *   <li>Nếu thành công: lưu JWT, username, role vào SceneManager → điều hướng.
   *   <li>Nếu thất bại: hiển thị thông báo lỗi.
   * </ol>
   */
  @FXML
  public void handleLogin() {
    String username = usernameField.getText().trim();
    String password = passwordField.getText();

    if (username.isEmpty() || password.isEmpty()) {
      showError("Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.");
      return;
    }

    loginButton.setDisable(true);
    hideError();

    // Gọi API trên luồng nền để không block JavaFX thread
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                var body = new java.util.HashMap<String, String>();
                body.put("username", username);
                body.put("password", password);

                HttpResponse<String> response = RestClient.post("/api/auth/login", body);

                if (response.statusCode() == 200) {
                  JsonNode json = MAPPER.readTree(response.body());
                  String token = json.get("token").asText();
                  String role = json.get("role").asText();
                  String uname = json.get("username").asText();
                  long userId = json.get("userId").asLong();

                  Platform.runLater(() -> onLoginSuccess(token, role, uname, userId));
                } else {
                  JsonNode json = MAPPER.readTree(response.body());
                  String msg =
                      json.has("message")
                          ? json.get("message").asText()
                          : "Đăng nhập thất bại. Vui lòng kiểm tra lại.";
                  Platform.runLater(
                      () -> {
                        showError(msg);
                        loginButton.setDisable(false);
                      });
                }
              } catch (Exception e) {
                LOGGER.error("Lỗi kết nối server khi đăng nhập", e);
                Platform.runLater(
                    () -> {
                      showError("Không thể kết nối đến server. Vui lòng thử lại.");
                      loginButton.setDisable(false);
                    });
              }
            });
  }

  /** Chuyển sang màn hình đăng ký. */
  @FXML
  public void goToRegister() {
    SceneManager.getInstance().navigateTo("register.fxml");
  }

  /** Chuyển sang màn hình quên mật khẩu. */
  @FXML
  public void goToForgotPassword() {
    SceneManager.getInstance().navigateTo("forgot-password.fxml");
  }

  /** Quay lại màn hình chào mừng. */
  @FXML
  public void goBack() {
    SceneManager.getInstance().navigateTo("welcome.fxml");
  }

  // ========== PRIVATE HELPERS ==========

  /** Lưu session và điều hướng đến màn hình phù hợp theo role. */
  private void onLoginSuccess(String token, String role, String username, long userId) {
    // Kiểm tra role thực tế có khớp với cổng đã chọn không
    if (expectedRole != null && !expectedRole.equals(role)) {
      String portalName =
          switch (expectedRole) {
            case "ADMIN" -> "Quản trị viên";
            case "SELLER" -> "Người bán";
            case "BIDDER" -> "Người đặt giá";
            default -> expectedRole;
          };
      showError(
          "Tài khoản này không phải "
              + portalName
              + ". Vui lòng chọn đúng vai trò ở màn hình chào.");
      loginButton.setDisable(false);
      return;
    }

    SceneManager sm = SceneManager.getInstance();
    sm.setJwtToken(token);
    sm.setCurrentUsername(username);
    sm.setCurrentRole(role);
    sm.setCurrentUserId(userId);

    LOGGER.info("Đăng nhập thành công: username={}, role={}", username, role);

    if ("ADMIN".equals(role)) {
      sm.navigateTo("admin-panel.fxml");
    } else {
      sm.navigateTo("auction-list.fxml");
    }
  }

  private void showError(String message) {
    errorLabel.setText(message);
    errorLabel.setVisible(true);
    errorLabel.setManaged(true);
  }

  private void hideError() {
    errorLabel.setVisible(false);
    errorLabel.setManaged(false);
  }

  private void clearForm() {
    if (usernameField != null) usernameField.clear();
    if (passwordField != null) passwordField.clear();
    // Only reset the hint label when there is no expected role — onDataReceived may have set it
    if (roleHintLabel != null && expectedRole == null) {
      roleHintLabel.setText("Đăng nhập");
    }
    hideError();
    if (loginButton != null) loginButton.setDisable(false);
  }
}
