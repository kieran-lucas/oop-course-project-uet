package com.auction;

import com.auction.ui.util.SceneManager;
import javafx.application.Application;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Điểm khởi động ứng dụng JavaFX (Client) — Online Auction System.
 *
 * <p><b>Kiến trúc Client:</b>
 *
 * <pre>
 *   ClientApp (entry point)
 *     → SceneManager (singleton, quản lý navigation + session)
 *     → welcome.fxml → login.fxml / register.fxml
 *     → auction-list.fxml (danh sách phiên)
 *     → auction-detail.fxml (đặt giá realtime + chart)
 *     → create-item.fxml, create-auction.fxml (Seller)
 *     → admin-panel.fxml (Admin)
 * </pre>
 *
 * <p><b>Cách chạy client:</b>
 *
 * <pre>
 *   ./gradlew runClient
 * </pre>
 *
 * <p><b>Lưu ý:</b> Server phải đang chạy ({@code ./gradlew run}) trước khi khởi động client. Server
 * mặc định trên {@code http://localhost:8080}.
 *
 * <p><b>Liên kết với các file khác:</b>
 *
 * <ul>
 *   <li>{@link SceneManager} — singleton quản lý toàn bộ màn hình và session JWT
 *   <li>{@code /resources/fxml/} — tất cả file FXML (View trong MVC)
 *   <li>{@code /resources/css/style.css} — dark theme áp dụng cho toàn bộ ứng dụng
 * </ul>
 */
public class ClientApp extends Application {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClientApp.class);

  /** Chiều rộng cửa sổ tối thiểu */
  private static final double MIN_WIDTH = 1000;

  /** Chiều cao cửa sổ tối thiểu */
  private static final double MIN_HEIGHT = 700;

  /**
   * Khởi động giao diện JavaFX.
   *
   * <p>Thứ tự khởi tạo:
   *
   * <ol>
   *   <li>Khởi tạo SceneManager với Stage chính.
   *   <li>Cài đặt kích thước tối thiểu cho cửa sổ.
   *   <li>Hiển thị màn hình chào mừng.
   *   <li>Show Stage.
   * </ol>
   *
   * @param primaryStage Stage chính do JavaFX cung cấp
   */
  @Override
  public void start(Stage primaryStage) {
    try {
      loadFonts();
      primaryStage.initStyle(StageStyle.UNDECORATED);

      double windowHeight = MIN_HEIGHT + SceneManager.TITLE_BAR_HEIGHT;

      // Khởi tạo SceneManager singleton
      SceneManager sceneManager = SceneManager.init(primaryStage, MIN_WIDTH, windowHeight);

      // Cấu hình Stage
      primaryStage.setTitle("Online Auction System");
      primaryStage.setMinWidth(MIN_WIDTH);
      primaryStage.setMinHeight(windowHeight);

      // Load màn hình chào mừng đầu tiên
      sceneManager.navigateTo("welcome.fxml");

      primaryStage.show();
      LOGGER.info("Ứng dụng đã khởi động — {}x{}", MIN_WIDTH, MIN_HEIGHT);

    } catch (Exception e) {
      LOGGER.error("Lỗi khởi động ứng dụng JavaFX", e);
      throw new RuntimeException("Không thể khởi động ứng dụng", e);
    }
  }

  private static void loadFonts() {
    String[] variants = {
      "Lexend-Thin", "Lexend-ExtraLight", "Lexend-Light",
      "Lexend-Regular", "Lexend-Medium", "Lexend-SemiBold",
      "Lexend-Bold", "Lexend-ExtraBold", "Lexend-Black"
    };
    int loaded = 0;
    for (String variant : variants) {
      String path = "/fonts/" + variant + ".ttf";
      try (var stream = ClientApp.class.getResourceAsStream(path)) {
        if (stream == null) {
          LOGGER.warn("Không tìm thấy font file: {}", path);
          continue;
        }
        Font font = Font.loadFont(stream, 12);
        if (font != null) {
          LOGGER.info("Font loaded: {} → family=\"{}\"", variant, font.getFamily());
          loaded++;
        } else {
          LOGGER.warn("Font.loadFont trả về null cho: {}", path);
        }
      } catch (Exception e) {
        LOGGER.warn("Không thể load font {}: {}", path, e.getMessage());
      }
    }
    LOGGER.info("Đã load {}/{} biến thể font Lexend", loaded, variants.length);

    // Diagnostic: verify which font families JavaFX CSS can actually see
    java.util.List<String> lexendFamilies =
        Font.getFamilies().stream()
            .filter(f -> f.toLowerCase().startsWith("lexend"))
            .collect(java.util.stream.Collectors.toList());
    LOGGER.info("=== Lexend families visible to CSS: {} ===", lexendFamilies);
    for (String fam : lexendFamilies) {
      LOGGER.info("  '{}' → names: {}", fam, Font.getFontNames(fam));
    }
  }

  /**
   * Main method — entry point cho JVM.
   *
   * <p>JavaFX Application cần gọi {@code Application.launch()} để khởi động Application Thread
   * (JavaFX Thread). Không gọi {@code new ClientApp().start()} trực tiếp.
   *
   * @param args tham số dòng lệnh (không sử dụng)
   */
  public static void main(String[] args) {
    // Keep Prism LCD text available so CSS can opt specific controls back into LCD AA.
    // Most UI text is still rendered with grayscale AA by the global
    // `* { -fx-font-smoothing-type: gray; }` rule in css/style.css, but PasswordField
    // bullets use the more precise `.password-field .text { -fx-font-smoothing-type: lcd; }`
    // override to avoid jagged password dots.
    System.setProperty("prism.lcdtext", "true");
    launch(args);
  }
}
