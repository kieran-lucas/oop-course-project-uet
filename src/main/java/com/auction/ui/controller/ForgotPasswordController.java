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
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller cho màn hình quên mật khẩu (forgot-password.fxml).
 *
 * <p>Luồng: User nhập email → gọi {@code POST /api/auth/forgot-password} → tạo yêu cầu PENDING →
 * Admin xét duyệt trong Admin Panel → server tạo mật khẩu tạm thời một lần.
 */
public class ForgotPasswordController implements Navigable {

  private static final Logger LOGGER = LoggerFactory.getLogger(ForgotPasswordController.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @FXML private TextField emailField;
  @FXML private Label statusLabel;
  @FXML private Button submitButton;

  @Override
  public void onNavigatedTo() {
    if (emailField != null) {
      emailField.clear();
    }
    if (submitButton != null) {
      submitButton.setDisable(false);
    }
    hideStatus();
  }

  /** Gửi yêu cầu đặt lại mật khẩu — tạo bản ghi PENDING để Admin duyệt. */
  @FXML
  public void handleSubmit() {
    String email = emailField.getText().trim();
    if (email.isEmpty() || !email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
      showStatus("Vui lòng nhập địa chỉ email hợp lệ.", true);
      return;
    }

    submitButton.setDisable(true);
    hideStatus();

    Thread.ofVirtual()
        .start(
            () -> {
              try {
                Map<String, String> body = new HashMap<>();
                body.put("email", email);
                HttpResponse<String> response = RestClient.post("/api/auth/forgot-password", body);

                if (response.statusCode() == 200) {
                  String msg = extractMessage(response.body(), "Đã gửi yêu cầu thành công.");
                  Platform.runLater(() -> showStatus(msg, false));
                } else {
                  String msg =
                      extractMessage(response.body(), "Không tìm thấy tài khoản với email này.");
                  Platform.runLater(
                      () -> {
                        showStatus(msg, true);
                        submitButton.setDisable(false);
                      });
                }
              } catch (Exception e) {
                LOGGER.error("Lỗi gửi yêu cầu quên mật khẩu", e);
                Platform.runLater(
                    () -> {
                      showStatus("Không thể kết nối đến server. Vui lòng thử lại.", true);
                      submitButton.setDisable(false);
                    });
              }
            });
  }

  /** Quay về màn hình đăng nhập. */
  @FXML
  public void goBack() {
    SceneManager.getInstance().navigateBack("login.fxml");
  }

  /**
   * Trích xuất trường {@code message} từ JSON body phản hồi của server. Trả về {@code fallback} nếu
   * body rỗng hoặc không phải JSON hợp lệ.
   */
  private String extractMessage(String responseBody, String fallback) {
    try {
      JsonNode json = MAPPER.readTree(responseBody);
      return json.has("message") ? json.get("message").asText() : fallback;
    } catch (Exception e) {
      return fallback;
    }
  }

  /**
   * Hiển thị kết quả gửi yêu cầu trên statusLabel.
   *
   * @param msg nội dung thông báo
   * @param isError {@code true} để hiển thị màu đỏ (lỗi), {@code false} cho màu xanh (thành công)
   */
  private void showStatus(String msg, boolean isError) {
    statusLabel.setText(msg);
    statusLabel.setStyle(isError ? "-fx-text-fill: #e53935;" : "-fx-text-fill: #43a047;");
    statusLabel.setVisible(true);
    statusLabel.setManaged(true);
  }

  /** Ẩn statusLabel và giải phóng layout space. */
  private void hideStatus() {
    if (statusLabel != null) {
      statusLabel.setVisible(false);
      statusLabel.setManaged(false);
    }
  }
}
