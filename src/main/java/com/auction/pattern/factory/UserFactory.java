package com.auction.pattern.factory;

import com.auction.model.Admin;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import com.auction.model.User;

/**
 * Factory tạo đối tượng {@link User} đúng kiểu con theo chuỗi vai trò — áp dụng Factory Pattern.
 *
 * <p>Tập trung logic phân loại người dùng (BIDDER/SELLER/ADMIN) vào một điểm duy nhất thay vì để
 * mỗi nơi tự viết switch-case. Khi thêm vai trò mới chỉ cần sửa tại lớp này.
 *
 * <p>Đối tượng User được trả về ở trạng thái chưa có dữ liệu (empty object) — người gọi hoặc
 * framework (JDBI ResultSetMapper, Jackson) sẽ tự điền các trường cụ thể sau đó.
 *
 * <p>Lớp này là utility class — chỉ có phương thức static, không thể khởi tạo.
 */
public final class UserFactory {

  /** Ngăn khởi tạo — đây là utility class chỉ chứa phương thức static. */
  private UserFactory() {}

  /**
   * Tạo đối tượng {@link User} đúng kiểu con tương ứng với vai trò.
   *
   * <p>Chuỗi {@code role} không phân biệt hoa thường — {@code "bidder"}, {@code "BIDDER"} hay
   * {@code "Bidder"} đều được chấp nhận.
   *
   * @param role chuỗi vai trò người dùng: {@code "BIDDER"}, {@code "SELLER"} hoặc {@code "ADMIN"}
   * @return đối tượng con của {@link User} tương ứng: {@link Bidder}, {@link Seller} hoặc {@link
   *     Admin}
   * @throws IllegalArgumentException nếu {@code role} không khớp bất kỳ vai trò nào đã biết
   */
  public static User create(String role) {
    return switch (role.toUpperCase()) {
      case "BIDDER" -> new Bidder();
      case "SELLER" -> new Seller();
      case "ADMIN" -> new Admin();
      default -> throw new IllegalArgumentException("Unknown role: " + role);
    };
  }
}
