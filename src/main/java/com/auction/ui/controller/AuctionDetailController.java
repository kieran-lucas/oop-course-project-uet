package com.auction.ui.controller;

import com.auction.dto.AuctionResponse;
import com.auction.dto.BidUpdateMessage;
import com.auction.model.BidTransaction;
import com.auction.ui.util.Navigable;
import com.auction.ui.util.SceneManager;
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
import javafx.scene.control.TextField;
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

  // ── Manual bid form ──
  @FXML private VBox bidBox;
  @FXML private TextField bidAmountField;
  @FXML private Button bidButton;
  @FXML private Label bidErrorLabel;

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

  private Long auctionId;
  private Long endTimeMs;
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

    bidSeries.setName("Giá bid");
    bidSeries.getData().clear();
    if (bidChart.getData().isEmpty()) {
      bidChart.getData().add(bidSeries);
    }

    // Hide legend (single series) and configure axes for VND amounts
    bidChart.setLegendVisible(false);

    NumberAxis yAxis = (NumberAxis) bidChart.getYAxis();
    yAxis.setForceZeroInRange(false);
    yAxis.setTickLabelFormatter(
        new StringConverter<>() {
          @Override
          public String toString(Number v) {
            long n = v.longValue();
            if (n >= 1_000_000_000) return String.format("%.1fT", n / 1_000_000_000.0);
            if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
            if (n >= 1_000) return (n / 1_000) + "K";
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
      loadAuctionDetail();
      loadBidHistory();
      connectWebSocket(sm.getJwtToken());
    }
  }

  @Override
  public void onNavigatedFrom() {
    wsClient.disconnect();
    stopCountdown();
    auctionId = null;
  }

  // ========== FXML ACTIONS ==========

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

    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> response =
                    RestClient.post("/api/auctions/" + auctionId + "/bid", body);

                if (response.statusCode() == 201) {
                  Platform.runLater(
                      () -> {
                        bidAmountField.clear();
                        bidButton.setDisable(false);
                        loadBidHistory();
                      });
                } else {
                  String msg = extractErrorMessage(response.body());
                  Platform.runLater(
                      () -> {
                        showBidError(msg);
                        bidButton.setDisable(false);
                      });
                }
              } catch (Exception e) {
                LOGGER.error("Lỗi đặt giá", e);
                Platform.runLater(
                    () -> {
                      showBidError("Không thể kết nối đến server.");
                      bidButton.setDisable(false);
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

    BigDecimal maxBid, increment;
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
    SceneManager.getInstance().navigateTo("auction-list.fxml");
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
                  Platform.runLater(
                      () -> {
                        bidHistoryItems.clear();
                        bidSeries.getData().clear();
                        for (int i = 0; i < bids.size(); i++) {
                          BidTransaction bid = bids.get(i);
                          // ListView entry
                          bidHistoryItems.add(
                              0,
                              String.format(
                                  "%s — %s",
                                  bid.getBidderId() != null ? "Bidder #" + bid.getBidderId() : "?",
                                  bid.getAmount() != null ? VND.format(bid.getAmount()) : "?"));
                          // Chart data
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
          // Append new data point to chart
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
      }
      default -> LOGGER.debug("WS message không xử lý: type={}", msg.getType());
    }
  }

  // ========== UI UPDATE HELPERS ==========

  private void updateAuctionUI(AuctionResponse auction) {
    itemNameLabel.setText(
        auction.getItemName() != null ? auction.getItemName() : "Sản phẩm #" + auction.getItemId());
    itemCategoryLabel.setText(auction.getItemCategory() != null ? auction.getItemCategory() : "");
    itemDescriptionLabel.setText(
        auction.getItemDescription() != null ? auction.getItemDescription() : "");
    auctionStatusLabel.setText(auction.getStatus() != null ? auction.getStatus() : "");

    if (auction.getCurrentPrice() != null) {
      currentPriceLabel.setText(VND.format(auction.getCurrentPrice()));
    }
    leadingBidderLabel.setText(
        auction.getLeadingBidderUsername() != null
            ? auction.getLeadingBidderUsername()
            : "Chưa có");

    endTimeMs = auction.getRemainingTimeMs();
    startCountdown();

    boolean isActive = "RUNNING".equals(auction.getStatus());
    if (!isActive) {
      bidBox.setVisible(false);
      bidBox.setManaged(false);
      if ("FINISHED".equals(auction.getStatus()) || "CANCELED".equals(auction.getStatus())) {
        endedBox.setVisible(true);
        endedBox.setManaged(true);
        String winner =
            auction.getLeadingBidderUsername() != null
                ? auction.getLeadingBidderUsername()
                : "Không có người thắng";
        winnerLabel.setText("Người thắng: " + winner);
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
