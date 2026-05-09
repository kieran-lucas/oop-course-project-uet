package com.auction.ui.controller;

import com.auction.dto.AuctionResponse;
import com.auction.dto.UserResponse;
import com.auction.model.DepositRecord;
import com.auction.model.PasswordResetRecord;
import com.auction.ui.util.Navigable;
import com.auction.ui.util.SceneManager;
import com.auction.util.RestClient;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
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
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller cho màn hình quản trị (admin-panel.fxml).
 *
 * <p><b>Mục đích:</b> Cung cấp giao diện quản trị cho ADMIN: xem tất cả phiên đấu giá, tìm kiếm, và
 * hủy phiên (DELETE /api/auctions/{id}).
 *
 * <p><b>Các phương thức chính:</b>
 *
 * <ul>
 *   <li>{@link #onNavigatedTo()} — Load tất cả phiên từ API khi vào màn hình.
 *   <li>{@link #handleSearch()} — Lọc phiên theo từ khóa.
 *   <li>{@link #handleRefresh()} — Tải lại dữ liệu từ server.
 *   <li>{@link #handleLogout()} — Đăng xuất về welcome.fxml.
 * </ul>
 *
 * <p><b>Vị trí trong kiến trúc:</b> AdminPanelController là màn hình chính của ADMIN sau đăng nhập.
 * Chỉ có thể vào được khi role = "ADMIN" (kiểm tra trong LoginController).
 */
public class AdminPanelController implements Navigable {

  private static final Logger LOGGER = LoggerFactory.getLogger(AdminPanelController.class);
  private static final NumberFormat VND = NumberFormat.getCurrencyInstance(Locale.of("vi", "VN"));

  @FXML private Label usernameLabel;
  @FXML private TableView<AuctionResponse> auctionTable;
  @FXML private TableColumn<AuctionResponse, Long> idCol;
  @FXML private TableColumn<AuctionResponse, String> itemCol;
  @FXML private TableColumn<AuctionResponse, String> statusCol;
  @FXML private TableColumn<AuctionResponse, BigDecimal> priceCol;
  @FXML private TableColumn<AuctionResponse, Void> actionCol;
  @FXML private TextField searchField;
  @FXML private Label statusLabel;

  @FXML private TableView<UserResponse> userTable;
  @FXML private TableColumn<UserResponse, Long> userIdCol;
  @FXML private TableColumn<UserResponse, String> usernameCol;
  @FXML private TableColumn<UserResponse, String> emailCol;
  @FXML private TableColumn<UserResponse, String> roleCol;
  @FXML private TableColumn<UserResponse, BigDecimal> balanceCol;
  @FXML private TableColumn<UserResponse, Void> userActionCol;

  @FXML private TableView<DepositRecord> depositTable;
  @FXML private TableColumn<DepositRecord, Long> depositIdCol;
  @FXML private TableColumn<DepositRecord, String> depositUserCol;
  @FXML private TableColumn<DepositRecord, BigDecimal> depositAmountCol;
  @FXML private TableColumn<DepositRecord, java.time.LocalDateTime> depositTimeCol;
  @FXML private TableColumn<DepositRecord, Void> depositActionCol;

  @FXML private TableView<PasswordResetRecord> passwordResetTable;
  @FXML private TableColumn<PasswordResetRecord, Long> prIdCol;
  @FXML private TableColumn<PasswordResetRecord, String> prUsernameCol;
  @FXML private TableColumn<PasswordResetRecord, String> prEmailCol;
  @FXML private TableColumn<PasswordResetRecord, java.time.LocalDateTime> prTimeCol;
  @FXML private TableColumn<PasswordResetRecord, Void> prActionCol;

  private final ObservableList<AuctionResponse> allAuctions = FXCollections.observableArrayList();
  private final ObservableList<UserResponse> allUsers = FXCollections.observableArrayList();
  private final ObservableList<DepositRecord> pendingDeposits = FXCollections.observableArrayList();
  private final ObservableList<PasswordResetRecord> pendingPasswordResets =
      FXCollections.observableArrayList();
  private Timeline depositRefreshTimeline;

  @FXML
  public void initialize() {
    setupColumns();
    setupUserColumns();
    setupDepositColumns();
    setupPasswordResetColumns();
  }

  // ========== NAVIGABLE LIFECYCLE ==========

  @Override
  public void onNavigatedTo() {
    SceneManager sm = SceneManager.getInstance();
    usernameLabel.setText(
        sm.getCurrentUsername() != null ? sm.getCurrentUsername() + " (ADMIN)" : "ADMIN");
    loadAuctions();
    loadUsers();
    loadDepositRequests();
    loadPasswordResetRequests();
    startDepositRefresh();
  }

  @Override
  public void onNavigatedFrom() {
    stopDepositRefresh();
  }

  // ========== FXML ACTIONS ==========

  @FXML
  public void handleSearch() {
    String keyword = searchField.getText().trim().toLowerCase();
    List<AuctionResponse> filtered =
        allAuctions.stream()
            .filter(
                a ->
                    keyword.isEmpty()
                        || (a.getItemName() != null
                            && a.getItemName().toLowerCase().contains(keyword))
                        || String.valueOf(a.getId()).contains(keyword))
            .toList();
    auctionTable.setItems(FXCollections.observableArrayList(filtered));
  }

  @FXML
  public void handleRefresh() {
    loadAuctions();
  }

  @FXML
  public void handleRefreshDeposits() {
    loadDepositRequests();
  }

  @FXML
  public void handleRefreshPasswordResets() {
    loadPasswordResetRequests();
  }

  @FXML
  public void handleProfile() {
    SceneManager.getInstance().navigateTo("profile.fxml");
  }

  @FXML
  public void handleLogout() {
    SceneManager.getInstance().logout();
  }

  // ========== DATA ==========

  private void loadAuctions() {
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

  private void hardDeleteAuction(Long id) {
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> response = RestClient.delete("/api/admin/auctions/" + id);
                Platform.runLater(
                    () -> {
                      if (response.statusCode() == 204 || response.statusCode() == 200) {
                        setStatus("Đã xóa vĩnh viễn phiên #" + id);
                        loadAuctions();
                      } else {
                        setStatus("Xóa thất bại: " + response.statusCode());
                      }
                    });
              } catch (Exception e) {
                LOGGER.error("Lỗi xóa phiên {}", id, e);
                Platform.runLater(() -> setStatus("Không thể kết nối đến server."));
              }
            });
  }

  private void deleteAuction(Long id) {
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> response = RestClient.delete("/api/auctions/" + id);
                Platform.runLater(
                    () -> {
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

  private void loadUsers() {
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> response = RestClient.get("/api/admin/users");
                if (response.statusCode() == 200) {
                  List<UserResponse> list =
                      RestClient.parseList(response.body(), UserResponse.class);
                  Platform.runLater(
                      () -> {
                        allUsers.setAll(list);
                        userTable.setItems(FXCollections.observableArrayList(allUsers));
                      });
                }
              } catch (Exception e) {
                LOGGER.error("Lỗi load users", e);
              }
            });
  }

  private void deleteUser(Long id) {
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> response = RestClient.delete("/api/admin/users/" + id);
                Platform.runLater(
                    () -> {
                      if (response.statusCode() == 204 || response.statusCode() == 200) {
                        setStatus("Đã xóa user #" + id);
                        loadUsers();
                      } else {
                        setStatus("Xóa user thất bại: " + response.statusCode());
                      }
                    });
              } catch (Exception e) {
                LOGGER.error("Lỗi xóa user {}", id, e);
                Platform.runLater(() -> setStatus("Không thể kết nối đến server."));
              }
            });
  }

  private void loadPasswordResetRequests() {
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> response =
                    RestClient.get("/api/admin/password-reset-requests");
                if (response.statusCode() == 200) {
                  List<PasswordResetRecord> list =
                      RestClient.parseList(response.body(), PasswordResetRecord.class);
                  Platform.runLater(
                      () -> {
                        pendingPasswordResets.setAll(list);
                        passwordResetTable.setItems(
                            FXCollections.observableArrayList(pendingPasswordResets));
                      });
                }
              } catch (Exception e) {
                LOGGER.error("Lỗi load password reset requests", e);
              }
            });
  }

  private void approvePasswordReset(Long id) {
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> response =
                    RestClient.post("/api/admin/password-reset-requests/" + id + "/approve", null);
                Platform.runLater(
                    () -> {
                      if (response.statusCode() == 200) {
                        setStatus("Đã reset mật khẩu về 123456 cho yêu cầu #" + id);
                        loadPasswordResetRequests();
                      } else {
                        setStatus("Duyệt thất bại: " + response.statusCode());
                      }
                    });
              } catch (Exception e) {
                LOGGER.error("Lỗi duyệt password reset {}", id, e);
                Platform.runLater(() -> setStatus("Không thể kết nối đến server."));
              }
            });
  }

  private void rejectPasswordReset(Long id) {
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> response =
                    RestClient.post("/api/admin/password-reset-requests/" + id + "/reject", null);
                Platform.runLater(
                    () -> {
                      if (response.statusCode() == 204) {
                        setStatus("Đã từ chối yêu cầu đặt lại mật khẩu #" + id);
                        loadPasswordResetRequests();
                      } else {
                        setStatus("Từ chối thất bại: " + response.statusCode());
                      }
                    });
              } catch (Exception e) {
                LOGGER.error("Lỗi từ chối password reset {}", id, e);
                Platform.runLater(() -> setStatus("Không thể kết nối đến server."));
              }
            });
  }

  // ========== UI SETUP ==========

  private void setupColumns() {
    idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
    itemCol.setCellValueFactory(new PropertyValueFactory<>("itemName"));

    statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
    statusCol.setCellFactory(
        col ->
            new TableCell<>() {
              @Override
              protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                setText(empty ? null : status);
                setStyle(
                    empty || status == null
                        ? ""
                        : switch (status) {
                          case "RUNNING" -> "-fx-text-fill: #00c853; -fx-font-weight: bold;";
                          case "OPEN" -> "-fx-text-fill: #2196f3; -fx-font-weight: bold;";
                          case "FINISHED" -> "-fx-text-fill: #9e9e9e;";
                          case "CANCELED" -> "-fx-text-fill: #e53935;";
                          case "PAID" -> "-fx-text-fill: #7b1fa2;";
                          default -> "";
                        });
              }
            });

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

    actionCol.setCellFactory(
        col ->
            new TableCell<>() {
              private final Button viewBtn = new Button("Xem");
              private final Button cancelBtn = new Button("Hủy");
              private final Button deleteBtn = new Button("Xóa");
              private final HBox box = new HBox(6, viewBtn, cancelBtn, deleteBtn);

              {
                deleteBtn.setStyle(
                    "-fx-background-color: #880e4f; -fx-text-fill: white; -fx-cursor: hand;");
                viewBtn.setOnAction(
                    e -> {
                      AuctionResponse a = getTableView().getItems().get(getIndex());
                      SceneManager.getInstance().navigateTo("auction-detail.fxml", a.getId());
                    });
                cancelBtn.setOnAction(
                    e -> {
                      AuctionResponse a = getTableView().getItems().get(getIndex());
                      deleteAuction(a.getId());
                    });
                deleteBtn.setOnAction(
                    e -> {
                      AuctionResponse a = getTableView().getItems().get(getIndex());
                      hardDeleteAuction(a.getId());
                    });
              }

              @Override
              protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
              }
            });
  }

  private void setupUserColumns() {
    userIdCol.setCellValueFactory(new PropertyValueFactory<>("id"));
    usernameCol.setCellValueFactory(new PropertyValueFactory<>("username"));
    emailCol.setCellValueFactory(new PropertyValueFactory<>("email"));
    roleCol.setCellValueFactory(new PropertyValueFactory<>("role"));

    balanceCol.setCellValueFactory(new PropertyValueFactory<>("balance"));
    balanceCol.setCellFactory(
        col ->
            new TableCell<>() {
              @Override
              protected void updateItem(BigDecimal balance, boolean empty) {
                super.updateItem(balance, empty);
                setText(empty || balance == null ? null : VND.format(balance));
              }
            });

    userActionCol.setCellFactory(
        col ->
            new TableCell<>() {
              private final Button deleteBtn = new Button("Xóa");

              {
                deleteBtn.setOnAction(
                    e -> {
                      UserResponse u = getTableView().getItems().get(getIndex());
                      deleteUser(u.getId());
                    });
              }

              @Override
              protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteBtn);
              }
            });
  }

  private void loadDepositRequests() {
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> response = RestClient.get("/api/admin/deposit-requests");
                if (response.statusCode() == 200) {
                  List<DepositRecord> list =
                      RestClient.parseList(response.body(), DepositRecord.class);
                  Platform.runLater(
                      () -> {
                        pendingDeposits.setAll(list);
                        depositTable.setItems(FXCollections.observableArrayList(pendingDeposits));
                      });
                }
              } catch (Exception e) {
                LOGGER.error("Lỗi load deposit requests", e);
              }
            });
  }

  private void approveDeposit(Long id) {
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> response =
                    RestClient.post("/api/admin/deposit-requests/" + id + "/approve", null);
                Platform.runLater(
                    () -> {
                      if (response.statusCode() == 200) {
                        setStatus("Đã duyệt yêu cầu nạp tiền #" + id);
                        loadDepositRequests();
                        loadUsers();
                      } else {
                        setStatus("Duyệt thất bại: " + response.statusCode());
                      }
                    });
              } catch (Exception e) {
                LOGGER.error("Lỗi duyệt deposit {}", id, e);
                Platform.runLater(() -> setStatus("Không thể kết nối đến server."));
              }
            });
  }

  private void rejectDeposit(Long id) {
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> response =
                    RestClient.post("/api/admin/deposit-requests/" + id + "/reject", null);
                Platform.runLater(
                    () -> {
                      if (response.statusCode() == 204) {
                        setStatus("Đã từ chối yêu cầu nạp tiền #" + id);
                        loadDepositRequests();
                      } else {
                        setStatus("Từ chối thất bại: " + response.statusCode());
                      }
                    });
              } catch (Exception e) {
                LOGGER.error("Lỗi từ chối deposit {}", id, e);
                Platform.runLater(() -> setStatus("Không thể kết nối đến server."));
              }
            });
  }

  private void setupDepositColumns() {
    depositIdCol.setCellValueFactory(new PropertyValueFactory<>("id"));
    depositUserCol.setCellValueFactory(new PropertyValueFactory<>("username"));

    depositAmountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
    depositAmountCol.setCellFactory(
        col ->
            new TableCell<>() {
              @Override
              protected void updateItem(BigDecimal amount, boolean empty) {
                super.updateItem(amount, empty);
                setText(empty || amount == null ? null : VND.format(amount));
              }
            });

    depositTimeCol.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
    depositTimeCol.setCellFactory(
        col ->
            new TableCell<>() {
              private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

              @Override
              protected void updateItem(java.time.LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.format(fmt));
              }
            });

    depositActionCol.setCellFactory(
        col ->
            new TableCell<>() {
              private final Button approveBtn = new Button("Duyệt");
              private final Button rejectBtn = new Button("Từ chối");
              private final HBox box = new HBox(6, approveBtn, rejectBtn);

              {
                approveBtn.setStyle("-fx-background-color: #43a047; -fx-text-fill: white;");
                rejectBtn.setStyle("-fx-background-color: #e53935; -fx-text-fill: white;");
                approveBtn.setOnAction(
                    e -> {
                      DepositRecord r = getTableView().getItems().get(getIndex());
                      approveDeposit(r.getId());
                    });
                rejectBtn.setOnAction(
                    e -> {
                      DepositRecord r = getTableView().getItems().get(getIndex());
                      rejectDeposit(r.getId());
                    });
              }

              @Override
              protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
              }
            });
  }

  private void setupPasswordResetColumns() {
    prIdCol.setCellValueFactory(new PropertyValueFactory<>("id"));
    prUsernameCol.setCellValueFactory(new PropertyValueFactory<>("username"));
    prEmailCol.setCellValueFactory(new PropertyValueFactory<>("email"));

    prTimeCol.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
    prTimeCol.setCellFactory(
        col ->
            new TableCell<>() {
              private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

              @Override
              protected void updateItem(java.time.LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.format(fmt));
              }
            });

    prActionCol.setCellFactory(
        col ->
            new TableCell<>() {
              private final Button approveBtn = new Button("Duyệt");
              private final Button rejectBtn = new Button("Từ chối");
              private final HBox box = new HBox(6, approveBtn, rejectBtn);

              {
                approveBtn.setStyle("-fx-background-color: #43a047; -fx-text-fill: white;");
                rejectBtn.setStyle("-fx-background-color: #e53935; -fx-text-fill: white;");
                approveBtn.setOnAction(
                    e -> {
                      PasswordResetRecord r = getTableView().getItems().get(getIndex());
                      approvePasswordReset(r.getId());
                    });
                rejectBtn.setOnAction(
                    e -> {
                      PasswordResetRecord r = getTableView().getItems().get(getIndex());
                      rejectPasswordReset(r.getId());
                    });
              }

              @Override
              protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
              }
            });
  }

  private void startDepositRefresh() {
    stopDepositRefresh();
    depositRefreshTimeline =
        new Timeline(
            new KeyFrame(
                Duration.seconds(5),
                e -> {
                  loadDepositRequests();
                  loadPasswordResetRequests();
                }));
    depositRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
    depositRefreshTimeline.play();
  }

  private void stopDepositRefresh() {
    if (depositRefreshTimeline != null) {
      depositRefreshTimeline.stop();
      depositRefreshTimeline = null;
    }
  }

  private void setStatus(String text) {
    statusLabel.setText(text);
    statusLabel.setVisible(true);
    statusLabel.setManaged(true);
  }
}
