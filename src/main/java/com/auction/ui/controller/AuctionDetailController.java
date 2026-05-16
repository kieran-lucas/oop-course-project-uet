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
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.RotateTransition;
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
 *   <li>Poll số dư tài khoản mỗi 5 giây CHỈ để cập nhật label số dư hiển thị trên form. Thông báo
 *       biến động số dư (deposit, settlement, payout...) do server đẩy qua WebSocket
 *       BALANCE_UPDATED — không tự suy ra từ diff polling để tránh thông báo sai.
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
  @FXML private Label timerHeaderLabel;

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
  @FXML private Label autoBidSpinner;

  // ── Ended state ──
  @FXML private VBox endedBox;
  @FXML private Label winnerLabel;

  // ── Bid history ──
  @FXML private ListView<String> bidHistoryList;
  @FXML private Label totalBidsLabel;

  // ── Bid chart ──
  @FXML private AreaChart<Number, Number> bidChart;
  private final XYChart.Series<Number, Number> bidSeries = new XYChart.Series<>();

  // ── Notification toast ──
  @FXML private Label bidNotificationLabel;
  private Timeline notificationTimeline;

  private Long auctionId;
  private Long endTimeMs;

  /**
   * True while the timer counts down to startTime (auction is OPEN, not yet started). When the
   * value reaches 0 the controller flips this back to false and switches to counting down to
   * endTime — mirroring the AuctionScheduler's OPEN→RUNNING transition.
   */
  private boolean countdownToStart;

  /**
   * End-of-auction timestamp captured when entering OPEN state, used to recompute the RUNNING
   * countdown after the start-countdown reaches zero.
   */
  private java.time.LocalDateTime pendingRunningEndTime;

  private String currentItemName;
  private boolean userHasBid;
  private BigDecimal lastKnownBalance;
  private BigDecimal currentPriceValue;
  private Timeline balancePollTimeline;
  private RotateTransition autoBidSpinnerTransition;

  /** Last seen auto-bid status — used to detect transitions and surface a notification once. */
  private String lastAutoBidStatus;

  private final WebSocketClient wsClient = new WebSocketClient();
  private final ObservableList<String> bidHistoryItems = FXCollections.observableArrayList();
  private Timeline countdownTimeline;

  /**
   * LRU-bounded cache of the most recent {@link AuctionResponse} per auctionId. Lets the controller
   * render previously-visited auctions instantly when the user navigates back instead of blanking
   * the screen to "Đang tải..." while waiting for the GET request.
   */
  private static final int DETAIL_CACHE_MAX = 16;

  private final java.util.Map<Long, AuctionResponse> detailCache =
      java.util.Collections.synchronizedMap(
          new java.util.LinkedHashMap<>(16, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(java.util.Map.Entry<Long, AuctionResponse> eldest) {
              return size() > DETAIL_CACHE_MAX;
            }
          });

  /** Cached snapshot of bid history per auctionId — same purpose as {@link #detailCache}. */
  private final java.util.Map<Long, java.util.List<String>> bidHistoryCache =
      java.util.Collections.synchronizedMap(
          new java.util.LinkedHashMap<>(16, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(
                java.util.Map.Entry<Long, java.util.List<String>> eldest) {
              return size() > DETAIL_CACHE_MAX;
            }
          });

  /** AuctionId that is currently fully painted onto the screen (after updateAuctionUI). */
  private Long renderedAuctionId;

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

    // Decide rendering strategy BEFORE blanking anything:
    //   - Same auction as last render → keep UI on screen, just refresh in background.
    //   - Different auction but cached → repaint from cache instantly, refresh in background.
    //   - Truly fresh navigation → blank to placeholder + load.
    // This avoids the "Đang tải..." flash on revisit and the stale-data flash on first visit.
    boolean sameAsRendered = auctionId != null && auctionId.equals(renderedAuctionId);
    AuctionResponse cached = auctionId == null ? null : detailCache.get(auctionId);

    if (sameAsRendered) {
      // Quietly refresh — labels already correct for this auction.
      if (bidNotificationLabel != null) {
        bidNotificationLabel.setVisible(false);
        bidNotificationLabel.setManaged(false);
      }
    } else if (cached != null) {
      // Repaint from cache so the user sees the auction immediately, then refresh below.
      updateAuctionUI(cached);
      java.util.List<String> cachedHistory = bidHistoryCache.get(auctionId);
      if (cachedHistory != null) {
        bidHistoryItems.setAll(cachedHistory);
        updateTotalBidsLabel(cachedHistory.size());
      } else {
        updateTotalBidsLabel(0);
      }
      if (bidNotificationLabel != null) {
        bidNotificationLabel.setVisible(false);
        bidNotificationLabel.setManaged(false);
      }
    } else {
      // First-time render of this auction in this session — blank to neutral placeholders.
      updateTotalBidsLabel(0);
      stopCountdown();
      markCountdownActive();
      setTimerHeader(false);
      countdownLabel.setText("—");
      countdownToStart = false;
      pendingRunningEndTime = null;
      endTimeMs = null;
      currentPriceLabel.setText("—");
      leadingBidderLabel.setText("—");
      itemNameLabel.setText("Đang tải...");
      itemCategoryLabel.setText("");
      itemDescriptionLabel.setText("");
      itemBrandLabel.setVisible(false);
      itemBrandLabel.setManaged(false);
      itemArtistLabel.setVisible(false);
      itemArtistLabel.setManaged(false);
      itemYearLabel.setVisible(false);
      itemYearLabel.setManaged(false);
      auctionStatusLabel.setText("");
      auctionStatusLabel.setStyle("");
      winnerLabel.setText("");
      if (bidNotificationLabel != null) {
        bidNotificationLabel.setVisible(false);
        bidNotificationLabel.setManaged(false);
      }
    }

    // Reset bid input on every navigation regardless of cache — the user must not see a stale
    // typed amount from a previous visit get submitted accidentally.
    hideBidError();
    bidAmountField.clear();
    bidButton.setDisable(false);

    if (sameAsRendered) {
      // Same auction as last paint → keep balanceLabel + autoBidStatusLabel + lastKnownBalance
      // exactly as they were. loadAutoBidState() + the balance poll below will refresh them
      // when the network response arrives, with no visible "Đang tải..." flicker.
      userHasBid = false;
    } else {
      // Different auction (cached or fresh) → reset per-auction state and stale form fields.
      userHasBid = false;
      if (cached == null) {
        currentItemName = null;
        currentPriceValue = null;
      }
      lastKnownBalance = null;
      lastAutoBidStatus = null;
      if (balanceLabel != null) {
        balanceLabel.setText("Số dư: đang tải...");
        // Clear stale inline colour from the previous auction so the placeholder uses
        // .balance-inline's slate tone instead of leftover red/green.
        balanceLabel.setStyle("");
      }
      autoBidStatusLabel.setText("");
      autoBidStatusLabel.setStyle("");
      maxBidField.clear();
      incrementField.clear();
      applyAutoBidState(null, null, null, null);
    }

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

    if (!sameAsRendered && cached == null) {
      // Fresh navigation — clear chart and endedBox so prior auction's data never bleeds through.
      // When we have cached data, updateAuctionUI already painted the correct state above and
      // loadBidHistory below will atomically replace the chart points; skipping the reset here
      // avoids an empty-chart flash during the 1-2s refresh window.
      endedBox.setVisible(false);
      endedBox.setManaged(false);
      bidSeries.getData().clear();
      bidChart.getData().clear();
      bidSeries.setName("Giá bid");
      bidChart.getData().add(bidSeries);
    } else if (bidChart.getData().isEmpty()) {
      // Defensive: re-attach series if a previous reset cleared the chart.
      bidSeries.setName("Giá bid");
      bidChart.getData().add(bidSeries);
    }

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
      if (isBidder) {
        loadAutoBidState();
      }
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
    stopAutoBidSpinner();
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
    // NOTE: do NOT null auctionId here. navigateBack() does not call onDataReceived(), so if we
    // clear the id on leaving, returning to this screen (e.g. detail → profile → back) lands in
    // onNavigatedTo() with auctionId=null, which skips loadAuctionDetail() and leaves the UI
    // frozen on "Đang tải...". Stale-id races are already guarded by the id.equals(auctionId)
    // check in every async response callback, so keeping the previous id is safe.
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
   * Bật auto-bid cho phiên hiện tại. Validate client-side: maxBid và increment đều dương; maxBid
   * phải lớn hơn hoặc bằng (giá hiện tại + bước tăng) — nếu không, autobid không thể đặt được bid
   * đầu tiên. Sau khi POST thành công, đọc trạng thái thực tế từ response (ACTIVE / EXHAUSTED /
   * FAILED) để hiển thị đúng.
   */
  @FXML
  public void handleAutoBid() {
    String maxBidText = maxBidField.getText().trim();
    String incrementText = incrementField.getText().trim();

    if (maxBidText.isEmpty() || incrementText.isEmpty()) {
      showAutoBidMessage("Vui lòng nhập đầy đủ thông tin.", "#DC2626");
      return;
    }

    BigDecimal maxBid;
    BigDecimal increment;
    try {
      maxBid = new BigDecimal(maxBidText.replace(",", "").replace(".", ""));
      increment = new BigDecimal(incrementText.replace(",", "").replace(".", ""));
    } catch (NumberFormatException e) {
      showAutoBidMessage("Giá trị không hợp lệ.", "#DC2626");
      return;
    }

    if (maxBid.signum() <= 0 || increment.signum() <= 0) {
      showAutoBidMessage("Giá tối đa và bước tăng phải lớn hơn 0.", "#DC2626");
      return;
    }

    if (currentPriceValue != null && currentPriceValue.add(increment).compareTo(maxBid) > 0) {
      showAutoBidMessage(
          "Giá tối đa ("
              + vnd(maxBid)
              + ") không đủ để đặt bid đầu tiên ("
              + vnd(currentPriceValue.add(increment))
              + ").",
          "#DC2626");
      return;
    }

    autoBidButton.setDisable(true);
    Map<String, Object> body = new HashMap<>();
    body.put("maxBid", maxBid);
    body.put("increment", increment);

    final Long autoBidAuctionId = this.auctionId;
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> response =
                    RestClient.post("/api/auctions/" + autoBidAuctionId + "/auto-bid", body);
                Platform.runLater(
                    () -> {
                      if (!autoBidAuctionId.equals(auctionId)) {
                        return;
                      }
                      autoBidButton.setDisable(false);
                      if (response.statusCode() == 200 || response.statusCode() == 201) {
                        applyAutoBidStateFromJson(response.body());
                      } else {
                        String err = extractErrorMessage(response.body());
                        showAutoBidMessage(
                            err != null && !err.isBlank()
                                ? err
                                : "Bật auto-bid thất bại: " + response.statusCode(),
                            "#DC2626");
                      }
                    });
              } catch (Exception e) {
                LOGGER.error("Lỗi bật auto-bid", e);
                Platform.runLater(
                    () -> {
                      if (!autoBidAuctionId.equals(auctionId)) {
                        return;
                      }
                      autoBidButton.setDisable(false);
                      showAutoBidMessage("Không thể kết nối đến server.", "#DC2626");
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
    final Long autoBidAuctionId = this.auctionId;
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> response =
                    RestClient.delete("/api/auctions/" + autoBidAuctionId + "/auto-bid");
                Platform.runLater(
                    () -> {
                      if (!autoBidAuctionId.equals(auctionId)) {
                        return;
                      }
                      if (response.statusCode() == 204 || response.statusCode() == 200) {
                        // applyAutoBidState(STOPPED, ...) tự xóa field + hiện thông báo grey.
                        applyAutoBidState("STOPPED", null, null, null);
                      } else {
                        showAutoBidMessage("Tắt auto-bid thất bại.", "#DC2626");
                      }
                    });
              } catch (Exception e) {
                LOGGER.error("Lỗi tắt auto-bid", e);
                Platform.runLater(() -> showAutoBidMessage("Không thể kết nối.", "#DC2626"));
              }
            });
  }

  /** Quay về màn hình danh sách phiên đấu giá. */
  @FXML
  public void goBack() {
    SceneManager.getInstance().navigateBack("auction-list.fxml");
  }

  // ========== DATA LOADING ==========

  private void loadAutoBidState() {
    final Long currentAuctionId = auctionId;
    if (currentAuctionId == null) {
      return;
    }
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> response =
                    RestClient.get("/api/auctions/" + currentAuctionId + "/auto-bid");
                if (response.statusCode() != 200) {
                  return;
                }
                final String body = response.body();
                Platform.runLater(
                    () -> {
                      if (currentAuctionId.equals(auctionId)) {
                        applyAutoBidStateFromJson(body);
                      }
                    });
              } catch (Exception e) {
                LOGGER.debug("Không thể load auto-bid state: {}", e.getMessage());
              }
            });
  }

  /**
   * Parse JSON body trả về từ {@code GET/POST /api/auctions/{id}/auto-bid} và áp dụng trạng thái
   * tương ứng lên UI. Body có thể là:
   *
   * <ul>
   *   <li>{@code {"active": false}} — chưa có config nào.
   *   <li>Full AutoBidConfig — có các field {@code status}, {@code failureReason}, {@code maxBid},
   *       {@code increment}.
   * </ul>
   */
  private void applyAutoBidStateFromJson(String body) {
    try {
      var node = MAPPER.readTree(body);
      String status =
          node.hasNonNull("status")
              ? node.get("status").asText()
              : (node.path("active").asBoolean(false) ? "ACTIVE" : null);
      String reason = node.hasNonNull("failureReason") ? node.get("failureReason").asText() : null;
      BigDecimal maxBid = node.hasNonNull("maxBid") ? node.get("maxBid").decimalValue() : null;
      BigDecimal increment =
          node.hasNonNull("increment") ? node.get("increment").decimalValue() : null;
      applyAutoBidState(status, reason, maxBid, increment);
    } catch (Exception e) {
      LOGGER.debug("Không thể parse auto-bid state: {}", e.getMessage());
    }
  }

  /**
   * Áp dụng trạng thái auto-bid lên UI: cập nhật label, spinner, nút tắt/bật, các field
   * maxBid/increment, và trạng thái disable của form. {@code status} có thể là {@code null} (chưa
   * có config), {@code ACTIVE}, {@code EXHAUSTED}, {@code FAILED}, hoặc {@code STOPPED}.
   *
   * <p>Quy tắc UI theo trạng thái:
   *
   * <ul>
   *   <li>ACTIVE: hai field + nút "Bật" bị disable (phải tắt trước rồi mới được sửa); nút "Tắt"
   *       enable; spinner xoay; label xanh kèm thông số.
   *   <li>EXHAUSTED/FAILED: form mở để user chỉnh lại giá; nút "Bật" enable; nút "Tắt" disable; có
   *       điền sẵn maxBid/increment cũ để user thấy giá trị đã thử.
   *   <li>STOPPED hoặc chưa có: form trống, "Bật" enable, "Tắt" disable.
   * </ul>
   */
  private void applyAutoBidState(
      String status, String reason, BigDecimal maxBid, BigDecimal increment) {
    boolean active = "ACTIVE".equals(status);

    // Surface an in-app notification when auto-bid transitions ACTIVE -> EXHAUSTED/FAILED so
    // the bell list reflects the change immediately instead of only after WS reconnect.
    if ("ACTIVE".equals(lastAutoBidStatus)
        && ("EXHAUSTED".equals(status) || "FAILED".equals(status))) {
      String itemLabel = currentItemName != null ? "[" + currentItemName + "] " : "";
      String text;
      if ("EXHAUSTED".equals(status)) {
        text =
            itemLabel
                + "Auto-bid đã đạt mức tối đa"
                + (maxBid != null ? " (" + vnd(maxBid) + ")" : "")
                + " và đã được dừng.";
      } else if ("INSUFFICIENT_BALANCE".equals(reason)) {
        text = itemLabel + "Auto-bid đã dừng: số dư không đủ để tiếp tục.";
      } else {
        text = itemLabel + "Auto-bid đã dừng tự động.";
      }
      NotificationStore.getInstance().add(text);
      displayToast(text, "#D97706", 4);
    }
    lastAutoBidStatus = status;

    autoBidButton.setDisable(active);
    maxBidField.setDisable(active);
    incrementField.setDisable(active);

    if (active) {
      if (maxBid != null) {
        maxBidField.setText(maxBid.stripTrailingZeros().toPlainString());
      }
      if (increment != null) {
        incrementField.setText(increment.stripTrailingZeros().toPlainString());
      }
      cancelAutoBidButton.setDisable(false);
      startAutoBidSpinner();
      showAutoBidMessage(
          "Auto-bid đang hoạt động. Tối đa: "
              + (maxBid != null ? vnd(maxBid) : "—")
              + " — Bước: "
              + (increment != null ? vnd(increment) : "—"),
          "#16A34A");
      return;
    }

    cancelAutoBidButton.setDisable(true);
    stopAutoBidSpinner();
    if (autoBidSpinner != null) {
      autoBidSpinner.setVisible(false);
      autoBidSpinner.setManaged(false);
    }

    if ("EXHAUSTED".equals(status)) {
      // Giữ lại giá trị cũ để user thấy hạn mức cũ và chỉnh lại nhanh
      if (maxBid != null) {
        maxBidField.setText(maxBid.stripTrailingZeros().toPlainString());
      }
      if (increment != null) {
        incrementField.setText(increment.stripTrailingZeros().toPlainString());
      }
      String msg =
          "Auto-bid đã dừng: hạn mức (max) thấp hơn giá hiện tại + bước tăng."
              + " Hãy chỉnh giá tối đa cao hơn rồi bật lại.";
      showAutoBidMessage(msg, "#D97706");
    } else if ("FAILED".equals(status)) {
      if (maxBid != null) {
        maxBidField.setText(maxBid.stripTrailingZeros().toPlainString());
      }
      if (increment != null) {
        incrementField.setText(increment.stripTrailingZeros().toPlainString());
      }
      String msg =
          "INSUFFICIENT_BALANCE".equals(reason)
              ? "Auto-bid đã dừng: số dư khả dụng không đủ để tiếp tục."
                  + " Hãy nạp thêm tiền rồi bật lại."
              : "Auto-bid đã dừng do lỗi" + (reason != null ? " (" + reason + ")." : ".");
      showAutoBidMessage(msg, "#DC2626");
    } else if ("STOPPED".equals(status)) {
      maxBidField.clear();
      incrementField.clear();
      showAutoBidMessage("Auto-bid đã tắt.", "#64748B");
    } else {
      maxBidField.clear();
      incrementField.clear();
      autoBidStatusLabel.setText("");
      autoBidStatusLabel.setStyle("");
    }
  }

  private void showAutoBidMessage(String message, String color) {
    autoBidStatusLabel.setText(message);
    autoBidStatusLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px;");
  }

  private void startAutoBidSpinner() {
    if (autoBidSpinner == null) {
      return;
    }
    autoBidSpinner.setVisible(true);
    autoBidSpinner.setManaged(true);
    if (autoBidSpinnerTransition == null) {
      autoBidSpinnerTransition = new RotateTransition(Duration.seconds(1.7), autoBidSpinner);
      autoBidSpinnerTransition.setFromAngle(0);
      autoBidSpinnerTransition.setToAngle(360);
      autoBidSpinnerTransition.setCycleCount(Timeline.INDEFINITE);
      autoBidSpinnerTransition.setInterpolator(Interpolator.LINEAR);
    }
    autoBidSpinnerTransition.play();
  }

  private void stopAutoBidSpinner() {
    if (autoBidSpinnerTransition != null) {
      autoBidSpinnerTransition.stop();
    }
    if (autoBidSpinner != null) {
      autoBidSpinner.setRotate(0);
    }
  }

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
                  // Build the new model OFF the FX thread so the swap on FX is atomic — no
                  // intermediate clear+rebuild flicker on the chart/list.
                  java.util.List<String> newItems = new java.util.ArrayList<>(bids.size());
                  java.util.List<XYChart.Data<Number, Number>> newPoints =
                      new java.util.ArrayList<>(bids.size());
                  boolean foundMyBid = false;
                  for (int i = 0; i < bids.size(); i++) {
                    BidTransaction bid = bids.get(i);
                    boolean isMyBid =
                        currentUserId != null && currentUserId.equals(bid.getBidderId());
                    if (isMyBid) {
                      foundMyBid = true;
                    }
                    String bidderLabel;
                    if (isMyBid) {
                      bidderLabel = "Bạn";
                    } else if (bid.getBidderUsername() != null
                        && !bid.getBidderUsername().isBlank()) {
                      bidderLabel = bid.getBidderUsername();
                    } else {
                      // Final fallback when the server couldn't resolve the username (e.g. user
                      // since deleted). Better than crashing — surface the raw id so admins can
                      // still trace the row.
                      bidderLabel =
                          "Bidder #" + (bid.getBidderId() != null ? bid.getBidderId() : "?");
                    }
                    newItems.add(
                        0,
                        String.format(
                            "%s — %s",
                            bidderLabel, bid.getAmount() != null ? vnd(bid.getAmount()) : "?"));
                    if (bid.getAmount() != null) {
                      newPoints.add(new XYChart.Data<>(i + 1, bid.getAmount().doubleValue()));
                    }
                  }
                  final boolean userHasBidNow = foundMyBid;
                  final int bidCount = bids.size();
                  Platform.runLater(
                      () -> {
                        if (!id.equals(auctionId)) {
                          return;
                        }
                        userHasBid = userHasBidNow;
                        bidHistoryItems.setAll(newItems);
                        bidSeries.getData().setAll(newPoints);
                        updateTotalBidsLabel(bidCount);
                        // Snapshot for instant re-render on revisit (see detailCache).
                        bidHistoryCache.put(id, new java.util.ArrayList<>(newItems));
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
          currentPriceValue = msg.getCurrentPrice();
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
        // Re-check autobid state — chain bidding may have changed our config to EXHAUSTED/FAILED
        if ("BIDDER".equals(SceneManager.getInstance().getCurrentRole())) {
          loadAutoBidState();
        }
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
        markCountdownEnded();
        setTimerHeader(false);
        auctionStatusLabel.setText("FINISHED");
        applyStatusStyle(auctionStatusLabel, "FINISHED");
        bidBox.setVisible(false);
        bidBox.setManaged(false);
        endedBox.setVisible(true);
        endedBox.setManaged(true);
        boolean hasWinner = msg.getLeadingBidderUsername() != null;
        String winner = hasWinner ? msg.getLeadingBidderUsername() : "Không có người thắng";
        String price = msg.getCurrentPrice() != null ? vnd(msg.getCurrentPrice()) : "—";
        winnerLabel.setText("Người thắng: " + winner + " — Giá cuối: " + price);
        // The server-driven AUCTION_RESULT notification (broadcast to every distinct bidder +
        // seller) already lands in the bell via /ws/user/{id}. Skip the client-side fallback so
        // bidders don't see two near-identical entries.
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
    String itemPlain = currentItemName != null ? "[" + currentItemName + "] " : "";
    // Marker-wrapped variant for the bell: server-side messages use the same convention so the
    // popup's segment renderer can colour usernames blue and the auction name brown.
    String itemMarker =
        currentItemName != null
            ? com.auction.util.NotificationFormat.auctionName(auctionId, currentItemName) + " "
            : "";
    String bidderMarker = com.auction.util.NotificationFormat.user(bidder);

    String toastText;
    String storeText;
    String color;

    if (isSeller) {
      toastText = itemPlain + "Có bid mới: " + price + " từ " + bidder;
      storeText = itemMarker + "Có bid mới: " + price + " từ " + bidderMarker;
      color = "#1565C0";
      // Bell entry được đẩy qua kênh /ws/user/{sellerId} (SELLER_BID_RECEIVED) — tránh nhân đôi.
    } else if (isOwnBid && msg.isAutoBid()) {
      toastText = itemPlain + "Auto-bid đã đặt " + price + " cho bạn";
      storeText = itemMarker + "Auto-bid đã đặt " + price + " cho bạn";
      color = "#16A34A";
      NotificationStore.getInstance().add(storeText);
    } else if (isOwnBid) {
      toastText = itemPlain + "Bạn đặt giá: " + price;
      storeText = itemMarker + "Bạn đặt giá: " + price;
      color = "#16A34A";
      NotificationStore.getInstance().add(storeText);
    } else {
      toastText = itemPlain + bidder + " vừa bid " + price;
      storeText = itemMarker + bidderMarker + " vừa bid " + price;
      color = "#1565C0";
      if (userHasBid) {
        NotificationStore.getInstance().add(storeText);
      }
    }

    displayToast(toastText, color, 3);
  }

  /**
   * Hiển thị toast notification với màu nền và thời gian tùy chỉnh. Nếu có toast đang hiện,
   * Timeline cũ bị dừng và thay bằng Timeline mới.
   *
   * @param text nội dung hiển thị
   * @param bgColor mã màu CSS nền (vd: {@code "#16A34A"})
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
            ? "-fx-text-fill: #DC2626;"
            : "-fx-text-fill: #16A34A;");
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

    // Poll chỉ cập nhật label số dư trong form, KHÔNG hiện toast khi số dư thay đổi.
    // Nguồn DUY NHẤT phát thông báo biến động số dư là server qua WebSocket BALANCE_UPDATED
    // (UserBalanceWatcher). Phát hiện diff từ poll có thể fire sai (race với reserved_balance,
    // chạy chậm sau khi server đã push WS, hoặc trùng với notification đã có).
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
      currentPriceValue = auction.getCurrentPrice();
      currentPriceLabel.setText(vnd(auction.getCurrentPrice()));
    }
    leadingBidderLabel.setText(
        auction.getLeadingBidderUsername() != null
            ? auction.getLeadingBidderUsername()
            : "Chưa có");

    String st = auction.getStatus();
    if ("CANCELED".equals(st) || "FINISHED".equals(st) || "PAID".equals(st)) {
      stopCountdown();
      markCountdownEnded();
      setTimerHeader(false);
    } else if ("OPEN".equals(st) && auction.getStartTime() != null) {
      // Phiên chưa bắt đầu — đếm ngược đến startTime, header chuyển sang "BẮT ĐẦU SAU"
      countdownToStart = true;
      pendingRunningEndTime = auction.getEndTime();
      endTimeMs =
          java.time.Duration.between(java.time.LocalDateTime.now(), auction.getStartTime())
              .toMillis();
      setTimerHeader(true);
      markCountdownActive();
      startCountdown();
    } else {
      // RUNNING (hoặc không xác định) — đếm ngược đến endTime.
      // Use the absolute endTime (not the server-snapshot remainingTimeMs) so re-rendering
      // from the local cache after a navigation away/back still produces an accurate countdown.
      countdownToStart = false;
      pendingRunningEndTime = null;
      if (auction.getEndTime() != null) {
        endTimeMs =
            java.time.Duration.between(java.time.LocalDateTime.now(), auction.getEndTime())
                .toMillis();
      } else {
        endTimeMs = auction.getRemainingTimeMs();
      }
      setTimerHeader(false);
      markCountdownActive();
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

    // Snapshot for instant re-render on next visit.
    if (auctionId != null) {
      detailCache.put(auctionId, auction);
      renderedAuctionId = auctionId;
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
                  } else if (countdownToStart && pendingRunningEndTime != null) {
                    // OPEN → RUNNING: chuyển sang đếm ngược đến endTime
                    countdownToStart = false;
                    endTimeMs =
                        java.time.Duration.between(
                                java.time.LocalDateTime.now(), pendingRunningEndTime)
                            .toMillis();
                    pendingRunningEndTime = null;
                    setTimerHeader(false);
                    if (endTimeMs == null || endTimeMs <= 0) {
                      markCountdownEnded();
                      stopCountdown();
                    }
                  } else {
                    markCountdownEnded();
                    stopCountdown();
                  }
                }));
    countdownTimeline.setCycleCount(Timeline.INDEFINITE);
    countdownTimeline.play();
  }

  /** Cập nhật header "THỜI GIAN CÒN" / "BẮT ĐẦU SAU" theo trạng thái phiên. */
  private void setTimerHeader(boolean toStart) {
    if (timerHeaderLabel != null) {
      timerHeaderLabel.setText(toStart ? "BẮT ĐẦU SAU" : "THỜI GIAN CÒN");
    }
  }

  /** Đặt countdownLabel sang trạng thái "đã kết thúc" — text + style class .ended. */
  private void markCountdownEnded() {
    countdownLabel.setText("Đã kết thúc");
    if (!countdownLabel.getStyleClass().contains("ended")) {
      countdownLabel.getStyleClass().add("ended");
    }
  }

  /** Đảm bảo countdownLabel không còn ở trạng thái "đã kết thúc" trước khi tick HH:MM:SS. */
  private void markCountdownActive() {
    countdownLabel.getStyleClass().removeAll("ended");
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
          case "FINISHED", "PAID" -> "#64748B";
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

  private void updateTotalBidsLabel(int count) {
    if (totalBidsLabel == null) {
      return;
    }
    totalBidsLabel.setText(count + " lượt");
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
