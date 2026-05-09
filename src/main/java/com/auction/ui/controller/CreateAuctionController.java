package com.auction.ui.controller;

import com.auction.model.Item;
import com.auction.ui.util.Navigable;
import com.auction.ui.util.SceneManager;
import com.auction.util.RestClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller cho màn hình tạo phiên đấu giá (create-auction.fxml).
 *
 * <p><b>Mục đích:</b> Cho phép SELLER tạo phiên đấu giá mới bằng cách chọn sản phẩm, đặt giá khởi
 * điểm và lên lịch thời gian bắt đầu/kết thúc. Gửi request đến {@code POST /api/auctions}.
 *
 * <p><b>Các phương thức chính:</b>
 *
 * <ul>
 *   <li>{@link #onNavigatedTo()} — Load danh sách sản phẩm của seller vào ComboBox.
 *   <li>{@link #handleCreate()} — Validate và gửi request tạo phiên.
 *   <li>{@link #goToCreateItem()} — Chuyển sang màn hình tạo sản phẩm mới.
 * </ul>
 *
 * <p><b>Vị trí trong kiến trúc:</b> CreateAuctionController phụ thuộc vào ItemController (GET
 * /api/items để lấy danh sách) và AuctionController (POST /api/auctions để tạo phiên).
 * AuctionScheduler phía server sẽ tự động chuyển trạng thái phiên từ OPEN → RUNNING → FINISHED.
 */
public class CreateAuctionController implements Navigable {

  private static final Logger LOGGER = LoggerFactory.getLogger(CreateAuctionController.class);
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());
  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

  @FXML private ComboBox<Item> itemCombo;
  @FXML private TextField startingPriceField;
  @FXML private DatePicker startDatePicker;
  @FXML private TextField startTimeField;
  @FXML private DatePicker endDatePicker;
  @FXML private TextField endTimeField;
  @FXML private Label statusLabel;
  @FXML private Button createButton;

  // ========== NAVIGABLE LIFECYCLE ==========

  /** Load danh sách sản phẩm của seller từ API khi vào màn hình. */
  @Override
  public void onNavigatedTo() {
    clearForm();
    loadMyItems();
  }

  // ========== FXML ACTIONS ==========

  /**
   * Xử lý tạo phiên đấu giá. Validate: sản phẩm đã chọn, giá > 0, thời gian hợp lệ, endTime >
   * startTime.
   */
  @FXML
  public void handleCreate() {
    Item selectedItem = itemCombo.getValue();
    String priceText = startingPriceField.getText().trim();
    LocalDate startDate = startDatePicker.getValue();
    String startTimeStr = startTimeField.getText().trim();
    LocalDate endDate = endDatePicker.getValue();
    String endTimeStr = endTimeField.getText().trim();

    if (selectedItem == null) {
      showStatus("Vui lòng chọn sản phẩm.", true);
      return;
    }

    BigDecimal startingPrice;
    try {
      startingPrice = new BigDecimal(priceText.replace(",", ""));
      if (startingPrice.compareTo(BigDecimal.ZERO) <= 0) {
        showStatus("Giá khởi điểm phải lớn hơn 0.", true);
        return;
      }
    } catch (NumberFormatException e) {
      showStatus("Giá khởi điểm không hợp lệ.", true);
      return;
    }

    if (startDate == null || startTimeStr.isEmpty()) {
      showStatus("Vui lòng nhập ngày và giờ bắt đầu.", true);
      return;
    }
    if (endDate == null || endTimeStr.isEmpty()) {
      showStatus("Vui lòng nhập ngày và giờ kết thúc.", true);
      return;
    }

    LocalDateTime startTime;
    LocalDateTime endTime;
    try {
      startTime = LocalDateTime.of(startDate, LocalTime.parse(startTimeStr, TIME_FMT));
      endTime = LocalDateTime.of(endDate, LocalTime.parse(endTimeStr, TIME_FMT));
    } catch (DateTimeParseException e) {
      showStatus("Định dạng giờ không hợp lệ. Vui lòng dùng HH:mm (vd: 09:00).", true);
      return;
    }

    if (!endTime.isAfter(startTime)) {
      showStatus("Thời gian kết thúc phải sau thời gian bắt đầu.", true);
      return;
    }

    createButton.setDisable(true);
    hideStatus();

    Map<String, Object> body = new HashMap<>();
    body.put("itemId", selectedItem.getId());
    body.put("startingPrice", startingPrice);
    body.put("startTime", startTime.toString());
    body.put("endTime", endTime.toString());

    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> response = RestClient.post("/api/auctions", body);
                if (response.statusCode() == 201) {
                  Platform.runLater(
                      () -> {
                        showStatus("Tạo phiên đấu giá thành công!", false);
                        clearForm();
                        createButton.setDisable(false);
                      });
                } else {
                  String msg = extractMessage(response.body(), "Tạo phiên thất bại.");
                  Platform.runLater(
                      () -> {
                        showStatus(msg, true);
                        createButton.setDisable(false);
                      });
                }
              } catch (Exception e) {
                LOGGER.error("Lỗi tạo phiên đấu giá", e);
                Platform.runLater(
                    () -> {
                      showStatus("Không thể kết nối đến server.", true);
                      createButton.setDisable(false);
                    });
              }
            });
  }

  /** Chuyển sang màn hình tạo sản phẩm mới. */
  @FXML
  public void goToCreateItem() {
    SceneManager.getInstance().navigateTo("create-item.fxml");
  }

  /** Quay lại danh sách phiên. */
  @FXML
  public void goBack() {
    SceneManager.getInstance().navigateBack("auction-list.fxml");
  }

  // ========== DATA LOADING ==========

  /** Load danh sách sản phẩm thuộc seller hiện tại từ GET /api/items?sellerId=X. */
  private void loadMyItems() {
    Long sellerId = SceneManager.getInstance().getCurrentUserId();
    System.out.println(">>> sellerId = " + sellerId);
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> response = RestClient.get("/api/items?sellerId=" + sellerId);
                System.out.println(">>> response = " + response.body());
                if (response.statusCode() == 200) {
                  List<Item> items = RestClient.parseList(response.body(), Item.class);
                  Platform.runLater(
                      () -> {
                        itemCombo.setItems(FXCollections.observableArrayList(items));
                        itemCombo.setCellFactory(
                            lv ->
                                new ListCell<>() {
                                  @Override
                                  protected void updateItem(Item item, boolean empty) {
                                    super.updateItem(item, empty);
                                    setText(empty || item == null ? null : item.getName());
                                  }
                                });
                        itemCombo.setButtonCell(
                            new ListCell<>() {
                              @Override
                              protected void updateItem(Item item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(
                                    empty || item == null
                                        ? "Chọn sản phẩm của bạn"
                                        : item.getName());
                              }
                            });
                        if (items.isEmpty()) {
                          showStatus("Bạn chưa có sản phẩm nào. Hãy tạo sản phẩm trước.", true);
                        }
                      });
                }
              } catch (Exception e) {
                LOGGER.error("Lỗi load danh sách sản phẩm", e);
                Platform.runLater(() -> showStatus("Không thể tải danh sách sản phẩm.", true));
              }
            });
  }

  // ========== PRIVATE HELPERS ==========

  private void showStatus(String msg, boolean isError) {
    statusLabel.setText(msg);
    statusLabel.setStyle("");
    statusLabel.getStyleClass().setAll(isError ? "error-label" : "status-label");
    statusLabel.setVisible(true);
    statusLabel.setManaged(true);
  }

  private void hideStatus() {
    statusLabel.setVisible(false);
    statusLabel.setManaged(false);
    statusLabel.setStyle("");
    statusLabel.getStyleClass().setAll("status-label");
  }

  private void clearForm() {
    if (itemCombo != null) {
      itemCombo.setValue(null);
    }
    if (startingPriceField != null) {
      startingPriceField.clear();
    }
    if (startDatePicker != null) {
      startDatePicker.setValue(null);
    }
    if (startTimeField != null) {
      startTimeField.clear();
    }
    if (endDatePicker != null) {
      endDatePicker.setValue(null);
    }
    if (endTimeField != null) {
      endTimeField.clear();
    }
    hideStatus();
    if (createButton != null) {
      createButton.setDisable(false);
    }
  }

  private String extractMessage(String body, String fallback) {
    try {
      return MAPPER.readTree(body).path("message").asText(fallback);
    } catch (Exception e) {
      return fallback;
    }
  }
}
