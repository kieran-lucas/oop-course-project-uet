package com.auction.ui.controller;

import com.auction.model.DepositRecord;
import com.auction.ui.util.Navigable;
import com.auction.ui.util.SceneManager;
import com.auction.util.NotificationStore;
import com.auction.util.RestClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller cho màn hình nạp tiền (deposit.fxml).
 *
 * <p><b>Mục đích:</b> Cho phép BIDDER nạp tiền vào tài khoản để tham gia đấu giá. Gửi request đến
 * {@code POST /api/users/me/deposit}.
 *
 * <p><b>Vị trí trong kiến trúc:</b> Được điều hướng đến từ màn hình Profile. Yêu cầu người dùng đã
 * xác thực.
 */
public class DepositController implements Navigable {

  private static final Logger LOGGER = LoggerFactory.getLogger(DepositController.class);
  private static final NumberFormat VND = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
  private static final ObjectMapper MAPPER = new ObjectMapper()
      .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

  @FXML private Label balanceLabel;
  @FXML private TextField amountField;
  @FXML private Label statusLabel;
  @FXML private Button depositButton;
  @FXML private ListView<String> historyList;

  private final ObservableList<String> historyItems = FXCollections.observableArrayList();
  private Timeline depositPollTimeline;
  private final Map<Long, String> knownStatuses = new HashMap<>();

  @Override
  public void onNavigatedTo() {
    if (amountField != null) amountField.clear();
    hideStatus();
    if (historyList != null) historyList.setItems(historyItems);
    knownStatuses.clear();
    loadBalance();
    loadHistory();
    startDepositPoll();
  }

  @Override
  public void onNavigatedFrom() {
    stopDepositPoll();
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

    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> response = RestClient.post("/api/users/me/deposit", body);
                Platform.runLater(
                    () -> {
                      if (response.statusCode() == 202) {
                        showStatus(
                            "Yêu cầu nạp " + VND.format(amount) + " đã được gửi. Chờ Admin xác nhận.",
                            false);
                        amountField.clear();
                        loadHistory();
                      } else {
                        String errMsg = "Gửi yêu cầu thất bại (HTTP " + response.statusCode() + ").";
                        try {
                          var node = MAPPER.readTree(response.body());
                          if (node.has("message")) errMsg = node.get("message").asText();
                        } catch (Exception ignored) {}
                        showStatus(errMsg, true);
                      }
                      depositButton.setDisable(false);
                    });
              } catch (Exception e) {
                LOGGER.error("Lỗi nạp tiền", e);
                Platform.runLater(
                    () -> {
                      showStatus("Không thể kết nối đến server.", true);
                      depositButton.setDisable(false);
                    });
              }
            });
  }

  @FXML
  public void goBack() {
    SceneManager.getInstance().navigateBack("profile.fxml");
  }

  private void loadBalance() {
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> response = RestClient.get("/api/users/me");
                if (response.statusCode() == 200) {
                  com.fasterxml.jackson.databind.ObjectMapper mapper =
                      new com.fasterxml.jackson.databind.ObjectMapper();
                  var node = mapper.readTree(response.body());
                  if (node.has("balance")) {
                    BigDecimal balance = node.get("balance").decimalValue();
                    Platform.runLater(
                        () -> balanceLabel.setText("Số dư hiện tại: " + VND.format(balance)));
                  }
                }
              } catch (Exception e) {
                LOGGER.debug("Không thể load số dư: {}", e.getMessage());
              }
            });
  }

  private void loadHistory() {
    Thread.ofVirtual().start(() -> {
      try {
        HttpResponse<String> response = RestClient.get("/api/users/me/deposit-requests");
        if (response.statusCode() == 200) {
          List<DepositRecord> records = RestClient.parseList(response.body(), DepositRecord.class);
          Platform.runLater(() -> applyDepositRecords(records, false));
        } else {
          LOGGER.error("Lỗi load lịch sử nạp tiền: HTTP {}", response.statusCode());
        }
      } catch (Exception e) {
        LOGGER.error("Không thể load lịch sử nạp tiền", e);
      }
    });
  }

  /** Áp dụng danh sách DepositRecord lên UI. Nếu notify=true thì so sánh trạng thái và gửi thông báo khi có thay đổi. */
  private void applyDepositRecords(List<DepositRecord> records, boolean notify) {
    var items = FXCollections.<String>observableArrayList();
    boolean balanceChanged = false;
    for (DepositRecord r : records) {
      String curr = r.getStatus() != null ? r.getStatus() : "PENDING";
      String statusText = switch (curr) {
        case "APPROVED" -> "✓ Đã duyệt";
        case "REJECTED" -> "✗ Từ chối";
        default -> "⏳ Chờ duyệt";
      };
      String dateStr = r.getCreatedAt() != null ? r.getCreatedAt().format(DATE_FMT) : "—";
      String amtStr = r.getAmount() != null ? VND.format(r.getAmount()) : "—";
      items.add(amtStr + "  |  " + statusText + "  |  " + dateStr);

      if (r.getId() != null) {
        String prev = knownStatuses.get(r.getId());
        if (notify && prev != null && !prev.equals(curr)) {
          String notif = switch (curr) {
            case "APPROVED" -> "Nạp tiền " + amtStr + " đã được duyệt ✓";
            case "REJECTED" -> "Nạp tiền " + amtStr + " bị từ chối ✗";
            default -> "Trạng thái nạp tiền " + amtStr + " đã thay đổi";
          };
          NotificationStore.getInstance().add(notif);
          showStatus(notif, "REJECTED".equals(curr));
          if ("APPROVED".equals(curr)) balanceChanged = true;
        }
        knownStatuses.put(r.getId(), curr);
      }
    }
    historyItems.setAll(items);
    if (balanceChanged) loadBalance();
  }

  private void startDepositPoll() {
    stopDepositPoll();
    depositPollTimeline = new Timeline(
        new KeyFrame(Duration.seconds(4), e ->
            Thread.ofVirtual().start(() -> {
              try {
                HttpResponse<String> response = RestClient.get("/api/users/me/deposit-requests");
                if (response.statusCode() == 200) {
                  List<DepositRecord> records =
                      RestClient.parseList(response.body(), DepositRecord.class);
                  Platform.runLater(() -> applyDepositRecords(records, true));
                }
              } catch (Exception e2) {
                LOGGER.debug("Deposit poll lỗi: {}", e2.getMessage());
              }
            })
        )
    );
    depositPollTimeline.setCycleCount(Timeline.INDEFINITE);
    depositPollTimeline.play();
  }

  private void stopDepositPoll() {
    if (depositPollTimeline != null) {
      depositPollTimeline.stop();
      depositPollTimeline = null;
    }
  }

  private void showStatus(String msg, boolean isError) {
    statusLabel.setText(msg);
    statusLabel.setStyle(isError ? "-fx-text-fill: #e53935;" : "-fx-text-fill: #43a047;");
    statusLabel.setVisible(true);
    statusLabel.setManaged(true);
  }

  private void hideStatus() {
    if (statusLabel != null) {
      statusLabel.setVisible(false);
      statusLabel.setManaged(false);
    }
  }
}
