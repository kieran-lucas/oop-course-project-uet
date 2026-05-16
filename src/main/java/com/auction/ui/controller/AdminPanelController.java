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
  private static final NumberFormat VND = NumberFormat.getNumberInstance(Locale.of("vi", "VN"));
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
                        setStatus("Đã xóa vĩnh viễn phiên #" + id + ".");
                        // Remove from UI immediately so admin sees the row vanish even before
                        // loadAuctions() round-trips.
                        allAuctions.removeIf(a -> id.equals(a.getId()));
                        auctionTable.setItems(FXCollections.observableArrayList(allAuctions));
                        loadAuctions();
                      } else {
                        String serverMsg = extractServerMessage(response.body());
                        setStatus("Xóa thất bại (" + response.statusCode() + "): " + serverMsg);
                        showErrorDialog("Không thể xóa phiên #" + id, serverMsg);
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
                        setStatus("Đã hủy phiên #" + id + ".");
                        loadAuctions();
                      } else {
                        String serverMsg = extractServerMessage(response.body());
                        setStatus(
                            "Hủy phiên thất bại (" + response.statusCode() + "): " + serverMsg);
                        showErrorDialog("Không thể hủy phiên #" + id, serverMsg);
                      }
                    });
              } catch (Exception e) {
                LOGGER.error("Lỗi hủy phiên {}", id, e);
                Platform.runLater(() -> setStatus("Không thể kết nối đến server."));
              }
            });
  }

  /**
   * Pull the human-readable message out of an error JSON body. Returns a reasonable fallback if the
   * body isn't JSON or doesn't have a "message" field — keeps the status bar useful even when the
   * server emits a non-standard error payload.
   */
  private String extractServerMessage(String body) {
    if (body == null || body.isBlank()) {
      return "Không có thông tin lỗi từ server.";
    }
    try {
      JsonNode node = MAPPER.readTree(body);
      if (node.hasNonNull("message")) {
        return node.get("message").asText();
      }
    } catch (Exception ignored) {
      // body wasn't JSON — fall through and return it raw (truncated).
    }
    return body.length() > 200 ? body.substring(0, 200) + "…" : body;
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
   * thành công. Hiển thị lỗi rõ ràng trong dialog modal (cùng style với confirm dialog) nếu server
   * từ chối — statusLabel ở section auction quá xa table user nên admin dễ bỏ sót.
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
                        setStatus("Đã xóa tài khoản #" + id + " thành công.");
                        loadUsers();
                      } else {
                        String serverMsg = extractServerMessage(response.body());
                        setStatus("Không thể xóa tài khoản #" + id + ": " + serverMsg);
                        showErrorDialog("Không thể xóa tài khoản #" + id, serverMsg);
                      }
                    });
              } catch (Exception e) {
                LOGGER.error("Lỗi xóa user {}", id, e);
                Platform.runLater(
                    () -> {
                      setStatus("Không thể kết nối đến server.");
                      showErrorDialog(
                          "Lỗi kết nối", "Không thể kết nối đến server. Vui lòng thử lại.");
                    });
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

  // ========== ROW ACTION SAFETY HELPER ==========

  /**
   * Safely dispatch a per-row action from a table cell button. Returns silently if the cell's
   * TableRow or its item is null — cell recycling can leave a stale graphic for a frame, and {@code
   * getTableView().getItems().get(getIndex())} would have thrown {@code IndexOutOfBoundsException}
   * (which JavaFX SWALLOWS silently from action handlers, making the click appear to "do nothing").
   * Any uncaught exception during the action is logged + surfaced on the status bar so a failure
   * never goes silent again.
   *
   * <p>Replaces the prior {@code AuctionResponse a = getTableView().getItems().get(getIndex());}
   * pattern in every action cell of this screen.
   */
  private <T> void runRowAction(
      javafx.scene.control.TableRow<T> row, java.util.function.Consumer<T> action, String label) {
    if (row == null) {
      LOGGER.warn("{}: TableRow is null — không có dữ liệu hàng", label);
      return;
    }
    T item = row.getItem();
    if (item == null) {
      LOGGER.warn("{}: TableRow.getItem() is null — bỏ qua", label);
      return;
    }
    try {
      action.accept(item);
    } catch (Exception ex) {
      LOGGER.error("Lỗi xử lý hành động '{}'", label, ex);
      setStatus("Lỗi mở '" + label + "': " + ex.getMessage());
    }
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
                          case "RUNNING" -> "-fx-text-fill: #16A34A; -fx-font-weight: bold;";
                          case "OPEN" -> "-fx-text-fill: #1565C0; -fx-font-weight: bold;";
                          case "FINISHED" -> "-fx-text-fill: #64748B;";
                          case "CANCELED" -> "-fx-text-fill: #DC2626;";
                          case "PAID" -> "-fx-text-fill: #9333EA;";
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
                setText(empty || price == null ? null : VND.format(price) + " VND");
              }
            });

    actionCol.setCellFactory(
        col ->
            new TableCell<>() {
              private final Button viewBtn = new Button("Xem");
              private final Button cancelBtn = new Button("Hủy");
              private final Button deleteBtn = new Button("Xóa");
              private final HBox box = new HBox(8, viewBtn, cancelBtn, deleteBtn);

              {
                box.setAlignment(javafx.geometry.Pos.CENTER);
                viewBtn.getStyleClass().add("table-action-view");
                cancelBtn.getStyleClass().add("table-action-cancel");
                deleteBtn.getStyleClass().add("table-action-delete");
                viewBtn.setOnAction(
                    e ->
                        runRowAction(
                            getTableRow(),
                            a ->
                                SceneManager.getInstance()
                                    .navigateTo("auction-detail.fxml", a.getId()),
                            "Xem phiên"));
                cancelBtn.setOnAction(
                    e ->
                        runRowAction(
                            getTableRow(),
                            AdminPanelController.this::confirmCancelAuction,
                            "Hủy phiên"));
                deleteBtn.setOnAction(
                    e ->
                        runRowAction(
                            getTableRow(),
                            AdminPanelController.this::confirmHardDeleteAuction,
                            "Xóa phiên"));
              }

              @Override
              protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                  setGraphic(null);
                  return;
                }
                AuctionResponse a = getTableRow().getItem();
                // "Hủy" chỉ áp dụng cho phiên CHƯA kết thúc — OPEN / RUNNING. Phiên đã
                // FINISHED / PAID / CANCELED chỉ còn lựa chọn xóa cứng.
                String status = a.getStatus();
                boolean canCancel = "OPEN".equals(status) || "RUNNING".equals(status);
                cancelBtn.setDisable(!canCancel);
                cancelBtn.setOpacity(canCancel ? 1.0 : 0.45);
                setGraphic(box);
                setAlignment(javafx.geometry.Pos.CENTER);
              }
            });
  }

  /**
   * Hiện custom dialog xác nhận trước khi hủy phiên — có animation fade+scale, font Lexend, màu
   * đồng bộ với design system của app.
   */
  private void confirmCancelAuction(AuctionResponse a) {
    String name = a.getItemName() != null ? a.getItemName() : "#" + a.getId();
    showConfirmDialog(
        "Xác nhận hủy phiên",
        "Hủy phiên đấu giá #" + a.getId() + "?",
        "Phiên \""
            + name
            + "\" sẽ chuyển sang trạng thái CANCELED.\n"
            + "Seller và tất cả bidder đã tham gia sẽ nhận thông báo.\n"
            + "Người dẫn đầu (nếu có) sẽ được hoàn lại tiền giữ chỗ.\n"
            + "Thao tác này không thể hoàn tác.",
        false,
        () -> deleteAuction(a.getId()));
  }

  /** Hiện custom dialog xác nhận trước khi xóa cứng phiên — destructive action. */
  private void confirmHardDeleteAuction(AuctionResponse a) {
    String name = a.getItemName() != null ? a.getItemName() : "#" + a.getId();
    showConfirmDialog(
        "Xác nhận xóa vĩnh viễn",
        "Xóa phiên đấu giá #" + a.getId() + "?",
        "Phiên \""
            + name
            + "\" cùng toàn bộ lịch sử bid,\n"
            + "auto-bid và giao dịch ví liên quan sẽ bị xóa\n"
            + "khỏi cơ sở dữ liệu. Thao tác này KHÔNG THỂ hoàn tác.",
        true,
        () -> hardDeleteAuction(a.getId()));
  }

  /** Hiện custom dialog xác nhận trước khi xóa tài khoản người dùng. */
  private void confirmDeleteUser(UserResponse u) {
    showConfirmDialog(
        "Xác nhận xóa tài khoản",
        "Xóa tài khoản \"" + u.getUsername() + "\"?",
        "Tài khoản #"
            + u.getId()
            + " sẽ bị xóa vĩnh viễn.\n"
            + "Lưu ý: không thể xóa nếu tài khoản đã có\n"
            + "lịch sử đấu giá, bid hoặc giao dịch liên quan.",
        true,
        () -> deleteUser(u.getId()));
  }

  /**
   * Hiển thị custom confirm dialog có animation fade+scale, font Lexend, màu đồng bộ app.
   *
   * @param title tiêu đề cửa sổ dialog
   * @param header dòng header lớn bên trong dialog
   * @param body nội dung mô tả chi tiết
   * @param danger true → nút xác nhận màu đỏ (destructive); false → màu cam (warning)
   * @param onConfirm callback thực thi khi người dùng bấm xác nhận
   */
  private void showConfirmDialog(
      String title, String header, String body, boolean danger, Runnable onConfirm) {
    try {
      showConfirmDialogImpl(title, header, body, danger, onConfirm);
    } catch (Exception ex) {
      // Without this catch, any failure in dialog construction (font loading, scene wiring,
      // owner-window cast on a corner-case JavaFX runtime) would be swallowed by JavaFX's
      // action-handler boundary and the user would see "nothing happens" on click — which
      // is exactly the symptom that was reported.
      LOGGER.error("Không thể mở confirm dialog '{}': {}", title, ex.getMessage(), ex);
      setStatus("Lỗi mở dialog: " + ex.getMessage());
    }
  }

  private void showConfirmDialogImpl(
      String title, String header, String body, boolean danger, Runnable onConfirm) {
    javafx.stage.Stage dialog = new javafx.stage.Stage();
    // WINDOW_MODAL (not APPLICATION_MODAL) — APPLICATION_MODAL + StageStyle.TRANSPARENT has
    // a known Z-order bug on Windows where the transparent modal stage spawns BEHIND the
    // owner window and is therefore invisible to the user. WINDOW_MODAL with a proper
    // owner + setOnShown→toFront below puts it reliably on top.
    dialog.initModality(javafx.stage.Modality.WINDOW_MODAL);
    dialog.initStyle(javafx.stage.StageStyle.TRANSPARENT);
    dialog.setTitle(title);

    // ── Root overlay (semi-transparent dark backdrop) ──────────────────────
    javafx.scene.layout.StackPane overlay = new javafx.scene.layout.StackPane();
    overlay.setStyle("-fx-background-color: rgba(0,0,0,0.45);");

    // ── Card ───────────────────────────────────────────────────────────────
    javafx.scene.layout.VBox card = new javafx.scene.layout.VBox(0);
    card.setMaxWidth(420);
    card.setStyle(
        "-fx-background-color: #FFFFFF;"
            + "-fx-background-radius: 16;"
            + "-fx-border-color: #E8F0FE;"
            + "-fx-border-width: 1;"
            + "-fx-border-radius: 16;"
            + "-fx-effect: dropshadow(gaussian, rgba(13,71,161,0.22), 32, 0.05, 0, 8);");

    // ── Header strip ───────────────────────────────────────────────────────
    String stripColor = danger ? "#DC2626" : "#D97706";
    javafx.scene.layout.VBox headerStrip = new javafx.scene.layout.VBox(4);
    headerStrip.setPadding(new javafx.geometry.Insets(20, 24, 16, 24));
    headerStrip.setStyle(
        "-fx-background-color: "
            + (danger ? "#FEF2F2" : "#FFFBEB")
            + ";"
            + "-fx-background-radius: 15 15 0 0;");

    javafx.scene.control.Label headerLabel = new javafx.scene.control.Label(header);
    headerLabel.setWrapText(true);
    headerLabel.setStyle(
        "-fx-font-family: 'LexendSemiBold';"
            + "-fx-font-size: 16px;"
            + "-fx-text-fill: "
            + stripColor
            + ";"
            + "-fx-font-smoothing-type: gray;");

    headerStrip.getChildren().add(headerLabel);

    // ── Body ───────────────────────────────────────────────────────────────
    javafx.scene.layout.VBox bodyBox = new javafx.scene.layout.VBox(0);
    bodyBox.setPadding(new javafx.geometry.Insets(16, 24, 20, 24));

    javafx.scene.control.Label bodyLabel = new javafx.scene.control.Label(body);
    bodyLabel.setWrapText(true);
    bodyLabel.setStyle(
        "-fx-font-family: 'Lexend';"
            + "-fx-font-size: 13px;"
            + "-fx-text-fill: #475569;"
            + "-fx-line-spacing: 2;"
            + "-fx-font-smoothing-type: gray;");
    bodyBox.getChildren().add(bodyLabel);

    // ── Divider ────────────────────────────────────────────────────────────
    javafx.scene.control.Separator divider = new javafx.scene.control.Separator();
    divider.setStyle("-fx-background-color: #E8F0FE;");

    // ── Button row ─────────────────────────────────────────────────────────
    javafx.scene.layout.HBox btnRow = new javafx.scene.layout.HBox(10);
    btnRow.setPadding(new javafx.geometry.Insets(14, 24, 18, 24));
    btnRow.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

    javafx.scene.control.Button cancelBtn = new javafx.scene.control.Button("Hủy bỏ");
    cancelBtn.setStyle(
        "-fx-font-family: 'Lexend';"
            + "-fx-font-size: 13px;"
            + "-fx-font-weight: bold;"
            + "-fx-text-fill: #64748B;"
            + "-fx-background-color: #F1F5F9;"
            + "-fx-border-color: #E2E8F0;"
            + "-fx-border-width: 1.2;"
            + "-fx-background-radius: 8;"
            + "-fx-border-radius: 8;"
            + "-fx-padding: 0 20 0 20;"
            + "-fx-pref-height: 36;"
            + "-fx-cursor: hand;"
            + "-fx-font-smoothing-type: gray;");
    cancelBtn.setOnMouseEntered(
        e -> cancelBtn.setStyle(cancelBtn.getStyle().replace("#F1F5F9", "#E2E8F0")));
    cancelBtn.setOnMouseExited(
        e -> cancelBtn.setStyle(cancelBtn.getStyle().replace("#E2E8F0;", "#F1F5F9;")));

    String confirmBg =
        danger
            ? "linear-gradient(to bottom, #EF4444 0%, #DC2626 100%)"
            : "linear-gradient(to bottom, #F59E0B 0%, #D97706 100%)";
    String confirmBgHover =
        danger
            ? "linear-gradient(to bottom, #DC2626 0%, #B91C1C 100%)"
            : "linear-gradient(to bottom, #D97706 0%, #B45309 100%)";
    String confirmLabel = danger ? "Xóa vĩnh viễn" : "Xác nhận hủy";

    javafx.scene.control.Button confirmBtn = new javafx.scene.control.Button(confirmLabel);
    confirmBtn.setStyle(
        "-fx-font-family: 'LexendSemiBold';"
            + "-fx-font-size: 13px;"
            + "-fx-text-fill: #FFFFFF;"
            + "-fx-background-color: "
            + confirmBg
            + ";"
            + "-fx-border-color: transparent;"
            + "-fx-background-radius: 8;"
            + "-fx-border-radius: 8;"
            + "-fx-padding: 0 20 0 20;"
            + "-fx-pref-height: 36;"
            + "-fx-cursor: hand;"
            + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 6, 0, 0, 2);"
            + "-fx-font-smoothing-type: gray;");
    confirmBtn.setOnMouseEntered(
        e -> confirmBtn.setStyle(confirmBtn.getStyle().replace(confirmBg, confirmBgHover)));
    confirmBtn.setOnMouseExited(
        e -> confirmBtn.setStyle(confirmBtn.getStyle().replace(confirmBgHover, confirmBg)));

    cancelBtn.setOnAction(e -> closeDialogWithAnimation(dialog, overlay));
    confirmBtn.setOnAction(
        e -> {
          closeDialogWithAnimation(dialog, overlay);
          onConfirm.run();
        });

    btnRow.getChildren().addAll(cancelBtn, confirmBtn);
    card.getChildren().addAll(headerStrip, bodyBox, divider, btnRow);
    overlay.getChildren().add(card);

    // ── Scene & stage ──────────────────────────────────────────────────────
    // Pass explicit width/height to the Scene constructor so the stage always has a sane
    // size even before show() — otherwise the scene defaults to root.prefSize, which can
    // be 0 for an initially empty StackPane on some platforms (dialog invisible).
    javafx.scene.Scene scene =
        new javafx.scene.Scene(overlay, 480, 280, javafx.scene.paint.Color.TRANSPARENT);
    // Inherit the app stylesheet so Lexend fonts resolve correctly
    try {
      java.net.URL cssUrl = getClass().getResource("/css/style.css");
      if (cssUrl != null) {
        scene.getStylesheets().add(cssUrl.toExternalForm());
      }
    } catch (Exception ignored) {
    }
    dialog.setScene(scene);

    // Size dialog to content + padding
    dialog.setWidth(480);
    dialog.setHeight(280);

    // Center over owner window. Defensive: skip owner wiring if the scene chain isn't
    // ready — without this guard, a null owner.getScene() NPEs and the click silently
    // does nothing.
    javafx.stage.Stage owner = resolveOwnerStage();
    if (owner != null) {
      dialog.initOwner(owner);
      dialog.setX(owner.getX() + (owner.getWidth() - dialog.getWidth()) / 2);
      dialog.setY(owner.getY() + (owner.getHeight() - dialog.getHeight()) / 2);
    }

    // ── Entrance animation: fade-in + scale-up ─────────────────────────────
    overlay.setOpacity(0);
    card.setScaleX(0.88);
    card.setScaleY(0.88);

    javafx.animation.FadeTransition fadeIn =
        new javafx.animation.FadeTransition(javafx.util.Duration.millis(180), overlay);
    fadeIn.setFromValue(0);
    fadeIn.setToValue(1);

    javafx.animation.ScaleTransition scaleIn =
        new javafx.animation.ScaleTransition(javafx.util.Duration.millis(200), card);
    scaleIn.setFromX(0.88);
    scaleIn.setFromY(0.88);
    scaleIn.setToX(1.0);
    scaleIn.setToY(1.0);
    // EASE_OUT (not the prior SPLINE(0.34, 1.56, 0.64, 1) with overshoot y=1.56) — the
    // custom spline could throw IllegalArgumentException at play() on some JavaFX runtimes,
    // leaving overlay stuck at opacity=0 → dialog invisible → user reported "nothing
    // happens" on click.
    scaleIn.setInterpolator(javafx.animation.Interpolator.EASE_OUT);

    javafx.animation.ParallelTransition openAnim =
        new javafx.animation.ParallelTransition(fadeIn, scaleIn);

    // Guarantee the transparent modal stage is on top + focused after show(). Without
    // toFront(), a StageStyle.TRANSPARENT modal occasionally spawns under the owner on
    // Windows and the user sees nothing happen.
    dialog.setOnShown(
        e -> {
          dialog.toFront();
          dialog.requestFocus();
          if (resolveOwnerStage() == null) {
            dialog.centerOnScreen();
          }
        });

    dialog.show();
    openAnim.play();
  }

  /**
   * Resolve the current main-window stage as the dialog owner. Tries every admin table — whichever
   * is wired to the active scene — so we never NPE just because one of them isn't visible yet.
   */
  private javafx.stage.Stage resolveOwnerStage() {
    javafx.scene.control.TableView<?>[] candidates = {
      auctionTable, userTable, depositTable, passwordResetTable
    };
    for (var t : candidates) {
      if (t != null
          && t.getScene() != null
          && t.getScene().getWindow() instanceof javafx.stage.Stage s) {
        return s;
      }
    }
    return null;
  }

  /**
   * Hiện custom error dialog có animation fade+scale, font Lexend, màu đỏ — dùng khi thao tác thất
   * bại (server reject xóa user, lỗi kết nối, ...). Single-button dialog "Đã hiểu" để admin xác
   * nhận đã đọc thông báo. Tái dụng helper với {@link #showConfirmDialog}: bố cục giống nhau, chỉ
   * khác ở nút (1 nút "Đã hiểu" thay vì 2 nút Hủy/Xác nhận).
   */
  private void showErrorDialog(String header, String body) {
    try {
      showErrorDialogImpl(header, body);
    } catch (Exception ex) {
      LOGGER.error("Không thể mở error dialog '{}': {}", header, ex.getMessage(), ex);
      setStatus("Lỗi mở dialog: " + ex.getMessage());
    }
  }

  private void showErrorDialogImpl(String header, String body) {
    javafx.stage.Stage dialog = new javafx.stage.Stage();
    // WINDOW_MODAL + setOnShown→toFront — same Windows TRANSPARENT-stage Z-order fix as
    // showConfirmDialog above (otherwise APPLICATION_MODAL + TRANSPARENT renders behind).
    dialog.initModality(javafx.stage.Modality.WINDOW_MODAL);
    dialog.initStyle(javafx.stage.StageStyle.TRANSPARENT);
    dialog.setTitle(header);

    javafx.scene.layout.StackPane overlay = new javafx.scene.layout.StackPane();
    overlay.setStyle("-fx-background-color: rgba(0,0,0,0.45);");

    javafx.scene.layout.VBox card = new javafx.scene.layout.VBox(0);
    card.setMaxWidth(440);
    card.setStyle(
        "-fx-background-color: #FFFFFF;"
            + "-fx-background-radius: 16;"
            + "-fx-border-color: #E8F0FE;"
            + "-fx-border-width: 1;"
            + "-fx-border-radius: 16;"
            + "-fx-effect: dropshadow(gaussian, rgba(13,71,161,0.22), 32, 0.05, 0, 8);");

    javafx.scene.layout.VBox headerStrip = new javafx.scene.layout.VBox(4);
    headerStrip.setPadding(new javafx.geometry.Insets(20, 24, 16, 24));
    headerStrip.setStyle("-fx-background-color: #FEF2F2; -fx-background-radius: 15 15 0 0;");

    javafx.scene.control.Label headerLabel = new javafx.scene.control.Label(header);
    headerLabel.setWrapText(true);
    headerLabel.setStyle(
        "-fx-font-family: 'LexendSemiBold';"
            + "-fx-font-size: 16px;"
            + "-fx-text-fill: #DC2626;"
            + "-fx-font-smoothing-type: gray;");
    headerStrip.getChildren().add(headerLabel);

    javafx.scene.layout.VBox bodyBox = new javafx.scene.layout.VBox(0);
    bodyBox.setPadding(new javafx.geometry.Insets(16, 24, 20, 24));
    javafx.scene.control.Label bodyLabel = new javafx.scene.control.Label(body);
    bodyLabel.setWrapText(true);
    bodyLabel.setStyle(
        "-fx-font-family: 'Lexend';"
            + "-fx-font-size: 13px;"
            + "-fx-text-fill: #475569;"
            + "-fx-line-spacing: 2;"
            + "-fx-font-smoothing-type: gray;");
    bodyBox.getChildren().add(bodyLabel);

    javafx.scene.control.Separator divider = new javafx.scene.control.Separator();
    divider.setStyle("-fx-background-color: #E8F0FE;");

    javafx.scene.layout.HBox btnRow = new javafx.scene.layout.HBox(10);
    btnRow.setPadding(new javafx.geometry.Insets(14, 24, 18, 24));
    btnRow.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

    String confirmBg = "linear-gradient(to bottom, #EF4444 0%, #DC2626 100%)";
    String confirmBgHover = "linear-gradient(to bottom, #DC2626 0%, #B91C1C 100%)";
    javafx.scene.control.Button okBtn = new javafx.scene.control.Button("Đã hiểu");
    okBtn.setStyle(
        "-fx-font-family: 'LexendSemiBold';"
            + "-fx-font-size: 13px;"
            + "-fx-text-fill: #FFFFFF;"
            + "-fx-background-color: "
            + confirmBg
            + ";"
            + "-fx-border-color: transparent;"
            + "-fx-background-radius: 8;"
            + "-fx-border-radius: 8;"
            + "-fx-padding: 0 26 0 26;"
            + "-fx-pref-height: 36;"
            + "-fx-cursor: hand;"
            + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 6, 0, 0, 2);"
            + "-fx-font-smoothing-type: gray;");
    okBtn.setOnMouseEntered(
        e -> okBtn.setStyle(okBtn.getStyle().replace(confirmBg, confirmBgHover)));
    okBtn.setOnMouseExited(
        e -> okBtn.setStyle(okBtn.getStyle().replace(confirmBgHover, confirmBg)));
    okBtn.setOnAction(e -> closeDialogWithAnimation(dialog, overlay));

    btnRow.getChildren().add(okBtn);
    card.getChildren().addAll(headerStrip, bodyBox, divider, btnRow);
    overlay.getChildren().add(card);

    javafx.scene.Scene scene =
        new javafx.scene.Scene(overlay, 500, 260, javafx.scene.paint.Color.TRANSPARENT);
    try {
      java.net.URL cssUrl = getClass().getResource("/css/style.css");
      if (cssUrl != null) {
        scene.getStylesheets().add(cssUrl.toExternalForm());
      }
    } catch (Exception ignored) {
    }
    dialog.setScene(scene);
    dialog.setWidth(500);
    dialog.setHeight(260);

    javafx.stage.Stage owner = resolveOwnerStage();
    if (owner != null) {
      dialog.initOwner(owner);
      dialog.setX(owner.getX() + (owner.getWidth() - dialog.getWidth()) / 2);
      dialog.setY(owner.getY() + (owner.getHeight() - dialog.getHeight()) / 2);
    }

    overlay.setOpacity(0);
    card.setScaleX(0.88);
    card.setScaleY(0.88);
    javafx.animation.FadeTransition fadeIn =
        new javafx.animation.FadeTransition(javafx.util.Duration.millis(180), overlay);
    fadeIn.setFromValue(0);
    fadeIn.setToValue(1);
    javafx.animation.ScaleTransition scaleIn =
        new javafx.animation.ScaleTransition(javafx.util.Duration.millis(200), card);
    scaleIn.setFromX(0.88);
    scaleIn.setFromY(0.88);
    scaleIn.setToX(1.0);
    scaleIn.setToY(1.0);
    // EASE_OUT — same fix as showConfirmDialog (SPLINE overshoot could throw on play()).
    scaleIn.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
    dialog.setOnShown(
        e -> {
          dialog.toFront();
          dialog.requestFocus();
          if (resolveOwnerStage() == null) {
            dialog.centerOnScreen();
          }
        });
    dialog.show();
    new javafx.animation.ParallelTransition(fadeIn, scaleIn).play();
  }

  /** Animation thoát dialog: fade-out + scale-down, rồi đóng Stage. */
  private void closeDialogWithAnimation(
      javafx.stage.Stage dialog, javafx.scene.layout.StackPane overlay) {
    javafx.scene.Node card = overlay.getChildren().get(0);

    javafx.animation.FadeTransition fadeOut =
        new javafx.animation.FadeTransition(javafx.util.Duration.millis(140), overlay);
    fadeOut.setFromValue(1);
    fadeOut.setToValue(0);

    javafx.animation.ScaleTransition scaleOut =
        new javafx.animation.ScaleTransition(javafx.util.Duration.millis(140), card);
    scaleOut.setToX(0.92);
    scaleOut.setToY(0.92);

    javafx.animation.ParallelTransition closeAnim =
        new javafx.animation.ParallelTransition(fadeOut, scaleOut);
    closeAnim.setOnFinished(e -> dialog.close());
    closeAnim.play();
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
                setText(empty || balance == null ? null : VND.format(balance) + " VND");
              }
            });

    userActionCol.setCellFactory(
        col ->
            new TableCell<>() {
              private final Button deleteBtn = new Button("Xóa");

              {
                deleteBtn.getStyleClass().add("table-action-delete");
                deleteBtn.setOnAction(
                    e ->
                        runRowAction(
                            getTableRow(),
                            AdminPanelController.this::confirmDeleteUser,
                            "Xóa tài khoản"));
              }

              @Override
              protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteBtn);
                setAlignment(javafx.geometry.Pos.CENTER);
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
                setText(empty || amount == null ? null : VND.format(amount) + " VND");
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
              private final HBox box = new HBox(8, approveBtn, rejectBtn);

              {
                box.setAlignment(javafx.geometry.Pos.CENTER);
                approveBtn.getStyleClass().add("approve-button");
                rejectBtn.getStyleClass().add("reject-button");
                approveBtn.setOnAction(
                    e ->
                        runRowAction(
                            getTableRow(), r -> approveDeposit(r.getId()), "Duyệt nạp tiền"));
                rejectBtn.setOnAction(
                    e ->
                        runRowAction(
                            getTableRow(), r -> rejectDeposit(r.getId()), "Từ chối nạp tiền"));
              }

              @Override
              protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
                setAlignment(javafx.geometry.Pos.CENTER);
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
              private final HBox box = new HBox(8, approveBtn, rejectBtn);

              {
                box.setAlignment(javafx.geometry.Pos.CENTER);
                approveBtn.getStyleClass().add("approve-button");
                rejectBtn.getStyleClass().add("reject-button");
                approveBtn.setOnAction(
                    e ->
                        runRowAction(
                            getTableRow(),
                            r -> approvePasswordReset(r.getId()),
                            "Duyệt đặt lại mật khẩu"));
                rejectBtn.setOnAction(
                    e ->
                        runRowAction(
                            getTableRow(),
                            r -> rejectPasswordReset(r.getId()),
                            "Từ chối đặt lại mật khẩu"));
              }

              @Override
              protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
                setAlignment(javafx.geometry.Pos.CENTER);
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
