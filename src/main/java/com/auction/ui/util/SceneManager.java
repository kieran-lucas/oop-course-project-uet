package com.auction.ui.util;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
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
   * Container gốc — toàn bộ màn hình đều được render bên trong StackPane này.
   *
   * <p>Chuyển màn hình = gọi {@code rootContainer.getChildren().setAll(newView)}. Global
   * notification badge cũng được đặt trực tiếp trên StackPane này để nổi trên mọi màn hình.
   */
  private final StackPane rootContainer;

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
    this.scene = new Scene(rootContainer, width, height);

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
    StackPane.setMargin(globalBadgeLabel, new Insets(10, 10, 0, 0));
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

    rootContainer.getChildren().setAll(view);
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
    rootContainer.getChildren().setAll(errorLabel);
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
