package com.auction.ui.controller;

import com.auction.ui.util.Navigable;
import com.auction.ui.util.SceneManager;
import com.auction.util.RestClient;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller cho màn hình nạp tiền (deposit.fxml).
 *
 * <p><b>Mục đích:</b>
 * Cho phép BIDDER nạp tiền vào tài khoản để tham gia đấu giá.
 * Gửi request đến {@code POST /api/users/me/deposit}.
 *
 * <p><b>Vị trí trong kiến trúc:</b>
 * Được điều hướng đến từ màn hình Profile. Yêu cầu người dùng đã xác thực.
 */
public class DepositController implements Navigable {

  private static final Logger LOGGER = LoggerFactory.getLogger(DepositController.class);
  private static final NumberFormat VND = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

  @FXML private Label balanceLabel;
  @FXML private TextField amountField;
  @FXML private Label statusLabel;
  @FXML private Button depositButton;

  @Override
  public void onNavigatedTo() {
    if (amountField != null) {
        amountField.clear();
    }
    hideStatus();
    loadBalance();
  }

  /** Xử lý nạp tiền — validate số tiền và gửi POST request. */
  @FXML
  public void handleDeposit() {
    String amountText = amountField.getText().trim().replace(",", "");
    BigDecimal amount;
    try {
      amount = new BigDecimal(amountText);
      if (amount.compareTo(BigDecimal.ZERO) <= 0) {
        showStatus("Số tiền nạp phải lớn hơn 0.", true);
        return;
      }
    } catch (NumberFormatException e) {
      showStatus("Số tiền không hợp lệ.", true);
      return;
    }

    depositButton.setDisable(true);
    hideStatus();

    Map<String, Object> body = new HashMap<>();
    body.put("amount", amount);

    Thread.ofVirtual().start(() -> {
      try {
        HttpResponse<String> response = RestClient.post("/api/users/me/deposit", body);
        Platform.runLater(() -> {
          if (response.statusCode() == 200) {
            showStatus("Nạp tiền thành công: " + VND.format(amount), false);
            amountField.clear();
            loadBalance();
          } else {
            showStatus("Nạp tiền thất bại.", true);
          }
          depositButton.setDisable(false);
        });
      } catch (Exception e) {
        LOGGER.error("Lỗi nạp tiền", e);
        Platform.runLater(() -> {
          showStatus("Không thể kết nối đến server.", true);
          depositButton.setDisable(false);
        });
      }
    });
  }

  @FXML
  public void goBack() {
    SceneManager.getInstance().navigateTo("profile.fxml");
  }

  private void loadBalance() {
    Thread.ofVirtual().start(() -> {
      try {
        HttpResponse<String> response = RestClient.get("/api/users/me");
        if (response.statusCode() == 200) {
          com.fasterxml.jackson.databind.ObjectMapper mapper =
              new com.fasterxml.jackson.databind.ObjectMapper();
          var node = mapper.readTree(response.body());
          if (node.has("balance")) {
            BigDecimal balance = node.get("balance").decimalValue();
            Platform.runLater(() ->
                balanceLabel.setText("Số dư hiện tại: " + VND.format(balance)));
          }
        }
      } catch (Exception e) {
        LOGGER.debug("Không thể load số dư: {}", e.getMessage());
      }
    });
  }

  private void showStatus(String msg, boolean isError) {
    statusLabel.setText(msg);
    statusLabel.setStyle(isError ? "-fx-text-fill: #e53935;" : "-fx-text-fill: #43a047;");
    statusLabel.setVisible(true);
  }

  private void hideStatus() {
    if (statusLabel != null) {
        statusLabel.setVisible(false);
    }
  }
}