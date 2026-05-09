package com.auction.ui.controller;

import com.auction.dto.AuctionResponse;
import com.auction.ui.util.Navigable;
import com.auction.ui.util.SceneManager;
import com.auction.util.NotificationStore;
import com.auction.util.RestClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Controller cho màn hình danh sách phiên đấu giá (auction-list.fxml). */
public class AuctionListController implements Navigable {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuctionListController.class);
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());
  private static final NumberFormat VND = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

  @FXML private Label usernameLabel;
  @FXML private TableView<AuctionResponse> auctionTable;
  @FXML private TableColumn<AuctionResponse, String> itemCol;
  @FXML private TableColumn<AuctionResponse, String> categoryCol;
  @FXML private TableColumn<AuctionResponse, BigDecimal> priceCol;
  @FXML private TableColumn<AuctionResponse, Long> timeCol;
  @FXML private TableColumn<AuctionResponse, String> statusCol;
  @FXML private TableColumn<AuctionResponse, Void> actionCol;
  @FXML private TextField searchField;
  @FXML private ComboBox<String> statusFilter;
  @FXML private ComboBox<String> categoryFilter;
  @FXML private Button createAuctionButton;
  @FXML private Label statusLabel;
  @FXML private StackPane bellPane;
  @FXML private Button bellButton;
  @FXML private Label badgeLabel;

  private final ObservableList<AuctionResponse> allAuctions = FXCollections.observableArrayList();
  private Timeline autoRefreshTimeline;
  private Timeline tableCountdownTimeline;
  private Timeline balancePollTimeline;
  private javafx.beans.value.ChangeListener<Number> notificationListener;
  private BigDecimal lastKnownBalance;

  // ========== JAVAFX INITIALIZE ==========

  @FXML
  public void initialize() {
    setupColumns();
    setupStatusFilter();
  }

  // ========== NAVIGABLE LIFECYCLE ==========

  @Override
  public void onNavigatedTo() {
    SceneManager sm = SceneManager.getInstance();
    usernameLabel.setText(sm.getCurrentUsername() != null ? sm.getCurrentUsername() : "");

    // Hiển thị nút tạo phiên chỉ cho SELLER
    boolean isSeller = "SELLER".equals(sm.getCurrentRole());
    createAuctionButton.setVisible(isSeller);
    createAuctionButton.setManaged(isSeller);

    // Hiển thị chuông thông báo
    String role = sm.getCurrentRole();
    boolean showBell = "BIDDER".equals(role) || "SELLER".equals(role);
    if (bellPane != null) {
      bellPane.setVisible(showBell);
      bellPane.setManaged(showBell);
    }
    if (showBell) {
      updateBadge();
      if (notificationListener != null) {
        NotificationStore.getInstance().unreadCountProperty().removeListener(notificationListener);
      }
      notificationListener = (obs, oldVal, newVal) -> updateBadge();
      NotificationStore.getInstance().unreadCountProperty().addListener(notificationListener);
    }

    loadAuctions();
    startAutoRefresh();
    startTableCountdown();
    startBalancePoll();
  }

  @Override
  public void onNavigatedFrom() {
    stopAutoRefresh();
    stopTableCountdown();
    stopBalancePoll();
    if (notificationListener != null) {
      NotificationStore.getInstance().unreadCountProperty().removeListener(notificationListener);
      notificationListener = null;
    }
  }

  // ========== FXML ACTIONS ==========

  @FXML
  public void handleSearch() {
    String keyword = searchField.getText().trim().toLowerCase();
    String status = statusFilter.getValue();
    String category = categoryFilter != null ? categoryFilter.getValue() : null;

    List<AuctionResponse> filtered =
        allAuctions.stream()
            .filter(
                a -> {
                  boolean matchName =
                      keyword.isEmpty()
                          || (a.getItemName() != null
                              && a.getItemName().toLowerCase().contains(keyword));
                  boolean matchStatus =
                      status == null || "Tất cả".equals(status) || status.equals(a.getStatus());
                  boolean matchCategory =
                      category == null
                          || "Tất cả".equals(category)
                          || category.equals(a.getItemCategory());
                  return matchName && matchStatus && matchCategory;
                })
            .toList();

    auctionTable.setItems(FXCollections.observableArrayList(filtered));
  }

  @FXML
  public void handleRefresh() {
    loadAuctions();
  }

  @FXML
  public void handleCreateAuction() {
    SceneManager.getInstance().navigateTo("create-auction.fxml");
  }

  @FXML
  public void handleProfile() {
    SceneManager.getInstance().navigateTo("profile.fxml");
  }

  @FXML
  public void handleLogout() {
    stopAutoRefresh();
    SceneManager.getInstance().logout();
  }

  // ========== DATA LOADING ==========

  public void loadAuctions() {
    setStatus("Đang tải dữ liệu...");
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> response = RestClient.get("/api/auctions");
                if (response.statusCode() == 200) {
                  List<AuctionResponse> list =
                      RestClient.parseList(response.body(), AuctionResponse.class);
                  Platform.runLater(
                      () -> {
                        allAuctions.setAll(list);
                        updateCategoryFilter(list);
                        handleSearch();
                        setStatus("Tổng cộng " + list.size() + " phiên đấu giá.");
                      });
                } else {
                  Platform.runLater(() -> setStatus("Lỗi tải dữ liệu: " + response.statusCode()));
                }
              } catch (Exception e) {
                LOGGER.error("Lỗi load danh sách auction", e);
                Platform.runLater(() -> setStatus("Không thể kết nối đến server."));
              }
            });
  }

  @FXML
  public void handleBellClick() {
    NotificationStore store = NotificationStore.getInstance();
    store.markAllRead();
    updateBadge();

    Popup popup = new Popup();
    popup.setAutoHide(true);

    VBox content = new VBox(8);
    content.setPadding(new Insets(14, 16, 14, 16));
    content.setPrefWidth(320);
    content.setStyle(
        "-fx-background-color: #1e2d40; "
            + "-fx-border-color: rgba(33,150,243,0.45); "
            + "-fx-border-width: 1; "
            + "-fx-border-radius: 10; "
            + "-fx-background-radius: 10; "
            + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.45), 14, 0, 0, 4);");

    Label header = new Label("Thông báo");
    header.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");

    Separator sep = new Separator();
    sep.setStyle("-fx-background-color: rgba(255,255,255,0.15);");
    content.getChildren().addAll(header, sep);

    ObservableList<String> notifications = store.getNotifications();
    if (notifications.isEmpty()) {
      Label empty = new Label("Chưa có thông báo nào.");
      empty.setStyle("-fx-text-fill: #78909c; -fx-font-size: 13px; -fx-padding: 4 0 4 0;");
      content.getChildren().add(empty);
    } else {
      VBox items = new VBox(4);
      int limit = Math.min(notifications.size(), 8);
      for (int i = 0; i < limit; i++) {
        Label item = new Label(notifications.get(i));
        item.setWrapText(true);
        item.setMaxWidth(Double.MAX_VALUE);
        item.setPadding(new Insets(7, 10, 7, 10));
        item.setStyle(
            "-fx-text-fill: #e0e0e0; -fx-font-size: 12px; "
                + "-fx-background-color: rgba(255,255,255,0.06); "
                + "-fx-background-radius: 6;");
        items.getChildren().add(item);
      }
      if (notifications.size() > 8) {
        Label more = new Label("... và " + (notifications.size() - 8) + " thông báo khác");
        more.setStyle("-fx-text-fill: #78909c; -fx-font-size: 11px; -fx-padding: 2 0 0 0;");
        items.getChildren().add(more);
      }
      content.getChildren().add(items);
    }

    popup.getContent().add(content);
    Stage stage = (Stage) bellButton.getScene().getWindow();
    javafx.geometry.Bounds bounds = bellButton.localToScreen(bellButton.getBoundsInLocal());
    popup.show(
        stage, bounds.getMinX() - content.getPrefWidth() + bounds.getWidth(), bounds.getMaxY() + 6);
  }

  // ========== PRIVATE HELPERS ==========

  private void updateBadge() {
    if (badgeLabel == null) {
      return;
    }
    int count = NotificationStore.getInstance().getUnreadCount();
    if (count > 0) {
      badgeLabel.setText(count > 99 ? "99+" : String.valueOf(count));
      badgeLabel.setVisible(true);
      badgeLabel.setManaged(true);
    } else {
      badgeLabel.setVisible(false);
    }
  }

  private void updateCategoryFilter(List<AuctionResponse> auctions) {
    if (categoryFilter == null) {
      return;
    }
    String current = categoryFilter.getValue();
    java.util.List<String> categories = new java.util.ArrayList<>();
    categories.add("Tất cả");
    for (String cat : new String[] {"ART", "ELECTRONICS", "VEHICLE"}) {
      categories.add(cat);
    }
    auctions.stream()
        .map(AuctionResponse::getItemCategory)
        .filter(c -> c != null && !c.isBlank() && !categories.contains(c))
        .distinct()
        .sorted()
        .forEach(categories::add);
    categoryFilter.setItems(FXCollections.observableArrayList(categories));
    if (current != null && categories.contains(current)) {
      categoryFilter.setValue(current);
    }
  }

  private void setupColumns() {
    itemCol.setCellValueFactory(new PropertyValueFactory<>("itemName"));
    categoryCol.setCellValueFactory(new PropertyValueFactory<>("itemCategory"));

    priceCol.setCellValueFactory(new PropertyValueFactory<>("currentPrice"));
    priceCol.setCellFactory(
        col ->
            new TableCell<>() {
              @Override
              protected void updateItem(BigDecimal price, boolean empty) {
                super.updateItem(price, empty);
                setText(empty || price == null ? null : VND.format(price));
              }
            });

    timeCol.setCellValueFactory(new PropertyValueFactory<>("remainingTimeMs"));
    timeCol.setCellFactory(
        col ->
            new TableCell<>() {
              @Override
              protected void updateItem(Long ignored, boolean empty) {
                super.updateItem(ignored, empty);
                if (empty) {
                  setText(null);
                  setStyle("");
                  return;
                }
                AuctionResponse a = getTableRow() != null ? getTableRow().getItem() : null;
                if (a == null) {
                  setText(null);
                  setStyle("");
                  return;
                }
                String st = a.getStatus();
                if ("FINISHED".equals(st) || "CANCELED".equals(st) || "PAID".equals(st)) {
                  setText("Đã kết thúc");
                  setStyle("-fx-font-size: 11px;");
                  return;
                }
                setStyle("");
                LocalDateTime endTime = a.getEndTime();
                if (endTime == null) {
                  setText("—");
                  return;
                }
                long ms = java.time.Duration.between(LocalDateTime.now(), endTime).toMillis();
                if (ms <= 0) {
                  setText("Đã kết thúc");
                  setStyle("-fx-font-size: 11px;");
                } else {
                  long totalSec = ms / 1000;
                  long h = totalSec / 3600;
                  long m = (totalSec % 3600) / 60;
                  long s = totalSec % 60;
                  setText(String.format("%02d:%02d:%02d", h, m, s));
                }
              }
            });

    statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
    statusCol.setCellFactory(
        col ->
            new TableCell<>() {
              @Override
              protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                  setText(null);
                  setStyle("");
                } else {
                  setText(status);
                  String color =
                      switch (status) {
                        case "RUNNING" -> "-fx-text-fill: #00c853; -fx-font-weight: bold;";
                        case "OPEN" -> "-fx-text-fill: #2196f3; -fx-font-weight: bold;";
                        case "FINISHED", "PAID" -> "-fx-text-fill: #9e9e9e;";
                        case "CANCELED" -> "-fx-text-fill: #e53935;";
                        default -> "";
                      };
                  setStyle(color);
                }
              }
            });
    actionCol.setMinWidth(130);
    actionCol.setCellFactory(
        col ->
            new TableCell<>() {
              private final Button btnEnter = new Button("Xem"); // Đổi thành Xem theo ảnh
              private final Button btnCancel = new Button("Hủy");
              private final javafx.scene.layout.HBox actionBox =
                  new javafx.scene.layout.HBox(8, btnEnter, btnCancel);

              {
                actionBox.setAlignment(javafx.geometry.Pos.CENTER);
                btnEnter.setStyle("-fx-cursor: hand;");

                btnEnter.setMinWidth(50);
                btnCancel.setMinWidth(50);

                btnEnter.setOnAction(
                    e -> {
                      AuctionResponse auction = getTableView().getItems().get(getIndex());
                      SceneManager.getInstance().navigateTo("auction-detail.fxml", auction.getId());
                    });

                btnCancel.setOnAction(
                    e -> {
                      AuctionResponse auction = getTableView().getItems().get(getIndex());
                      try {
                        HttpResponse<String> response =
                            RestClient.delete("/api/auctions/" + auction.getId());
                        if (response.statusCode() == 204) {
                          System.out.println("Hủy thành công!");
                          loadAuctions();
                        } else {
                          System.out.println("Lỗi khi hủy: " + response.body());
                        }
                      } catch (Exception ex) {
                        ex.printStackTrace();
                      }
                    });
              }

              @Override
              protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                  setGraphic(null);
                } else {
                  AuctionResponse auction = getTableView().getItems().get(getIndex());
                  if (auction != null) {
                    String currentRole = SceneManager.getInstance().getCurrentRole();
                    boolean isSeller = "SELLER".equals(currentRole);
                    boolean isOpen = "OPEN".equals(auction.getStatus());

                    // LOGIC GIAO DIỆN MỚI
                    if (isSeller) {
                      // Luôn hiện nút Hủy cho Seller để giữ form bảng đẹp
                      btnCancel.setVisible(true);
                      btnCancel.setManaged(true);

                      if (isOpen) {
                        // Nếu đang OPEN -> Nút đỏ, cho phép bấm
                        btnCancel.setDisable(false);
                        btnCancel.setStyle(
                            "-fx-background-color: #d32f2f; -fx-text-fill: white; -fx-cursor: hand;");
                      } else {
                        // Nếu ở trạng thái khác -> Khóa nút (Disable), chuyển màu xám
                        btnCancel.setDisable(true);
                        btnCancel.setStyle(
                            "-fx-background-color: #e0e0e0; -fx-text-fill: #9e9e9e; -fx-cursor: default;");
                      }
                    } else {
                      // Nếu là BIDDER thì giấu hoàn toàn nút Hủy
                      btnCancel.setVisible(false);
                      btnCancel.setManaged(false);
                    }
                  }
                  setGraphic(actionBox);
                }
                setAlignment(javafx.geometry.Pos.CENTER);
              }
            });
  }

  private void setupStatusFilter() {
    statusFilter.setItems(
        FXCollections.observableArrayList(
            "Tất cả", "OPEN", "RUNNING", "FINISHED", "CANCELED", "PAID"));
  }

  private void startAutoRefresh() {
    stopAutoRefresh();
    autoRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(10), e -> loadAuctions()));
    autoRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
    autoRefreshTimeline.play();
  }

  private void stopAutoRefresh() {
    if (autoRefreshTimeline != null) {
      autoRefreshTimeline.stop();
      autoRefreshTimeline = null;
    }
  }

  private void startTableCountdown() {
    stopTableCountdown();
    tableCountdownTimeline =
        new Timeline(new KeyFrame(Duration.seconds(1), e -> auctionTable.refresh()));
    tableCountdownTimeline.setCycleCount(Timeline.INDEFINITE);
    tableCountdownTimeline.play();
  }

  private void stopTableCountdown() {
    if (tableCountdownTimeline != null) {
      tableCountdownTimeline.stop();
      tableCountdownTimeline = null;
    }
  }

  private void startBalancePoll() {
    stopBalancePoll();
    if (!"BIDDER".equals(SceneManager.getInstance().getCurrentRole())) {
      return;
    }
    lastKnownBalance = null;
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> resp = RestClient.get("/api/users/me");
                if (resp.statusCode() == 200) {
                  var node = MAPPER.readTree(resp.body());
                  if (node.has("balance")) {
                    BigDecimal bal = node.get("balance").decimalValue();
                    Platform.runLater(() -> lastKnownBalance = bal);
                  }
                }
              } catch (Exception ignored) {
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
                                    BigDecimal bal = node.get("balance").decimalValue();
                                    Platform.runLater(
                                        () -> {
                                          if (lastKnownBalance != null
                                              && bal.compareTo(lastKnownBalance) > 0) {
                                            BigDecimal diff = bal.subtract(lastKnownBalance);
                                            NotificationStore.getInstance()
                                                .add(
                                                    "Số dư tăng +"
                                                        + VND.format(diff)
                                                        + " — yêu cầu nạp tiền được duyệt");
                                          }
                                          lastKnownBalance = bal;
                                        });
                                  }
                                }
                              } catch (Exception e) {
                                LOGGER.debug("Balance poll lỗi: {}", e.getMessage());
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

  private void setStatus(String text) {
    statusLabel.setText(text);
    statusLabel.setVisible(true);
    statusLabel.setManaged(true);
  }
}
