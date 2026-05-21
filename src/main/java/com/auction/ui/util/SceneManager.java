package com.auction.ui.util;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.event.EventTarget;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton quản lý toàn bộ điều hướng màn hình (navigation) trong ứng dụng JavaFX.
 *
 * <h2>Kiến trúc — Single Scene + StackPane swap (tương tự SPA trong web)</h2>
 *
 * <ul>
 *   <li><b>1 Scene duy nhất</b> — không bao giờ tạo hay hủy {@link Scene} trong suốt vòng đời ứng
 *       dụng; tránh overhead khởi tạo Stage và mất trạng thái CSS.
 *   <li><b>1 StackPane root</b> — chuyển màn hình đồng nghĩa với việc swap {@code children} của
 *       StackPane, không tạo window mới.
 *   <li><b>Lazy load + cache vĩnh viễn</b> — mỗi FXML chỉ được parse và instantiate controller
 *       <em>một lần duy nhất</em> khi lần đầu điều hướng tới. Từ lần thứ hai, view và controller
 *       được lấy thẳng từ {@link HashMap} → tốc độ hiển thị nhanh, giữ nguyên state.
 *   <li><b>CSS load 1 lần</b> — stylesheet gắn vào {@link Scene} một lần, áp dụng tự động cho mọi
 *       FXML được swap vào sau đó.
 * </ul>
 *
 * <h2>Vòng đời điều hướng</h2>
 *
 * Khi gọi bất kỳ method {@code navigateTo} / {@code navigateBack} nào, thứ tự thực thi là:
 *
 * <ol>
 *   <li>{@link Navigable#onNavigatedFrom()} — gọi trên controller <em>đang hiển thị</em> để cleanup
 *       tài nguyên.
 *   <li>Load hoặc lấy từ cache view của màn hình đích.
 *   <li>Swap {@code rootContainer.children} sang view mới.
 *   <li>{@link Navigable#onDataReceived(Object)} — gọi nếu có data được truyền kèm.
 *   <li>{@link Navigable#onNavigatedTo()} — gọi để controller refresh UI.
 *   <li>Cập nhật global notification badge.
 * </ol>
 *
 * <h2>Cách sử dụng</h2>
 *
 * <pre>{@code
 * // Điều hướng đơn giản
 * SceneManager.getInstance().navigateTo("auction-list.fxml");
 *
 * // Điều hướng kèm data
 * SceneManager.getInstance().navigateTo("auction-detail.fxml", auctionId);
 *
 * // Quay lại màn hình trước; fallback về "auction-list.fxml" nếu stack rỗng
 * SceneManager.getInstance().navigateBack("auction-list.fxml");
 * }</pre>
 *
 * <p><b>Khởi tạo:</b> Phải gọi {@link #init(Stage, double, double)} trong {@code ClientApp.start()}
 * trước khi sử dụng bất kỳ method nào khác.
 */
public class SceneManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(SceneManager.class);

  public static final double TITLE_BAR_HEIGHT = 32;
  private static final double WINDOW_DRAG_HEIGHT = TITLE_BAR_HEIGHT;
  private static final double WINDOW_RESIZE_MARGIN = 7;
  private static final double WINDOW_BUTTON_WIDTH = 46;
  private static final double WINDOW_BUTTON_HEIGHT = TITLE_BAR_HEIGHT;

  // ========== SINGLETON ==========

  /** Instance duy nhất của SceneManager — khởi tạo qua {@link #init}. */
  private static SceneManager instance;

  /**
   * Trả về instance đã được khởi tạo.
   *
   * @return instance hiện tại của SceneManager
   * @throws IllegalStateException nếu {@link #init(Stage, double, double)} chưa được gọi
   */
  public static SceneManager getInstance() {
    if (instance == null) {
      throw new IllegalStateException(
          "SceneManager chưa được khởi tạo! Gọi init() trong ClientApp.start() trước.");
    }
    return instance;
  }

  /**
   * Khởi tạo SceneManager — chỉ gọi <b>đúng một lần</b> trong {@code ClientApp.start()}.
   *
   * <p>Nếu gọi lại sau khi đã khởi tạo, method sẽ log cảnh báo và trả về instance cũ thay vì tạo
   * instance mới, nhằm tránh tình trạng reset state giữa chừng.
   *
   * @param primaryStage Stage chính do JavaFX Application cung cấp
   * @param width chiều rộng ban đầu của cửa sổ (px)
   * @param height chiều cao ban đầu của cửa sổ (px)
   * @return instance SceneManager đã khởi tạo
   */
  public static SceneManager init(Stage primaryStage, double width, double height) {
    if (instance != null) {
      LOGGER.warn("SceneManager đã được khởi tạo trước đó, trả về instance cũ.");
      return instance;
    }
    instance = new SceneManager(primaryStage, width, height);
    LOGGER.info("SceneManager khởi tạo thành công — {}x{}", width, height);
    return instance;
  }

  // ========== CACHE ==========

  /**
   * Cache view (node gốc của FXML) đã được parse.
   *
   * <ul>
   *   <li>Key: tên file FXML (ví dụ: {@code "auction-list.fxml"})
   *   <li>Value: {@link Parent} — root node trả về bởi {@link FXMLLoader}
   * </ul>
   *
   * <p>Entry được thêm vào lần đầu tiên FXML được load và tồn tại suốt phiên làm việc (trừ khi bị
   * xóa bởi {@link #logout()} hoặc {@link #invalidateCache(String)}).
   */
  private final Map<String, Parent> viewCache = new HashMap<>();

  /**
   * Cache controller tương ứng với mỗi FXML đã load.
   *
   * <ul>
   *   <li>Key: tên file FXML — phải khớp với key trong {@link #viewCache}
   *   <li>Value: controller object (kiểu thực tế phụ thuộc vào {@code fx:controller} trong FXML)
   * </ul>
   *
   * <p>Controller được cache giúp giữ nguyên state (dữ liệu đã load, trạng thái UI...) khi người
   * dùng điều hướng đi rồi quay lại. Nếu cần reset hoàn toàn, dùng {@link #invalidateCache}.
   */
  private final Map<String, Object> controllerCache = new HashMap<>();

  // ========== CORE COMPONENTS ==========

  /** Stage chính của ứng dụng — được truyền vào lúc khởi tạo và không thay đổi. */
  private final Stage primaryStage;

  /**
   * Scene duy nhất của ứng dụng — bọc quanh {@link #rootContainer}.
   *
   * <p>CSS stylesheet được gắn vào đây một lần duy nhất, áp dụng cho toàn bộ node trong scene
   * graph, kể cả các FXML được swap vào sau này.
   */
  private final Scene scene;

  /**
   * Container gốc — giữ app content, overlay tạm thời và titlebar custom cùng một scene.
   *
   * <p>Root này giữ chrome toàn cục (window controls, badge) ổn định qua mọi navigation; FXML hiện
   * tại được swap bên trong {@link #viewHost}.
   */
  private final StackPane rootContainer;

  /** Frame chính chừa titlebar ở trên và đặt FXML hiện tại ở vùng center. */
  private final BorderPane appFrame;

  /** Layer chứa FXML hiện tại; titlebar nằm ngoài layer này để độc lập với UI. */
  private final StackPane viewHost;

  /**
   * Tên FXML của màn hình đang hiển thị.
   *
   * <p>Dùng để:
   *
   * <ul>
   *   <li>Gọi {@link Navigable#onNavigatedFrom()} trước khi rời màn hình hiện tại.
   *   <li>Kiểm tra màn hình hiện tại để ẩn/hiện global badge (ví dụ: ẩn khi đang ở {@code
   *       auction-list.fxml}).
   * </ul>
   */
  private String currentFxml;

  /**
   * Badge thông báo nổi toàn cục, hiển thị số lượng thông báo chưa đọc ở góc trên-phải.
   *
   * <p>Badge này active ở mọi màn hình <em>ngoại trừ</em> {@code auction-list.fxml} — màn hình đó
   * đã có nút bell riêng, nên badge toàn cục sẽ tự động ẩn khi điều hướng tới đó.
   */
  private Label globalBadgeLabel;

  /** Titlebar custom độc lập với UI của từng FXML. */
  private StackPane titleBar;

  /** Custom window controls rendered inside the titlebar for the undecorated stage. */
  private HBox windowControls;

  private Button maximizeButton;
  private ParallelTransition titleBarIntroTransition;
  private ParallelTransition titleSheenTransition;
  private TranslateTransition titleLedSweepTransition;

  private boolean movingWindow;
  private ResizeDirection activeResizeDirection = ResizeDirection.NONE;
  private double dragOffsetX;
  private double dragOffsetY;
  private double resizeStartScreenX;
  private double resizeStartScreenY;
  private double resizeStartStageX;
  private double resizeStartStageY;
  private double resizeStartWidth;
  private double resizeStartHeight;

  /**
   * Stack lịch sử điều hướng — hỗ trợ tính năng quay lại ({@link #navigateBack}).
   *
   * <p>Mỗi lần gọi {@link #navigateTo}, {@code currentFxml} được push vào stack trước khi chuyển.
   * Khi gọi {@link #navigateBack}, pop một entry ra và điều hướng tới đó. Stack bị xóa hoàn toàn
   * khi {@link #logout()} để tránh quay lại các màn hình yêu cầu xác thực.
   */
  private final Deque<String> backStack = new ArrayDeque<>();

  // ========== SESSION STATE ==========

  /** JWT token của phiên đăng nhập hiện tại — đính kèm vào header các request cần auth. */
  private String jwtToken;

  /** Tên đăng nhập (username) của người dùng đang đăng nhập. */
  private String currentUsername;

  /**
   * Vai trò (role) của người dùng đang đăng nhập — ví dụ: {@code "ROLE_ADMIN"}, {@code
   * "ROLE_USER"}. Dùng để kiểm tra quyền truy cập các tính năng.
   */
  private String currentRole;

  /** ID của người dùng đang đăng nhập — dùng khi gọi API cần định danh user. */
  private Long currentUserId;

  // ========== CONSTRUCTOR (private — Singleton) ==========

  /**
   * Khởi tạo các thành phần cốt lõi của SceneManager.
   *
   * <p>Thứ tự khởi tạo:
   *
   * <ol>
   *   <li>Tạo {@link StackPane} làm root container.
   *   <li>Tạo {@link Scene} bọc quanh root container với kích thước được chỉ định.
   *   <li>Load CSS từ {@code /css/style.css} vào Scene (nếu không tìm thấy, log warning và tiếp tục
   *       — không crash).
   *   <li>Gắn Scene vào Stage.
   *   <li>Khởi tạo global notification badge.
   * </ol>
   *
   * @param primaryStage Stage chính từ JavaFX Application
   * @param width chiều rộng cửa sổ
   * @param height chiều cao cửa sổ
   */
  private SceneManager(Stage primaryStage, double width, double height) {
    this.primaryStage = primaryStage;
    this.rootContainer = new StackPane();
    this.appFrame = new BorderPane();
    this.viewHost = new StackPane();
    this.scene = new Scene(rootContainer, width, height);
    appFrame.setTop(createTitleBarSpacer());
    appFrame.setCenter(viewHost);
    rootContainer.getChildren().add(appFrame);

    // Load CSS theme 1 lần duy nhất — áp dụng cho tất cả FXML
    URL cssUrl = getClass().getResource("/css/style.css");
    if (cssUrl != null) {
      scene.getStylesheets().add(cssUrl.toExternalForm());
    } else {
      // FIX: Không crash nếu thiếu CSS, chỉ log warning
      LOGGER.warn("Không tìm thấy /css/style.css — ứng dụng chạy không có theme.");
    }

    primaryStage.setScene(scene);
    setupGlobalNotificationBadge();
    setupWindowTitleBar();
    installWindowChromeHandlers();
  }

  private Region createTitleBarSpacer() {
    Region spacer = new Region();
    spacer.setMinHeight(TITLE_BAR_HEIGHT);
    spacer.setPrefHeight(TITLE_BAR_HEIGHT);
    spacer.setMaxHeight(TITLE_BAR_HEIGHT);
    return spacer;
  }

  // ========== GLOBAL NOTIFICATION BADGE ==========

  /**
   * Khởi tạo badge thông báo toàn cục và đăng ký lắng nghe {@code unreadCountProperty}.
   *
   * <p>Badge được đặt ở góc trên-phải của {@link #rootContainer} bằng {@link
   * StackPane#setAlignment} và {@link StackPane#setMargin}, nổi lên trên mọi màn hình được swap
   * vào. Badge khởi đầu ở trạng thái ẩn ({@code visible=false, managed=false}).
   *
   * <p>Một {@link ChangeListener} lắng nghe {@code NotificationStore.unreadCountProperty()} và gọi
   * {@link #updateGlobalBadge(int)} mỗi khi số lượng thông báo chưa đọc thay đổi.
   *
   * <p><b>Ngoại lệ:</b> {@code auction-list.fxml} đã có bell button riêng tích hợp counter, nên
   * badge toàn cục tự động ẩn khi người dùng đang ở màn hình đó.
   */
  private void setupGlobalNotificationBadge() {
    globalBadgeLabel = new Label();
    globalBadgeLabel.getStyleClass().add("notification-badge");
    StackPane.setAlignment(globalBadgeLabel, Pos.TOP_RIGHT);
    StackPane.setMargin(globalBadgeLabel, new Insets(TITLE_BAR_HEIGHT + 12, 12, 0, 0));
    globalBadgeLabel.setVisible(false);
    globalBadgeLabel.setManaged(false);
    rootContainer.getChildren().add(globalBadgeLabel);

    ChangeListener<Number> listener = (obs, oldVal, newVal) -> updateGlobalBadge(newVal.intValue());
    com.auction.util.NotificationStore.getInstance().unreadCountProperty().addListener(listener);
  }

  /**
   * Cập nhật hiển thị badge toàn cục dựa trên số thông báo chưa đọc và màn hình hiện tại.
   *
   * <p>Logic hiển thị:
   *
   * <ul>
   *   <li>Nếu đang ở {@code auction-list.fxml} → luôn ẩn (màn hình đó có bell riêng).
   *   <li>Nếu {@code count > 0} → hiển thị, nội dung là số đếm hoặc {@code "99+"} nếu vượt quá 99.
   *   <li>Nếu {@code count == 0} → ẩn badge.
   * </ul>
   *
   * @param count số lượng thông báo chưa đọc hiện tại
   */
  private void updateGlobalBadge(int count) {
    if ("auction-list.fxml".equals(currentFxml)) {
      globalBadgeLabel.setVisible(false);
      globalBadgeLabel.setManaged(false);
      return;
    }
    if (count > 0) {
      globalBadgeLabel.setText(count > 99 ? "99+" : String.valueOf(count));
      globalBadgeLabel.setVisible(true);
      globalBadgeLabel.setManaged(true);
    } else {
      globalBadgeLabel.setVisible(false);
      globalBadgeLabel.setManaged(false);
    }
  }

  // ========== CUSTOM WINDOW CHROME ==========

  private void setupWindowTitleBar() {
    titleBar = new StackPane();
    titleBar.setMinHeight(TITLE_BAR_HEIGHT);
    titleBar.setPrefHeight(TITLE_BAR_HEIGHT);
    titleBar.setMaxHeight(TITLE_BAR_HEIGHT);
    titleBar.setMaxWidth(Double.MAX_VALUE);
    titleBar.setPickOnBounds(true);
    titleBar.getStyleClass().add("window-title-bar");
    StackPane.setAlignment(titleBar, Pos.TOP_LEFT);

    HBox titleContent = new HBox(0);
    titleContent.setAlignment(Pos.CENTER_LEFT);
    titleContent.setMaxWidth(Double.MAX_VALUE);
    titleContent.getStyleClass().add("window-title-content");

    Label titleLabel = new Label("Online Auction System");
    titleLabel.getStyleClass().add("window-title");
    titleLabel.setStyle(
        "-fx-font-family: 'Lexend';"
            + " -fx-font-size: 13.5px;"
            + " -fx-font-weight: bold;"
            + " -fx-text-fill: #1565C0;");

    Region appMark = new Region();
    appMark.getStyleClass().add("window-app-mark");

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    windowControls = new HBox(0);
    windowControls.setAlignment(Pos.CENTER);
    windowControls.setPickOnBounds(false);
    windowControls.setMinSize(WINDOW_BUTTON_WIDTH * 3, WINDOW_BUTTON_HEIGHT);
    windowControls.setPrefSize(WINDOW_BUTTON_WIDTH * 3, WINDOW_BUTTON_HEIGHT);
    windowControls.setMaxSize(WINDOW_BUTTON_WIDTH * 3, WINDOW_BUTTON_HEIGHT);
    windowControls.getStyleClass().add("window-controls");

    Button minimizeButton =
        createWindowControlButton("window-minimize-button", "window-minimize-icon", "Thu nhỏ");
    minimizeButton.setOnAction(event -> minimizeWindow());

    maximizeButton =
        createWindowControlButton("window-maximize-button", "window-maximize-icon", "Phóng to");
    maximizeButton.setOnAction(event -> toggleMaximizeWindow());
    primaryStage
        .maximizedProperty()
        .addListener((obs, wasMaximized, isMaximized) -> updateMaximizeButtonState(isMaximized));

    Button closeButton =
        createWindowControlButton("window-close-button", "window-close-icon", "Đóng");
    closeButton.setOnAction(event -> closeWindow());

    windowControls.getChildren().addAll(minimizeButton, maximizeButton, closeButton);
    titleContent.getChildren().addAll(appMark, titleLabel, spacer, windowControls);

    Region titleLedBase = new Region();
    titleLedBase.getStyleClass().add("window-title-led-base");
    titleLedBase.setMouseTransparent(true);
    titleLedBase.setMinHeight(1.0);
    titleLedBase.setPrefHeight(1.15);
    titleLedBase.setMaxSize(Double.MAX_VALUE, 1.15);
    StackPane.setAlignment(titleLedBase, Pos.BOTTOM_LEFT);

    Region titleLedPulse = createTitleLedPulse();
    StackPane.setAlignment(titleLedPulse, Pos.BOTTOM_LEFT);

    Region titleSheen = new Region();
    titleSheen.getStyleClass().add("window-title-sheen");
    titleSheen.setMouseTransparent(true);
    titleSheen.setMaxSize(180, TITLE_BAR_HEIGHT);
    StackPane.setAlignment(titleSheen, Pos.TOP_LEFT);

    titleBar.getChildren().addAll(titleContent, titleSheen, titleLedBase, titleLedPulse);
    rootContainer.getChildren().add(titleBar);
    installTitleBarAnimations(titleContent, appMark, titleSheen, titleLedBase, titleLedPulse);
  }

  private void installTitleBarAnimations(
      HBox titleContent,
      Region appMark,
      Region titleSheen,
      Region titleLedBase,
      Node titleLedPulse) {
    titleBar.setOpacity(0);
    titleBar.setTranslateY(-4);
    titleContent.setOpacity(0);
    titleContent.setTranslateX(-7);
    appMark.setScaleX(0.82);
    appMark.setScaleY(0.82);
    titleSheen.setOpacity(0);
    titleSheen.setTranslateX(-190);
    titleLedBase.setOpacity(0);
    titleLedPulse.setOpacity(0);
    titleLedPulse.setTranslateX(-260);

    FadeTransition fadeIn = new FadeTransition(Duration.millis(150), titleBar);
    fadeIn.setToValue(1);
    fadeIn.setInterpolator(Interpolator.EASE_OUT);

    TranslateTransition slideIn = new TranslateTransition(Duration.millis(185), titleBar);
    slideIn.setToY(0);
    slideIn.setInterpolator(Interpolator.EASE_OUT);

    FadeTransition contentFade = new FadeTransition(Duration.millis(220), titleContent);
    contentFade.setToValue(1);
    contentFade.setInterpolator(Interpolator.EASE_OUT);

    TranslateTransition contentSlide = new TranslateTransition(Duration.millis(245), titleContent);
    contentSlide.setToX(0);
    contentSlide.setInterpolator(Interpolator.EASE_OUT);

    ScaleTransition markPop = new ScaleTransition(Duration.millis(240), appMark);
    markPop.setToX(1);
    markPop.setToY(1);
    markPop.setInterpolator(Interpolator.EASE_OUT);

    FadeTransition ledBaseFade = new FadeTransition(Duration.millis(260), titleLedBase);
    ledBaseFade.setToValue(0.82);
    ledBaseFade.setInterpolator(Interpolator.EASE_OUT);

    titleBarIntroTransition =
        new ParallelTransition(fadeIn, slideIn, contentFade, contentSlide, markPop, ledBaseFade);
    titleBarIntroTransition.setOnFinished(
        event -> {
          playTitleSheen(titleSheen, 285, 460);
          startTitleLedSweep(titleLedPulse);
        });
    titleBarIntroTransition.play();

    titleBar.setOnMouseEntered(
        event -> {
          animateTitleLedBase(titleLedBase, 0.95, 150);
          animateTitleLed(titleLedPulse, 1.0, 1.05, 150);
          playTitleSheen(titleSheen, 300, 360);
        });
    titleBar.setOnMouseExited(
        event -> {
          animateTitleLedBase(titleLedBase, 0.82, 190);
          animateTitleLed(titleLedPulse, 0.84, 1.0, 190);
        });
  }

  private Region createTitleLedPulse() {
    Region led = new Region();
    led.getStyleClass().add("window-title-led-pulse");
    led.setMouseTransparent(true);
    led.setMinSize(240, 2.2);
    led.setPrefSize(240, 2.2);
    led.setMaxSize(240, 2.2);
    return led;
  }

  private void startTitleLedSweep(Node titleLed) {
    restartTitleLedSweep(titleLed, titleBar.getWidth());
    titleBar
        .widthProperty()
        .addListener(
            (obs, oldWidth, newWidth) -> restartTitleLedSweep(titleLed, newWidth.doubleValue()));
  }

  private void restartTitleLedSweep(Node titleLed, double width) {
    if (width <= 0) {
      return;
    }
    if (titleLedSweepTransition != null) {
      titleLedSweepTransition.stop();
    }

    titleLed.setOpacity(0.84);
    titleLedSweepTransition = new TranslateTransition(Duration.seconds(4.15), titleLed);
    titleLedSweepTransition.setFromX(-260);
    titleLedSweepTransition.setToX(width + 120);
    titleLedSweepTransition.setCycleCount(javafx.animation.Animation.INDEFINITE);
    titleLedSweepTransition.setInterpolator(Interpolator.LINEAR);
    titleLedSweepTransition.play();
  }

  private void playTitleSheen(Region titleSheen, double travelX, int durationMillis) {
    if (titleSheenTransition != null) {
      titleSheenTransition.stop();
    }
    titleSheen.setTranslateX(-190);
    titleSheen.setOpacity(0);

    TranslateTransition sweep =
        new TranslateTransition(Duration.millis(durationMillis), titleSheen);
    sweep.setToX(travelX);
    sweep.setInterpolator(Interpolator.EASE_OUT);

    FadeTransition glow = new FadeTransition(Duration.millis(durationMillis), titleSheen);
    glow.setFromValue(0);
    glow.setToValue(0.82);
    glow.setAutoReverse(true);
    glow.setCycleCount(2);
    glow.setInterpolator(Interpolator.EASE_BOTH);

    titleSheenTransition = new ParallelTransition(sweep, glow);
    titleSheenTransition.setOnFinished(event -> titleSheen.setOpacity(0));
    titleSheenTransition.play();
  }

  private void animateTitleLedBase(Region titleLedBase, double opacity, int durationMillis) {
    FadeTransition fade = new FadeTransition(Duration.millis(durationMillis), titleLedBase);
    fade.setToValue(opacity);
    fade.setInterpolator(Interpolator.EASE_BOTH);
    fade.play();
  }

  private void animateTitleLed(Node titleLed, double opacity, double scaleX, int durationMillis) {
    FadeTransition fade = new FadeTransition(Duration.millis(durationMillis), titleLed);
    fade.setToValue(opacity);
    fade.setInterpolator(Interpolator.EASE_BOTH);

    ScaleTransition scale = new ScaleTransition(Duration.millis(durationMillis), titleLed);
    scale.setToX(scaleX);
    scale.setInterpolator(Interpolator.EASE_BOTH);

    new ParallelTransition(fade, scale).play();
  }

  private Button createWindowControlButton(
      String buttonStyleClass, String iconStyleClass, String tooltipText) {
    Button button = new Button();
    button.setMnemonicParsing(false);
    button.setFocusTraversable(false);
    button.setMinSize(WINDOW_BUTTON_WIDTH, WINDOW_BUTTON_HEIGHT);
    button.setPrefSize(WINDOW_BUTTON_WIDTH, WINDOW_BUTTON_HEIGHT);
    button.setMaxSize(WINDOW_BUTTON_WIDTH, WINDOW_BUTTON_HEIGHT);
    button.getStyleClass().addAll("window-button", buttonStyleClass);

    Region icon = new Region();
    icon.getStyleClass().add(iconStyleClass);
    button.setGraphic(icon);
    button.setTooltip(new Tooltip(tooltipText));
    installWindowButtonAnimation(button);

    return button;
  }

  private void installWindowButtonAnimation(Button button) {
    button.setOnMouseEntered(event -> animateWindowButtonIcon(button, 1.32, 135));
    button.setOnMouseExited(event -> animateWindowButtonIcon(button, 1.0, 145));
    button.setOnMousePressed(event -> animateWindowButtonIcon(button, 0.82, 75));
    button.setOnMouseReleased(
        event -> animateWindowButtonIcon(button, button.isHover() ? 1.32 : 1.0, 115));
  }

  private void animateWindowButtonIcon(Button button, double scale, int durationMillis) {
    Node graphic = button.getGraphic();
    if (graphic == null) {
      return;
    }
    ScaleTransition transition = new ScaleTransition(Duration.millis(durationMillis), graphic);
    transition.setToX(scale);
    transition.setToY(scale);
    transition.setInterpolator(Interpolator.EASE_BOTH);
    transition.play();
  }

  private void installWindowChromeHandlers() {
    scene.addEventFilter(MouseEvent.MOUSE_MOVED, this::handleWindowMouseMoved);
    scene.addEventFilter(MouseEvent.MOUSE_EXITED, this::handleWindowMouseExited);
    scene.addEventFilter(MouseEvent.MOUSE_PRESSED, this::handleWindowMousePressed);
    scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::handleWindowMouseDragged);
    scene.addEventFilter(MouseEvent.MOUSE_RELEASED, this::handleWindowMouseReleased);
  }

  private void handleWindowMouseMoved(MouseEvent event) {
    if (movingWindow || activeResizeDirection != ResizeDirection.NONE) {
      return;
    }
    scene.setCursor(resolveResizeDirection(event).cursor);
  }

  private void handleWindowMouseExited(MouseEvent event) {
    if (!movingWindow && activeResizeDirection == ResizeDirection.NONE) {
      scene.setCursor(Cursor.DEFAULT);
    }
  }

  private void handleWindowMousePressed(MouseEvent event) {
    if (event.getButton() != MouseButton.PRIMARY) {
      return;
    }

    if (event.getClickCount() == 2 && isWindowDragEvent(event)) {
      toggleMaximizeWindow();
      event.consume();
      return;
    }

    if (primaryStage.isMaximized()) {
      return;
    }

    ResizeDirection resizeDirection = resolveResizeDirection(event);
    if (resizeDirection != ResizeDirection.NONE) {
      beginWindowResize(event, resizeDirection);
      event.consume();
      return;
    }

    if (isWindowDragEvent(event)) {
      movingWindow = true;
      dragOffsetX = event.getScreenX() - primaryStage.getX();
      dragOffsetY = event.getScreenY() - primaryStage.getY();
      event.consume();
    }
  }

  private void handleWindowMouseDragged(MouseEvent event) {
    if (activeResizeDirection != ResizeDirection.NONE) {
      resizeWindow(event);
      event.consume();
      return;
    }

    if (movingWindow) {
      primaryStage.setX(event.getScreenX() - dragOffsetX);
      primaryStage.setY(event.getScreenY() - dragOffsetY);
      event.consume();
    }
  }

  private void handleWindowMouseReleased(MouseEvent event) {
    movingWindow = false;
    activeResizeDirection = ResizeDirection.NONE;
    scene.setCursor(resolveResizeDirection(event).cursor);
  }

  private void beginWindowResize(MouseEvent event, ResizeDirection resizeDirection) {
    activeResizeDirection = resizeDirection;
    resizeStartScreenX = event.getScreenX();
    resizeStartScreenY = event.getScreenY();
    resizeStartStageX = primaryStage.getX();
    resizeStartStageY = primaryStage.getY();
    resizeStartWidth = primaryStage.getWidth();
    resizeStartHeight = primaryStage.getHeight();
  }

  private void resizeWindow(MouseEvent event) {
    double deltaX = event.getScreenX() - resizeStartScreenX;
    double deltaY = event.getScreenY() - resizeStartScreenY;

    if (activeResizeDirection.resizesEast()) {
      primaryStage.setWidth(Math.max(primaryStage.getMinWidth(), resizeStartWidth + deltaX));
    } else if (activeResizeDirection.resizesWest()) {
      resizeFromWest(deltaX);
    }

    if (activeResizeDirection.resizesSouth()) {
      primaryStage.setHeight(Math.max(primaryStage.getMinHeight(), resizeStartHeight + deltaY));
    } else if (activeResizeDirection.resizesNorth()) {
      resizeFromNorth(deltaY);
    }
  }

  private void resizeFromWest(double deltaX) {
    double minWidth = primaryStage.getMinWidth();
    double requestedWidth = resizeStartWidth - deltaX;
    if (requestedWidth <= minWidth) {
      primaryStage.setX(resizeStartStageX + resizeStartWidth - minWidth);
      primaryStage.setWidth(minWidth);
      return;
    }

    primaryStage.setX(resizeStartStageX + deltaX);
    primaryStage.setWidth(requestedWidth);
  }

  private void resizeFromNorth(double deltaY) {
    double minHeight = primaryStage.getMinHeight();
    double requestedHeight = resizeStartHeight - deltaY;
    if (requestedHeight <= minHeight) {
      primaryStage.setY(resizeStartStageY + resizeStartHeight - minHeight);
      primaryStage.setHeight(minHeight);
      return;
    }

    primaryStage.setY(resizeStartStageY + deltaY);
    primaryStage.setHeight(requestedHeight);
  }

  private ResizeDirection resolveResizeDirection(MouseEvent event) {
    if (!primaryStage.isResizable()
        || primaryStage.isMaximized()
        || isInteractiveWindowTarget(event.getTarget())) {
      return ResizeDirection.NONE;
    }

    double sceneX = event.getSceneX();
    double sceneY = event.getSceneY();
    double width = scene.getWidth();
    double height = scene.getHeight();

    boolean west = sceneX <= WINDOW_RESIZE_MARGIN;
    boolean east = sceneX >= width - WINDOW_RESIZE_MARGIN;
    boolean north = sceneY <= WINDOW_RESIZE_MARGIN;
    boolean south = sceneY >= height - WINDOW_RESIZE_MARGIN;

    if (north && west) {
      return ResizeDirection.NORTH_WEST;
    }
    if (north && east) {
      return ResizeDirection.NORTH_EAST;
    }
    if (south && west) {
      return ResizeDirection.SOUTH_WEST;
    }
    if (south && east) {
      return ResizeDirection.SOUTH_EAST;
    }
    if (north) {
      return ResizeDirection.NORTH;
    }
    if (south) {
      return ResizeDirection.SOUTH;
    }
    if (west) {
      return ResizeDirection.WEST;
    }
    if (east) {
      return ResizeDirection.EAST;
    }
    return ResizeDirection.NONE;
  }

  private boolean isWindowDragEvent(MouseEvent event) {
    return event.getSceneY() <= WINDOW_DRAG_HEIGHT && !isInteractiveWindowTarget(event.getTarget());
  }

  private boolean isInteractiveWindowTarget(EventTarget target) {
    Node node = target instanceof Node targetNode ? targetNode : null;
    while (node != null) {
      if (node == windowControls
          || node instanceof ButtonBase
          || node instanceof TextInputControl
          || node instanceof ComboBoxBase
          || node.getStyleClass().contains("username-label")) {
        return true;
      }
      node = node.getParent();
    }
    return false;
  }

  private void minimizeWindow() {
    ParallelTransition transition = createRootTransition(0.985, 0.9, 95);
    transition.setOnFinished(
        event -> {
          resetRootTransitionState();
          primaryStage.setIconified(true);
        });
    transition.play();
  }

  private void toggleMaximizeWindow() {
    boolean maximize = !primaryStage.isMaximized();
    ParallelTransition transition = createRootTransition(0.992, 0.96, 70);
    transition.setOnFinished(
        event -> {
          primaryStage.setMaximized(maximize);
          resetRootTransitionState();
          updateMaximizeButtonState(maximize);
        });
    transition.play();
  }

  private void updateMaximizeButtonState(boolean maximized) {
    if (maximizeButton == null || !(maximizeButton.getGraphic() instanceof Region icon)) {
      return;
    }

    icon.getStyleClass().setAll(maximized ? "window-restore-icon" : "window-maximize-icon");
    maximizeButton.setTooltip(new Tooltip(maximized ? "Khôi phục" : "Phóng to"));
  }

  private void closeWindow() {
    ParallelTransition transition = createRootTransition(0.965, 0.0, 130);
    transition.setOnFinished(event -> Platform.exit());
    transition.play();
  }

  private ParallelTransition createRootTransition(
      double scale, double opacity, int durationMillis) {
    FadeTransition fade = new FadeTransition(Duration.millis(durationMillis), rootContainer);
    fade.setToValue(opacity);
    fade.setInterpolator(Interpolator.EASE_BOTH);

    ScaleTransition scaleTransition =
        new ScaleTransition(Duration.millis(durationMillis), rootContainer);
    scaleTransition.setToX(scale);
    scaleTransition.setToY(scale);
    scaleTransition.setInterpolator(Interpolator.EASE_BOTH);

    return new ParallelTransition(fade, scaleTransition);
  }

  private void resetRootTransitionState() {
    rootContainer.setOpacity(1);
    rootContainer.setScaleX(1);
    rootContainer.setScaleY(1);
  }

  private void setMainView(Node view) {
    removeTransientOverlays();
    viewHost.getChildren().setAll(view);
    restoreChromeLayers();
  }

  private void removeTransientOverlays() {
    rootContainer
        .getChildren()
        .removeIf(node -> node != appFrame && node != globalBadgeLabel && node != titleBar);
  }

  private void restoreChromeLayers() {
    if (!rootContainer.getChildren().contains(appFrame)) {
      rootContainer.getChildren().add(0, appFrame);
    }
    if (globalBadgeLabel != null && !rootContainer.getChildren().contains(globalBadgeLabel)) {
      rootContainer.getChildren().add(globalBadgeLabel);
    }
    if (titleBar != null && !rootContainer.getChildren().contains(titleBar)) {
      rootContainer.getChildren().add(titleBar);
    }

    if (globalBadgeLabel != null) {
      globalBadgeLabel.toFront();
    }
    if (titleBar != null) {
      titleBar.toFront();
    }
  }

  private enum ResizeDirection {
    NONE(Cursor.DEFAULT),
    NORTH(Cursor.N_RESIZE),
    SOUTH(Cursor.S_RESIZE),
    WEST(Cursor.W_RESIZE),
    EAST(Cursor.E_RESIZE),
    NORTH_WEST(Cursor.NW_RESIZE),
    NORTH_EAST(Cursor.NE_RESIZE),
    SOUTH_WEST(Cursor.SW_RESIZE),
    SOUTH_EAST(Cursor.SE_RESIZE);

    private final Cursor cursor;

    ResizeDirection(Cursor cursor) {
      this.cursor = cursor;
    }

    private boolean resizesNorth() {
      return this == NORTH || this == NORTH_WEST || this == NORTH_EAST;
    }

    private boolean resizesSouth() {
      return this == SOUTH || this == SOUTH_WEST || this == SOUTH_EAST;
    }

    private boolean resizesWest() {
      return this == WEST || this == NORTH_WEST || this == SOUTH_WEST;
    }

    private boolean resizesEast() {
      return this == EAST || this == NORTH_EAST || this == SOUTH_EAST;
    }
  }

  // ========== OVERLAY API ==========

  /**
   * Add a transient overlay node on top of the current view inside {@link #rootContainer}.
   *
   * <p>Dùng để hiển thị các dropdown / popup được render <em>như một node trong scene</em> thay vì
   * {@link javafx.stage.Popup} (native window không bo góc đáng tin cậy trên Windows). Overlay sẽ
   * tự bị xoá khi {@link #performNavigate} swap màn hình ({@code setAll(view)}), nhưng caller vẫn
   * nên gọi {@link #removeOverlay(Node)} trong {@code onNavigatedFrom()} để dọn reference.
   *
   * <p>No-op nếu {@code overlay} đã nằm trong rootContainer (tránh add trùng).
   */
  public void addOverlay(Node overlay) {
    if (overlay != null && !rootContainer.getChildren().contains(overlay)) {
      rootContainer.getChildren().add(overlay);
      restoreChromeLayers();
    }
  }

  /** Remove an overlay previously added via {@link #addOverlay(Node)}. No-op nếu không tồn tại. */
  public void removeOverlay(Node overlay) {
    if (overlay != null) {
      rootContainer.getChildren().remove(overlay);
    }
  }

  // ========== NAVIGATION — CORE ==========

  /**
   * Điều hướng tới màn hình được chỉ định và ghi lại màn hình hiện tại vào {@link #backStack}.
   *
   * <p>Gọi {@link Navigable#onNavigatedFrom()} trên controller hiện tại trước khi chuyển, và {@link
   * Navigable#onNavigatedTo()} trên controller đích sau khi chuyển xong.
   *
   * <p>FXML sẽ được lazy load lần đầu, sau đó lấy từ cache — controller giữ nguyên state.
   *
   * @param fxmlName tên file FXML đích, ví dụ: {@code "auction-list.fxml"}
   * @see #navigateTo(String, Object)
   * @see #navigateBack(String)
   */
  public void navigateTo(String fxmlName) {
    if (currentFxml != null) {
      backStack.push(currentFxml);
    }
    performNavigate(fxmlName, null, false);
  }

  /**
   * Điều hướng tới màn hình được chỉ định kèm theo dữ liệu truyền sang controller đích.
   *
   * <p>{@link Navigable#onDataReceived(Object)} được gọi <em>trước</em> {@link
   * Navigable#onNavigatedTo()}, đảm bảo controller đích luôn có đủ dữ liệu trước khi thực hiện
   * logic khởi tạo.
   *
   * <p>Ví dụ: truyền {@code auctionId} từ {@code auction-list.fxml} sang {@code
   * auction-detail.fxml} để load chi tiết phiên đấu giá đúng.
   *
   * @param fxmlName tên file FXML đích
   * @param data dữ liệu bất kỳ cần truyền cho controller đích; controller tự cast về kiểu mong đợi
   * @see #navigateTo(String)
   */
  public void navigateTo(String fxmlName, Object data) {
    if (currentFxml != null) {
      backStack.push(currentFxml);
    }
    performNavigate(fxmlName, data, true);
  }

  /**
   * Điều hướng tới màn hình mới mà không đưa màn hình hiện tại vào backStack.
   *
   * <p>Dùng cho các bước onboarding cần thay thế màn hình trung gian nhưng vẫn giữ lịch sử trước
   * đó, ví dụ welcome → login → register nhưng Back từ register phải về welcome thay vì login.
   *
   * @param fxmlName tên file FXML đích
   */
  public void replaceCurrentWith(String fxmlName) {
    performNavigate(fxmlName, null, false);
  }

  /**
   * Quay lại màn hình trước trong lịch sử điều hướng.
   *
   * <p>Pop một entry từ {@link #backStack} và điều hướng tới đó. Nếu stack rỗng (ví dụ: người dùng
   * vừa đăng nhập và chưa điều hướng đi đâu), sẽ fallback về {@code defaultFxml}.
   *
   * <p><b>Lưu ý:</b> Màn hình hiện tại <em>không</em> được push vào stack — tránh tạo vòng lặp A →
   * B → back → B → back → B.
   *
   * @param defaultFxml FXML hiển thị khi backStack rỗng, ví dụ: {@code "auction-list.fxml"}
   */
  public void navigateBack(String defaultFxml) {
    String target = backStack.isEmpty() ? defaultFxml : backStack.pop();
    performNavigate(target, null, false);
  }

  /**
   * Thực hiện thao tác điều hướng — dùng chung cho {@link #navigateTo}, {@link #navigateBack} và
   * {@link #logout}.
   *
   * <p>Thứ tự thực thi:
   *
   * <ol>
   *   <li>Gọi {@link #notifyNavigatedFrom()} trên controller màn hình hiện tại.
   *   <li>Lấy view từ cache hoặc load mới từ FXML.
   *   <li>Swap {@code rootContainer.children} sang view mới.
   *   <li>Cập nhật {@link #currentFxml}.
   *   <li>Nếu {@code hasData == true}, gọi {@link Navigable#onDataReceived(Object)}.
   *   <li>Gọi {@link Navigable#onNavigatedTo()}.
   *   <li>Đồng bộ trạng thái global badge.
   * </ol>
   *
   * <p>Nếu load FXML thất bại, hiển thị màn hình lỗi thay vì crash ứng dụng.
   *
   * @param fxmlName tên file FXML đích
   * @param data dữ liệu truyền cho controller đích (có thể {@code null} nếu {@code hasData=false})
   * @param hasData {@code true} nếu cần gọi {@link Navigable#onDataReceived(Object)}
   */
  private void performNavigate(String fxmlName, Object data, boolean hasData) {
    notifyNavigatedFrom();

    Parent view;
    try {
      view = viewCache.get(fxmlName);
      if (view == null) {
        view = loadFxml(fxmlName);
      }
    } catch (Exception e) {
      LOGGER.error("performNavigate thất bại khi load '{}': {}", fxmlName, e.getMessage(), e);
      showErrorScreen("Không thể load màn hình: " + fxmlName + "\n" + e.getMessage());
      return;
    }

    setMainView(view);
    currentFxml = fxmlName;

    Object controller = controllerCache.get(fxmlName);
    if (controller instanceof Navigable nav) {
      if (hasData) {
        try {
          nav.onDataReceived(data);
        } catch (Exception e) {
          LOGGER.error("onDataReceived() lỗi ở '{}': {}", fxmlName, e.getMessage(), e);
        }
      }
      try {
        nav.onNavigatedTo();
      } catch (Exception e) {
        LOGGER.error("onNavigatedTo() lỗi ở '{}': {}", fxmlName, e.getMessage(), e);
      }
    }

    LOGGER.debug("Navigated to: {}", fxmlName);

    // Đồng bộ global badge sau mỗi lần chuyển màn hình
    updateGlobalBadge(com.auction.util.NotificationStore.getInstance().getUnreadCount());
  }

  // ========== FXML LOADING ==========

  /**
   * Load FXML từ {@code /ui/fxml/} trong resources, cache cả view lẫn controller.
   *
   * <p>Chỉ được gọi khi {@code viewCache} chưa có entry tương ứng — tức là mỗi FXML chỉ load đúng
   * <em>một lần</em> trong toàn bộ phiên làm việc.
   *
   * <p><b>Kiểm tra URL trước khi load:</b> Thay vì để {@link FXMLLoader} ném {@code
   * NullPointerException} khó debug, method này kiểm tra URL tồn tại trước và ném {@link
   * RuntimeException} với thông báo rõ ràng chỉ đúng đường dẫn cần kiểm tra.
   *
   * @param fxmlName tên file FXML (chỉ tên file, không kèm đường dẫn), ví dụ: {@code
   *     "auction-list.fxml"}
   * @return {@link Parent} — root node của FXML vừa được load
   * @throws RuntimeException nếu file FXML không tồn tại hoặc xảy ra lỗi khi parse
   */
  private Parent loadFxml(String fxmlName) {
    // FIX: Kiểm tra URL tồn tại trước — nếu null thì path sai
    URL url = getClass().getResource("/ui/fxml/" + fxmlName);
    if (url == null) {
      String msg =
          "Không tìm thấy FXML tại: /ui/fxml/"
              + fxmlName
              + " — Kiểm tra file có nằm đúng trong src/main/resources/ui/fxml/ không.";
      LOGGER.error(msg);
      throw new RuntimeException(msg);
    }

    try {
      FXMLLoader loader = new FXMLLoader(url);
      Parent view = loader.load();

      viewCache.put(fxmlName, view);
      controllerCache.put(fxmlName, loader.getController());

      LOGGER.info("Lazy loaded FXML: {}", fxmlName);
      return view;
    } catch (IOException e) {
      LOGGER.error("Không thể load FXML '{}': {}", fxmlName, e.getMessage(), e);
      throw new RuntimeException("Load FXML thất bại: " + fxmlName, e);
    }
  }

  // ========== LIFECYCLE HELPERS ==========

  /**
   * Gọi {@link Navigable#onNavigatedFrom()} trên controller của màn hình đang hiển thị.
   *
   * <p>Được gọi tự động ở đầu mỗi lần thực hiện điều hướng (trong {@link #performNavigate}).
   * Exception bị bắt và log riêng — không để lỗi của màn hình cũ cản trở việc hiển thị màn hình
   * mới.
   */
  private void notifyNavigatedFrom() {
    if (currentFxml != null) {
      Object controller = controllerCache.get(currentFxml);
      if (controller instanceof Navigable nav) {
        try {
          nav.onNavigatedFrom();
        } catch (Exception e) {
          LOGGER.error("onNavigatedFrom() lỗi ở '{}': {}", currentFxml, e.getMessage(), e);
        }
      }
    }
  }

  /**
   * Hiển thị màn hình lỗi đơn giản khi không thể load FXML đích.
   *
   * <p>Thay thế cho việc crash hoặc hiển thị màn hình trắng — giúp developer dễ nhận biết lỗi ngay
   * trên giao diện khi debug, thay vì phải đọc log.
   *
   * @param message mô tả lỗi hiển thị trên màn hình
   */
  private void showErrorScreen(String message) {
    Label errorLabel = new Label("⚠ Lỗi điều hướng:\n" + message);
    errorLabel.setStyle("-fx-text-fill: red; -fx-font-size: 14px; -fx-padding: 20;");
    errorLabel.setWrapText(true);
    setMainView(errorLabel);
  }

  // ========== SESSION MANAGEMENT ==========

  /**
   * Đăng xuất người dùng — xóa toàn bộ thông tin phiên, dừng các tác vụ nền, dọn cache các màn hình
   * yêu cầu xác thực, và điều hướng về {@code welcome.fxml}.
   *
   * <p>Chi tiết các bước thực hiện:
   *
   * <ol>
   *   <li>Xóa JWT token và thông tin user ({@code username}, {@code role}, {@code userId}).
   *   <li>Xóa toàn bộ {@link #backStack} để ngăn quay lại màn hình đã logout.
   *   <li>Dừng {@code BackgroundBidWatcher} — hủy các job đang theo dõi đấu giá.
   *   <li>Xóa {@code NotificationStore} — reset danh sách thông báo và unread count.
   *   <li>Ẩn global notification badge.
   *   <li>Xóa khỏi cache tất cả FXML <em>trừ</em> {@code welcome.fxml}, {@code login.fxml}, {@code
   *       register.fxml} — ba màn hình này không cần auth, giữ lại để tránh load lại.
   *   <li>Điều hướng thẳng về {@code welcome.fxml} qua {@link #performNavigate} (không push vào
   *       backStack vì stack đã bị clear ở bước 2).
   * </ol>
   */
  public void logout() {
    jwtToken = null;
    currentUsername = null;
    currentRole = null;
    currentUserId = null;
    backStack.clear();
    com.auction.util.BackgroundBidWatcher.getInstance().stopAll();
    com.auction.util.NotificationStore.getInstance().clear();
    globalBadgeLabel.setVisible(false);
    globalBadgeLabel.setManaged(false);

    // Dừng background tasks (Timeline, WebSocket...) trên tất cả controller đang cache
    // trước khi xóa cache, tránh leak Timeline chạy sau khi token đã bị clear
    for (Object ctrl : controllerCache.values()) {
      if (ctrl instanceof Navigable nav) {
        try {
          nav.onNavigatedFrom();
        } catch (Exception ignored) {
          // Không để lỗi của một controller chặn cleanup các controller còn lại
        }
      }
    }

    // Xóa cache các view cần auth, giữ lại welcome + login + register
    viewCache
        .keySet()
        .removeIf(
            k ->
                !k.equals("welcome.fxml") && !k.equals("login.fxml") && !k.equals("register.fxml"));
    controllerCache
        .keySet()
        .removeIf(
            k ->
                !k.equals("welcome.fxml") && !k.equals("login.fxml") && !k.equals("register.fxml"));

    // Dùng performNavigate trực tiếp — không push vào backStack sau khi đã clear
    performNavigate("welcome.fxml", null, false);
  }

  /**
   * Xóa cache (view + controller) của một FXML cụ thể, buộc load lại lần điều hướng tiếp theo.
   *
   * <p>Dùng khi cần đảm bảo controller được tạo mới hoàn toàn, ví dụ: sau khi tạo phiên đấu giá mới
   * cần reset form {@code create-auction.fxml} về trạng thái ban đầu.
   *
   * <p><b>Lưu ý:</b> Nếu controller đang giữ kết nối mở (WebSocket, Timer...), hãy đảm bảo cleanup
   * trước khi gọi method này, hoặc dùng {@link Navigable#onNavigatedFrom()} để tự cleanup.
   *
   * @param fxmlName tên FXML cần xóa khỏi cache, ví dụ: {@code "create-auction.fxml"}
   */
  public void invalidateCache(String fxmlName) {
    viewCache.remove(fxmlName);
    controllerCache.remove(fxmlName);
    LOGGER.debug("Đã xóa cache: {}", fxmlName);
  }

  // ========== GETTERS / SETTERS — SESSION STATE ==========

  /**
   * Trả về JWT token của phiên đăng nhập hiện tại.
   *
   * @return JWT token, hoặc {@code null} nếu chưa đăng nhập
   */
  public String getJwtToken() {
    return jwtToken;
  }

  /**
   * Lưu JWT token sau khi đăng nhập thành công.
   *
   * @param jwtToken token nhận được từ server
   */
  public void setJwtToken(String jwtToken) {
    this.jwtToken = jwtToken;
  }

  /**
   * Trả về username của người dùng đang đăng nhập.
   *
   * @return username hiện tại, hoặc {@code null} nếu chưa đăng nhập
   */
  public String getCurrentUsername() {
    return currentUsername;
  }

  /**
   * Lưu username sau khi đăng nhập thành công.
   *
   * @param currentUsername username nhận được từ response đăng nhập
   */
  public void setCurrentUsername(String currentUsername) {
    this.currentUsername = currentUsername;
  }

  /**
   * Trả về role của người dùng đang đăng nhập.
   *
   * @return role hiện tại (ví dụ: {@code "ROLE_ADMIN"}, {@code "ROLE_USER"}), hoặc {@code null} nếu
   *     chưa đăng nhập
   */
  public String getCurrentRole() {
    return currentRole;
  }

  /**
   * Lưu role sau khi đăng nhập thành công.
   *
   * @param currentRole role nhận được từ response đăng nhập
   */
  public void setCurrentRole(String currentRole) {
    this.currentRole = currentRole;
  }

  /**
   * Trả về ID của người dùng đang đăng nhập.
   *
   * @return user ID hiện tại, hoặc {@code null} nếu chưa đăng nhập
   */
  public Long getCurrentUserId() {
    return currentUserId;
  }

  /**
   * Lưu user ID sau khi đăng nhập thành công.
   *
   * @param currentUserId ID nhận được từ response đăng nhập
   */
  public void setCurrentUserId(Long currentUserId) {
    this.currentUserId = currentUserId;
  }

  /**
   * Trả về Stage chính của ứng dụng.
   *
   * <p>Dùng khi controller cần tương tác trực tiếp với Stage (ví dụ: setTitle, setFullScreen...).
   *
   * @return {@link Stage} chính đã được khởi tạo
   */
  public Stage getPrimaryStage() {
    return primaryStage;
  }

  /**
   * Lấy controller đã được cache của một màn hình cụ thể.
   *
   * <p>Hữu ích khi màn hình A cần gọi trực tiếp method trên controller của màn hình B đang được
   * cache (ví dụ: trigger refresh từ ngoài). Trả về {@code null} nếu FXML chưa được load lần nào.
   *
   * @param <T> kiểu controller mong đợi
   * @param fxmlName tên FXML tương ứng với controller cần lấy
   * @return controller đã cast về kiểu {@code T}, hoặc {@code null} nếu chưa có trong cache
   */
  @SuppressWarnings("unchecked")
  public <T> T getController(String fxmlName) {
    return (T) controllerCache.get(fxmlName);
  }
}
