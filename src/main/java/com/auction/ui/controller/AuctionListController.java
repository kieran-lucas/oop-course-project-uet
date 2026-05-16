package com.auction.ui.controller;

import com.auction.dto.AuctionResponse;
import com.auction.ui.util.Navigable;
import com.auction.ui.util.SceneManager;
import com.auction.util.NotificationItem;
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
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
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
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Popup;
import javafx.stage.Screen;
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
 *   <li>SELLER thấy nút "Tạo phiên" để mở màn hình tạo phiên mới.
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
  private static final NumberFormat VND_AMOUNT =
      NumberFormat.getNumberInstance(Locale.of("vi", "VN"));
  private static final Pattern AUCTION_ID_PATTERN = Pattern.compile("#(\\d+)");
  private static final Pattern VND_AMOUNT_PATTERN =
      Pattern.compile("([+-]?\\s*[\\d.,]+\\s*(?:VND|VNĐ|₫|đ))", Pattern.CASE_INSENSITIVE);
  private static final Pattern NEW_BALANCE_PATTERN =
      Pattern.compile(
          "S\u1ed1 d\u01b0 (?:m\u1edbi|bi\u1ebfn \u0111\u1ed9ng):\\s*([\\d.,]+)",
          Pattern.CASE_INSENSITIVE);
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

  /**
   * Ticked once per second by {@link #tableCountdownTimeline}. The time + status cells listen to
   * this property and redraw only themselves on each tick, instead of relying on {@code
   * auctionTable.refresh()} which rebuilt every cell and made hovered rows flash.
   */
  private final IntegerProperty timerTick = new SimpleIntegerProperty(0);

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
    // markAllRead calls PATCH /api/notifications/mark-all-read async, then updates local state.
    // Badge update is driven by the unreadCountProperty listener registered in onNavigatedTo().
    store.markAllRead();

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

    ObservableList<NotificationItem> notifications = store.getNotifications();
    ScrollPane scrollPane = null;
    if (notifications.isEmpty()) {
      Label empty = new Label("Chưa có thông báo nào.");
      empty.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 13px; -fx-padding: 4 0 4 0;");
      content.getChildren().add(empty);
    } else {
      VBox items = new VBox(4);
      int limit = Math.min(notifications.size(), 50);
      // NotificationStore: index 0 = moi nhat, hien thi moi nhat o tren cung.
      for (int i = 0; i < limit; i++) {
        items.getChildren().add(buildNotificationRow(notifications.get(i).getMessage()));
      }
      scrollPane = new ScrollPane(items);
      // Always reserve a tall viewport so the popup doesn't collapse to a single-row height
      // when the user only has 1–2 notifications. Cap at ~1/3 of screen height so the popup
      // never overflows the visible area.
      double notifPopupHeight = Screen.getPrimary().getVisualBounds().getHeight() / 3.0;
      scrollPane.setPrefViewportHeight(notifPopupHeight);
      scrollPane.setMaxHeight(notifPopupHeight);
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
      Platform.runLater(() -> shownScrollPane.setVvalue(0.0));
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

  // Notification palette ─ chosen against the bell popup's dark navy background
  //  USER_COLOR  → light sky-blue, clearly reads as "blue"
  //  AUCTION_COLOR → warm tan/brown, clearly reads as "nâu"
  //  PRICE_COLOR → existing yellow highlight for amounts
  //  DEFAULT_COLOR → near-white body text
  private static final String USER_COLOR = "#60A5FA";
  private static final String AUCTION_COLOR = "#E0A458";
  private static final String PRICE_COLOR = "#ffd600";
  private static final String DEFAULT_COLOR = "#e0e0e0";

  private Node buildNotificationRow(String notification) {
    BalanceDisplay balance = toBalanceDisplay(notification);
    if (balance != null) {
      return createBalanceNotificationNode(balance);
    }
    String text = replaceAuctionIdWithName(notification);
    return createGenericFlowNode(text);
  }

  /**
   * General-purpose notification renderer. Splits the message into colored segments:
   *
   * <ul>
   *   <li>{@code «name»} → blue (username)
   *   <li>{@code [item]} → brown (auction display name)
   *   <li>price amounts → yellow bold
   *   <li>remainder → default near-white
   * </ul>
   */
  private Node createGenericFlowNode(String text) {
    TextFlow flow = new TextFlow();
    flow.setMaxWidth(Double.MAX_VALUE);
    flow.setPadding(new Insets(7, 10, 7, 10));
    flow.setStyle("-fx-background-color: rgba(255,255,255,0.06); -fx-background-radius: 6;");
    appendColoredSegments(flow, text, DEFAULT_COLOR);
    return flow;
  }

  /**
   * Tao node thong bao bien dong so du: dong chu mo ta mau trang, dong so tien co mau tuong ung.
   * prefix (VD: "Yeu cau nap tien da duoc duyet") mau trang, delta (VD: "+ 435.345 VND") co mau.
   */
  private Node createBalanceNotificationNode(BalanceDisplay balance) {
    String[] parts = balance.text().split("\n", 2);
    VBox box = new VBox(2);
    box.setMaxWidth(Double.MAX_VALUE);
    box.setPadding(new Insets(7, 10, 7, 10));
    box.setStyle("-fx-background-color: rgba(255,255,255,0.06); -fx-background-radius: 6;");
    if (parts.length == 2 && !parts[0].isBlank()) {
      // Dong 1: prefix mo ta — TextFlow co mau de phan ten user/phien noi bat.
      TextFlow descFlow = new TextFlow();
      descFlow.setMaxWidth(Double.MAX_VALUE);
      appendColoredSegments(descFlow, parts[0], DEFAULT_COLOR);
      box.getChildren().add(descFlow);
      // Dong 2: "So du bien dong: " trang + "+ 50.000.000 VND" co mau
      // Tach tai dau "+" hoac "-" de lay phan label va phan so
      int signIdx = -1;
      for (int ci = 0; ci < parts[1].length(); ci++) {
        char ch = parts[1].charAt(ci);
        if (ch == '+' || ch == '-') {
          signIdx = ci;
          break;
        }
      }
      HBox amountRow = new HBox(0);
      amountRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
      if (signIdx > 0) {
        Label labelPart =
            new Label(
                withTrailingSpaceIfNeeded(
                    parts[1].substring(0, signIdx), parts[1].substring(signIdx)));
        labelPart.setStyle("-fx-text-fill: " + DEFAULT_COLOR + "; -fx-font-size: 12px;");
        Label amountPart = new Label(parts[1].substring(signIdx));
        amountPart.setWrapText(false);
        amountPart.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        amountPart.setStyle(
            "-fx-text-fill: " + balance.color() + "; -fx-font-size: 12px; -fx-font-weight: bold;");
        amountRow.getChildren().addAll(labelPart, amountPart);
      } else {
        Label amountPart = new Label(parts[1]);
        amountPart.setWrapText(false);
        amountPart.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        amountPart.setStyle(
            "-fx-text-fill: " + balance.color() + "; -fx-font-size: 12px; -fx-font-weight: bold;");
        amountRow.getChildren().add(amountPart);
      }
      box.getChildren().add(amountRow);
    } else {
      // Chi co so tien, khong co prefix
      Label amountLabel = new Label(balance.text());
      amountLabel.setWrapText(false);
      amountLabel.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
      amountLabel.setStyle(
          "-fx-text-fill: " + balance.color() + "; -fx-font-size: 12px; -fx-font-weight: bold;");
      box.getChildren().add(amountLabel);
    }
    return box;
  }

  /**
   * Scan {@code text} for {@code «name»} / {@code [item]} / price markers and append
   * correspondingly coloured {@link Text} nodes to {@code flow}. Unrecognised stretches use {@code
   * defaultColor}.
   *
   * <p>Markers are stripped from the visible output — only the inner content is rendered (e.g.
   * {@code «alice»} → "alice" in blue). That keeps the notification readable while still letting
   * the server tag entities for colour-coding.
   */
  private void appendColoredSegments(TextFlow flow, String text, String defaultColor) {
    if (text == null || text.isEmpty()) {
      return;
    }
    int i = 0;
    int n = text.length();
    StringBuilder buf = new StringBuilder();
    while (i < n) {
      char c = text.charAt(i);
      if (c == '«') {
        int end = text.indexOf('»', i + 1);
        if (end > i) {
          flushDefault(flow, buf, defaultColor);
          flow.getChildren()
              .add(createNotificationText(text.substring(i + 1, end), USER_COLOR, true));
          i = end + 1;
          continue;
        }
      } else if (c == '[') {
        int end = text.indexOf(']', i + 1);
        if (end > i) {
          flushDefault(flow, buf, defaultColor);
          flow.getChildren()
              .add(createNotificationText(text.substring(i + 1, end), AUCTION_COLOR, true));
          i = end + 1;
          continue;
        }
      }
      buf.append(c);
      i++;
    }
    flushDefault(flow, buf, defaultColor);
  }

  /**
   * Drain {@code buf} into {@code flow}. The drained text is further split so any VND price inside
   * it gets yellow highlighting — the rest stays at {@code defaultColor}.
   */
  private void flushDefault(TextFlow flow, StringBuilder buf, String defaultColor) {
    if (buf.length() == 0) {
      return;
    }
    String segment = buf.toString();
    buf.setLength(0);
    Matcher priceMatcher = VND_AMOUNT_PATTERN.matcher(segment);
    int last = 0;
    while (priceMatcher.find()) {
      if (priceMatcher.start() > last) {
        flow.getChildren()
            .add(
                createNotificationText(
                    segment.substring(last, priceMatcher.start()), defaultColor, false));
      }
      String priceText =
          priceMatcher.group(1).replaceAll("(?i)\\s*(?:VN\\s*Đ|VNĐ|VND|₫|đ)\\s*$", "").trim()
              + " VND";
      flow.getChildren().add(createNotificationText(priceText, PRICE_COLOR, true));
      last = priceMatcher.end();
    }
    if (last < segment.length()) {
      flow.getChildren().add(createNotificationText(segment.substring(last), defaultColor, false));
    }
  }

  private Text createNotificationText(String text, String color, boolean bold) {
    Text node = new Text(text);
    node.setStyle(
        "-fx-fill: " + color + "; -fx-font-size: 12px;" + (bold ? " -fx-font-weight: bold;" : ""));
    return node;
  }

  private String withTrailingSpaceIfNeeded(String text, String after) {
    if (text == null || text.isBlank() || after == null || after.isBlank()) {
      return text;
    }
    return Character.isWhitespace(after.charAt(0)) ? text : text + " ";
  }

  private String replaceAuctionIdWithName(String notification) {
    Matcher matcher = AUCTION_ID_PATTERN.matcher(notification);
    if (!matcher.find()) {
      return notification;
    }
    Long auctionId = Long.valueOf(matcher.group(1));
    String displayName = resolveAuctionDisplayName(auctionId);
    String replacement =
        "tại phiên [" + (displayName != null ? displayName : "#" + auctionId) + "]";
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

  private BalanceDisplay toBalanceDisplay(String notification) {
    String lower = notification.toLowerCase(Locale.ROOT);
    boolean isBalanceNotification =
        lower.contains("số dư biến động")
            || lower.contains("so du bien dong")
            || lower.contains("balance");
    if (!isBalanceNotification) {
      return null;
    }

    Matcher deltaMatcher = BALANCE_DELTA_PATTERN.matcher(notification);
    if (deltaMatcher.find()) {
      BigDecimal delta = parseAmount(deltaMatcher.group(1));
      String rawPrefix = notification.substring(0, deltaMatcher.start()).trim();
      String prefix =
          rawPrefix
              .replaceAll("(?i)[.,\\s]*S\u1ed1 d\u01b0 bi\u1ebfn \u0111\u1ed9ng:\\s*$", "")
              .trim();
      prefix = prefix.replaceAll("[.,;:]+$", "").trim();
      // \u0110\u1ed5i "#5" trong ph\u1ea7n m\u00f4 t\u1ea3 th\u00e0nh "[T\u00ean s\u1ea3n
      // ph\u1ea9m]" cho th\u00f4ng b\u00e1o settlement/payout
      if (!prefix.isEmpty()) {
        prefix = replaceAuctionIdWithName(prefix);
      }
      String deltaLine = "S\u1ed1 d\u01b0 bi\u1ebfn \u0111\u1ed9ng: " + formatDelta(delta);
      String deltaText = prefix.isEmpty() ? deltaLine : prefix + "\n" + deltaLine;
      return new BalanceDisplay(deltaText, delta.signum() >= 0 ? "#22C55E" : "#EF4444");
    }

    Matcher newBalanceMatcher = NEW_BALANCE_PATTERN.matcher(notification);
    if (newBalanceMatcher.find()) {
      return null;
    }

    String lowerAgain = notification.toLowerCase(Locale.ROOT);
    if (lowerAgain.contains("số dư")
        || lowerAgain.contains("so du")
        || lowerAgain.contains("balance")) {
      Matcher fallbackDeltaMatcher = BALANCE_DELTA_PATTERN.matcher(notification);
      if (fallbackDeltaMatcher.find()) {
        BigDecimal delta = parseAmount(fallbackDeltaMatcher.group(1));
        return new BalanceDisplay(formatDelta(delta), delta.signum() >= 0 ? "#22C55E" : "#EF4444");
      }
    }
    return null;
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

  private record BalanceDisplay(String text, String color) {}

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
   * lại (đồng hồ đếm ngược hoặc "Đã kết thúc"), màu trạng thái badge, và cột hành động (chỉ chứa
   * nút "Xem" — không còn nút Hủy phiên cho Seller).
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
              // Held strongly so the WeakChangeListener attached to timerTick isn't GC'd before
              // this cell is. When the cell itself becomes unreferenced, the weak wrapper lets
              // timerTick release the listener and the cell.
              private final ChangeListener<Number> tickListener = (obs, oldV, newV) -> redraw();

              {
                setWrapText(true);
                setAlignment(javafx.geometry.Pos.CENTER);
                timerTick.addListener(new WeakChangeListener<>(tickListener));
              }

              @Override
              protected void updateItem(Long ignored, boolean empty) {
                super.updateItem(ignored, empty);
                redraw();
              }

              private void redraw() {
                // Tooltip is only meaningful while the row is OPEN; clear at the start of every
                // redraw so a stale "Bắt đầu sau: ..." doesn't follow the row into RUNNING/ENDED.
                setTooltip(null);
                if (isEmpty() || getTableRow() == null) {
                  setText(null);
                  setStyle("");
                  return;
                }
                AuctionResponse a = getTableRow().getItem();
                if (a == null) {
                  setText(null);
                  setStyle("");
                  return;
                }
                String st = computeClientStatus(a);
                if ("FINISHED".equals(st) || "CANCELED".equals(st) || "PAID".equals(st)) {
                  setText("Đã kết thúc");
                  setStyle("");
                  return;
                }
                setStyle("");
                LocalDateTime now = LocalDateTime.now();
                if ("OPEN".equals(st)) {
                  // Phien chua bat dau: hien thi thoi gian den khi bat dau, format hai dong de
                  // dong bo voi card "BAT DAU SAU / HH:MM:SS" ben man hinh chi tiet.
                  LocalDateTime startTime = a.getStartTime();
                  if (startTime == null) {
                    setText("—");
                    return;
                  }
                  long ms = java.time.Duration.between(now, startTime).toMillis();
                  if (ms <= 0) {
                    setText("Sắp bắt đầu");
                  } else {
                    long totalSec = ms / 1000;
                    long h = totalSec / 3600;
                    long m = (totalSec % 3600) / 60;
                    long s = totalSec % 60;
                    String timeStr = String.format("%02d:%02d:%02d", h, m, s);
                    setText("Bắt đầu sau:\n" + timeStr);
                    setTooltip(new javafx.scene.control.Tooltip("Bắt đầu sau: " + timeStr));
                  }
                } else {
                  // RUNNING: hien thi thoi gian den khi ket thuc
                  LocalDateTime endTime = a.getEndTime();
                  if (endTime == null) {
                    setText("—");
                    return;
                  }
                  long ms = java.time.Duration.between(now, endTime).toMillis();
                  if (ms <= 0) {
                    setText("Đã kết thúc");
                    setStyle("");
                  } else {
                    long totalSec = ms / 1000;
                    long h = totalSec / 3600;
                    long m = (totalSec % 3600) / 60;
                    long s = totalSec % 60;
                    setText(String.format("%02d:%02d:%02d", h, m, s));
                  }
                }
              }
            });

    statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
    statusCol.setCellFactory(
        col ->
            new TableCell<>() {
              private final ChangeListener<Number> tickListener = (obs, oldV, newV) -> redraw();

              {
                timerTick.addListener(new WeakChangeListener<>(tickListener));
              }

              @Override
              protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                redraw();
              }

              private void redraw() {
                if (isEmpty() || getTableRow() == null || getTableRow().getItem() == null) {
                  setText(null);
                  setStyle("");
                  return;
                }
                // Tinh status tu thoi gian phia client de hien thi luc tuc
                String effective = computeClientStatus(getTableRow().getItem());
                setText(effective);
                String color =
                    switch (effective) {
                      case "RUNNING" -> "-fx-text-fill: #16A34A; -fx-font-weight: bold;";
                      case "OPEN" -> "-fx-text-fill: #1565C0; -fx-font-weight: bold;";
                      case "FINISHED", "PAID" -> "-fx-text-fill: #64748B;";
                      case "CANCELED" -> "-fx-text-fill: #DC2626;";
                      default -> "";
                    };
                setStyle(color);
              }
            });
    actionCol.setMinWidth(80);
    actionCol.setCellFactory(
        col ->
            new TableCell<>() {
              private final Button btnEnter = new Button("Xem");

              {
                btnEnter.getStyleClass().add("table-action-view");
                btnEnter.setMinWidth(60);
                btnEnter.setOnAction(
                    e -> {
                      AuctionResponse auction = getTableView().getItems().get(getIndex());
                      SceneManager.getInstance().navigateTo("auction-detail.fxml", auction.getId());
                    });
              }

              @Override
              protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btnEnter);
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
    // Increment timerTick instead of calling auctionTable.refresh(): refresh() rebuilds every
    // cell every second, which interrupts the :hover pseudo-class on the row under the cursor
    // and produces the visible flash. Time / status cells listen to timerTick and redraw only
    // themselves, leaving every other cell (including the hovered row's background) untouched.
    tableCountdownTimeline =
        new Timeline(new KeyFrame(Duration.seconds(1), e -> timerTick.set(timerTick.get() + 1)));
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
  /**
   * Tinh trang thai phien dua tren thoi gian hien tai phia client. Neu server tra ve
   * FINISHED/CANCELED/PAID thi giu nguyen, khong tinh lai. OPEN: startTime > now (chua bat dau)
   * RUNNING: startTime <= now < endTime FINISHED: endTime <= now
   */
  private static String computeClientStatus(AuctionResponse a) {
    if (a == null) {
      return "OPEN";
    }
    String serverStatus = a.getStatus();
    // Cac trang thai ket thuc khong tinh lai
    if ("FINISHED".equals(serverStatus)
        || "CANCELED".equals(serverStatus)
        || "PAID".equals(serverStatus)
        || "SETTLING".equals(serverStatus)) {
      return serverStatus;
    }
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime start = a.getStartTime();
    LocalDateTime end = a.getEndTime();
    if (start != null && now.isBefore(start)) {
      return "OPEN";
    }
    if (end != null && !now.isBefore(end)) {
      return "FINISHED";
    }
    return "RUNNING";
  }

  private void setStatus(String text) {
    statusLabel.setText(text);
    statusLabel.setVisible(true);
    statusLabel.setManaged(true);
  }
}
