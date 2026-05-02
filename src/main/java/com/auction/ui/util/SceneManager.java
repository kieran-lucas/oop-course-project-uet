package com.auction.config;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
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

  // ========== SESSION STATE ==========
  // Lưu thông tin đăng nhập toàn cục, mọi controller đều truy cập được

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
    String css = getClass().getResource("/css/style.css").toExternalForm();
    scene.getStylesheets().add(css);

    primaryStage.setScene(scene);
  }

  // ========== NAVIGATION — CORE ==========

  /**
   * Chuyển sang màn hình khác (không truyền data).
   *
   * <p>Luồng: 1. Gọi onNavigatedFrom() trên controller hiện tại (nếu có) 2. Kiểm tra cache → nếu
   * chưa có thì lazy load FXML 3. Swap view trong StackPane (instant, < 5ms) 4. Gọi onNavigatedTo()
   * trên controller mới
   *
   * @param fxmlName tên file FXML (ví dụ: "login.fxml", "auction-list.fxml")
   */
  public void navigateTo(String fxmlName) {
    // Bước 1: Thông báo controller cũ rằng sắp rời đi
    notifyNavigatedFrom();

    // Bước 2: Lấy view từ cache hoặc lazy load
    Parent view = viewCache.get(fxmlName);
    if (view == null) {
      view = loadFxml(fxmlName);
    }

    // Bước 3: Swap — thao tác cực nhanh, chỉ thay DOM node
    rootContainer.getChildren().setAll(view);
    currentFxml = fxmlName;

    // Bước 4: Thông báo controller mới rằng đã navigate tới
    Object controller = controllerCache.get(fxmlName);
    if (controller instanceof Navigable nav) {
      nav.onNavigatedTo();
    }

    LOGGER.debug("Navigated to: {}", fxmlName);
  }

  /**
   * Chuyển màn hình + truyền data cho controller đích.
   *
   * <p>Ví dụ: từ auction-list, click vào 1 auction:
   * SceneManager.getInstance().navigateTo("auction-detail.fxml", auctionId);
   *
   * <p>Controller đích nhận data trong onDataReceived(): public void onDataReceived(Object data) {
   * Long auctionId = (Long) data; loadAuctionDetail(auctionId); }
   *
   * @param fxmlName tên file FXML đích
   * @param data dữ liệu truyền sang (bất kỳ kiểu)
   */
  public void navigateTo(String fxmlName, Object data) {
    navigateTo(fxmlName);
    Object controller = controllerCache.get(fxmlName);
    if (controller instanceof Navigable nav) {
      nav.onDataReceived(data);
    }
  }

  // ========== FXML LOADING ==========

  /**
   * Load FXML từ resources, cache cả view lẫn controller. Chỉ gọi 1 lần cho mỗi FXML — từ lần thứ 2
   * trở đi lấy từ cache.
   */
  private Parent loadFxml(String fxmlName) {
    try {
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/" + fxmlName));
      Parent view = loader.load();

      // Cache cả view và controller
      viewCache.put(fxmlName, view);
      controllerCache.put(fxmlName, loader.getController());

      LOGGER.info("Lazy loaded FXML: {}", fxmlName);
      return view;
    } catch (IOException e) {
      LOGGER.error("Không thể load FXML: {}", fxmlName, e);
      throw new RuntimeException("Load FXML thất bại: " + fxmlName, e);
    }
  }

  // ========== LIFECYCLE HELPERS ==========

  /**
   * Gọi onNavigatedFrom() trên controller đang hiển thị. Controller dùng method này để cleanup
   * (đóng WebSocket, hủy Timer...).
   */
  private void notifyNavigatedFrom() {
    if (currentFxml != null) {
      Object controller = controllerCache.get(currentFxml);
      if (controller instanceof Navigable nav) {
        nav.onNavigatedFrom();
      }
    }
  }

  // ========== SESSION MANAGEMENT ==========

  public void logout() {
    jwtToken = null;
    currentUsername = null;
    currentRole = null;
    currentUserId = null;

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

    navigateTo("welcome.fxml");
  }

  /**
   * Xóa cache của 1 màn hình cụ thể. Dùng khi cần force reload FXML (ví dụ: sau khi thay đổi role).
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

  /**
   * Lấy controller đã cache theo tên FXML. Dùng khi cần giao tiếp giữa 2 controller.
   *
   * <p>Ví dụ: từ CreateAuctionController cần refresh AuctionListController: AuctionListController
   * ctrl = (AuctionListController) SceneManager.getInstance().getController("auction-list.fxml");
   * ctrl.refreshList();
   */
  @SuppressWarnings("unchecked")
  public <T> T getController(String fxmlName) {
    return (T) controllerCache.get(fxmlName);
  }
}
