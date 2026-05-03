package com.auction.ui.controller;

import com.auction.dto.AuctionResponse;
import com.auction.model.BidTransaction;
import com.auction.dto.BidUpdateMessage;
import com.auction.ui.util.Navigable;
import com.auction.ui.util.SceneManager;
import com.auction.util.RestClient;
import com.auction.util.WebSocketClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.text.NumberFormat;
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
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller cho màn hình chi tiết phiên đấu giá (auction-detail.fxml).
 *
 * <p><b>Mục đích:</b>
 * Hiển thị thông tin đầy đủ của một phiên: tên sản phẩm, giá hiện tại, người dẫn đầu,
 * đồng hồ đếm ngược, lịch sử bid, và form đặt giá realtime.
 * Kết nối WebSocket để nhận BID_UPDATE / TIME_EXTENDED / AUCTION_ENDED từ server.
 *
 * <p><b>Các phương thức chính:</b>
 * <ul>
 *   <li>{@link #onDataReceived(Object)} — Nhận auctionId từ AuctionListController.</li>
 *   <li>{@link #onNavigatedTo()} — Load dữ liệu, kết nối WebSocket, bật countdown.</li>
 *   <li>{@link #onNavigatedFrom()} — Đóng WebSocket, dừng countdown.</li>
 *   <li>{@link #handleBid()} — Đặt giá thủ công qua REST API.</li>
 * </ul>
 *
 * <p><b>Vị trí trong kiến trúc:</b>
 * AuctionDetailController kết hợp cả REST (load dữ liệu ban đầu, đặt giá) và
 * WebSocket (nhận realtime updates). Mỗi BidUpdateMessage nhận được sẽ cập nhật
 * UI trên JavaFX Application Thread qua {@code Platform.runLater()}.
 */
public class AuctionDetailController implements Navigable {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuctionDetailController.class);
  private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
  private static final NumberFormat VND = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

  @FXML private Label usernameLabel;
  @FXML private Label itemNameLabel;
  @FXML private Label itemCategoryLabel;
  @FXML private Label auctionStatusLabel;
  @FXML private Label currentPriceLabel;
  @FXML private Label leadingBidderLabel;
  @FXML private Label countdownLabel;
  @FXML private VBox bidBox;
  @FXML private TextField bidAmountField;
  @FXML private Button bidButton;
  @FXML private Label bidErrorLabel;
  @FXML private VBox endedBox;
  @FXML private Label winnerLabel;
  @FXML private ListView<String> bidHistoryList;

  private Long auctionId;
  private Long endTimeMs;
  private final WebSocketClient wsClient = new WebSocketClient();
  private final ObservableList<String> bidHistoryItems = FXCollections.observableArrayList();
  private Timeline countdownTimeline;

  // ========== NAVIGABLE LIFECYCLE ==========

  /** Nhận auctionId từ AuctionListController khi người dùng click "Vào". */
  @Override
  public void onDataReceived(Object data) {
    if (data instanceof Long id) {
      this.auctionId = id;
    }
  }

  /** Load chi tiết phiên, lịch sử bid, kết nối WebSocket, bật countdown. */
  @Override
  public void onNavigatedTo() {
    SceneManager sm = SceneManager.getInstance();
    usernameLabel.setText(sm.getCurrentUsername() != null ? sm.getCurrentUsername() : "");
    bidHistoryList.setItems(bidHistoryItems);

    // Chỉ BIDDER mới thấy form đặt giá
    boolean isBidder = "BIDDER".equals(sm.getCurrentRole());
    bidBox.setVisible(isBidder);
    bidBox.setManaged(isBidder);

    if (auctionId != null) {
      loadAuctionDetail();
      loadBidHistory();
      connectWebSocket(sm.getJwtToken());
    }
  }

  /** Đóng WebSocket và dừng countdown khi rời màn hình. */
  @Override
  public void onNavigatedFrom() {
    wsClient.disconnect();
    stopCountdown();
    auctionId = null;
  }

  // ========== FXML ACTIONS ==========

  /**
   * Xử lý đặt giá — gọi {@code POST /api/auctions/{id}/bid}.
   * Chỉ chấp nhận số dương, validate cơ bản phía client trước khi gửi.
   */
  @FXML
  public void handleBid() {
    String amountText = bidAmountField.getText().trim();
    if (amountText.isEmpty()) {
      showBidError("Vui lòng nhập số tiền.");
      return;
    }

    BigDecimal amount;
    try {
      amount = new BigDecimal(amountText.replace(",", ""));
      if (amount.compareTo(BigDecimal.ZERO) <= 0) {
        showBidError("Số tiền phải lớn hơn 0.");
        return;
      }
    } catch (NumberFormatException e) {
      showBidError("Số tiền không hợp lệ.");
      return;
    }

    bidButton.setDisable(true);
    hideBidError();

    Map<String, Object> body = new HashMap<>();
    body.put("amount", amount);

    Thread.ofVirtual().start(() -> {
      try {
        HttpResponse<String> response = RestClient.post(
            "/api/auctions/" + auctionId + "/bid", body);

        if (response.statusCode() == 201) {
          Platform.runLater(() -> {
            bidAmountField.clear();
            bidButton.setDisable(false);
            loadBidHistory();
          });
        } else {
          String msg = extractErrorMessage(response.body());
          Platform.runLater(() -> {
            showBidError(msg);
            bidButton.setDisable(false);
          });
        }
      } catch (Exception e) {
        LOGGER.error("Lỗi đặt giá", e);
        Platform.runLater(() -> {
          showBidError("Không thể kết nối đến server.");
          bidButton.setDisable(false);
        });
      }
    });
  }

  /** Quay lại danh sách phiên đấu giá. */
  @FXML
  public void goBack() {
    SceneManager.getInstance().navigateTo("auction-list.fxml");
  }

  // ========== DATA LOADING ==========

  private void loadAuctionDetail() {
    Thread.ofVirtual().start(() -> {
      try {
        HttpResponse<String> response = RestClient.get("/api/auctions/" + auctionId);
        if (response.statusCode() == 200) {
          AuctionResponse auction = MAPPER.readValue(response.body(), AuctionResponse.class);
          Platform.runLater(() -> updateAuctionUI(auction));
        }
      } catch (Exception e) {
        LOGGER.error("Lỗi load chi tiết phiên", e);
      }
    });
  }

  private void loadBidHistory() {
    Thread.ofVirtual().start(() -> {
      try {
        HttpResponse<String> response = RestClient.get("/api/auctions/" + auctionId + "/bids");
        if (response.statusCode() == 200) {
          List<BidTransaction> bids = RestClient.parseList(response.body(), BidTransaction.class);
          Platform.runLater(() -> {
            bidHistoryItems.clear();
            for (int i = bids.size() - 1; i >= 0; i--) {
              BidTransaction bid = bids.get(i);
              String entry = String.format("%s — %s",
                  bid.getBidderId() != null ? "Bidder #" + bid.getBidderId() : "?",
                  bid.getAmount() != null ? VND.format(bid.getAmount()) : "?");
              bidHistoryItems.add(entry);
            }
          });
        }
      } catch (Exception e) {
        LOGGER.error("Lỗi load lịch sử bid", e);
      }
    });
  }

  private void connectWebSocket(String token) {
    if (token == null || token.isEmpty()) return;
    wsClient.connect(auctionId, token, json -> {
      try {
        BidUpdateMessage msg = MAPPER.readValue(json, BidUpdateMessage.class);
        Platform.runLater(() -> handleWsMessage(msg));
      } catch (Exception e) {
        LOGGER.error("Lỗi parse WS message", e);
      }
    });
  }

  // ========== WEBSOCKET MESSAGE HANDLING ==========

  private void handleWsMessage(BidUpdateMessage msg) {
    switch (msg.getType()) {
      case BidUpdateMessage.TYPE_BID_UPDATE -> {
        if (msg.getCurrentPrice() != null) {
          currentPriceLabel.setText(VND.format(msg.getCurrentPrice()));
        }
        if (msg.getLeadingBidderUsername() != null) {
          leadingBidderLabel.setText(msg.getLeadingBidderUsername());
        }
        if (msg.getEndTime() != null) {
          endTimeMs = java.time.Duration.between(
              java.time.LocalDateTime.now(), msg.getEndTime()).toMillis();
        }
        loadBidHistory();
      }
      case BidUpdateMessage.TYPE_TIME_EXTENDED -> {
        if (msg.getEndTime() != null) {
          endTimeMs = java.time.Duration.between(
              java.time.LocalDateTime.now(), msg.getEndTime()).toMillis();
          LOGGER.info("Anti-sniping: thời gian gia hạn đến {}", msg.getEndTime());
        }
      }
      case BidUpdateMessage.TYPE_AUCTION_ENDED -> {
        stopCountdown();
        countdownLabel.setText("Đã kết thúc");
        auctionStatusLabel.setText("FINISHED");
        bidBox.setVisible(false);
        bidBox.setManaged(false);
        endedBox.setVisible(true);
        endedBox.setManaged(true);
        String winner = msg.getLeadingBidderUsername() != null
            ? msg.getLeadingBidderUsername() : "Không có người thắng";
        String price = msg.getCurrentPrice() != null ? VND.format(msg.getCurrentPrice()) : "—";
        winnerLabel.setText("Người thắng: " + winner + " — Giá cuối: " + price);
      }
      default -> LOGGER.debug("WS message không xử lý: type={}", msg.getType());
    }
  }

  // ========== UI UPDATE HELPERS ==========

  private void updateAuctionUI(AuctionResponse auction) {
    itemNameLabel.setText(auction.getItemName() != null ? auction.getItemName() : "Sản phẩm #" + auction.getItemId());
    itemCategoryLabel.setText(auction.getItemCategory() != null ? auction.getItemCategory() : "");
    auctionStatusLabel.setText(auction.getStatus() != null ? auction.getStatus() : "");

    if (auction.getCurrentPrice() != null) {
      currentPriceLabel.setText(VND.format(auction.getCurrentPrice()));
    }
    leadingBidderLabel.setText(
        auction.getLeadingBidderUsername() != null ? auction.getLeadingBidderUsername() : "Chưa có");

    endTimeMs = auction.getRemainingTimeMs();
    startCountdown();

    boolean isActive = "RUNNING".equals(auction.getStatus());
    if (!isActive) {
      bidBox.setVisible(false);
      bidBox.setManaged(false);
      if ("FINISHED".equals(auction.getStatus()) || "CANCELED".equals(auction.getStatus())) {
        endedBox.setVisible(true);
        endedBox.setManaged(true);
        String winner = auction.getLeadingBidderUsername() != null
            ? auction.getLeadingBidderUsername() : "Không có người thắng";
        winnerLabel.setText("Người thắng: " + winner);
      }
    }
  }

  private void startCountdown() {
    stopCountdown();
    countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
      if (endTimeMs != null && endTimeMs > 0) {
        endTimeMs -= 1000;
        long h = endTimeMs / 3_600_000;
        long m = (endTimeMs % 3_600_000) / 60_000;
        long s = (endTimeMs % 60_000) / 1000;
        countdownLabel.setText(String.format("%02d:%02d:%02d", h, m, s));
      } else {
        countdownLabel.setText("Đã kết thúc");
        stopCountdown();
      }
    }));
    countdownTimeline.setCycleCount(Timeline.INDEFINITE);
    countdownTimeline.play();
  }

  private void stopCountdown() {
    if (countdownTimeline != null) {
      countdownTimeline.stop();
      countdownTimeline = null;
    }
  }

  private void showBidError(String msg) {
    bidErrorLabel.setText(msg);
    bidErrorLabel.setVisible(true);
  }

  private void hideBidError() {
    bidErrorLabel.setVisible(false);
  }

  private String extractErrorMessage(String body) {
    try {
      return MAPPER.readTree(body).path("message").asText("Đặt giá thất bại.");
    } catch (Exception e) {
      return "Đặt giá thất bại.";
    }
  }
}
