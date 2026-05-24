package com.auction.pattern.factory;

import com.auction.dto.CreateItemRequest;
import com.auction.model.Art;
import com.auction.model.Electronics;
import com.auction.model.Item;
import com.auction.model.Vehicle;

/**
 * Factory tạo đối tượng {@link Item} đúng kiểu con từ dữ liệu yêu cầu — áp dụng Factory Pattern.
 *
 * <p>Tập trung toàn bộ logic phân loại sản phẩm vào một điểm duy nhất. Mọi nơi muốn tạo Item từ
 * request đều gọi {@link #create(CreateItemRequest, Long)} thay vì tự viết switch-case phân loại;
 * nhờ đó khi thêm category mới chỉ cần sửa tại đây.
 *
 * <p>Trường {@code categoryDetail} trong {@link CreateItemRequest} có ngữ nghĩa khác nhau theo từng
 * loại:
 *
 * <ul>
 *   <li>{@code ELECTRONICS} → {@code brand} (thương hiệu, ví dụ: "Apple", "Samsung")
 *   <li>{@code ART} → {@code artist} (tên nghệ sĩ, ví dụ: "Van Gogh")
 *   <li>{@code VEHICLE} → {@code year} (năm sản xuất dạng chuỗi, ví dụ: "2023")
 * </ul>
 *
 * <p>Lớp này là utility class — chỉ có phương thức static, không thể khởi tạo.
 */
public final class ItemFactory {

  /** Ngăn khởi tạo — đây là utility class chỉ chứa phương thức static. */
  private ItemFactory() {
    throw new UnsupportedOperationException("ItemFactory is a utility class");
  }

  /**
   * Tạo đối tượng {@link Item} đúng kiểu con tương ứng với {@code category} trong request.
   *
   * <p>Chuỗi {@code category} được chuẩn hóa (trim + toUpperCase) trước khi so khớp, nên các giá
   * trị như {@code "electronics"} hay {@code " Vehicle "} đều được chấp nhận.
   *
   * <p>Với {@code VEHICLE}, trường {@code categoryDetail} được parse thành {@link Integer} năm sản
   * xuất và kiểm tra nằm trong khoảng hợp lệ 1886–2100.
   *
   * @param req yêu cầu tạo sản phẩm chứa name, description, category và categoryDetail
   * @param sellerId ID người bán đang đăng sản phẩm
   * @return đối tượng con của {@link Item} tương ứng: {@link Electronics}, {@link Art} hoặc {@link
   *     Vehicle}
   * @throws IllegalArgumentException nếu {@code category} không hợp lệ, hoặc {@code categoryDetail}
   *     không parse được thành số nguyên/nằm ngoài khoảng cho VEHICLE
   */
  public static Item create(CreateItemRequest req, Long sellerId) {
    String category = req.getCategory().toUpperCase().trim();

    return switch (category) {
      case "ELECTRONICS" ->
          new Electronics(req.getName(), req.getDescription(), sellerId, req.getCategoryDetail());
      case "ART" -> new Art(req.getName(), req.getDescription(), sellerId, req.getCategoryDetail());
      case "VEHICLE" ->
          new Vehicle(
              req.getName(), req.getDescription(), sellerId, parseYear(req.getCategoryDetail()));
      default ->
          throw new IllegalArgumentException(
              "Invalid category: '"
                  + req.getCategory()
                  + "'. Accepted values: ELECTRONICS, ART, VEHICLE");
    };
  }

  /**
   * Parse và validate năm sản xuất của phương tiện từ chuỗi.
   *
   * <p>Khoảng hợp lệ 1886–2100 được chọn dựa trên năm chiếc ô tô đầu tiên ra đời (1886) và một mốc
   * tương lai hợp lý để hệ thống không cần cập nhật giới hạn trên trong nhiều năm tới.
   *
   * @param yearText chuỗi năm sản xuất (ví dụ: "2023", " 2020 ")
   * @return năm sản xuất đã được validate
   * @throws NumberFormatException nếu {@code yearText} không phải số nguyên hợp lệ
   * @throws IllegalArgumentException nếu năm nằm ngoài khoảng 1886–2100
   */
  private static int parseYear(String yearText) {
    int year = Integer.parseInt(yearText.trim());
    if (year < 1886 || year > 2100) {
      throw new IllegalArgumentException("Invalid manufacture year (1886-2100): " + year);
    }
    return year;
  }
}
