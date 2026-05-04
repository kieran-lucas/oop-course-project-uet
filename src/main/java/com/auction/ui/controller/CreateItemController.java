package com.auction.ui.controller;

import com.auction.ui.util.Navigable;
import com.auction.ui.util.SceneManager;
import com.auction.util.RestClient;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller cho màn hình tạo sản phẩm mới (create-item.fxml).
 *
 * <p><b>Mục đích:</b>
 * Cho phép SELLER tạo sản phẩm mới để đưa vào đấu giá. Gửi request đến
 * {@code POST /api/items} với thông tin tên, mô tả, danh mục và chi tiết
 * danh mục (brand/artist/năm sản xuất tùy loại sản phẩm).
 *
 * <p><b>Các phương thức chính:</b>
 * <ul>
 *   <li>{@link #handleCategoryChange()} — Cập nhật label "Chi tiết danh mục" theo loại chọn.</li>
 *   <li>{@link #handleCreate()} — Validate và gửi request tạo sản phẩm.</li>
 *   <li>{@link #goBack()} — Quay lại màn hình tạo phiên hoặc danh sách.</li>
 * </ul>
 *
 * <p><b>Vị trí trong kiến trúc:</b>
 * CreateItemController sử dụng Factory Method Pattern (qua ItemService/ItemController phía server)
 * để tạo đúng subclass Item (Electronics, Art, Vehicle) dựa trên category.
 * SELLER có thể tạo sản phẩm trước, sau đó chọn sản phẩm đó khi tạo phiên đấu giá.
 */
public class CreateItemController implements Navigable {

  private static final Logger LOGGER = LoggerFactory.getLogger(CreateItemController.class);

  @FXML private TextField nameField;
  @FXML private TextArea descriptionField;
  @FXML private ComboBox<String> categoryCombo;
  @FXML private Label categoryDetailLabel;
  @FXML private TextField categoryDetailField;
  @FXML private Label statusLabel;
  @FXML private Button createButton;

  // ========== NAVIGABLE LIFECYCLE ==========

  /** Reset form khi navigate đến màn hình này. */
  @Override
  public void onNavigatedTo() {
    clearForm();
  }

  // ========== FXML ACTIONS ==========

  /**
   * Cập nhật label và prompt của trường chi tiết danh mục theo category đã chọn.
   * - ELECTRONICS → "Thương hiệu" (vd: Apple, Samsung)
   * - ART         → "Nghệ sĩ" (vd: Van Gogh)
   * - VEHICLE     → "Năm sản xuất" (vd: 2022)
   */
  @FXML
  public void handleCategoryChange() {
    String category = categoryCombo.getValue();
    if (category == null) return;
    switch (category) {
      case "ELECTRONICS" -> {
        categoryDetailLabel.setText("Thương hiệu *");
        categoryDetailField.setPromptText("Vd: Apple, Samsung, Sony...");
      }
      case "ART" -> {
        categoryDetailLabel.setText("Nghệ sĩ *");
        categoryDetailField.setPromptText("Vd: Van Gogh, Picasso...");
      }
      case "VEHICLE" -> {
        categoryDetailLabel.setText("Năm sản xuất *");
        categoryDetailField.setPromptText("Vd: 2022");
      }
      default -> {
        categoryDetailLabel.setText("Chi tiết danh mục *");
        categoryDetailField.setPromptText("Nhập thông tin chi tiết");
      }
    }
  }

  /**
   * Xử lý tạo sản phẩm mới.
   * Validate phía client: tên không trống, category đã chọn, categoryDetail không trống.
   * Gửi {@code POST /api/items} với body JSON, hiển thị kết quả.
   */
  @FXML
  public void handleCreate() {
    String name = nameField.getText().trim();
    String description = descriptionField.getText().trim();
    String category = categoryCombo.getValue();
    String categoryDetail = categoryDetailField.getText().trim();

    if (name.isEmpty()) {
      showStatus("Tên sản phẩm không được để trống.", true);
      return;
    }
    if (category == null) {
      showStatus("Vui lòng chọn danh mục.", true);
      return;
    }
    if (categoryDetail.isEmpty()) {
      showStatus("Vui lòng nhập thông tin chi tiết danh mục.", true);
      return;
    }

    createButton.setDisable(true);
    hideStatus();

    Map<String, String> body = new HashMap<>();
    body.put("name", name);
    body.put("description", description);
    body.put("category", category);
    body.put("categoryDetail", categoryDetail);

    Thread.ofVirtual().start(() -> {
      try {
        HttpResponse<String> response = RestClient.post("/api/items", body);
        if (response.statusCode() == 201) {
          Platform.runLater(() -> {
            // Quay về create-auction để seller chọn sản phẩm vừa tạo ngay
            SceneManager.getInstance().navigateTo("create-auction.fxml");
          });
        } else {
          String msg = extractMessage(response.body(), "Tạo sản phẩm thất bại.");
          Platform.runLater(() -> {
            showStatus(msg, true);
            createButton.setDisable(false);
          });
        }
      } catch (Exception e) {
        LOGGER.error("Lỗi tạo sản phẩm", e);
        Platform.runLater(() -> {
          showStatus("Không thể kết nối đến server.", true);
          createButton.setDisable(false);
        });
      }
    });
  }

  /** Quay lại màn hình tạo phiên đấu giá. */
  @FXML
  public void goBack() {
    SceneManager.getInstance().navigateTo("create-auction.fxml");
  }

  // ========== PRIVATE HELPERS ==========

  private void showStatus(String msg, boolean isError) {
    statusLabel.setText(msg);
    statusLabel.setStyle(isError ? "-fx-text-fill: #e53935;" : "-fx-text-fill: #43a047;");
    statusLabel.setVisible(true);
  }

  private void hideStatus() {
    statusLabel.setVisible(false);
  }

  private void clearForm() {
    if (nameField != null) nameField.clear();
    if (descriptionField != null) descriptionField.clear();
    if (categoryCombo != null) categoryCombo.setValue(null);
    if (categoryDetailField != null) categoryDetailField.clear();
    if (categoryDetailLabel != null) categoryDetailLabel.setText("Chi tiết danh mục *");
    hideStatus();
    if (createButton != null) createButton.setDisable(false);
  }

  private String extractMessage(String body, String fallback) {
    try {
      com.fasterxml.jackson.databind.ObjectMapper mapper =
          new com.fasterxml.jackson.databind.ObjectMapper();
      return mapper.readTree(body).path("message").asText(fallback);
    } catch (Exception e) {
      return fallback;
    }
  }
}
