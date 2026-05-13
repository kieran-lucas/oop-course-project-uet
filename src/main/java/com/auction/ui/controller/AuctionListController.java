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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller cho màn hình danh sách phiên đấu giá (auction-list.fxml).
 *
 * <p><b>Mục đích:</b> Màn hình chính của BIDDER và SELLER sau khi đăng nhập. Hiển thị toàn bộ phiên
 * đấu giá dạng bảng, hỗ trợ lọc theo từ khóa, trạng thái và danh mục. Tự động làm mới dữ liệu mỗi
 * 10 giây và cập nhật đồng hồ đếm ngược mỗi giây.
 *
 * <p><b>Các tính năng:</b>
 *
 * <ul>
 *   <li>SELLER thấy nút "Tạo phiên" và nút Hủy (có thể dùng khi phiên ở trạng thái OPEN/RUNNING).
 *   <li>BIDDER thấy chuông thông báo với badge đếm số thông báo chưa đọc từ {@link
 *       NotificationStore}.
 *   <li>Poll số dư tài khoản mỗi 5 giây (silent fallback; nguồn chính là WebSocket).
 * </ul>
 *
 * <p><b>Các phương thức chính:</b>
 *
 * <ul>
 *   <li>{@link #onNavigatedTo()} — Load phiên, đăng ký listener thông báo, khởi động auto-refresh.
 *   <li>{@link #handleSearch()} — Lọc bảng phía client theo keyword/status/category.
 *   <li>{@link #handleBellClick()} — Mở popup danh sách thông báo và đánh dấu đã đọc.
 *   <li>{@link #loadAuctions()} — Gọi {@code GET /api/auctions} và cập nhật bảng.
 * </ul>
 *
 * <p><b>Vị trí trong kiến trúc:</b> Trung tâm điều hướng của người dùng thường — từ đây có thể vào
 * chi tiết phiên ({@link AuctionDetailController}), tạo phiên, hoặc hồ sơ cá nhân.
 */
public class AuctionListController implements Navigable {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuctionListController.class);
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());
  private static final NumberFormat VND = NumberFormat.getCurrencyInstance(Locale.of("vi", "VN"));
  private static final NumberFormat VND_AMOUNT =
      NumberFormat.getNumberInstance(Locale.of("vi", "VN"));
  private static final Pattern AUCTION_ID_PATTERN = Pattern.compile("#(\\d+)");
  private static final Pattern VND_AMOUNT_PATTERN =
      Pattern.compile("([+-]?\\s*[\\d.,]+\\s*(?:VND|VNĐ|₫|đ))", Pattern.CASE_INSENSITIVE);
  private static final Pattern NEW_BALANCE_PATTERN =
      Pattern.compile("Số dư mới:\\s*([\\d.,]+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern BALANCE_DELTA_PATTERN =
      Pattern.compile("([+-]\\s*[\\d.,]+\\s*(?:VND|VNĐ|₫|đ))", Pattern.CASE_INSENSITIVE);

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

  /** Khởi tạo cấu hình các cột bảng và bộ lọc trạng thái — gọi một lần sau khi FXML được load. */
  @FXML
  public void initialize() {
    setupColumns();
    setupStatusFilter();
  }

  // ========== NAVIGABLE LIFECYCLE ==========

  /**
   * Được gọi mỗi khi điều hướng đến màn hình này. Thực hiện:
   *
   * <ol>
   *   <li>Hiển thị username và ẩn/hiện nút tạo phiên theo role.
   *   <li>Hiển thị chuông thông báo và đăng ký listener badge cho BIDDER/SELLER.
   *   <li>Load danh sách phiên, khởi động auto-refresh, countdown và poll số dư.
   * </ol>
   */
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

  /**
   * Dừng toàn bộ Timeline (auto-refresh, countdown, poll số dư) và hủy listener thông báo để tránh
   * callback rò rỉ khi không còn ở màn hình này.
   */
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

  /**
   * Lọc bảng phía client theo ba tiêu chí kết hợp: từ khóa tên sản phẩm, trạng thái phiên, và danh
   * mục sản phẩm. Không gọi lại server — lọc trực tiếp trên {@code allAuctions}.
   */
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

  /** Tải lại danh sách phiên từ server và reset bộ lọc theo kết quả mới. */
  @FXML
  public void handleRefresh() {
    loadAuctions();
  }

  /** Chuyển sang màn hình tạo phiên đấu giá (chỉ SELLER). */
  @FXML
  public void handleCreateAuction() {
    SceneManager.getInstance().navigateTo("create-auction.fxml");
  }

  /** Chuyển sang màn hình hồ sơ cá nhân. */
  @FXML
  public void handleProfile() {
    SceneManager.getInstance().navigateTo("profile.fxml");
  }

  /** Dừng auto-refresh và đăng xuất về màn hình chào mừng. */
  @FXML
  public void handleLogout() {
    stopAutoRefresh();
    SceneManager.getInstance().logout();
  }

  // ========== DATA LOADING ==========

  /**
   * Tải toàn bộ phiên đấu giá từ {@code GET /api/auctions} trên luồng nền. Sau khi nhận kết quả:
   * cập nhật {@code allAuctions}, làm mới bộ lọc danh mục, áp lại bộ lọc hiện tại và cập nhật
   * statusLabel với tổng số phiên.
   */
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

  /**
   * Xử lý click chuông thông báo: đánh dấu tất cả thông báo đã đọc, cập nhật badge về 0, rồi mở
   * Popup hiển thị tối đa 50 thông báo gần nhất. Popup tự đóng khi click ra ngoài.
   */
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
    ScrollPane scrollPane = null;
    if (notifications.isEmpty()) {
      Label empty = new Label("Chưa có thông báo nào.");
      empty.setStyle("-fx-text-fill: #78909c; -fx-font-size: 13px; -fx-padding: 4 0 4 0;");
      content.getChildren().add(empty);
    } else {
      VBox items = new VBox(4);
      int limit = Math.min(notifications.size(), 50);
      // Hien thi tu cu nhat (index 0) den moi nhat (index limit-1)
      // VBox se co: [cu nhat o tren, moi nhat o duoi] -> setVvalue(1.0) cuon xuong day = thay moi
      // nhat
      BigDecimal previousBalance = findPreviousBalanceBefore(notifications, limit);
      for (int i = 0; i < limit; i++) {
        NotificationRow row = buildNotificationRow(notifications.get(i), previousBalance);
        items.getChildren().add(row.node());
        if (row.newBalance() != null) {
          previousBalance = row.newBalance();
        }
      }
      scrollPane = new ScrollPane(items);
      scrollPane.setMaxHeight(400);
      scrollPane.setFitToWidth(true);
      scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
      scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
      scrollPane.setStyle(
          "-fx-background-color: transparent; "
              + "-fx-background: transparent; "
              + "-fx-control-inner-background: transparent;");
      content.getChildren().add(scrollPane);
    }

    popup.getContent().add(content);
    Stage stage = (Stage) bellButton.getScene().getWindow();
    javafx.geometry.Bounds bounds = bellButton.localToScreen(bellButton.getBoundsInLocal());
    popup.show(
        stage, bounds.getMinX() - content.getPrefWidth() + bounds.getWidth(), bounds.getMaxY() + 6);
    ScrollPane shownScrollPane = scrollPane;
    if (shownScrollPane != null) {
      Platform.runLater(() -> shownScrollPane.setVvalue(1.0));
    }
  }

  // ========== PRIVATE HELPERS ==========

  /**
   * Cập nhật badge số thông báo chưa đọc trên icon chuông. Ẩn badge khi không có thông báo mới;
   * hiển thị tối đa "99+" để tránh tràn layout.
   */
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
      badgeLabel.setManaged(false);
    }
  }

  private NotificationRow buildNotificationRow(String notification, BigDecimal previousBalance) {
    BalanceDisplay balance = toBalanceDisplay(notification, previousBalance);
    if (balance != null) {
      return new NotificationRow(
          createNotificationLabel(balance.text(), balance.color()), balance.newBalance());
    }

    String text = replaceAuctionIdWithName(notification);
    PriceSplit priceSplit = splitCurrentPrice(text);
    if (priceSplit != null) {
      return new NotificationRow(createPriceNotificationNode(priceSplit), null);
    }
    return new NotificationRow(createNotificationLabel(text, "#e0e0e0"), null);
  }

  private Label createNotificationLabel(String text, String color) {
    Label item = new Label(text);
    item.setWrapText(true);
    item.setMaxWidth(Double.MAX_VALUE);
    item.setPadding(new Insets(7, 10, 7, 10));
    item.setStyle(
        "-fx-text-fill: "
            + color
            + "; -fx-font-size: 12px; "
            + "-fx-background-color: rgba(255,255,255,0.06); "
            + "-fx-background-radius: 6;");
    return item;
  }

  private Node createPriceNotificationNode(PriceSplit split) {
    HBox item = new HBox(4);
    item.setMaxWidth(Double.MAX_VALUE);
    item.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
    item.setPadding(new Insets(7, 10, 7, 10));
    item.setStyle("-fx-background-color: rgba(255,255,255,0.06); " + "-fx-background-radius: 6;");
    Label beforeLabel = createInlineNotificationLabel(split.before(), "#e0e0e0");
    beforeLabel.setWrapText(true);
    Label priceLabel = createInlineNotificationLabel(split.price(), "#ffd600");
    item.getChildren().add(beforeLabel);
    item.getChildren().add(priceLabel);
    if (!split.after().isBlank()) {
      item.getChildren().add(createInlineNotificationLabel(split.after(), "#e0e0e0"));
    }
    HBox.setHgrow(beforeLabel, javafx.scene.layout.Priority.ALWAYS);
    return item;
  }

  private Label createInlineNotificationLabel(String text, String color) {
    Label label = new Label(text);
    label.setWrapText(false);
    label.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
    label.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px;");
    return label;
  }

  private String replaceAuctionIdWithName(String notification) {
    Matcher matcher = AUCTION_ID_PATTERN.matcher(notification);
    if (!matcher.find()) {
      return notification;
    }
    Long auctionId = Long.valueOf(matcher.group(1));
    String displayName = resolveAuctionDisplayName(auctionId);
    String replacement = "tại phiên " + (displayName != null ? displayName : "#" + auctionId);
    return notification.replaceFirst(
        "(?:tại\\s+)?(?:phiên\\s+)?#" + auctionId, Matcher.quoteReplacement(replacement));
  }

  private String resolveAuctionDisplayName(Long auctionId) {
    return allAuctions.stream()
        .filter(a -> auctionId.equals(a.getId()))
        .map(AuctionResponse::getItemName)
        .filter(name -> name != null && !name.isBlank())
        .findFirst()
        .orElse(null);
  }

  private PriceSplit splitCurrentPrice(String text) {
    String lower = text.toLowerCase(Locale.ROOT);
    if (!lower.contains("giá") && !lower.contains("bid") && !lower.contains("price")) {
      return null;
    }
    Matcher matcher = VND_AMOUNT_PATTERN.matcher(text);
    if (!matcher.find()) {
      return null;
    }
    // Normalize ky hieu tien te: doi "đ" / "₫" thanh "VND"
    String priceText = matcher.group(1).replaceAll("[₫đĐ]", "").trim();
    if (!priceText.toUpperCase().contains("VND")) {
      priceText = priceText + " VND";
    }
    return new PriceSplit(
        text.substring(0, matcher.start()), priceText, text.substring(matcher.end()));
  }

  private BalanceDisplay toBalanceDisplay(String notification, BigDecimal previousBalance) {
    Matcher newBalanceMatcher = NEW_BALANCE_PATTERN.matcher(notification);
    if (newBalanceMatcher.find()) {
      BigDecimal newBalance = parseAmount(newBalanceMatcher.group(1));
      BigDecimal delta =
          previousBalance != null ? newBalance.subtract(previousBalance) : newBalance;
      // Giu lai phan chu truoc "So du moi:" lam prefix (VD: "Yeu cau nap tien da duoc duyet")
      String rawPrefix = notification.substring(0, newBalanceMatcher.start()).trim();
      String prefix = rawPrefix.replaceAll("(?i)[.,\\s]*S\u1ed1 d\u01b0 m\u1edbi:\\s*$", "").trim();
      String deltaText = prefix.isEmpty() ? formatDelta(delta) : prefix + ": " + formatDelta(delta);
      return new BalanceDisplay(deltaText, delta.signum() >= 0 ? "#4caf50" : "#ef5350", newBalance);
    }

    String lower = notification.toLowerCase(Locale.ROOT);
    if (lower.contains("số dư") || lower.contains("so du") || lower.contains("balance")) {
      Matcher deltaMatcher = BALANCE_DELTA_PATTERN.matcher(notification);
      if (deltaMatcher.find()) {
        BigDecimal delta = parseAmount(deltaMatcher.group(1));
        return new BalanceDisplay(
            formatDelta(delta), delta.signum() >= 0 ? "#4caf50" : "#ef5350", null);
      }
    }
    return null;
  }

  private BigDecimal findPreviousBalanceBefore(
      ObservableList<String> notifications, int displayedLimit) {
    for (int i = notifications.size() - 1; i >= displayedLimit; i--) {
      BigDecimal balance = parseNewBalance(notifications.get(i));
      if (balance != null) {
        return balance;
      }
    }
    return null;
  }

  private BigDecimal parseNewBalance(String notification) {
    Matcher matcher = NEW_BALANCE_PATTERN.matcher(notification);
    return matcher.find() ? parseAmount(matcher.group(1)) : null;
  }

  private BigDecimal parseAmount(String text) {
    String normalized = text.replaceAll("[^0-9-]", "");
    if (normalized.isBlank() || "-".equals(normalized)) {
      return BigDecimal.ZERO;
    }
    return new BigDecimal(normalized);
  }

  private String formatDelta(BigDecimal delta) {
    String sign = delta.signum() >= 0 ? "+ " : "- ";
    return sign + VND_AMOUNT.format(delta.abs()) + " VND";
  }

  private record NotificationRow(Node node, BigDecimal newBalance) {}

  private record BalanceDisplay(String text, String color, BigDecimal newBalance) {}

  private record PriceSplit(String before, String price, String after) {}

  /**
   * Cập nhật danh sách tùy chọn của bộ lọc danh mục dựa trên dữ liệu phiên hiện tại. Luôn giữ các
   * danh mục cố định (ART, ELECTRONICS, VEHICLE) và thêm các danh mục động khác nếu có. Bảo toàn
   * lựa chọn hiện tại của người dùng nếu vẫn còn hợp lệ.
   */
  private void updateCategoryFilter(List<AuctionResponse> auctions) {
    if (categoryFilter == null) {
      return;
    }
    String current = categoryFilter.getValue();
    List<String> categories = new ArrayList<>();
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

  /**
   * Cấu hình các cột bảng phiên đấu giá: tên sản phẩm, danh mục, giá hiện tại (VND), thời gian còn
   * lại (đồng hồ đếm ngược hoặc "Đã kết thúc"), màu trạng thái badge, và cột hành động (nút Xem +
   * nút Hủy có điều kiện theo role/trạng thái phiên).
   */
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
                setText(empty || price == null ? null : VND_AMOUNT.format(price) + " VND");
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
              private final Button btnEnter = new Button("Xem");
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
                      Thread.ofVirtual()
                          .start(
                              () -> {
                                try {
                                  HttpResponse<String> response =
                                      RestClient.delete("/api/auctions/" + auction.getId());
                                  Platform.runLater(
                                      () -> {
                                        if (response.statusCode() == 204
                                            || response.statusCode() == 200) {
                                          loadAuctions();
                                        } else {
                                          setStatus("Hủy phiên thất bại: " + response.statusCode());
                                        }
                                      });
                                } catch (Exception ex) {
                                  LOGGER.error("Lỗi hủy phiên {}", auction.getId(), ex);
                                  Platform.runLater(
                                      () -> setStatus("Không thể kết nối đến server."));
                                }
                              });
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
                    String st = auction.getStatus();
                    boolean isCancelable = "OPEN".equals(st) || "RUNNING".equals(st);

                    // LOGIC GIAO DIỆN MỚI
                    if (isSeller) {
                      // Luôn hiện nút Hủy cho Seller để giữ form bảng đẹp
                      btnCancel.setVisible(true);
                      btnCancel.setManaged(true);

                      if (isCancelable) {
                        // OPEN hoặc RUNNING -> Nút đỏ, cho phép bấm
                        btnCancel.setDisable(false);
                        btnCancel.setStyle(
                            "-fx-background-color: #d32f2f; -fx-text-fill: white; -fx-cursor: hand;");
                      } else {
                        // Đã kết thúc/hủy -> Khóa nút (Disable), chuyển màu xám
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

  /** Khởi tạo danh sách tùy chọn cho bộ lọc trạng thái phiên đấu giá. */
  private void setupStatusFilter() {
    statusFilter.setItems(
        FXCollections.observableArrayList(
            "Tất cả", "OPEN", "RUNNING", "FINISHED", "CANCELED", "PAID"));
  }

  /**
   * Khởi động Timeline tự động tải lại danh sách phiên mỗi 10 giây. Gọi {@link #stopAutoRefresh()}
   * trước để tránh nhiều Timeline chạy song song.
   */
  private void startAutoRefresh() {
    stopAutoRefresh();
    autoRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(10), e -> loadAuctions()));
    autoRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
    autoRefreshTimeline.play();
  }

  /** Dừng và hủy Timeline auto-refresh nếu đang chạy. */
  private void stopAutoRefresh() {
    if (autoRefreshTimeline != null) {
      autoRefreshTimeline.stop();
      autoRefreshTimeline = null;
    }
  }

  /**
   * Khởi động Timeline gọi {@code auctionTable.refresh()} mỗi giây để cột thời gian còn lại tự tính
   * lại từ {@code endTime} mà không cần request server.
   */
  private void startTableCountdown() {
    stopTableCountdown();
    tableCountdownTimeline =
        new Timeline(new KeyFrame(Duration.seconds(1), e -> auctionTable.refresh()));
    tableCountdownTimeline.setCycleCount(Timeline.INDEFINITE);
    tableCountdownTimeline.play();
  }

  /** Dừng và hủy Timeline refresh bảng nếu đang chạy. */
  private void stopTableCountdown() {
    if (tableCountdownTimeline != null) {
      tableCountdownTimeline.stop();
      tableCountdownTimeline = null;
    }
  }

  /**
   * Khởi động poll số dư tài khoản mỗi 5 giây — chỉ dành cho BIDDER. Số dư được lưu vào {@code
   * lastKnownBalance} để làm fallback kiểm tra đủ tiền trước khi bid. Không thêm thông báo vào
   * NotificationStore; nguồn chính là UserBalanceWatcher (WebSocket).
   */
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
                                          // FIX Bug 1: KHÔNG add vào NotificationStore ở đây.
                                          // UserBalanceWatcher (WebSocket) là nguồn DUY NHẤT
                                          // add notification deposit. Poll chỉ track balance
                                          // thầm lặng làm fallback.
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

  /** Dừng và hủy Timeline poll số dư nếu đang chạy. */
  private void stopBalancePoll() {
    if (balancePollTimeline != null) {
      balancePollTimeline.stop();
      balancePollTimeline = null;
    }
  }

  /** Hiển thị thông báo trạng thái ở thanh dưới bảng (số phiên, lỗi kết nối, v.v.). */
  private void setStatus(String text) {
    statusLabel.setText(text);
    statusLabel.setVisible(true);
    statusLabel.setManaged(true);
  }
}
