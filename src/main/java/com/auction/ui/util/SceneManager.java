package com.auction.ui.util;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton quản lý toàn bộ navigation trong ứng dụng JavaFX.
 *
 * <p>Kiến trúc: Single Scene + StackPane swap (giống SPA trong web). - 1 Scene duy nhất, không bao
 * giờ tạo/hủy Scene - 1 StackPane root, chuyển màn = swap children - FXML lazy load lần đầu, cache
 * vĩnh viễn trong HashMap - Controller cũng cache → giữ state khi quay lại - CSS load 1 lần gắn vào
 * Scene → tất cả FXML đều có theme
 *
 * <p>Sử dụng: SceneManager.getInstance().navigateTo("auction-list.fxml");
 * SceneManager.getInstance().navigateTo("auction-detail.fxml", auctionId);
 */
public class SceneManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(SceneManager.class);

  // ========== SINGLETON ==========
  private static SceneManager instance;

  public static SceneManager getInstance() {
    if (instance == null) {
      throw new IllegalStateException(
          "SceneManager chưa được khởi tạo! Gọi init() trong ClientApp.start() trước.");
    }
    return instance;
  }

  /**
   * Khởi tạo SceneManager — chỉ gọi 1 lần duy nhất trong ClientApp.start().
   *
   * @param primaryStage Stage chính từ JavaFX Application
   * @param width chiều rộng cửa sổ
   * @param height chiều cao cửa sổ
   * @return instance đã khởi tạo
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

  /** Cache view (Parent) đã load — key là tên FXML, value là root node */
  private final Map<String, Parent> viewCache = new HashMap<>();

  /** Cache controller tương ứng — key là tên FXML, value là controller object */
  private final Map<String, Object> controllerCache = new HashMap<>();

  // ========== CORE COMPONENTS ==========

  private final Stage primaryStage;
  private final Scene scene;
  private final StackPane rootContainer;

  /** Tên FXML đang hiển thị hiện tại — dùng để gọi onNavigatedFrom() */
  private String currentFxml;

  /** Stack lịch sử điều hướng — dùng cho navigateBack() */
  private final Deque<String> backStack = new ArrayDeque<>();

  // ========== SESSION STATE ==========

  private String jwtToken;
  private String currentUsername;
  private String currentRole;
  private Long currentUserId;

  // ========== CONSTRUCTOR (private — Singleton) ==========

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
  }

  // ========== NAVIGATION — CORE ==========

  /**
   * Chuyển sang màn hình mới — ghi lại màn hình hiện tại vào backStack để có thể quay lại.
   *
   * @param fxmlName tên file FXML đích (ví dụ: "auction-list.fxml")
   */
  public void navigateTo(String fxmlName) {
    if (currentFxml != null) {
      backStack.push(currentFxml);
    }
    performNavigate(fxmlName, null, false);
  }

  /**
   * Chuyển màn hình + truyền data cho controller đích. onDataReceived() được gọi TRƯỚC
   * onNavigatedTo() để controller có đủ data.
   *
   * @param fxmlName tên file FXML đích
   * @param data dữ liệu truyền sang
   */
  public void navigateTo(String fxmlName, Object data) {
    if (currentFxml != null) {
      backStack.push(currentFxml);
    }
    performNavigate(fxmlName, data, true);
  }

  /**
   * Quay lại màn hình trước trong backStack. Nếu stack rỗng, dùng {@code defaultFxml}. KHÔNG ghi
   * màn hình hiện tại vào stack (không tạo vòng lặp).
   */
  public void navigateBack(String defaultFxml) {
    String target = backStack.isEmpty() ? defaultFxml : backStack.pop();
    performNavigate(target, null, false);
  }

  /**
   * Thực hiện điều hướng — dùng chung cho navigateTo, navigateBack, và logout. Gọi
   * onNavigatedFrom() của controller cũ và onNavigatedTo() của controller mới.
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
  }

  // ========== FXML LOADING ==========

  /**
   * Load FXML từ resources, cache cả view lẫn controller. Chỉ gọi 1 lần cho mỗi FXML — từ lần thứ 2
   * trở đi lấy từ cache.
   *
   * <p>FIX: kiểm tra URL null trước khi load để báo lỗi rõ ràng thay vì NullPointerException khó
   * debug.
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

  /** Hiển thị màn hình lỗi tạm thời thay vì crash im lặng. Giúp debug khi FXML không load được. */
  private void showErrorScreen(String message) {
    Label errorLabel = new Label("⚠ Lỗi điều hướng:\n" + message);
    errorLabel.setStyle("-fx-text-fill: red; -fx-font-size: 14px; -fx-padding: 20;");
    errorLabel.setWrapText(true);
    rootContainer.getChildren().setAll(errorLabel);
  }

  // ========== SESSION MANAGEMENT ==========

  public void logout() {
    jwtToken = null;
    currentUsername = null;
    currentRole = null;
    currentUserId = null;
    backStack.clear();
    com.auction.util.BackgroundBidWatcher.getInstance().stopAll();
    com.auction.util.NotificationStore.getInstance().clear();

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
   * Xóa cache của 1 màn hình cụ thể. Dùng khi cần force reload FXML (ví dụ: sau khi tạo item mới
   * cần reload create-auction).
   */
  public void invalidateCache(String fxmlName) {
    viewCache.remove(fxmlName);
    controllerCache.remove(fxmlName);
    LOGGER.debug("Đã xóa cache: {}", fxmlName);
  }

  // ========== GETTERS / SETTERS — SESSION STATE ==========

  public String getJwtToken() {
    return jwtToken;
  }

  public void setJwtToken(String jwtToken) {
    this.jwtToken = jwtToken;
  }

  public String getCurrentUsername() {
    return currentUsername;
  }

  public void setCurrentUsername(String currentUsername) {
    this.currentUsername = currentUsername;
  }

  public String getCurrentRole() {
    return currentRole;
  }

  public void setCurrentRole(String currentRole) {
    this.currentRole = currentRole;
  }

  public Long getCurrentUserId() {
    return currentUserId;
  }

  public void setCurrentUserId(Long currentUserId) {
    this.currentUserId = currentUserId;
  }

  public Stage getPrimaryStage() {
    return primaryStage;
  }

  @SuppressWarnings("unchecked")
  public <T> T getController(String fxmlName) {
    return (T) controllerCache.get(fxmlName);
  }
}
