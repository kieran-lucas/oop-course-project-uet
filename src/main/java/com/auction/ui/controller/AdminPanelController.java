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
import javafx.beans.value.ChangeListener;
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
 *       server tạo mật khẩu mới 6 ký tự.
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
  private static final String ADMIN_SILVER = "#CBD5E1";
  private static final String AUCTION_NAME_BROWN = "#7C5430";
  private static final String MONEY_COLOR = "#F59E0B";
  private static final String ENDED_STATUS_COLOR = "#64748B";

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
  @FXML private Label passwordResetStatusLabel;

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
    applyAdminColumnWidths();
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
   * /api/admin/password-reset-requests/{id}/approve}. Server sẽ tạo mật khẩu mới 6 ký tự và đánh
   * dấu yêu cầu là APPROVED.
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
                        setPasswordResetStatus(
                            "Đã duyệt yêu cầu #"
                                + id
                                + ". Mật khẩu mới: "
                                + extractTempPassword(response.body()));
                        loadPasswordResetRequests();
                      } else {
                        setPasswordResetStatus("Duyệt thất bại: " + response.statusCode(), true);
                      }
                    });
              } catch (Exception e) {
                LOGGER.error("Lỗi duyệt password reset {}", id, e);
                Platform.runLater(
                    () -> setPasswordResetStatus("Không thể kết nối đến server.", true));
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
                        setPasswordResetStatus("Đã từ chối yêu cầu đặt lại mật khẩu #" + id);
                        loadPasswordResetRequests();
                      } else {
                        setPasswordResetStatus("Từ chối thất bại: " + response.statusCode(), true);
                      }
                    });
              } catch (Exception e) {
                LOGGER.error("Lỗi từ chối password reset {}", id, e);
                Platform.runLater(
                    () -> setPasswordResetStatus("Không thể kết nối đến server.", true));
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

  /**
   * Keep admin tables inside the available app width while giving money/email columns more room.
   *
   * <p>Do NOT hard-code a large total width: that pushes columns past the viewport and creates
   * horizontal overflow. Instead, each table listens to its actual rendered width and redistributes
   * that width by ratios. Money/email columns get a larger share; action columns stay just large
   * enough for their buttons.
   */
  private void applyAdminColumnWidths() {
    setAdminColumnMinWidths();

    applyResponsiveColumnWidths(
        auctionTable,
        new double[] {0.06, 0.25, 0.13, 0.24, 0.32},
        idCol,
        itemCol,
        statusCol,
        priceCol,
        actionCol);

    applyResponsiveColumnWidths(
        userTable,
        new double[] {0.05, 0.15, 0.31, 0.10, 0.27, 0.12},
        userIdCol,
        usernameCol,
        emailCol,
        roleCol,
        balanceCol,
        userActionCol);

    applyResponsiveColumnWidths(
        depositTable,
        new double[] {0.06, 0.19, 0.27, 0.23, 0.25},
        depositIdCol,
        depositUserCol,
        depositAmountCol,
        depositTimeCol,
        depositActionCol);

    applyResponsiveColumnWidths(
        passwordResetTable,
        new double[] {0.06, 0.17, 0.34, 0.22, 0.21},
        prIdCol,
        prUsernameCol,
        prEmailCol,
        prTimeCol,
        prActionCol);
  }

  private void setAdminColumnMinWidths() {
    idCol.setMinWidth(48);
    itemCol.setMinWidth(150);
    statusCol.setMinWidth(100);
    priceCol.setMinWidth(180);
    actionCol.setMinWidth(260);

    userIdCol.setMinWidth(48);
    usernameCol.setMinWidth(120);
    emailCol.setMinWidth(230);
    roleCol.setMinWidth(80);
    balanceCol.setMinWidth(190);
    userActionCol.setMinWidth(95);

    depositIdCol.setMinWidth(48);
    depositUserCol.setMinWidth(120);
    depositAmountCol.setMinWidth(190);
    depositTimeCol.setMinWidth(150);
    depositActionCol.setMinWidth(190);

    prIdCol.setMinWidth(48);
    prUsernameCol.setMinWidth(120);
    prEmailCol.setMinWidth(230);
    prTimeCol.setMinWidth(150);
    prActionCol.setMinWidth(190);
  }

  @SafeVarargs
  private final void applyResponsiveColumnWidths(
      TableView<?> table, double[] ratios, TableColumn<?, ?>... columns) {
    ChangeListener<Number> widthListener =
        (obs, oldWidth, newWidth) -> resizeColumns(table, ratios, columns);
    table.widthProperty().addListener(widthListener);
    Platform.runLater(() -> resizeColumns(table, ratios, columns));
  }

  private void resizeColumns(TableView<?> table, double[] ratios, TableColumn<?, ?>... columns) {
    if (table == null || table.getWidth() <= 0) {
      return;
    }
    // Leave a small reserve for borders / scrollbar so the last column does not spill horizontally.
    double usableWidth = Math.max(0, table.getWidth() - 18);
    for (int i = 0; i < columns.length && i < ratios.length; i++) {
      columns[i].setPrefWidth(usableWidth * ratios[i]);
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
    itemCol.setCellFactory(
        col ->
            new TableCell<>() {
              @Override
              protected void updateItem(String itemName, boolean empty) {
                super.updateItem(itemName, empty);
                setText(empty ? null : itemName);
                setStyle(
                    empty || itemName == null
                        ? ""
                        : "-fx-text-fill: " + AUCTION_NAME_BROWN + "; -fx-font-weight: bold;");
              }
            });

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
                          case "FINISHED", "PAID" ->
                              "-fx-text-fill: " + ENDED_STATUS_COLOR + "; -fx-font-weight: bold;";
                          case "CANCELED" -> "-fx-text-fill: #DC2626; -fx-font-weight: bold;";
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
                setStyle(
                    empty || price == null
                        ? ""
                        : "-fx-text-fill: " + MONEY_COLOR + "; -fx-font-weight: bold;");
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
                            a -> SceneManager.getInstance().navigateTo("auction-detail.fxml", a),
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
    String auctionName = formatAuctionName(a);
    showConfirmDialog(
        "Xác nhận hủy phiên",
        "Hủy phiên đấu giá #" + a.getId() + " - " + auctionName + "?",
        "Phiên "
            + auctionName
            + " sẽ chuyển sang trạng thái CANCELED.\n"
            + "Seller và tất cả bidder đã tham gia sẽ nhận thông báo.\n"
            + "Người dẫn đầu (nếu có) sẽ được hoàn lại tiền giữ chỗ.\n"
            + "Thao tác này không thể hoàn tác.",
        false,
        () -> deleteAuction(a.getId()));
  }

  /** Hiện custom dialog xác nhận trước khi xóa cứng phiên — destructive action. */
  private void confirmHardDeleteAuction(AuctionResponse a) {
    String auctionName = formatAuctionName(a);
    showConfirmDialog(
        "Xác nhận xóa vĩnh viễn",
        "Xóa phiên đấu giá #" + a.getId() + " - " + auctionName + "?",
        "Phiên "
            + auctionName
            + " cùng toàn bộ lịch sử bid,\n"
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

  private String formatAuctionName(AuctionResponse auction) {
    String rawName =
        auction.getItemName() != null && !auction.getItemName().isBlank()
            ? auction.getItemName().trim()
            : "#" + auction.getId();
    return "[" + rawName + "]";
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
    // WINDOW_MODAL + StageStyle.TRANSPARENT + setOnShown→toFront — covers the Windows
    // Z-order bug where APPLICATION_MODAL + TRANSPARENT spawns the dialog behind owner.
    dialog.initModality(javafx.stage.Modality.WINDOW_MODAL);
    dialog.initStyle(javafx.stage.StageStyle.TRANSPARENT);
    dialog.setTitle(title);

    // ── Color palette: danger (red) for hard-delete, warning (amber) for soft-cancel ─
    String accent = danger ? "#DC2626" : "#D97706";
    String accentBg = danger ? "#FEE2E2" : "#FEF3C7";
    String accentRing = danger ? "rgba(220,38,38,0.22)" : "rgba(217,119,6,0.22)";
    String accentGradient =
        danger
            ? "linear-gradient(to bottom, #EF4444 0%, #DC2626 100%)"
            : "linear-gradient(to bottom, #F59E0B 0%, #D97706 100%)";
    String accentGradientHover =
        danger
            ? "linear-gradient(to bottom, #DC2626 0%, #B91C1C 100%)"
            : "linear-gradient(to bottom, #D97706 0%, #B45309 100%)";
    // Material Design SVG paths — trash bin for hard-delete, warning triangle for cancel.
    String iconSvg =
        danger
            ? "M19,4H15.5L14.5,3H9.5L8.5,4H5V6H19M6,19A2,2 0 0,0 8,21H16A2,2 "
                + "0 0,0 18,19V7H6V19Z"
            : "M13,14H11V10H13M13,18H11V16H13M1,21H23L12,2L1,21Z";
    String confirmLabel = danger ? "Xóa vĩnh viễn" : "Xác nhận hủy";

    // ── Backdrop ──────────────────────────────────────────────────────────
    // Slate-900 at 55% — premium feel vs pure black, matches app's #0F172A text color.
    javafx.scene.layout.StackPane overlay = new javafx.scene.layout.StackPane();
    overlay.setStyle("-fx-background-color: rgba(15, 23, 42, 0.55);");

    // ── Card: floating white sheet with generous 20px radius ──────────────
    javafx.scene.layout.VBox card = new javafx.scene.layout.VBox(0);
    card.setMaxWidth(440);
    card.setAlignment(javafx.geometry.Pos.TOP_CENTER);
    card.setStyle(
        "-fx-background-color: #FFFFFF;"
            + "-fx-background-radius: 20;"
            + "-fx-border-color: rgba(226, 232, 240, 0.9);"
            + "-fx-border-width: 1;"
            + "-fx-border-radius: 20;"
            + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.32), 48, 0.15, 0, 16);");

    // ── Icon badge: 76px tinted circle with SVG icon inside ───────────────
    javafx.scene.layout.StackPane iconCircle = new javafx.scene.layout.StackPane();
    iconCircle.setPrefSize(76, 76);
    iconCircle.setMinSize(76, 76);
    iconCircle.setMaxSize(76, 76);
    iconCircle.setStyle(
        "-fx-background-color: "
            + accentBg
            + ";"
            + "-fx-background-radius: 38;"
            + "-fx-effect: dropshadow(gaussian, "
            + accentRing
            + ", 16, 0.18, 0, 2);");

    javafx.scene.layout.Region iconShape = new javafx.scene.layout.Region();
    iconShape.setPrefSize(36, 36);
    iconShape.setMinSize(36, 36);
    iconShape.setMaxSize(36, 36);
    iconShape.setStyle("-fx-background-color: " + accent + ";" + "-fx-shape: \"" + iconSvg + "\";");
    iconCircle.getChildren().add(iconShape);

    javafx.scene.layout.VBox iconWrap = new javafx.scene.layout.VBox(iconCircle);
    iconWrap.setAlignment(javafx.geometry.Pos.CENTER);
    iconWrap.setPadding(new javafx.geometry.Insets(32, 24, 6, 24));

    // ── Title (header) ────────────────────────────────────────────────────
    javafx.scene.text.TextFlow titleFlow =
        createSemanticTextFlow(header, "#0F172A", 19, true, "Lexend");
    titleFlow.setPrefWidth(376);

    javafx.scene.layout.VBox titleWrap = new javafx.scene.layout.VBox(titleFlow);
    titleWrap.setAlignment(javafx.geometry.Pos.CENTER);
    titleWrap.setPadding(new javafx.geometry.Insets(18, 32, 0, 32));

    // ── Body description ──────────────────────────────────────────────────
    javafx.scene.text.TextFlow bodyFlow =
        createSemanticTextFlow(body, "#64748B", 13.5, false, "Lexend");
    bodyFlow.setPrefWidth(376);
    bodyFlow.setLineSpacing(4);

    javafx.scene.layout.VBox bodyWrap = new javafx.scene.layout.VBox(bodyFlow);
    bodyWrap.setAlignment(javafx.geometry.Pos.CENTER);
    bodyWrap.setPadding(new javafx.geometry.Insets(12, 32, 24, 32));

    // ── Button row: two full-width buttons split equally ──────────────────
    String cancelBaseBg = "#F1F5F9";
    String cancelHoverBg = "#E2E8F0";
    final String cancelBaseStyle =
        "-fx-font-family: 'Lexend';"
            + "-fx-font-weight: bold;"
            + "-fx-font-size: 14px;"
            + "-fx-text-fill: #475569;"
            + "-fx-background-color: "
            + cancelBaseBg
            + ";"
            + "-fx-background-radius: 10;"
            + "-fx-border-color: transparent;"
            + "-fx-padding: 0 22 0 22;"
            + "-fx-pref-height: 44;"
            + "-fx-min-height: 44;"
            + "-fx-cursor: hand;"
            + "-fx-font-smoothing-type: gray;";
    final String cancelHoverStyle = cancelBaseStyle.replace(cancelBaseBg, cancelHoverBg);

    javafx.scene.control.Button cancelBtn = new javafx.scene.control.Button("Hủy bỏ");
    cancelBtn.setMaxWidth(Double.MAX_VALUE);
    javafx.scene.layout.HBox.setHgrow(cancelBtn, javafx.scene.layout.Priority.ALWAYS);
    cancelBtn.setStyle(cancelBaseStyle);
    cancelBtn.setOnMouseEntered(e -> cancelBtn.setStyle(cancelHoverStyle));
    cancelBtn.setOnMouseExited(e -> cancelBtn.setStyle(cancelBaseStyle));

    final String confirmBaseStyle =
        "-fx-font-family: 'Lexend';"
            + "-fx-font-weight: bold;"
            + "-fx-font-size: 14px;"
            + "-fx-text-fill: #FFFFFF;"
            + "-fx-background-color: "
            + accentGradient
            + ";"
            + "-fx-background-radius: 10;"
            + "-fx-border-color: transparent;"
            + "-fx-padding: 0 22 0 22;"
            + "-fx-pref-height: 44;"
            + "-fx-min-height: 44;"
            + "-fx-cursor: hand;"
            + "-fx-effect: dropshadow(gaussian, "
            + accentRing
            + ", 12, 0.1, 0, 4);"
            + "-fx-font-smoothing-type: gray;";
    final String confirmHoverStyle = confirmBaseStyle.replace(accentGradient, accentGradientHover);

    javafx.scene.control.Button confirmBtn = new javafx.scene.control.Button(confirmLabel);
    confirmBtn.setMaxWidth(Double.MAX_VALUE);
    javafx.scene.layout.HBox.setHgrow(confirmBtn, javafx.scene.layout.Priority.ALWAYS);
    confirmBtn.setStyle(confirmBaseStyle);
    confirmBtn.setOnMouseEntered(e -> confirmBtn.setStyle(confirmHoverStyle));
    confirmBtn.setOnMouseExited(e -> confirmBtn.setStyle(confirmBaseStyle));

    cancelBtn.setOnAction(e -> closeDialogWithAnimation(dialog, overlay));
    confirmBtn.setOnAction(
        e -> {
          closeDialogWithAnimation(dialog, overlay);
          onConfirm.run();
        });

    javafx.scene.layout.HBox btnRow = new javafx.scene.layout.HBox(12, cancelBtn, confirmBtn);
    btnRow.setAlignment(javafx.geometry.Pos.CENTER);
    btnRow.setPadding(new javafx.geometry.Insets(0, 28, 28, 28));

    card.getChildren().addAll(iconWrap, titleWrap, bodyWrap, btnRow);
    overlay.getChildren().add(card);

    // ── Scene & stage ────────────────────────────────────────────────────
    javafx.stage.Stage owner = resolveOwnerStage();
    double sceneWidth = owner != null ? owner.getWidth() : 520;
    double sceneHeight = owner != null ? owner.getHeight() : 420;

    javafx.scene.Scene scene =
        new javafx.scene.Scene(
            overlay, sceneWidth, sceneHeight, javafx.scene.paint.Color.TRANSPARENT);
    scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
    try {
      java.net.URL cssUrl = getClass().getResource("/css/style.css");
      if (cssUrl != null) {
        scene.getStylesheets().add(cssUrl.toExternalForm());
      }
    } catch (Exception ignored) {
    }
    dialog.setScene(scene);

    if (owner != null) {
      dialog.initOwner(owner);
      dialog.setX(owner.getX());
      dialog.setY(owner.getY());
      dialog.setWidth(owner.getWidth());
      dialog.setHeight(owner.getHeight());
    } else {
      dialog.setWidth(sceneWidth);
      dialog.setHeight(sceneHeight);
    }

    // ── Entrance animation ────────────────────────────────────────────────
    // Backdrop fades in (220ms) + card fades + scales 0.94→1.0 + slides up
    // 16→0px (280ms, ease-out) — three-axis "settling in" feel.
    overlay.setOpacity(0);
    card.setOpacity(0);
    card.setScaleX(0.94);
    card.setScaleY(0.94);
    card.setTranslateY(16);

    javafx.animation.FadeTransition overlayFadeIn =
        new javafx.animation.FadeTransition(javafx.util.Duration.millis(220), overlay);
    overlayFadeIn.setFromValue(0);
    overlayFadeIn.setToValue(1);
    overlayFadeIn.setInterpolator(javafx.animation.Interpolator.EASE_OUT);

    javafx.animation.FadeTransition cardFadeIn =
        new javafx.animation.FadeTransition(javafx.util.Duration.millis(280), card);
    cardFadeIn.setFromValue(0);
    cardFadeIn.setToValue(1);

    javafx.animation.ScaleTransition cardScaleIn =
        new javafx.animation.ScaleTransition(javafx.util.Duration.millis(280), card);
    cardScaleIn.setFromX(0.94);
    cardScaleIn.setFromY(0.94);
    cardScaleIn.setToX(1.0);
    cardScaleIn.setToY(1.0);

    javafx.animation.TranslateTransition cardTransIn =
        new javafx.animation.TranslateTransition(javafx.util.Duration.millis(280), card);
    cardTransIn.setFromY(16);
    cardTransIn.setToY(0);

    javafx.animation.ParallelTransition cardAnim =
        new javafx.animation.ParallelTransition(cardFadeIn, cardScaleIn, cardTransIn);
    cardAnim.setInterpolator(javafx.animation.Interpolator.EASE_OUT);

    javafx.animation.ParallelTransition openAnim =
        new javafx.animation.ParallelTransition(overlayFadeIn, cardAnim);

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

  private javafx.scene.text.TextFlow createSemanticTextFlow(
      String value, String baseColor, double fontSize, boolean bold, String fontFamily) {
    javafx.scene.text.TextFlow flow = new javafx.scene.text.TextFlow();
    flow.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
    flow.setLineSpacing(2);
    flow.setMaxWidth(Double.MAX_VALUE);

    String text = value == null ? "" : value;
    int index = 0;
    while (index < text.length()) {
      int bracketStart = text.indexOf('[', index);
      int adminStart = text.indexOf("Admin", index);

      int nextStart;
      boolean auctionSegment;
      if (bracketStart >= 0 && (adminStart < 0 || bracketStart < adminStart)) {
        nextStart = bracketStart;
        auctionSegment = true;
      } else if (adminStart >= 0) {
        nextStart = adminStart;
        auctionSegment = false;
      } else {
        appendDialogText(flow, text.substring(index), baseColor, fontSize, bold, fontFamily);
        break;
      }

      if (nextStart > index) {
        appendDialogText(
            flow, text.substring(index, nextStart), baseColor, fontSize, bold, fontFamily);
      }

      if (auctionSegment) {
        int bracketEnd = text.indexOf(']', nextStart);
        if (bracketEnd < 0) {
          appendDialogText(flow, text.substring(nextStart), baseColor, fontSize, bold, fontFamily);
          break;
        }
        appendDialogText(
            flow,
            text.substring(nextStart, bracketEnd + 1),
            AUCTION_NAME_BROWN,
            fontSize,
            true,
            fontFamily);
        index = bracketEnd + 1;
      } else {
        appendDialogText(flow, "Admin", ADMIN_SILVER, fontSize, true, fontFamily);
        index = nextStart + "Admin".length();
      }
    }

    return flow;
  }

  private void appendDialogText(
      javafx.scene.text.TextFlow flow,
      String value,
      String color,
      double fontSize,
      boolean bold,
      String fontFamily) {
    if (value == null || value.isEmpty()) {
      return;
    }
    javafx.scene.text.Text text = new javafx.scene.text.Text(value);
    text.setStyle(
        "-fx-font-family: '"
            + fontFamily
            + "';"
            + "-fx-font-size: "
            + fontSize
            + "px;"
            + "-fx-fill: "
            + color
            + ";"
            + (bold ? "-fx-font-weight: bold;" : "")
            + "-fx-font-smoothing-type: gray;");
    flow.getChildren().add(text);
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
    dialog.initModality(javafx.stage.Modality.WINDOW_MODAL);
    dialog.initStyle(javafx.stage.StageStyle.TRANSPARENT);
    dialog.setTitle(header);

    // Always red theme for errors (alert-circle icon, red-tinted badge, red CTA).
    String accent = "#DC2626";
    String accentBg = "#FEE2E2";
    String accentRing = "rgba(220,38,38,0.22)";
    String accentGradient = "linear-gradient(to bottom, #EF4444 0%, #DC2626 100%)";
    String accentGradientHover = "linear-gradient(to bottom, #DC2626 0%, #B91C1C 100%)";
    // Material alert-circle SVG path — distinct from confirm dialog's warning triangle.
    String iconSvg =
        "M11,15H13V17H11V15M11,7H13V13H11V7M12,2C6.47,2 2,6.5 2,12A10,10 "
            + "0 0,0 12,22A10,10 0 0,0 22,12A10,10 0 0,0 12,2Z";

    javafx.scene.layout.StackPane overlay = new javafx.scene.layout.StackPane();
    overlay.setStyle("-fx-background-color: rgba(15, 23, 42, 0.55);");

    javafx.scene.layout.VBox card = new javafx.scene.layout.VBox(0);
    card.setMaxWidth(440);
    card.setAlignment(javafx.geometry.Pos.TOP_CENTER);
    card.setStyle(
        "-fx-background-color: #FFFFFF;"
            + "-fx-background-radius: 20;"
            + "-fx-border-color: rgba(226, 232, 240, 0.9);"
            + "-fx-border-width: 1;"
            + "-fx-border-radius: 20;"
            + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.32), 48, 0.15, 0, 16);");

    javafx.scene.layout.StackPane iconCircle = new javafx.scene.layout.StackPane();
    iconCircle.setPrefSize(76, 76);
    iconCircle.setMinSize(76, 76);
    iconCircle.setMaxSize(76, 76);
    iconCircle.setStyle(
        "-fx-background-color: "
            + accentBg
            + ";"
            + "-fx-background-radius: 38;"
            + "-fx-effect: dropshadow(gaussian, "
            + accentRing
            + ", 16, 0.18, 0, 2);");
    javafx.scene.layout.Region iconShape = new javafx.scene.layout.Region();
    iconShape.setPrefSize(36, 36);
    iconShape.setMinSize(36, 36);
    iconShape.setMaxSize(36, 36);
    iconShape.setStyle("-fx-background-color: " + accent + ";" + "-fx-shape: \"" + iconSvg + "\";");
    iconCircle.getChildren().add(iconShape);

    javafx.scene.layout.VBox iconWrap = new javafx.scene.layout.VBox(iconCircle);
    iconWrap.setAlignment(javafx.geometry.Pos.CENTER);
    iconWrap.setPadding(new javafx.geometry.Insets(32, 24, 6, 24));

    javafx.scene.control.Label titleLabel = new javafx.scene.control.Label(header);
    titleLabel.setWrapText(true);
    titleLabel.setMaxWidth(Double.MAX_VALUE);
    titleLabel.setAlignment(javafx.geometry.Pos.CENTER);
    titleLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
    titleLabel.setStyle(
        "-fx-font-family: 'LexendSemiBold';"
            + "-fx-font-size: 19px;"
            + "-fx-text-fill: #0F172A;"
            + "-fx-font-smoothing-type: gray;");

    javafx.scene.layout.VBox titleWrap = new javafx.scene.layout.VBox(titleLabel);
    titleWrap.setAlignment(javafx.geometry.Pos.CENTER);
    titleWrap.setPadding(new javafx.geometry.Insets(18, 32, 0, 32));

    javafx.scene.control.Label bodyLabel = new javafx.scene.control.Label(body);
    bodyLabel.setWrapText(true);
    bodyLabel.setMaxWidth(Double.MAX_VALUE);
    bodyLabel.setAlignment(javafx.geometry.Pos.CENTER);
    bodyLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
    bodyLabel.setStyle(
        "-fx-font-family: 'Lexend';"
            + "-fx-font-size: 13.5px;"
            + "-fx-text-fill: #64748B;"
            + "-fx-line-spacing: 4;"
            + "-fx-font-smoothing-type: gray;");

    javafx.scene.layout.VBox bodyWrap = new javafx.scene.layout.VBox(bodyLabel);
    bodyWrap.setAlignment(javafx.geometry.Pos.CENTER);
    bodyWrap.setPadding(new javafx.geometry.Insets(12, 32, 26, 32));

    final String okBaseStyle =
        "-fx-font-family: 'Lexend';"
            + "-fx-font-weight: bold;"
            + "-fx-font-size: 14px;"
            + "-fx-text-fill: #FFFFFF;"
            + "-fx-background-color: "
            + accentGradient
            + ";"
            + "-fx-background-radius: 10;"
            + "-fx-border-color: transparent;"
            + "-fx-padding: 0 22 0 22;"
            + "-fx-pref-height: 44;"
            + "-fx-min-height: 44;"
            + "-fx-cursor: hand;"
            + "-fx-effect: dropshadow(gaussian, "
            + accentRing
            + ", 12, 0.1, 0, 4);"
            + "-fx-font-smoothing-type: gray;";
    final String okHoverStyle = okBaseStyle.replace(accentGradient, accentGradientHover);

    javafx.scene.control.Button okBtn = new javafx.scene.control.Button("Đã hiểu");
    okBtn.setMaxWidth(Double.MAX_VALUE);
    javafx.scene.layout.HBox.setHgrow(okBtn, javafx.scene.layout.Priority.ALWAYS);
    okBtn.setStyle(okBaseStyle);
    okBtn.setOnMouseEntered(e -> okBtn.setStyle(okHoverStyle));
    okBtn.setOnMouseExited(e -> okBtn.setStyle(okBaseStyle));
    okBtn.setOnAction(e -> closeDialogWithAnimation(dialog, overlay));

    javafx.scene.layout.HBox btnRow = new javafx.scene.layout.HBox(okBtn);
    btnRow.setAlignment(javafx.geometry.Pos.CENTER);
    btnRow.setPadding(new javafx.geometry.Insets(0, 28, 28, 28));

    card.getChildren().addAll(iconWrap, titleWrap, bodyWrap, btnRow);
    overlay.getChildren().add(card);

    javafx.scene.Scene scene =
        new javafx.scene.Scene(overlay, 520, 420, javafx.scene.paint.Color.TRANSPARENT);
    try {
      java.net.URL cssUrl = getClass().getResource("/css/style.css");
      if (cssUrl != null) {
        scene.getStylesheets().add(cssUrl.toExternalForm());
      }
    } catch (Exception ignored) {
    }
    dialog.setScene(scene);
    dialog.setWidth(520);
    dialog.setHeight(420);

    javafx.stage.Stage owner = resolveOwnerStage();
    if (owner != null) {
      dialog.initOwner(owner);
      dialog.setX(owner.getX() + (owner.getWidth() - dialog.getWidth()) / 2);
      dialog.setY(owner.getY() + (owner.getHeight() - dialog.getHeight()) / 2);
    }

    overlay.setOpacity(0);
    card.setOpacity(0);
    card.setScaleX(0.94);
    card.setScaleY(0.94);
    card.setTranslateY(16);

    javafx.animation.FadeTransition overlayFadeIn =
        new javafx.animation.FadeTransition(javafx.util.Duration.millis(220), overlay);
    overlayFadeIn.setFromValue(0);
    overlayFadeIn.setToValue(1);
    overlayFadeIn.setInterpolator(javafx.animation.Interpolator.EASE_OUT);

    javafx.animation.FadeTransition cardFadeIn =
        new javafx.animation.FadeTransition(javafx.util.Duration.millis(280), card);
    cardFadeIn.setFromValue(0);
    cardFadeIn.setToValue(1);

    javafx.animation.ScaleTransition cardScaleIn =
        new javafx.animation.ScaleTransition(javafx.util.Duration.millis(280), card);
    cardScaleIn.setFromX(0.94);
    cardScaleIn.setFromY(0.94);
    cardScaleIn.setToX(1.0);
    cardScaleIn.setToY(1.0);

    javafx.animation.TranslateTransition cardTransIn =
        new javafx.animation.TranslateTransition(javafx.util.Duration.millis(280), card);
    cardTransIn.setFromY(16);
    cardTransIn.setToY(0);

    javafx.animation.ParallelTransition cardAnim =
        new javafx.animation.ParallelTransition(cardFadeIn, cardScaleIn, cardTransIn);
    cardAnim.setInterpolator(javafx.animation.Interpolator.EASE_OUT);

    javafx.animation.ParallelTransition openAnim =
        new javafx.animation.ParallelTransition(overlayFadeIn, cardAnim);

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
   * Animation thoát dialog: backdrop fade-out + card fade+scale-down+slide-down — mirror of the
   * entrance ({@link #showConfirmDialogImpl}) so the close feels symmetric. Slightly faster than
   * entrance (180ms vs 280ms) — dismissal should feel snappy.
   */
  private void closeDialogWithAnimation(
      javafx.stage.Stage dialog, javafx.scene.layout.StackPane overlay) {
    javafx.scene.Node card = overlay.getChildren().get(0);

    javafx.animation.FadeTransition overlayFadeOut =
        new javafx.animation.FadeTransition(javafx.util.Duration.millis(180), overlay);
    overlayFadeOut.setFromValue(1);
    overlayFadeOut.setToValue(0);

    javafx.animation.FadeTransition cardFadeOut =
        new javafx.animation.FadeTransition(javafx.util.Duration.millis(160), card);
    cardFadeOut.setFromValue(1);
    cardFadeOut.setToValue(0);

    javafx.animation.ScaleTransition cardScaleOut =
        new javafx.animation.ScaleTransition(javafx.util.Duration.millis(160), card);
    cardScaleOut.setFromX(1.0);
    cardScaleOut.setFromY(1.0);
    cardScaleOut.setToX(0.96);
    cardScaleOut.setToY(0.96);

    javafx.animation.TranslateTransition cardTransOut =
        new javafx.animation.TranslateTransition(javafx.util.Duration.millis(160), card);
    cardTransOut.setFromY(0);
    cardTransOut.setToY(8);

    javafx.animation.ParallelTransition closeAnim =
        new javafx.animation.ParallelTransition(
            overlayFadeOut, cardFadeOut, cardScaleOut, cardTransOut);
    closeAnim.setInterpolator(javafx.animation.Interpolator.EASE_IN);
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
                setStyle(
                    empty || balance == null
                        ? ""
                        : "-fx-text-fill: " + MONEY_COLOR + "; -fx-font-weight: bold;");
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
                setStyle(
                    empty || amount == null
                        ? ""
                        : "-fx-text-fill: " + MONEY_COLOR + "; -fx-font-weight: bold;");
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

  private void setPasswordResetStatus(String text) {
    setPasswordResetStatus(text, false);
  }

  private void setPasswordResetStatus(String text, boolean isError) {
    if (passwordResetStatusLabel == null) {
      setStatus(text);
      return;
    }
    passwordResetStatusLabel.setText(text);
    passwordResetStatusLabel.getStyleClass().setAll(isError ? "error-label" : "status-label");
    passwordResetStatusLabel.setVisible(true);
    passwordResetStatusLabel.setManaged(true);
  }

  /** Hiển thị thông báo trạng thái trên thanh status của màn hình quản trị. */
  private void setStatus(String text) {
    statusLabel.setText(text);
    statusLabel.setVisible(true);
    statusLabel.setManaged(true);
  }
}
