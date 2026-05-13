package com.auction.ui.controller;

import com.auction.dto.AuctionResponse;
import com.auction.dto.BidUpdateMessage;
import com.auction.model.BidTransaction;
import com.auction.ui.util.Navigable;
import com.auction.ui.util.SceneManager;
import com.auction.util.NotificationStore;
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
 * <p>Hiển thị thông tin đầy đủ của một phiên: tên/mô tả/category sản phẩm, giá hiện tại, người dẫn
 * đầu, đồng hồ đếm ngược, form đặt giá thủ công, cấu hình auto-bid, biểu đồ giá theo thời gian
 * (AreaChart), và lịch sử bid dạng ListView.
 *
 * <p><b>Luồng dữ liệu:</b>
 *
 * <ul>
 *   <li>Nhận {@code auctionId} từ {@link AuctionListController} hoặc {@link AdminPanelController}
 *       qua {@link #onDataReceived(Object)}.
 *   <li>Load chi tiết phiên qua {@code GET /api/auctions/{id}} và lịch sử bid qua {@code GET
 *       /api/auctions/{id}/bids} bằng luồng nền khi vào màn hình.
 *   <li>Kết nối WebSocket để nhận cập nhật giá real-time; ngắt kết nối và chuyển sang {@link
 *       com.auction.util.BackgroundBidWatcher} khi rời màn hình (nếu user đã bid).
 *   <li>Poll số dư tài khoản mỗi 5 giây để hiển thị số dư hiện tại và toast khi số dư tăng.
 * </ul>
 *
 * <p><b>Các phương thức chính:</b>
 *
 * <ul>
 *   <li>{@link #handleBid()} — Validate và gửi bid thủ công tới {@code POST
 *       /api/auctions/{id}/bid}.
 *   <li>{@link #handleAutoBid()} — Bật auto-bid qua {@code POST /api/auctions/{id}/auto-bid}.
 *   <li>{@link #handleCancelAutoBid()} — Tắt auto-bid qua {@code DELETE
 *       /api/auctions/{id}/auto-bid}.
 *   <li>{@link #goBack()} — Quay về danh sách phiên.
 * </ul>
 */
public class AuctionDetailController implements Navigable {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuctionDetailController.class);
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());
  private static final NumberFormat VND_FMT = NumberFormat.getNumberInstance(Locale.of("vi", "VN"));

  private static String vnd(java.math.BigDecimal v) {
    return v == null ? "?" : VND_FMT.format(v) + " VND";
  }

  private static String vnd(double v) {
    return VND_FMT.format(v) + " VND";
  }

  // ── Item info ──
  @FXML private Label usernameLabel;
  @FXML private Label itemNameLabel;
  @FXML private Label itemCategoryLabel;
  @FXML private Label itemDescriptionLabel;
  @FXML private Label itemBrandLabel; // ELECTRONICS: hãng sản xuất
  @FXML private Label itemArtistLabel; // ART: nghệ sĩ
  @FXML private Label itemYearLabel; // VEHICLE: năm sản xuất
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

  private final WebSocketClient wsClient = new WebSocketClient();
  private final ObservableList<String> bidHistoryItems = FXCollections.observableArrayList();
  private Timeline countdownTimeline;

  // ========== NAVIGABLE LIFECYCLE ==========

  /**
   * Nhận {@code auctionId} được truyền từ màn hình trước (AuctionListController hoặc
   * AdminPanelController). Phải gọi trước {@link #onNavigatedTo()}.
   */
  @Override
  public void onDataReceived(Object data) {
    if (data instanceof Long id) {
      this.auctionId = id;
    }
  }

  /**
   * Được gọi khi điều hướng đến màn hình này. Thực hiện các bước sau:
   *
   * <ol>
   *   <li>Hiển thị username và reset toàn bộ trạng thái cũ (form, chart, flag bid).
   *   <li>Ràng buộc kích thước hai cột trái/phải theo tỉ lệ 60/40.
   *   <li>Load chi tiết phiên, lịch sử bid, kết nối WebSocket và bắt đầu poll số dư.
   * </ol>
   *
   * <p>Chỉ hiển thị form đặt giá khi role hiện tại là BIDDER.
   */
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

  /**
   * Được gọi khi rời khỏi màn hình. Ngắt kết nối WebSocket và dừng đồng hồ đếm ngược, poll số dư.
   * Sau đó đăng ký {@link com.auction.util.BackgroundBidWatcher} để tiếp tục theo dõi phiên ở nền —
   * chỉ khi user đã đặt giá trong phiên này ({@code userHasBid = true}).
   */
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

  /** Chuyển sang màn hình hồ sơ cá nhân. */
  @FXML
  public void handleProfile() {
    SceneManager.getInstance().navigateTo("profile.fxml");
  }

  /**
   * Xử lý đặt giá thủ công. Validate: trường không rỗng, số tiền > 0, không vượt quá số dư hiện
   * tại. Gửi {@code POST /api/auctions/{id}/bid} trên luồng nền; cập nhật lịch sử bid sau khi thành
   * công. Nút bid bị disable trong khi chờ phản hồi để tránh double-submit.
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

    if (lastKnownBalance != null && amount.compareTo(lastKnownBalance) > 0) {
      showBidError(
          "Số dư của bạn ("
              + vnd(lastKnownBalance)
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

  /**
   * Bật auto-bid cho phiên hiện tại. Validate: cả hai trường maxBid và increment không rỗng và hợp
   * lệ. Gửi {@code POST /api/auctions/{id}/auto-bid}; server sẽ tự động đặt giá thay người dùng khi
   * có bid mới cho đến khi đạt maxBid hoặc bị hủy.
   */
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

  /**
   * Tắt auto-bid đang chạy qua {@code DELETE /api/auctions/{id}/auto-bid}. Xóa trắng form
   * maxBid/increment và cập nhật status label sau khi thành công.
   */
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

  /** Quay về màn hình danh sách phiên đấu giá. */
  @FXML
  public void goBack() {
    SceneManager.getInstance().navigateBack("auction-list.fxml");
  }

  // ========== DATA LOADING ==========

  /**
   * Load thông tin chi tiết phiên từ {@code GET /api/auctions/{id}} trên luồng nền. Bỏ qua kết quả
   * nếu người dùng đã chuyển sang phiên khác trong lúc chờ phản hồi (kiểm tra {@code
   * id.equals(auctionId)}).
   */
  private void loadAuctionDetail() {
    final Long id = this.auctionId;
    if (id == null) {
      return;
    }
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> response = RestClient.get("/api/auctions/" + id);
                if (response.statusCode() == 200) {
                  AuctionResponse auction =
                      MAPPER.readValue(response.body(), AuctionResponse.class);
                  Platform.runLater(
                      () -> {
                        if (!id.equals(auctionId)) {
                          return;
                        }
                        updateAuctionUI(auction);
                      });
                }
              } catch (Exception e) {
                LOGGER.error("Lỗi load chi tiết phiên", e);
              }
            });
  }

  /**
   * Load lịch sử bid từ {@code GET /api/auctions/{id}/bids} trên luồng nền. Cập nhật {@code
   * bidHistoryItems} (ListView) và {@code bidSeries} (AreaChart). Đồng thời đánh dấu {@code
   * userHasBid = true} nếu tìm thấy bid của người dùng hiện tại trong danh sách.
   */
  private void loadBidHistory() {
    final Long id = this.auctionId;
    if (id == null) {
      return;
    }
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> response = RestClient.get("/api/auctions/" + id + "/bids");
                if (response.statusCode() == 200) {
                  List<BidTransaction> bids =
                      RestClient.parseList(response.body(), BidTransaction.class);
                  Long currentUserId = SceneManager.getInstance().getCurrentUserId();
                  Platform.runLater(
                      () -> {
                        if (!id.equals(auctionId)) {
                          return;
                        }
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
                                  bid.getAmount() != null ? vnd(bid.getAmount()) : "?"));
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

  /**
   * Kết nối WebSocket tới kênh theo dõi phiên đấu giá. Nhận message JSON dạng {@link
   * com.auction.dto.BidUpdateMessage} và xử lý trên JavaFX thread qua {@link
   * #handleWsMessage(BidUpdateMessage)}.
   */
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

  /**
   * Xử lý message từ WebSocket theo loại:
   *
   * <ul>
   *   <li>{@code BID_UPDATE} — Cập nhật giá hiện tại, người dẫn đầu, biểu đồ và toast thông báo.
   *   <li>{@code TIME_EXTENDED} — Cập nhật {@code endTimeMs} khi anti-sniping gia hạn phiên.
   *   <li>{@code AUCTION_ENDED} — Hiển thị người thắng, dừng đồng hồ và thêm vào NotificationStore.
   * </ul>
   *
   * <p>Phải gọi trên JavaFX thread (được đảm bảo bởi {@link #connectWebSocket}).
   */
  private void handleWsMessage(BidUpdateMessage msg) {
    switch (msg.getType()) {
      case BidUpdateMessage.TYPE_BID_UPDATE -> {
        if (msg.getCurrentPrice() != null) {
          currentPriceLabel.setText(vnd(msg.getCurrentPrice()));
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
        String price = msg.getCurrentPrice() != null ? vnd(msg.getCurrentPrice()) : "—";
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
    String price = msg.getCurrentPrice() != null ? vnd(msg.getCurrentPrice()) : "—";
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

  /** Toast khi số dư tăng — màu xanh đậm, hiển thị 4 giây. Đồng thời thêm vào NotificationStore. */
  private void showBalanceChangeNotification(String amountText) {
    if (bidNotificationLabel == null) {
      return;
    }
    String text = "Số dư tăng " + amountText;
    NotificationStore.getInstance().add(text);
    displayToast(text, "#1B5E20", 4);
  }

  /**
   * Hiển thị toast notification với màu nền và thời gian tùy chỉnh. Nếu có toast đang hiện,
   * Timeline cũ bị dừng và thay bằng Timeline mới.
   *
   * @param text nội dung hiển thị
   * @param bgColor mã màu CSS nền (vd: {@code "#2e7d32"})
   * @param seconds thời gian hiển thị tính bằng giây
   */
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

  /**
   * Cập nhật label số dư: màu xanh lá nếu dương, đỏ nếu bằng 0.
   *
   * @param balance số dư hiện tại của người dùng
   */
  private void updateBalanceLabel(BigDecimal balance) {
    if (balanceLabel == null) {
      return;
    }
    balanceLabel.setText("Số dư: " + vnd(balance));
    balanceLabel.setStyle(
        balance.compareTo(BigDecimal.ZERO) == 0
            ? "-fx-font-size: 11px; -fx-text-fill: #ef5350;"
            : "-fx-font-size: 11px; -fx-text-fill: #66bb6a;");
  }

  /**
   * Fetch số dư ngay lập tức để hiển thị ban đầu, sau đó khởi động Timeline poll mỗi 5 giây. Khi số
   * dư tăng so với lần poll trước, hiển thị toast thông báo. Chỉ poll khi đang ở màn hình này; dừng
   * lại trong {@link #onNavigatedFrom()}.
   */
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
                                            showBalanceChangeNotification("+" + vnd(diff));
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

  /** Dừng và hủy Timeline poll số dư nếu đang chạy. */
  private void stopBalancePoll() {
    if (balancePollTimeline != null) {
      balancePollTimeline.stop();
      balancePollTimeline = null;
    }
  }

  // ========== UI UPDATE HELPERS ==========

  /**
   * Cập nhật toàn bộ UI từ dữ liệu phiên: tên/mô tả/category sản phẩm, metadata theo category, giá
   * hiện tại, người dẫn đầu, trạng thái badge, đồng hồ đếm ngược, và trạng thái box kết thúc/form
   * đặt giá.
   */
  private void updateAuctionUI(AuctionResponse auction) {
    currentItemName = auction.getItemName();
    itemNameLabel.setText(
        currentItemName != null ? currentItemName : "Sản phẩm #" + auction.getItemId());
    itemCategoryLabel.setText(auction.getItemCategory() != null ? auction.getItemCategory() : "");
    itemDescriptionLabel.setText(
        auction.getItemDescription() != null ? auction.getItemDescription() : "");

    // Hiển thị field đặc thù theo category
    updateCategoryMetadata(auction);

    String statusText = auction.getStatus() != null ? auction.getStatus() : "";
    auctionStatusLabel.setText(statusText);
    applyStatusStyle(auctionStatusLabel, auction.getStatus());

    if (auction.getCurrentPrice() != null) {
      currentPriceLabel.setText(vnd(auction.getCurrentPrice()));
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

  /**
   * Ẩn/hiện label đặc thù theo category của sản phẩm.
   *
   * <ul>
   *   <li>ELECTRONICS → itemBrandLabel (Hãng: ...)
   *   <li>ART → itemArtistLabel (Nghệ sĩ: ...)
   *   <li>VEHICLE → itemYearLabel (Năm sản xuất: ...)
   * </ul>
   *
   * <p>Label nào không có dữ liệu (null/blank/0) sẽ được ẩn, không chiếm layout space.
   */
  private void updateCategoryMetadata(AuctionResponse auction) {
    // Ẩn tất cả trước — chỉ 1 label sẽ được bật tùy category
    itemBrandLabel.setVisible(false);
    itemBrandLabel.setManaged(false);
    itemArtistLabel.setVisible(false);
    itemArtistLabel.setManaged(false);
    itemYearLabel.setVisible(false);
    itemYearLabel.setManaged(false);

    String category = auction.getItemCategory();
    if (category == null) {
      return;
    }

    switch (category) {
      case "ELECTRONICS" -> {
        String brand = auction.getItemBrand();
        if (brand != null && !brand.isBlank()) {
          itemBrandLabel.setText("Hãng: " + brand);
          itemBrandLabel.setVisible(true);
          itemBrandLabel.setManaged(true);
        }
      }
      case "ART" -> {
        String artist = auction.getItemArtist();
        if (artist != null && !artist.isBlank()) {
          itemArtistLabel.setText("Nghệ sĩ: " + artist);
          itemArtistLabel.setVisible(true);
          itemArtistLabel.setManaged(true);
        }
      }
      case "VEHICLE" -> {
        Integer year = auction.getItemYear();
        if (year != null && year > 0) {
          itemYearLabel.setText("Năm sản xuất: " + year);
          itemYearLabel.setVisible(true);
          itemYearLabel.setManaged(true);
        }
      }
      default -> {
        // Category khác: không hiển thị thêm gì
      }
    }
  }

  /**
   * Khởi động đồng hồ đếm ngược mỗi giây dựa trên {@code endTimeMs}. Tự dừng và hiển thị "Đã kết
   * thúc" khi hết thời gian.
   */
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

  /** Dừng và hủy Timeline đếm ngược nếu đang chạy. */
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

  /** Hiển thị thông báo lỗi dưới form đặt giá. */
  private void showBidError(String msg) {
    bidErrorLabel.setText(msg);
    bidErrorLabel.setVisible(true);
    bidErrorLabel.setManaged(true);
  }

  /** Ẩn label lỗi đặt giá và giải phóng layout space. */
  private void hideBidError() {
    bidErrorLabel.setVisible(false);
    bidErrorLabel.setManaged(false);
  }

  /**
   * Trích xuất trường {@code message} từ JSON body phản hồi lỗi của server. Trả về fallback {@code
   * "Đặt giá thất bại."} nếu body không hợp lệ.
   */
  private String extractErrorMessage(String body) {
    try {
      return MAPPER.readTree(body).path("message").asText("Đặt giá thất bại.");
    } catch (Exception e) {
      return "Đặt giá thất bại.";
    }
  }
}
