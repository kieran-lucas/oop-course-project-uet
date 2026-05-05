package com.auction.ui.controller;

import com.auction.dto.AuctionResponse;
import com.auction.ui.util.Navigable;
import com.auction.ui.util.SceneManager;
import com.auction.util.RestClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.Duration;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller cho màn hình danh sách phiên đấu giá (auction-list.fxml).
 *
 * <p><b>Mục đích:</b>
 * Hiển thị danh sách tất cả phiên đấu giá, hỗ trợ tìm kiếm/lọc theo trạng thái,
 * tự động làm mới dữ liệu mỗi 10 giây. Seller thấy thêm nút "+ Tạo phiên".
 *
 * <p><b>Các phương thức chính:</b>
 * <ul>
 *   <li>{@link #onNavigatedTo()} — Load danh sách phiên từ API khi vào màn hình.</li>
 *   <li>{@link #handleSearch()} — Lọc danh sách theo từ khóa và trạng thái.</li>
 *   <li>{@link #handleRefresh()} — Tải lại dữ liệu từ server.</li>
 *   <li>{@link #handleCreateAuction()} — Chuyển sang màn hình tạo phiên (chỉ SELLER).</li>
 *   <li>{@link #handleLogout()} — Đăng xuất và quay về welcome.fxml.</li>
 * </ul>
 *
 * <p><b>Vị trí trong kiến trúc:</b>
 * AuctionListController là màn hình chính sau đăng nhập. Nó gọi {@link RestClient}
 * để lấy dữ liệu từ {@code GET /api/auctions}, hiển thị vào TableView,
 * và điều hướng đến auction-detail.fxml khi người dùng click "Vào".
 */
public class AuctionListController implements Navigable {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuctionListController.class);
  private static final ObjectMapper MAPPER = new ObjectMapper()
      .registerModule(new JavaTimeModule());
  private static final NumberFormat VND =
      NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

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
  @FXML private Button createAuctionButton;
  @FXML private Label statusLabel;

  private final ObservableList<AuctionResponse> allAuctions = FXCollections.observableArrayList();
  private Timeline autoRefreshTimeline;

  // ========== JAVAFX INITIALIZE ==========

  /** Khởi tạo TableView columns và ComboBox filter khi FXML load. */
  @FXML
  public void initialize() {
    setupColumns();
    setupStatusFilter();
  }

  // ========== NAVIGABLE LIFECYCLE ==========

  /** Cập nhật username, phân quyền UI, load danh sách, bật auto-refresh. */
  @Override
  public void onNavigatedTo() {
    SceneManager sm = SceneManager.getInstance();
    usernameLabel.setText(sm.getCurrentUsername() != null ? sm.getCurrentUsername() : "");

    // Hiển thị nút tạo phiên chỉ cho SELLER
    boolean isSeller = "SELLER".equals(sm.getCurrentRole());
    createAuctionButton.setVisible(isSeller);
    createAuctionButton.setManaged(isSeller);

    loadAuctions();
    startAutoRefresh();
  }

  /** Dừng auto-refresh khi rời màn hình. */
  @Override
  public void onNavigatedFrom() {
    stopAutoRefresh();
  }

  // ========== FXML ACTIONS ==========

  /** Lọc danh sách theo từ khóa và trạng thái đã chọn. */
  @FXML
  public void handleSearch() {
    String keyword = searchField.getText().trim().toLowerCase();
    String status = statusFilter.getValue();

    List<AuctionResponse> filtered = allAuctions.stream()
        .filter(a -> {
          boolean matchName = keyword.isEmpty()
              || (a.getItemName() != null && a.getItemName().toLowerCase().contains(keyword));
          boolean matchStatus = status == null || "Tất cả".equals(status)
              || status.equals(a.getStatus());
          return matchName && matchStatus;
        })
        .toList();

    auctionTable.setItems(FXCollections.observableArrayList(filtered));
  }

  /** Tải lại danh sách từ server. */
  @FXML
  public void handleRefresh() {
    loadAuctions();
  }

  /** Chuyển sang màn hình tạo phiên đấu giá (chỉ dành cho SELLER). */
  @FXML
  public void handleCreateAuction() {
    SceneManager.getInstance().navigateTo("create-auction.fxml");
  }

  /** Chuyển sang màn hình hồ sơ cá nhân. */
  @FXML
  public void handleProfile() {
    SceneManager.getInstance().navigateTo("profile.fxml");
  }

  /** Đăng xuất — xóa session và quay về màn hình chào. */
  @FXML
  public void handleLogout() {
    stopAutoRefresh();
    SceneManager.getInstance().logout();
  }

  // ========== DATA LOADING ==========

  /** Gọi GET /api/auctions trên luồng nền, cập nhật TableView trên FX thread. */
  public void loadAuctions() {
    setStatus("Đang tải dữ liệu...");
    Thread.ofVirtual().start(() -> {
      try {
        HttpResponse<String> response = RestClient.get("/api/auctions");
        if (response.statusCode() == 200) {
          List<AuctionResponse> list = RestClient.parseList(response.body(), AuctionResponse.class);
          Platform.runLater(() -> {
            allAuctions.setAll(list);
            handleSearch(); // re-apply filter/search hiện tại
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

  // ========== PRIVATE HELPERS ==========

  private void setupColumns() {
    itemCol.setCellValueFactory(new PropertyValueFactory<>("itemName"));
    categoryCol.setCellValueFactory(new PropertyValueFactory<>("itemCategory"));

    // Cột giá: format VND
    priceCol.setCellValueFactory(new PropertyValueFactory<>("currentPrice"));
    priceCol.setCellFactory(col -> new TableCell<>() {
      @Override
      protected void updateItem(BigDecimal price, boolean empty) {
        super.updateItem(price, empty);
        setText(empty || price == null ? null : VND.format(price));
      }
    });

    // Cột thời gian còn lại
    timeCol.setCellValueFactory(new PropertyValueFactory<>("remainingTimeMs"));
    timeCol.setCellFactory(col -> new TableCell<>() {
      @Override
      protected void updateItem(Long ms, boolean empty) {
        super.updateItem(ms, empty);
        if (empty || ms == null || ms <= 0) {
          setText(empty ? null : "Đã kết thúc");
        } else {
          long totalSec = ms / 1000;
          long h = totalSec / 3600;
          long m = (totalSec % 3600) / 60;
          long s = totalSec % 60;
          setText(String.format("%02d:%02d:%02d", h, m, s));
        }
      }
    });

    // Cột trạng thái: badge màu sắc
    statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
    statusCol.setCellFactory(col -> new TableCell<>() {
      @Override
      protected void updateItem(String status, boolean empty) {
        super.updateItem(status, empty);
        if (empty || status == null) {
          setText(null);
          setStyle("");
        } else {
          setText(status);
          String color = switch (status) {
            case "RUNNING"  -> "-fx-text-fill: #00c853; -fx-font-weight: bold;";
            case "OPEN"     -> "-fx-text-fill: #2196f3; -fx-font-weight: bold;";
            case "FINISHED" -> "-fx-text-fill: #9e9e9e;";
            case "CANCELED" -> "-fx-text-fill: #e53935;";
            case "PAID"     -> "-fx-text-fill: #7b1fa2;";
            default         -> "";
          };
          setStyle(color);
        }
      }
    });

    // Cột hành động: nút "Vào"
    actionCol.setCellFactory(col -> new TableCell<>() {
      private final Button btn = new Button("Vào");
      {
        btn.setStyle("-fx-cursor: hand;");
        btn.setOnAction(e -> {
          AuctionResponse auction = getTableView().getItems().get(getIndex());
          SceneManager.getInstance().navigateTo("auction-detail.fxml", auction.getId());
        });
      }

      @Override
      protected void updateItem(Void item, boolean empty) {
        super.updateItem(item, empty);
        setGraphic(empty ? null : btn);
      }
    });
  }

  private void setupStatusFilter() {
    statusFilter.setItems(FXCollections.observableArrayList(
        "Tất cả", "OPEN", "RUNNING", "FINISHED", "CANCELED"));
  }

  private void startAutoRefresh() {
    stopAutoRefresh();
    autoRefreshTimeline = new Timeline(
        new KeyFrame(Duration.seconds(10), e -> loadAuctions()));
    autoRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
    autoRefreshTimeline.play();
  }

  private void stopAutoRefresh() {
    if (autoRefreshTimeline != null) {
      autoRefreshTimeline.stop();
      autoRefreshTimeline = null;
    }
  }

  private void setStatus(String text) {
    statusLabel.setText(text);
  }
}