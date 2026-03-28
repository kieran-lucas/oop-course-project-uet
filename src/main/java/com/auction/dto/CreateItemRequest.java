package com.auction.dto;

/**
 * DTO cho yêu cầu tạo sản phẩm đấu giá mới.
 *
 * <p>Chỉ Seller mới có quyền tạo sản phẩm. Server kiểm tra role từ JWT token.
 *
 * <p>Field category quyết định ItemFactory tạo subclass nào:
 * <ul>
 *   <li>"ELECTRONICS" → new Electronics(name, description, sellerId, categoryDetail)
 *       — categoryDetail ở đây là brand (ví dụ: "Apple")</li>
 *   <li>"ART" → new Art(name, description, sellerId, categoryDetail)
 *       — categoryDetail ở đây là artist (ví dụ: "Van Gogh")</li>
 *   <li>"VEHICLE" → new Vehicle(name, description, sellerId, categoryDetail)
 *       — categoryDetail ở đây là year dạng String (ví dụ: "2022"), sẽ được parse thành int</li>
 * </ul>
 *
 * <p>Tại sao dùng 1 field categoryDetail thay vì 3 field riêng (brand, artist, year)?
 * Vì mỗi lần chỉ cần đúng 1 giá trị — nếu category = "ELECTRONICS" thì chỉ cần brand,
 * artist và year vô nghĩa. Cách này giữ JSON request gọn gàng và tránh gửi null fields.
 *
 * <p>Ví dụ JSON:
 * <pre>
 * {
 *   "name": "iPhone 15 Pro Max",
 *   "description": "Mới 100%, fullbox",
 *   "category": "ELECTRONICS",
 *   "categoryDetail": "Apple"
 * }
 * </pre>
 *
 * <p>sellerId KHÔNG nằm trong JSON — lấy từ JWT token (đảm bảo seller chỉ tạo item cho chính
 * mình, không thể giả mạo sellerId của người khác).
 */
public class CreateItemRequest {

  private String name;
  private String description;
  private String category; // "ELECTRONICS", "ART", hoặc "VEHICLE"
  private String categoryDetail; // brand / artist / year (tùy category)

  public CreateItemRequest() {}

  public CreateItemRequest(
      String name, String description, String category, String categoryDetail) {
    this.name = name;
    this.description = description;
    this.category = category;
    this.categoryDetail = categoryDetail;
  }

  // === Getters & Setters ===

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getCategoryDetail() {
    return categoryDetail;
  }

  public void setCategoryDetail(String categoryDetail) {
    this.categoryDetail = categoryDetail;
  }
}
