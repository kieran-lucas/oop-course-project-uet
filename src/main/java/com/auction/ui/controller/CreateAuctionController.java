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
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.control.skin.DatePickerSkin;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.util.Duration;
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

  @FXML
  private void initialize() {
    installDatePickerMotion(startDatePicker);
    installDatePickerMotion(endDatePicker);
    configureDatePicker(startDatePicker);
    configureDatePicker(endDatePicker);
  }

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
      showStatus("Please choose a product.", true);
      return;
    }

    BigDecimal startingPrice;
    try {
      startingPrice = new BigDecimal(priceText.replace(",", ""));
      if (startingPrice.compareTo(BigDecimal.ZERO) <= 0) {
        showStatus("Starting price must be greater than 0.", true);
        return;
      }
    } catch (NumberFormatException e) {
      showStatus("That starting price doesn't look valid.", true);
      return;
    }

    if (startDate == null || startTimeStr.isEmpty()) {
      showStatus("Please enter a start date and time.", true);
      return;
    }
    if (endDate == null || endTimeStr.isEmpty()) {
      showStatus("Please enter an end date and time.", true);
      return;
    }

    LocalDateTime startTime;
    LocalDateTime endTime;
    try {
      startTime = LocalDateTime.of(startDate, LocalTime.parse(startTimeStr, TIME_FMT));
      endTime = LocalDateTime.of(endDate, LocalTime.parse(endTimeStr, TIME_FMT));
    } catch (DateTimeParseException e) {
      showStatus("Invalid time format. Please use HH:mm (e.g. 09:00).", true);
      return;
    }

    if (!endTime.isAfter(startTime)) {
      showStatus("End time must be after start time.", true);
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
                  // FIX: Hiển thị thông báo thành công, sau đó navigate về auction-list
                  Platform.runLater(
                      () -> {
                        showStatus("Auction created successfully.", false);
                        createButton.setDisable(false);
                      });
                  Thread.sleep(1500);
                  Platform.runLater(
                      () -> SceneManager.getInstance().navigateTo("auction-list.fxml"));
                } else {
                  String msg = extractMessage(response.body(), "Couldn't create the auction.");
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
                      showStatus("Unable to reach the server.", true);
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
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> response = RestClient.get("/api/items?sellerId=" + sellerId);
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
                                        ? "Choose one of your products"
                                        : item.getName());
                              }
                            });
                        if (items.isEmpty()) {
                          showStatus("You don't have any products yet. Create one first.", true);
                        }
                      });
                }
              } catch (Exception e) {
                LOGGER.error("Lỗi load danh sách sản phẩm", e);
                Platform.runLater(() -> showStatus("Couldn't load your product list.", true));
              }
            });
  }

  // ========== PRIVATE HELPERS ==========

  /**
   * Hiển thị thông báo kết quả trên statusLabel, áp dụng CSS class tương ứng.
   *
   * @param msg nội dung thông báo
   * @param isError {@code true} → class {@code error-label} (đỏ); {@code false} → {@code
   *     status-label} (xanh)
   */
  private void showStatus(String msg, boolean isError) {
    statusLabel.setText(msg);
    statusLabel.setStyle("");
    statusLabel.getStyleClass().setAll(isError ? "error-label" : "status-label");
    statusLabel.setVisible(true);
    statusLabel.setManaged(true);
  }

  /** Ẩn statusLabel, giải phóng layout space và reset về CSS class mặc định. */
  private void hideStatus() {
    statusLabel.setVisible(false);
    statusLabel.setManaged(false);
    statusLabel.setStyle("");
    statusLabel.getStyleClass().setAll("status-label");
  }

  /**
   * Xóa trắng toàn bộ form và reset trạng thái nút tạo phiên. Gọi trong {@link #onNavigatedTo()} để
   * đảm bảo form sạch mỗi lần vào màn hình.
   */
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

  /**
   * Trích xuất trường {@code message} từ JSON body phản hồi lỗi của server. Trả về {@code fallback}
   * nếu body không hợp lệ.
   */
  private String extractMessage(String body, String fallback) {
    try {
      return MAPPER.readTree(body).path("message").asText(fallback);
    } catch (Exception e) {
      return fallback;
    }
  }

  /**
   * Adds a subtle scale pulse when the picker gains focus or opens, so the field feels consistent
   * with the rest of the animated UI.
   */
  private void installDatePickerMotion(DatePicker picker) {
    if (picker == null) {
      return;
    }

    picker
        .focusedProperty()
        .addListener((obs, wasFocused, isFocused) -> animateDatePicker(picker, isFocused));
    picker
        .showingProperty()
        .addListener(
            (obs, wasShowing, isShowing) -> {
              animateDatePicker(picker, isShowing);
            });
  }

  private void animateDatePicker(DatePicker picker, boolean active) {
    ScaleTransition existing = (ScaleTransition) picker.getProperties().get("datePickerPulse");
    if (existing != null) {
      existing.stop();
    }

    ScaleTransition pulse = new ScaleTransition(Duration.millis(active ? 160 : 120), picker);
    pulse.setToX(active ? 1.01 : 1.0);
    pulse.setToY(active ? 1.01 : 1.0);
    pulse.setInterpolator(Interpolator.EASE_OUT);
    picker.getProperties().put("datePickerPulse", pulse);
    pulse.playFromStart();
  }

  private void configureDatePicker(DatePicker picker) {
    if (picker == null) {
      return;
    }

    if (!picker.getStyleClass().contains("auction-calendar-glass-picker")) {
      picker.getStyleClass().add("auction-calendar-glass-picker");
    }

    GlassCalendarState state = new GlassCalendarState();
    picker.getProperties().put("glassCalendarState", state);

    picker.setDayCellFactory(dp -> new GlassDateCell(picker, state));
    picker.valueProperty().addListener((obs, oldValue, newValue) -> state.refreshAll());
    state.hoverProgress.addListener((obs, oldValue, newValue) -> state.refreshAll());
    state.hoveredCell.addListener((obs, oldValue, newValue) -> state.refreshAll());
    picker
        .skinProperty()
        .addListener(
            (obs, oldSkin, newSkin) ->
                Platform.runLater(() -> applyPopupStyleWithRetry(picker, 3)));
    picker
        .showingProperty()
        .addListener(
            (obs, wasShowing, isShowing) -> {
              if (isShowing) {
                applyPopupStyleWithRetry(picker, 3);
              }
            });
  }

  private void applyPopupStyleWithRetry(DatePicker picker, int attemptsRemaining) {
    if (picker == null || attemptsRemaining < 0) {
      return;
    }
    if (applyPopupStyle(picker)) {
      return;
    }
    Platform.runLater(() -> applyPopupStyleWithRetry(picker, attemptsRemaining - 1));
  }

  private boolean applyPopupStyle(DatePicker picker) {
    if (!(picker.getSkin() instanceof DatePickerSkin skin)) {
      return false;
    }
    Region popup = (Region) skin.getPopupContent();
    if (popup == null) {
      return false;
    }
    if (!popup.getStyleClass().contains("auction-calendar-glass-popup")) {
      popup.getStyleClass().add("auction-calendar-glass-popup");
    }
    return true;
  }

  private final class GlassDateCell extends DateCell {
    private final DatePicker picker;
    private final GlassCalendarState state;
    private final DropShadow shadow = new DropShadow();

    private GlassDateCell(DatePicker picker, GlassCalendarState state) {
      this.picker = picker;
      this.state = state;
      setFocusTraversable(false);
      setPickOnBounds(false);
      // Shadow is permanently attached. Radius/offset are constants so the cell's effect-extended
      // bounds never change. Per-hover visual is achieved by mutating shadow.color (paint-only) —
      // setEffect is never called again, so neighboring cells never see a bounds invalidation when
      // hover crosses cell boundaries (which previously caused the popup to jump).
      shadow.setColor(Color.TRANSPARENT);
      shadow.setRadius(14);
      shadow.setOffsetY(1.2);
      setEffect(shadow);

      hoverProperty()
          .addListener(
              (obs, wasHover, isHover) -> {
                if (isHover) {
                  state.hoveredCell.set(this);
                  animateHover(state, 1.0);
                } else if (state.hoveredCell.get() == this) {
                  animateHover(state, 0.0);
                  Platform.runLater(
                      () -> {
                        if (!isHover() && state.hoveredCell.get() == this) {
                          state.hoveredCell.set(null);
                        }
                      });
                }
              });

      state.hoveredCell.addListener((obs, oldValue, newValue) -> refreshAppearance());
      state.hoverProgress.addListener((obs, oldValue, newValue) -> refreshAppearance());
      picker.valueProperty().addListener((obs, oldValue, newValue) -> refreshAppearance());
    }

    @Override
    public void updateItem(LocalDate item, boolean empty) {
      super.updateItem(item, empty);
      refreshAppearance();
    }

    private void refreshAppearance() {
      applyDayCellStyle(this, picker, state, shadow);
    }
  }

  private static final class GlassCalendarState {
    private final javafx.beans.property.ObjectProperty<DateCell> hoveredCell =
        new javafx.beans.property.SimpleObjectProperty<>(null);
    private final DoubleProperty hoverProgress = new SimpleDoubleProperty(0.0);
    private final Timeline hoverTimeline = new Timeline();

    private void refreshAll() {
      // Cells listen directly to hoverProgress/hoveredCell/picker.valueProperty.
    }
  }

  private void animateHover(GlassCalendarState state, double target) {
    state.hoverTimeline.stop();
    state
        .hoverTimeline
        .getKeyFrames()
        .setAll(
            new KeyFrame(
                Duration.ZERO,
                new KeyValue(
                    state.hoverProgress, state.hoverProgress.get(), Interpolator.EASE_BOTH)),
            new KeyFrame(
                Duration.millis(target > state.hoverProgress.get() ? 185 : 145),
                new KeyValue(state.hoverProgress, target, Interpolator.EASE_BOTH)));
    state.hoverTimeline.playFromStart();
  }

  // Cached cell styles. KEY INSIGHT: setBackground/setBorder with a different object reference
  // triggers Region.backgroundProperty.invalidated() → requestParentLayout() → propagates up
  // GridPane → popupContent.layoutBoundsProperty change → ComboBoxPopupControl's popupListener
  // fires picker.requestLayout() → reconfigurePopup() → popup window jumps. To eliminate the
  // jitter on mouse hover, the cell background/border MUST NOT change on hover state transitions.
  // We only swap backgrounds for selected/today (which are stable states). Hover is signaled via
  // shadow effect + opacity, both of which are paint-dirty only and never invalidate layout.
  private static final Color DEFAULT_CELL_TEXT = Color.rgb(51, 65, 85);
  private static final Color TODAY_TEXT = Color.rgb(14, 91, 181);
  private static final Color HOVER_TEXT = Color.rgb(15, 23, 42);

  private static final javafx.scene.layout.Border TRANSPARENT_CELL_BORDER =
      new javafx.scene.layout.Border(
          new javafx.scene.layout.BorderStroke(
              Color.TRANSPARENT,
              javafx.scene.layout.BorderStrokeStyle.SOLID,
              new CornerRadii(999),
              new javafx.scene.layout.BorderWidths(1)));

  private static final Background SELECTED_CELL_BG =
      new Background(
          new BackgroundFill(Color.rgb(35, 92, 226, 0.92), new CornerRadii(999), Insets.EMPTY),
          new BackgroundFill(Color.rgb(16, 102, 204, 0.84), new CornerRadii(999), new Insets(1.1)));
  private static final javafx.scene.layout.Border SELECTED_CELL_BORDER =
      new javafx.scene.layout.Border(
          new javafx.scene.layout.BorderStroke(
              Color.rgb(255, 255, 255, 0.52),
              javafx.scene.layout.BorderStrokeStyle.SOLID,
              new CornerRadii(999),
              new javafx.scene.layout.BorderWidths(1)));

  private static final Background TODAY_CELL_BG =
      new Background(
          new BackgroundFill(Color.rgb(255, 255, 255, 0.20), new CornerRadii(999), Insets.EMPTY),
          new BackgroundFill(
              Color.rgb(191, 219, 254, 0.64), new CornerRadii(999), new Insets(1.1)));
  private static final javafx.scene.layout.Border TODAY_CELL_BORDER =
      new javafx.scene.layout.Border(
          new javafx.scene.layout.BorderStroke(
              Color.rgb(37, 99, 235, 0.68),
              javafx.scene.layout.BorderStrokeStyle.SOLID,
              new CornerRadii(999),
              new javafx.scene.layout.BorderWidths(1)));

  private static final Background SELECTED_TODAY_CELL_BG =
      new Background(
          new BackgroundFill(Color.rgb(31, 111, 235, 0.98), new CornerRadii(999), Insets.EMPTY),
          new BackgroundFill(Color.rgb(15, 95, 184, 0.94), new CornerRadii(999), new Insets(1.1)));
  private static final javafx.scene.layout.Border SELECTED_TODAY_CELL_BORDER =
      new javafx.scene.layout.Border(
          new javafx.scene.layout.BorderStroke(
              Color.rgb(255, 255, 255, 0.70),
              javafx.scene.layout.BorderStrokeStyle.SOLID,
              new CornerRadii(999),
              new javafx.scene.layout.BorderWidths(1)));

  // Cached shadow tints. shadow.setColor(SAME_REF) is a no-op in JavaFX (StyleableObjectProperty
  // identity check), so the per-hover refresh is genuinely free for the most common states.
  private static final Color SHADOW_HIDDEN = Color.TRANSPARENT;
  private static final Color SHADOW_HOVER = Color.rgb(21, 101, 192, 0.36);
  private static final Color SHADOW_SELECTED = Color.rgb(21, 101, 192, 0.32);
  private static final Color SHADOW_TODAY = Color.rgb(21, 101, 192, 0.20);
  private static final Color SHADOW_SELECTED_TODAY = Color.rgb(21, 101, 192, 0.40);

  private void applyDayCellStyle(
      DateCell cell, DatePicker picker, GlassCalendarState state, DropShadow shadow) {
    LocalDate item = cell.getItem();
    if (cell.isEmpty() || item == null) {
      cell.setBackground(Background.EMPTY);
      cell.setBorder(javafx.scene.layout.Border.EMPTY);
      shadow.setColor(SHADOW_HIDDEN);
      if (cell.getEffect() != shadow) {
        cell.setEffect(shadow);
      }
      cell.setOpacity(1.0);
      cell.setTextFill(Color.TRANSPARENT);
      return;
    }

    if (cell.getEffect() != shadow) {
      cell.setEffect(shadow);
    }

    DateCell hovered = state.hoveredCell.get();
    boolean isHovered = hovered == cell;
    boolean hasHover = hovered != null;
    boolean selected = picker.getValue() != null && picker.getValue().equals(item);
    boolean today = LocalDate.now().equals(item);
    boolean previousMonth = cell.getStyleClass().contains("previous-month");
    boolean nextMonth = cell.getStyleClass().contains("next-month");
    boolean outOfMonth =
        previousMonth
            || nextMonth
            || (picker.getValue() != null && item.getMonth() != picker.getValue().getMonth());

    // Background/border: pick from cached constants. Same-reference setBackground/setBorder is a
    // no-op in JavaFX (ObjectPropertyBase.set checks identity), so per-hover refreshAppearance
    // calls become free as long as no background-affecting state actually changed.
    Background bg;
    javafx.scene.layout.Border bd;
    Color text;
    if (selected && today) {
      bg = SELECTED_TODAY_CELL_BG;
      bd = SELECTED_TODAY_CELL_BORDER;
      text = Color.WHITE;
    } else if (selected) {
      bg = SELECTED_CELL_BG;
      bd = SELECTED_CELL_BORDER;
      text = Color.WHITE;
    } else if (today) {
      bg = TODAY_CELL_BG;
      bd = TODAY_CELL_BORDER;
      text = TODAY_TEXT;
    } else {
      bg = Background.EMPTY;
      bd = TRANSPARENT_CELL_BORDER;
      text = isHovered ? HOVER_TEXT : DEFAULT_CELL_TEXT;
    }
    cell.setBackground(bg);
    cell.setBorder(bd);

    // Shadow color: cached constants so setColor with same reference is a no-op. Radius/offset
    // were set once at construction and never mutated again — this keeps the cell's effect-bounds
    // stable so hover transitions cannot trigger any bounds-related layout invalidation.
    Color shadowColor;
    if (selected && today) {
      shadowColor = SHADOW_SELECTED_TODAY;
    } else if (selected) {
      shadowColor = SHADOW_SELECTED;
    } else if (today) {
      shadowColor = SHADOW_TODAY;
    } else if (isHovered) {
      shadowColor = SHADOW_HOVER;
    } else {
      shadowColor = SHADOW_HIDDEN;
    }
    shadow.setColor(shadowColor);

    // Opacity: dims non-hovered cells when something is hovered, and out-of-month cells. Pure
    // paint property — no layout impact.
    double opacity = 1.0;
    if (!selected && !today && !isHovered) {
      if (outOfMonth) {
        opacity = hasHover ? 0.45 : 0.55;
      } else if (hasHover) {
        opacity = 0.58;
      }
    }

    cell.setTextFill(text);
    cell.setOpacity(opacity);
  }
}