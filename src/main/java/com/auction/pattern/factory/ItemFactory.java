package com.auction.pattern.factory;

import com.auction.dto.CreateItemRequest;
import com.auction.model.Art;
import com.auction.model.Electronics;
import com.auction.model.Item;
import com.auction.model.Vehicle;

/**
 * Factory tạo đúng subclass của {@link Item} dựa trên category trong request.
 *
 * <h2>Design Pattern: Factory Method</h2>
 *
 * <p><b>Vấn đề cần giải quyết:</b> Server nhận JSON từ client chứa trường {@code category}
 * là String ("ELECTRONICS", "ART", "VEHICLE"). Làm thế nào để tạo đúng subclass Java
 * ({@code Electronics}, {@code Art}, {@code Vehicle}) mà không làm lộ logic tạo object
 * ra khắp nơi trong codebase?
 *
 * <p><b>Giải pháp Factory Method:</b> Tập trung toàn bộ logic quyết định subclass vào
 * một điểm duy nhất — phương thức {@link #create}. Caller ({@code ItemService}) chỉ
 * gọi {@code ItemFactory.create(req, sellerId)} mà không cần biết nội bộ hoạt động thế nào.
 *
 * <p><b>Cấu trúc cây kế thừa được factory này xử lý:</b>
 * <pre>
 * Item (abstract)
 * ├── Electronics  ← category = "ELECTRONICS", categoryDetail = brand
 * ├── Art          ← category = "ART",         categoryDetail = artist
 * └── Vehicle      ← category = "VEHICLE",     categoryDetail = năm sản xuất (int)
 * </pre>
 *
 * <p><b>Lợi ích khi thêm category mới:</b> Chỉ cần thêm 1 case vào switch trong {@link #create}
 * và tạo subclass mới. Không cần sửa {@code ItemService}, {@code ItemController}, hay bất kỳ
 * caller nào khác — đây là nguyên tắc Open/Closed Principle.
 *
 * <p><b>Luồng dữ liệu:</b>
 * <pre>
 * Client gửi JSON → ItemController parse → CreateItemRequest
 *   → ItemService.create() → ItemFactory.create() → new Electronics/Art/Vehicle
 *   → ItemDao.insert() → PostgreSQL
 * </pre>
 */
public class ItemFactory {

  // Private constructor: class này chỉ có static method, không nên tạo instance
  private ItemFactory() {
    throw new UnsupportedOperationException("ItemFactory là utility class, không khởi tạo instance");
  }

  /**
   * Tạo đúng subclass Item dựa trên category trong request.
   *
   * <p>Trường {@code categoryDetail} trong request mang ý nghĩa khác nhau theo category:
   * <ul>
   *   <li>{@code ELECTRONICS}: categoryDetail = tên thương hiệu (brand), ví dụ "Samsung"</li>
   *   <li>{@code ART}: categoryDetail = tên nghệ sĩ (artist), ví dụ "Van Gogh"</li>
   *   <li>{@code VEHICLE}: categoryDetail = năm sản xuất dạng String, ví dụ "2020"</li>
   * </ul>
   *
   * @param req      request chứa name, description, category, categoryDetail
   * @param sellerId ID của seller tạo item — lưu vào item để check ownership sau này
   * @return subclass Item tương ứng với category, chưa có id (chưa lưu DB)
   * @throws IllegalArgumentException nếu category không hợp lệ (không phải 3 loại trên)
   * @throws NumberFormatException    nếu category là VEHICLE nhưng categoryDetail không parse được sang int
   */
  public static Item create(CreateItemRequest req, Long sellerId) {
    String category = req.getCategory().toUpperCase().trim();

    return switch (category) {
      case "ELECTRONICS" ->
          new Electronics(
              req.getName(),
              req.getDescription(),
              sellerId,
              req.getCategoryDetail() // brand
          );

      case "ART" ->
          new Art(
              req.getName(),
              req.getDescription(),
              sellerId,
              req.getCategoryDetail() // artist
          );

      case "VEHICLE" ->
          new Vehicle(
              req.getName(),
              req.getDescription(),
              sellerId,
              parseYear(req.getCategoryDetail(), req.getName()) // year
          );

      default ->
          throw new IllegalArgumentException(
              "Category không hợp lệ: '" + req.getCategory()
                  + "'. Chỉ chấp nhận: ELECTRONICS, ART, VEHICLE"
          );
    };
  }

  /**
   * Parse năm sản xuất từ String sang int với thông báo lỗi rõ ràng.
   *
   * @param yearStr  chuỗi năm sản xuất từ request
   * @param itemName tên item — dùng trong thông báo lỗi cho dễ debug
   * @return năm sản xuất dạng int
   * @throws IllegalArgumentException nếu yearStr không phải số nguyên hợp lệ
   */
  private static int parseYear(String yearStr, String itemName) {
    try {
      int year = Integer.parseInt(yearStr.trim());
      if (year < 1886 || year > 2100) {
        throw new IllegalArgumentException(
            "Năm sản xuất không hợp lệ cho '" + itemName + "': " + year
                + " (hợp lệ: 1886–2100)"
        );
      }
      return year;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          "Năm sản xuất phải là số nguyên, nhận được: '" + yearStr + "'", e
      );
    }
  }
}
