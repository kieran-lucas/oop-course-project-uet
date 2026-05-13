package com.auction.ui.controller;

import com.auction.dto.AuctionResponse;
import com.auction.dto.UserResponse;
import com.auction.model.DepositRecord;
import com.auction.model.PasswordResetRecord;
import com.auction.ui.util.Navigable;
import com.auction.ui.util.SceneManager;
import com.auction.util.RestClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * <p><b>Mục đích:</b> Cung cấp giao diện quản trị toàn diện cho ADMIN, bao gồm bốn tab chức năng:
 *
 * <ol>
 *   <li><b>Phiên đấu giá</b> — Xem, tìm kiếm, hủy mềm ({@code DELETE /api/auctions/{id}}) hoặc xóa
 *       vĩnh viễn ({@code DELETE /api/admin/auctions/{id}}) từng phiên.
 *   <li><b>Người dùng</b> — Xem danh sách toàn bộ user, xóa tài khoản ({@code DELETE
 *       /api/admin/users/{id}}).
 *   <li><b>Yêu cầu nạp tiền</b> — Duyệt hoặc từ chối DepositRecord đang PENDING; tự động poll mỗi 5
 *       giây.
 *   <li><b>Đặt lại mật khẩu</b> — Duyệt hoặc từ chối PasswordResetRecord đang PENDING; khi duyệt,
 *       server tạo mật khẩu tạm thời một lần.
 * </ol>
 *
 * <p><b>Các phương thức chính:</b>
 *
 * <ul>
 *   <li>{@link #onNavigatedTo()} — Load bốn bảng dữ liệu và khởi động auto-poll.
 *   <li>{@link #handleSearch()} — Lọc bảng phiên đấu giá theo tên sản phẩm hoặc ID.
 *   <li>{@link #handleRefresh()} — Tải lại bảng phiên đấu giá từ server.
 *   <li>{@link #handleRefreshDeposits()} — Tải lại bảng yêu cầu nạp tiền.
 *   <li>{@link #handleRefreshPasswordResets()} — Tải lại bảng yêu cầu đặt lại mật khẩu.
 *   <li>{@link #handleLogout()} — Đăng xuất và trả về welcome.fxml.
 * </ul>
 *
 * <p><b>Vị trí trong kiến trúc:</b> AdminPanelController là màn hình duy nhất của ADMIN sau đăng
 * nhập — chỉ điều hướng đến được khi role = "ADMIN" (kiểm tra trong LoginController). Các thao tác
 * duyệt/từ chối gọi trực tiếp REST API phía server; không dùng WebSocket.
 */
public class AdminPanelController implements Navigable {

  private static final Logger LOGGER = LoggerFactory.getLogger(AdminPanelController.class);
  private static final NumberFormat VND = NumberFormat.getCurrencyInstance(Locale.of("vi", "VN"));
  private static final ObjectMapper MAPPER = new ObjectMapper();

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

  /** Khởi tạo cấu hình các cột của bốn bảng — gọi một lần bởi JavaFX sau khi FXML được load. */
  @FXML
  public void initialize() {
    setupColumns();
    setupUserColumns();
    setupDepositColumns();
    setupPasswordResetColumns();
  }

  // ========== NAVIGABLE LIFECYCLE ==========

  /**
   * Được gọi mỗi khi điều hướng đến màn hình này. Load dữ liệu bốn bảng và khởi động auto-poll yêu
   * cầu nạp tiền / đặt lại mật khẩu mỗi 5 giây.
   */
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

  /** Dừng auto-poll khi rời khỏi màn hình để tránh request thừa khi ADMIN chuyển sang tab khác. */
  @Override
  public void onNavigatedFrom() {
    stopDepositRefresh();
  }

  // ========== FXML ACTIONS ==========

  /**
   * Lọc bảng phiên đấu giá theo từ khóa nhập vào. Tìm khớp theo tên sản phẩm (không phân biệt hoa
   * thường) hoặc ID phiên. Kết quả được áp dụng trực tiếp lên {@code auctionTable} mà không gọi lại
   * server.
   */
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

  /** Tải lại danh sách phiên đấu giá từ server và cập nhật bảng. */
  @FXML
  public void handleRefresh() {
    loadAuctions();
  }

  /** Tải lại danh sách yêu cầu nạp tiền đang chờ duyệt từ server. */
  @FXML
  public void handleRefreshDeposits() {
    loadDepositRequests();
  }

  /** Tải lại danh sách yêu cầu đặt lại mật khẩu đang chờ duyệt từ server. */
  @FXML
  public void handleRefreshPasswordResets() {
    loadPasswordResetRequests();
  }

  /** Chuyển sang màn hình hồ sơ cá nhân của ADMIN. */
  @FXML
  public void handleProfile() {
    SceneManager.getInstance().navigateTo("profile.fxml");
  }

  /** Đăng xuất và trả về màn hình chào mừng. */
  @FXML
  public void handleLogout() {
    SceneManager.getInstance().logout();
  }

  // ========== DATA ==========

  /**
   * Tải toàn bộ phiên đấu giá từ {@code GET /api/auctions} trên luồng nền và cập nhật bảng. Hiển
   * thị trạng thái tải ở statusLabel trong quá trình chờ phản hồi.
   */
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

  /**
   * Xóa vĩnh viễn phiên đấu giá khỏi database qua {@code DELETE /api/admin/auctions/{id}}. Khác với
   * {@link #deleteAuction(Long)} (hủy mềm), thao tác này không thể hoàn tác.
   */
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

  /**
   * Hủy mềm phiên đấu giá qua {@code DELETE /api/auctions/{id}} — server chuyển trạng thái thành
   * CANCELED nhưng vẫn giữ bản ghi. Tải lại bảng sau khi thành công.
   */
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

  /**
   * Tải danh sách toàn bộ người dùng từ {@code GET /api/admin/users} và cập nhật {@code userTable}.
   */
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

  /**
   * Xóa tài khoản người dùng qua {@code DELETE /api/admin/users/{id}}. Tải lại bảng user sau khi
   * thành công.
   */
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

  /**
   * Tải danh sách yêu cầu đặt lại mật khẩu đang chờ duyệt từ {@code GET
   * /api/admin/password-reset-requests} và cập nhật {@code passwordResetTable}.
   */
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

  /**
   * Duyệt yêu cầu đặt lại mật khẩu qua {@code POST
   * /api/admin/password-reset-requests/{id}/approve}. Server sẽ tạo mật khẩu tạm thời một lần và
   * đánh dấu yêu cầu là APPROVED.
   */
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
                        setStatus(
                            "Mật khẩu tạm thời cho yêu cầu #"
                                + id
                                + ": "
                                + extractTempPassword(response.body()));
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

  private String extractTempPassword(String responseBody) {
    try {
      JsonNode json = MAPPER.readTree(responseBody);
      return json.has("tempPassword") ? json.get("tempPassword").asText() : "(không có)";
    } catch (Exception e) {
      return "(không đọc được)";
    }
  }

  /**
   * Từ chối yêu cầu đặt lại mật khẩu qua {@code POST
   * /api/admin/password-reset-requests/{id}/reject}. Mật khẩu người dùng không bị thay đổi; yêu cầu
   * được đánh dấu REJECTED.
   */
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

  /**
   * Cấu hình các cột của bảng phiên đấu giá: ánh xạ giá trị, định dạng giá VND, màu trạng thái, và
   * nút hành động (Xem / Hủy / Xóa vĩnh viễn) cho từng hàng.
   */
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
                box.setMinWidth(200);
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

  /**
   * Cấu hình các cột của bảng người dùng: ID, username, email, role, số dư (định dạng VND), và nút
   * Xóa tài khoản.
   */
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

  /**
   * Cấu hình các cột của bảng yêu cầu nạp tiền: ID, username, số tiền (VND), thời gian tạo, và hai
   * nút hành động Duyệt / Từ chối cho từng hàng.
   */
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

  /**
   * Cấu hình các cột của bảng yêu cầu đặt lại mật khẩu: ID, username, email, thời gian tạo, và hai
   * nút hành động Duyệt / Từ chối cho từng hàng.
   */
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

  /**
   * Khởi động Timeline tự động poll yêu cầu nạp tiền và đặt lại mật khẩu mỗi 5 giây. Gọi {@link
   * #stopDepositRefresh()} trước để tránh tạo nhiều Timeline chạy song song.
   */
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

  /** Dừng và hủy Timeline auto-poll nếu đang chạy. */
  private void stopDepositRefresh() {
    if (depositRefreshTimeline != null) {
      depositRefreshTimeline.stop();
      depositRefreshTimeline = null;
    }
  }

  /** Hiển thị thông báo trạng thái trên thanh status của màn hình quản trị. */
  private void setStatus(String text) {
    statusLabel.setText(text);
    statusLabel.setVisible(true);
    statusLabel.setManaged(true);
  }
}
