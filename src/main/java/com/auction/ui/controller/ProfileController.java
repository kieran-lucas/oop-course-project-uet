package com.auction.ui.controller;

import com.auction.ui.util.Navigable;
import com.auction.ui.util.SceneManager;
import com.auction.util.BackgroundBidWatcher;
import com.auction.util.RestClient;
import com.auction.util.UserBalanceWatcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.text.NumberFormat;
import java.util.Locale;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller cho màn hình hồ sơ cá nhân (profile.fxml).
 *
 * <p>Hiển thị thông tin tài khoản: username, role, số dư (BIDDER/SELLER). Cung cấp điều hướng đến
 * đổi mật khẩu, nạp tiền (BIDDER), đăng xuất.
 */
public class ProfileController implements Navigable {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProfileController.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final NumberFormat VND = NumberFormat.getCurrencyInstance(Locale.of("vi", "VN"));

  @FXML private Label usernameLabel;
  @FXML private Label roleLabel;
  @FXML private Button depositButton;
  @FXML private VBox balanceBox;
  @FXML private Label profileBalanceLabel;

  @Override
  public void onNavigatedTo() {
    // Lắng nghe thông báo biến động số dư từ UserBalanceWatcher
    UserBalanceWatcher.getInstance().setOnBalanceUpdate(this::onBalanceUpdated);
    SceneManager sm = SceneManager.getInstance();

    if (usernameLabel != null) {
      usernameLabel.setText(sm.getCurrentUsername() != null ? sm.getCurrentUsername() : "—");
    }

    String role = sm.getCurrentRole();
    if (roleLabel != null) {
      roleLabel.setText(role != null ? role : "—");
    }

    boolean isBidder = "BIDDER".equals(role);
    if (depositButton != null) {
      depositButton.setVisible(isBidder);
      depositButton.setManaged(isBidder);
    }

    boolean hasBalance = "BIDDER".equals(role) || "SELLER".equals(role);
    if (balanceBox != null) {
      balanceBox.setVisible(hasBalance);
      balanceBox.setManaged(hasBalance);
      if (profileBalanceLabel != null) {
        profileBalanceLabel.setText("Đang tải...");
      }
    }
    if (hasBalance) {
      loadBalance();
    }
  }

  /** Chuyển sang màn hình đổi mật khẩu. */
  @FXML
  public void goToChangePassword() {
    SceneManager.getInstance().navigateTo("change-password.fxml");
  }

  /** Chuyển sang màn hình nạp tiền (chỉ hiện với BIDDER). */
  @FXML
  public void goToDeposit() {
    SceneManager.getInstance().navigateTo("deposit.fxml");
  }

  /**
   * Đăng xuất: ngắt kết nối WebSocket số dư, dừng toàn bộ BackgroundBidWatcher, rồi trả về màn hình
   * chào mừng qua SceneManager.
   */
  @FXML
  public void handleLogout() {
    // Dừng kết nối WebSocket user trước khi logout
    UserBalanceWatcher.getInstance().disconnect();
    BackgroundBidWatcher.getInstance().stopAll();
    SceneManager.getInstance().logout();
  }

  /** Quay về màn hình danh sách phiên đấu giá. */
  @FXML
  public void goBack() {
    SceneManager.getInstance().navigateBack("auction-list.fxml");
  }

  /**
   * Hủy đăng ký callback số dư khi rời khỏi màn hình để tránh cập nhật UI khi ProfileController
   * không còn được hiển thị.
   */
  @Override
  public void onNavigatedFrom() {
    // Hủy listener để tránh cập nhật UI khi không còn ở màn hình này
    UserBalanceWatcher.getInstance().setOnBalanceUpdate(null);
  }

  /**
   * Nhận thông báo biến động số dư từ UserBalanceWatcher — gọi trên FX thread.
   *
   * <p>FIX Bug 1: KHÔNG gọi NotificationStore.add() ở đây nữa — UserBalanceWatcher đã add rồi. Chỉ
   * cập nhật profileBalanceLabel để hiển thị số dư mới trên màn hình profile.
   */
  private void onBalanceUpdated(BigDecimal newBalance, boolean approved) {
    if (approved && newBalance != null && profileBalanceLabel != null) {
      profileBalanceLabel.setText(VND.format(newBalance));
    }
  }

  /**
   * Tải số dư tài khoản từ {@code GET /api/users/me} trên luồng nền và cập nhật {@code
   * profileBalanceLabel}. Chỉ gọi khi role là BIDDER hoặc SELLER.
   */
  private void loadBalance() {
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> response = RestClient.get("/api/users/me");
                if (response.statusCode() == 200) {
                  var node = MAPPER.readTree(response.body());
                  BigDecimal balance =
                      node.has("balance") && !node.get("balance").isNull()
                          ? node.get("balance").decimalValue()
                          : BigDecimal.ZERO;
                  Platform.runLater(
                      () -> {
                        if (profileBalanceLabel != null) {
                          profileBalanceLabel.setText(VND.format(balance));
                        }
                      });
                }
              } catch (Exception e) {
                LOGGER.error("Không thể load số dư profile", e);
              }
            });
  }
}
