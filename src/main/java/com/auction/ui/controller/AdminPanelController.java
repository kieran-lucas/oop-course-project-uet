package com.auction.ui.controller;

import com.auction.dto.AuctionResponse;
import com.auction.ui.util.Navigable;
import com.auction.ui.util.SceneManager;
import com.auction.util.RestClient;
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
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller cho màn hình quản trị (admin-panel.fxml).
 *
 * <p><b>Mục đích:</b>
 * Cung cấp giao diện quản trị cho ADMIN: xem tất cả phiên đấu giá,
 * tìm kiếm, và hủy phiên (DELETE /api/auctions/{id}).
 *
 * <p><b>Các phương thức chính:</b>
 * <ul>
 *   <li>{@link #onNavigatedTo()} — Load tất cả phiên từ API khi vào màn hình.</li>
 *   <li>{@link #handleSearch()} — Lọc phiên theo từ khóa.</li>
 *   <li>{@link #handleRefresh()} — Tải lại dữ liệu từ server.</li>
 *   <li>{@link #handleLogout()} — Đăng xuất về welcome.fxml.</li>
 * </ul>
 *
 * <p><b>Vị trí trong kiến trúc:</b>
 * AdminPanelController là màn hình chính của ADMIN sau đăng nhập.
 * Chỉ có thể vào được khi role = "ADMIN" (kiểm tra trong LoginController).
 */
public class AdminPanelController implements Navigable {

  private static final Logger LOGGER = LoggerFactory.getLogger(AdminPanelController.class);
  private static final NumberFormat VND = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

  @FXML private Label usernameLabel;
  @FXML private TableView<AuctionResponse> auctionTable;
  @FXML private TableColumn<AuctionResponse, Long> idCol;
  @FXML private TableColumn<AuctionResponse, String> itemCol;
  @FXML private TableColumn<AuctionResponse, String> statusCol;
  @FXML private TableColumn<AuctionResponse, BigDecimal> priceCol;
  @FXML private TableColumn<AuctionResponse, Void> actionCol;
  @FXML private TextField searchField;
  @FXML private Label statusLabel;

  private final ObservableList<AuctionResponse> allAuctions = FXCollections.observableArrayList();

  @FXML
  public void initialize() {
    setupColumns();
  }

  // ========== NAVIGABLE LIFECYCLE ==========

  @Override
  public void onNavigatedTo() {
    SceneManager sm = SceneManager.getInstance();
    usernameLabel.setText(sm.getCurrentUsername() != null
        ? sm.getCurrentUsername() + " (ADMIN)" : "ADMIN");
    loadAuctions();
  }

  // ========== FXML ACTIONS ==========

  @FXML
  public void handleSearch() {
    String keyword = searchField.getText().trim().toLowerCase();
    List<AuctionResponse> filtered = allAuctions.stream()
        .filter(a -> keyword.isEmpty()
            || (a.getItemName() != null && a.getItemName().toLowerCase().contains(keyword))
            || String.valueOf(a.getId()).contains(keyword))
        .toList();
    auctionTable.setItems(FXCollections.observableArrayList(filtered));
  }

  @FXML
  public void handleRefresh() {
    loadAuctions();
  }

  @FXML
  public void handleLogout() {
    SceneManager.getInstance().logout();
  }

  // ========== DATA ==========

  private void loadAuctions() {
    setStatus("Đang tải dữ liệu...");
    Thread.ofVirtual().start(() -> {
      try {
        HttpResponse<String> response = RestClient.get("/api/auctions");
        if (response.statusCode() == 200) {
          List<AuctionResponse> list = RestClient.parseList(response.body(), AuctionResponse.class);
          Platform.runLater(() -> {
            allAuctions.setAll(list);
            auctionTable.setItems(FXCollections.observableArrayList(allAuctions));
            setStatus("Tổng cộng " + list.size() + " phiên đấu giá.");
          });
        } else {
          Platform.runLater(() -> setStatus("Lỗi tải dữ liệu: " + response.statusCode()));
        }
      } catch (Exception e) {
        LOGGER.error("Lỗi load auctions", e);
        Platform.runLater(() -> setStatus("Không thể kết nối đến server."));
      }
    });
  }

  private void deleteAuction(Long id) {
    Thread.ofVirtual().start(() -> {
      try {
        HttpResponse<String> response = RestClient.delete("/api/auctions/" + id);
        Platform.runLater(() -> {
          if (response.statusCode() == 204 || response.statusCode() == 200) {
            setStatus("Đã hủy phiên #" + id);
            loadAuctions();
          } else {
            setStatus("Hủy phiên thất bại: " + response.statusCode());
          }
        });
      } catch (Exception e) {
        LOGGER.error("Lỗi hủy phiên {}", id, e);
        Platform.runLater(() -> setStatus("Không thể kết nối đến server."));
      }
    });
  }

  // ========== UI SETUP ==========

  private void setupColumns() {
    idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
    itemCol.setCellValueFactory(new PropertyValueFactory<>("itemName"));

    statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
    statusCol.setCellFactory(col -> new TableCell<>() {
      @Override
      protected void updateItem(String status, boolean empty) {
        super.updateItem(status, empty);
        setText(empty ? null : status);
        setStyle(empty || status == null ? "" : switch (status) {
          case "RUNNING"  -> "-fx-text-fill: #00c853; -fx-font-weight: bold;";
          case "OPEN"     -> "-fx-text-fill: #2196f3; -fx-font-weight: bold;";
          case "FINISHED" -> "-fx-text-fill: #9e9e9e;";
          default         -> "";
        });
      }
    });

    priceCol.setCellValueFactory(new PropertyValueFactory<>("currentPrice"));
    priceCol.setCellFactory(col -> new TableCell<>() {
      @Override
      protected void updateItem(BigDecimal price, boolean empty) {
        super.updateItem(price, empty);
        setText(empty || price == null ? null : VND.format(price));
      }
    });

    actionCol.setCellFactory(col -> new TableCell<>() {
      private final Button viewBtn = new Button("Xem");
      private final Button cancelBtn = new Button("Hủy");
      private final HBox box = new HBox(6, viewBtn, cancelBtn);
      {
        viewBtn.setOnAction(e -> {
          AuctionResponse a = getTableView().getItems().get(getIndex());
          SceneManager.getInstance().navigateTo("auction-detail.fxml", a.getId());
        });
        cancelBtn.setOnAction(e -> {
          AuctionResponse a = getTableView().getItems().get(getIndex());
          deleteAuction(a.getId());
        });
      }

      @Override
      protected void updateItem(Void item, boolean empty) {
        super.updateItem(item, empty);
        setGraphic(empty ? null : box);
      }
    });
  }

  private void setStatus(String text) {
    statusLabel.setText(text);
  }
}
