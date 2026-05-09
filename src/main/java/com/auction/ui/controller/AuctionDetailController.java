package com.auction.ui.controller;

import com.auction.dto.AuctionResponse;
import com.auction.dto.BidUpdateMessage;
import com.auction.model.BidTransaction;
import com.auction.ui.util.Navigable;
import com.auction.ui.util.SceneManager;
import com.auction.util.NotificationStore;
import com.auction.util.RestClient;
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
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller cho màn hình chi tiết phiên đấu giá (auction-detail.fxml).
 *
 * <p>Hiển thị thông tin đầy đủ: tên/mô tả sản phẩm, giá hiện tại, người dẫn đầu, đồng hồ đếm ngược,
 * form đặt giá thủ công, cấu hình auto-bid, biểu đồ giá theo thời gian, và lịch sử bid.
 */
public class AuctionDetailController implements Navigable {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuctionDetailController.class);
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());
  private static final NumberFormat VND = NumberFormat.getCurrencyInstance(Locale.of("vi", "VN"));

  // ── Item info ──
  @FXML private Label usernameLabel;
  @FXML private Label itemNameLabel;
  @FXML private Label itemCategoryLabel;
  @FXML private Label itemDescriptionLabel;
  @FXML private Label auctionStatusLabel;

  // ── Price & countdown ──
  @FXML private Label currentPriceLabel;
  @FXML private Label leadingBidderLabel;
  @FXML private Label countdownLabel;

  // ── Layout (60/40 split binding + 60% endedBox) ──
  @FXML private HBox contentHBox;
  @FXML private ScrollPane leftScrollPane;
  @FXML private VBox leftColumn;
  @FXML private VBox rightColumn;

  // ── Manual bid form ──
  @FXML private VBox bidBox;
  @FXML private TextField bidAmountField;
  @FXML private Button bidButton;
  @FXML private Label bidErrorLabel;
  @FXML private Label balanceLabel;

  // ── Auto-bid form ──
  @FXML private TextField maxBidField;
  @FXML private TextField incrementField;
  @FXML private Button autoBidButton;
  @FXML private Label autoBidStatusLabel;
  @FXML private Button cancelAutoBidButton;

  // ── Ended state ──
  @FXML private VBox endedBox;
  @FXML private Label winnerLabel;

  // ── Bid history ──
  @FXML private ListView<String> bidHistoryList;

  // ── Bid chart ──
  @FXML private AreaChart<Number, Number> bidChart;
  private final XYChart.Series<Number, Number> bidSeries = new XYChart.Series<>();

  // ── Notification toast ──
  @FXML private Label bidNotificationLabel;
  private Timeline notificationTimeline;

  private Long auctionId;
  private Long endTimeMs;
  private String currentItemName;
  private boolean userHasBid;
  private BigDecimal lastKnownBalance;
  private Timeline balancePollTimeline;

  private final com.auction.util.WebSocketClient wsClient = new com.auction.util.WebSocketClient();
  private final ObservableList<String> bidHistoryItems = FXCollections.observableArrayList();
  private Timeline countdownTimeline;

  // ========== NAVIGABLE LIFECYCLE ==========

  @Override
  public void onDataReceived(Object data) {
    if (data instanceof Long id) {
      this.auctionId = id;
    }
  }

  @Override
  public void onNavigatedTo() {
    SceneManager sm = SceneManager.getInstance();
    usernameLabel.setText(sm.getCurrentUsername() != null ? sm.getCurrentUsername() : "");
    bidHistoryList.setItems(bidHistoryItems);

    // Reset per-auction state
    userHasBid = false;
    currentItemName = null;
    lastKnownBalance = null;

    // Clear stale form state from previous navigation (controller is cached)
    hideBidError();
    bidAmountField.clear();
    bidButton.setDisable(false);
    if (balanceLabel != null) {
      balanceLabel.setText("Số dư: đang tải...");
    }
    autoBidStatusLabel.setText("");
    maxBidField.clear();
    incrementField.clear();

    // Bind both columns to explicit 60/40 fractions of the available content width.
    // Both use hgrow=NEVER so HBox doesn't redistribute extra space on top of the binding.
    // 68 = 24 (left pad) + 24 (right pad) + 20 (column gap).
    leftScrollPane.prefWidthProperty().unbind();
    rightColumn.prefWidthProperty().unbind();
    leftScrollPane
        .prefWidthProperty()
        .bind(contentHBox.widthProperty().subtract(68).multiply(0.60));
    rightColumn.prefWidthProperty().bind(contentHBox.widthProperty().subtract(68).multiply(0.40));

    // Bind endedBox to 60% of left column width
    endedBox.maxWidthProperty().unbind();
    endedBox.maxWidthProperty().bind(leftColumn.widthProperty().multiply(0.6));

    // Reset ended/bid box state for this navigation
    endedBox.setVisible(false);
    endedBox.setManaged(false);

    // Always fully reset the chart so data from a previous session never bleeds through
    bidSeries.getData().clear();
    bidChart.getData().clear();
    bidSeries.setName("Giá bid");
    bidChart.getData().add(bidSeries);

    bidChart.setLegendVisible(false);

    NumberAxis yAxis = (NumberAxis) bidChart.getYAxis();
    yAxis.setForceZeroInRange(false);
    yAxis.setTickLabelFormatter(
        new StringConverter<>() {
          @Override
          public String toString(Number v) {
            long n = v.longValue();
            if (n >= 1_000_000_000) {
              return String.format("%.1fT", n / 1_000_000_000.0);
            }
            if (n >= 1_000_000) {
              return String.format("%.1fM", n / 1_000_000.0);
            }
            if (n >= 1_000) {
              return (n / 1_000) + "K";
            }
            return Long.toString(n);
          }

          @Override
          public Number fromString(String s) {
            return 0;
          }
        });

    NumberAxis xAxis = (NumberAxis) bidChart.getXAxis();
    xAxis.setTickUnit(1);
    xAxis.setMinorTickCount(0);

    boolean isBidder = "BIDDER".equals(sm.getCurrentRole());
    bidBox.setVisible(isBidder);
    bidBox.setManaged(isBidder);

    if (auctionId != null) {
      // Stop background watcher for this auction before taking over with the detail WS
      com.auction.util.BackgroundBidWatcher.getInstance().stopWatching(auctionId);
      loadAuctionDetail();
      loadBidHistory();
      connectWebSocket(sm.getJwtToken());
    }

    startBalancePoll();
  }

  @Override
  public void onNavigatedFrom() {
    // Disconnect the detail WS FIRST so it stops receiving messages.
    // Only after that, register the background watcher to avoid a dual-connection window
    // where the same bid would trigger two notifications.
    wsClient.disconnect();
    stopCountdown();
    stopBalancePoll();
    if (notificationTimeline != null) {
      notificationTimeline.stop();
      notificationTimeline = null;
    }
    leftScrollPane.prefWidthProperty().unbind();
    rightColumn.prefWidthProperty().unbind();
    endedBox.maxWidthProperty().unbind();
    // Register background watcher AFTER disconnect to avoid dual-connection duplicates
    if (userHasBid && auctionId != null) {
      SceneManager sm = SceneManager.getInstance();
      String token = sm.getJwtToken();
      if (token != null) {
        com.auction.util.BackgroundBidWatcher.getInstance()
            .watch(auctionId, token, currentItemName, sm.getCurrentUserId());
      }
    }
    auctionId = null;
  }

  // ========== FXML ACTIONS ==========

  @FXML
  public void handleProfile() {
    SceneManager.getInstance().navigateTo("profile.fxml");
  }

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

    if (lastKnownBalance != null && amount.compareTo(lastKnownBalance) > 0) {
      showBidError(
          "Số dư của bạn ("
              + VND.format(lastKnownBalance)
              + ") không đủ. Hãy nạp tiền trước khi đặt giá.");
      return;
    }

    bidButton.setDisable(true);
    bidAmountField.requestFocus();
    hideBidError();

    Map<String, Object> body = new HashMap<>();
    body.put("amount", amount);
    final Long bidAuctionId = this.auctionId;

    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> response =
                    RestClient.post("/api/auctions/" + bidAuctionId + "/bid", body);
                if (response.statusCode() == 201) {
                  Platform.runLater(
                      () -> {
                        if (!bidAuctionId.equals(auctionId)) {
                          return;
                        }
                        userHasBid = true;
                        bidAmountField.clear();
                        hideBidError();
                        bidButton.setDisable(false);
                        bidAmountField.requestFocus();
                        loadBidHistory();
                      });
                } else {
                  String msg = extractErrorMessage(response.body());
                  Platform.runLater(
                      () -> {
                        if (!bidAuctionId.equals(auctionId)) {
                          return;
                        }
                        showBidError(msg);
                        bidButton.setDisable(false);
                        bidAmountField.requestFocus();
                      });
                }
              } catch (Exception e) {
                LOGGER.error("Lỗi đặt giá", e);
                Platform.runLater(
                    () -> {
                      if (!bidAuctionId.equals(auctionId)) {
                        return;
                      }
                      showBidError("Không thể kết nối đến server.");
                      bidButton.setDisable(false);
                      bidAmountField.requestFocus();
                    });
              }
            });
  }

  @FXML
  public void handleAutoBid() {
    String maxBidText = maxBidField.getText().trim();
    String incrementText = incrementField.getText().trim();

    if (maxBidText.isEmpty() || incrementText.isEmpty()) {
      autoBidStatusLabel.setText("Vui lòng nhập đầy đủ thông tin.");
      return;
    }

    BigDecimal maxBid;
    BigDecimal increment;
    try {
      maxBid = new BigDecimal(maxBidText.replace(",", ""));
      increment = new BigDecimal(incrementText.replace(",", ""));
    } catch (NumberFormatException e) {
      autoBidStatusLabel.setText("Giá trị không hợp lệ.");
      return;
    }

    autoBidButton.setDisable(true);
    Map<String, Object> body = new HashMap<>();
    body.put("maxBid", maxBid);
    body.put("increment", increment);

    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> response =
                    RestClient.post("/api/auctions/" + auctionId + "/auto-bid", body);
                Platform.runLater(
                    () -> {
                      autoBidButton.setDisable(false);
                      if (response.statusCode() == 200 || response.statusCode() == 201) {
                        autoBidStatusLabel.setText("Auto-bid đã được bật.");
                      } else {
                        autoBidStatusLabel.setText(
                            "Bật auto-bid thất bại: " + response.statusCode());
                      }
                    });
              } catch (Exception e) {
                LOGGER.error("Lỗi bật auto-bid", e);
                Platform.runLater(
                    () -> {
                      autoBidButton.setDisable(false);
                      autoBidStatusLabel.setText("Không thể kết nối đến server.");
                    });
              }
            });
  }

  @FXML
  public void handleCancelAutoBid() {
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> response =
                    RestClient.delete("/api/auctions/" + auctionId + "/auto-bid");
                Platform.runLater(
                    () -> {
                      if (response.statusCode() == 204 || response.statusCode() == 200) {
                        autoBidStatusLabel.setText("Auto-bid đã được tắt.");
                        maxBidField.clear();
                        incrementField.clear();
                      } else {
                        autoBidStatusLabel.setText("Tắt auto-bid thất bại.");
                      }
                    });
              } catch (Exception e) {
                LOGGER.error("Lỗi tắt auto-bid", e);
                Platform.runLater(() -> autoBidStatusLabel.setText("Không thể kết nối."));
              }
            });
  }

  @FXML
  public void goBack() {
    SceneManager.getInstance().navigateBack("auction-list.fxml");
  }

  // ========== DATA LOADING ==========

  private void loadAuctionDetail() {
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> response = RestClient.get("/api/auctions/" + auctionId);
                if (response.statusCode() == 200) {
                  AuctionResponse auction =
                      MAPPER.readValue(response.body(), AuctionResponse.class);
                  Platform.runLater(() -> updateAuctionUI(auction));
                }
              } catch (Exception e) {
                LOGGER.error("Lỗi load chi tiết phiên", e);
              }
            });
  }

  private void loadBidHistory() {
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> response =
                    RestClient.get("/api/auctions/" + auctionId + "/bids");
                if (response.statusCode() == 200) {
                  List<BidTransaction> bids =
                      RestClient.parseList(response.body(), BidTransaction.class);
                  Long currentUserId = SceneManager.getInstance().getCurrentUserId();
                  Platform.runLater(
                      () -> {
                        bidHistoryItems.clear();
                        bidSeries.getData().clear();
                        for (int i = 0; i < bids.size(); i++) {
                          BidTransaction bid = bids.get(i);
                          boolean isMyBid =
                              currentUserId != null && currentUserId.equals(bid.getBidderId());
                          if (isMyBid) {
                            userHasBid = true;
                          }
                          String bidderLabel =
                              isMyBid
                                  ? "Bạn"
                                  : ("Bidder #"
                                      + (bid.getBidderId() != null ? bid.getBidderId() : "?"));
                          bidHistoryItems.add(
                              0,
                              String.format(
                                  "%s — %s",
                                  bidderLabel,
                                  bid.getAmount() != null ? VND.format(bid.getAmount()) : "?"));
                          if (bid.getAmount() != null) {
                            bidSeries
                                .getData()
                                .add(new XYChart.Data<>(i + 1, bid.getAmount().doubleValue()));
                          }
                        }
                      });
                }
              } catch (Exception e) {
                LOGGER.error("Lỗi load lịch sử bid", e);
              }
            });
  }

  private void connectWebSocket(String token) {
    if (token == null || token.isEmpty()) {
      return;
    }
    wsClient.connect(
        auctionId,
        token,
        json -> {
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
          bidSeries
              .getData()
              .add(
                  new XYChart.Data<>(
                      bidSeries.getData().size() + 1, msg.getCurrentPrice().doubleValue()));
        }
        if (msg.getLeadingBidderUsername() != null) {
          leadingBidderLabel.setText(msg.getLeadingBidderUsername());
        }
        if (msg.getEndTime() != null) {
          endTimeMs =
              java.time.Duration.between(java.time.LocalDateTime.now(), msg.getEndTime())
                  .toMillis();
        }
        showBidNotification(msg);
        loadBidHistory();
      }
      case BidUpdateMessage.TYPE_TIME_EXTENDED -> {
        if (msg.getEndTime() != null) {
          endTimeMs =
              java.time.Duration.between(java.time.LocalDateTime.now(), msg.getEndTime())
                  .toMillis();
          LOGGER.info("Anti-sniping: thời gian gia hạn đến {}", msg.getEndTime());
        }
      }
      case BidUpdateMessage.TYPE_AUCTION_ENDED -> {
        stopCountdown();
        countdownLabel.setText("Đã kết thúc");
        auctionStatusLabel.setText("FINISHED");
        applyStatusStyle(auctionStatusLabel, "FINISHED");
        bidBox.setVisible(false);
        bidBox.setManaged(false);
        endedBox.setVisible(true);
        endedBox.setManaged(true);
        String winner =
            msg.getLeadingBidderUsername() != null
                ? msg.getLeadingBidderUsername()
                : "Không có người thắng";
        String price = msg.getCurrentPrice() != null ? VND.format(msg.getCurrentPrice()) : "—";
        winnerLabel.setText("Người thắng: " + winner + " — Giá cuối: " + price);

        String currentRole = SceneManager.getInstance().getCurrentRole();
        String label = currentItemName != null ? "[" + currentItemName + "] " : "";
        if (userHasBid || "SELLER".equals(currentRole)) {
          NotificationStore.getInstance()
              .add(label + "Phiên đã kết thúc — " + winner + " thắng với " + price);
        }
      }
      default -> LOGGER.debug("WS message không xử lý: type={}", msg.getType());
    }
  }

  // ========== NOTIFICATION ==========

  /**
   * Toast 3–4 giây khi có bid mới qua WebSocket. Seller → "Có bid mới"; own bid → xanh lá; người
   * khác → xanh dương. Chỉ thêm vào NotificationStore nếu user đã bid hoặc là seller.
   */
  private void showBidNotification(BidUpdateMessage msg) {
    if (bidNotificationLabel == null) {
      return;
    }

    Long currentUserId = SceneManager.getInstance().getCurrentUserId();
    String currentRole = SceneManager.getInstance().getCurrentRole();
    boolean isOwnBid =
        msg.getLeadingBidderId() != null && msg.getLeadingBidderId().equals(currentUserId);
    boolean isSeller = "SELLER".equals(currentRole);

    String bidder =
        msg.getLeadingBidderUsername() != null ? msg.getLeadingBidderUsername() : "Ẩn danh";
    String price = msg.getCurrentPrice() != null ? VND.format(msg.getCurrentPrice()) : "—";
    String itemLabel = currentItemName != null ? "[" + currentItemName + "] " : "";

    String text;
    String color;

    if (isSeller) {
      text = itemLabel + "Có bid mới: " + price + " từ " + bidder;
      color = "#0277BD";
      NotificationStore.getInstance().add(text);
    } else if (isOwnBid && msg.isAutoBid()) {
      text = itemLabel + "Auto-bid đặt " + price + " cho bạn";
      color = "#2e7d32";
      NotificationStore.getInstance().add(text);
    } else if (isOwnBid) {
      text = itemLabel + "Bạn đặt giá: " + price;
      color = "#2e7d32";
      NotificationStore.getInstance().add(text);
    } else {
      text = itemLabel + bidder + " vừa bid " + price;
      color = "#1565c0";
      if (userHasBid) {
        NotificationStore.getInstance().add(text);
      }
    }

    displayToast(text, color, 3);
  }

  /** Toast khi số dư tăng — màu xanh đậm, hiển thị 4 giây. */
  private void showBalanceChangeNotification(String amountText) {
    if (bidNotificationLabel == null) {
      return;
    }
    String text = "Số dư tăng " + amountText;
    NotificationStore.getInstance().add(text);
    displayToast(text, "#1B5E20", 4);
  }

  private void displayToast(String text, String bgColor, int seconds) {
    bidNotificationLabel.setText(text);
    bidNotificationLabel.setStyle(
        "-fx-background-color: "
            + bgColor
            + "; -fx-text-fill: white; "
            + "-fx-font-size: 13px; -fx-padding: 10 20 10 20; -fx-font-weight: bold;");
    bidNotificationLabel.setVisible(true);
    bidNotificationLabel.setManaged(true);

    if (notificationTimeline != null) {
      notificationTimeline.stop();
    }
    notificationTimeline =
        new Timeline(
            new KeyFrame(
                Duration.seconds(seconds),
                e -> {
                  bidNotificationLabel.setVisible(false);
                  bidNotificationLabel.setManaged(false);
                }));
    notificationTimeline.play();
  }

  // ========== BALANCE POLLING ==========

  private void updateBalanceLabel(BigDecimal balance) {
    if (balanceLabel == null) {
      return;
    }
    balanceLabel.setText("Số dư: " + VND.format(balance));
    balanceLabel.setStyle(
        balance.compareTo(BigDecimal.ZERO) == 0
            ? "-fx-font-size: 11px; -fx-text-fill: #ef5350;"
            : "-fx-font-size: 11px; -fx-text-fill: #66bb6a;");
  }

  private void startBalancePoll() {
    // Fetch initial balance and show it in the form
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> resp = RestClient.get("/api/users/me");
                if (resp.statusCode() == 200) {
                  var node = MAPPER.readTree(resp.body());
                  if (node.has("balance")) {
                    BigDecimal balance = node.get("balance").decimalValue();
                    Platform.runLater(
                        () -> {
                          lastKnownBalance = balance;
                          updateBalanceLabel(balance);
                        });
                  }
                }
              } catch (Exception e) {
                LOGGER.debug("Không thể load balance ban đầu", e);
              }
            });

    balancePollTimeline =
        new Timeline(
            new KeyFrame(
                Duration.seconds(5),
                ev ->
                    Thread.ofVirtual()
                        .start(
                            () -> {
                              try {
                                HttpResponse<String> resp = RestClient.get("/api/users/me");
                                if (resp.statusCode() == 200) {
                                  var node = MAPPER.readTree(resp.body());
                                  if (node.has("balance")) {
                                    BigDecimal balance = node.get("balance").decimalValue();
                                    Platform.runLater(
                                        () -> {
                                          if (lastKnownBalance != null
                                              && balance.compareTo(lastKnownBalance) > 0) {
                                            BigDecimal diff = balance.subtract(lastKnownBalance);
                                            showBalanceChangeNotification("+" + VND.format(diff));
                                          }
                                          lastKnownBalance = balance;
                                          updateBalanceLabel(balance);
                                        });
                                  }
                                }
                              } catch (Exception e) {
                                LOGGER.debug("Không thể poll balance: {}", e.getMessage());
                              }
                            })));
    balancePollTimeline.setCycleCount(Timeline.INDEFINITE);
    balancePollTimeline.play();
  }

  private void stopBalancePoll() {
    if (balancePollTimeline != null) {
      balancePollTimeline.stop();
      balancePollTimeline = null;
    }
  }

  // ========== UI UPDATE HELPERS ==========

  private void updateAuctionUI(AuctionResponse auction) {
    currentItemName = auction.getItemName();
    itemNameLabel.setText(
        currentItemName != null ? currentItemName : "Sản phẩm #" + auction.getItemId());
    itemCategoryLabel.setText(auction.getItemCategory() != null ? auction.getItemCategory() : "");
    itemDescriptionLabel.setText(
        auction.getItemDescription() != null ? auction.getItemDescription() : "");

    String statusText = auction.getStatus() != null ? auction.getStatus() : "";
    auctionStatusLabel.setText(statusText);
    applyStatusStyle(auctionStatusLabel, auction.getStatus());

    if (auction.getCurrentPrice() != null) {
      currentPriceLabel.setText(VND.format(auction.getCurrentPrice()));
    }
    leadingBidderLabel.setText(
        auction.getLeadingBidderUsername() != null
            ? auction.getLeadingBidderUsername()
            : "Chưa có");

    endTimeMs = auction.getRemainingTimeMs();
    String st = auction.getStatus();
    if ("CANCELED".equals(st) || "FINISHED".equals(st) || "PAID".equals(st)) {
      stopCountdown();
      countdownLabel.setText("Đã kết thúc");
    } else {
      startCountdown();
    }

    boolean isActive = "RUNNING".equals(auction.getStatus());
    if (isActive) {
      endedBox.setVisible(false);
      endedBox.setManaged(false);
    } else {
      bidBox.setVisible(false);
      bidBox.setManaged(false);
      String status = auction.getStatus();
      if ("FINISHED".equals(status) || "CANCELED".equals(status) || "PAID".equals(status)) {
        endedBox.setVisible(true);
        endedBox.setManaged(true);
        String winner =
            auction.getLeadingBidderUsername() != null
                ? auction.getLeadingBidderUsername()
                : "Không có người thắng";
        String prefix = "PAID".equals(status) ? "Đã thanh toán — " : "Người thắng: ";
        winnerLabel.setText(prefix + winner);
      }
    }
  }

  private void startCountdown() {
    stopCountdown();
    countdownTimeline =
        new Timeline(
            new KeyFrame(
                Duration.seconds(1),
                e -> {
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

  /**
   * Applies full badge style (background + text + shape) via inline style so it wins over CSS.
   * Inline style must include font-size and shape properties since the override replaces all inline
   * CSS but CSS-class properties still apply for anything NOT in the inline style.
   */
  private void applyStatusStyle(Label label, String status) {
    if (status == null) {
      label.setStyle("");
      return;
    }
    String bg =
        switch (status) {
          case "RUNNING" -> "#16A34A";
          case "OPEN" -> "#1565C0";
          case "FINISHED", "PAID" -> "#475569";
          case "CANCELED" -> "#DC2626";
          default -> "#64748B";
        };
    label.setStyle(
        "-fx-background-color: "
            + bg
            + "; "
            + "-fx-text-fill: white; "
            + "-fx-font-size: 11px; "
            + "-fx-font-weight: bold; "
            + "-fx-background-radius: 20; "
            + "-fx-padding: 4 12 4 12;");
  }

  private void showBidError(String msg) {
    bidErrorLabel.setText(msg);
    bidErrorLabel.setVisible(true);
    bidErrorLabel.setManaged(true);
  }

  private void hideBidError() {
    bidErrorLabel.setVisible(false);
    bidErrorLabel.setManaged(false);
  }

  private String extractErrorMessage(String body) {
    try {
      return MAPPER.readTree(body).path("message").asText("Đặt giá thất bại.");
    } catch (Exception e) {
      return "Đặt giá thất bại.";
    }
  }
}
