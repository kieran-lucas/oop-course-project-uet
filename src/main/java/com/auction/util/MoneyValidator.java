package com.auction.util;

import java.math.BigDecimal;

/**
 * Tiện ích kiểm tra tính hợp lệ của số tiền VND — là số nguyên dương không có phần thập phân.
 *
 * <p>Đồng Việt Nam (VND) không có đơn vị tiền xu — mọi giao dịch đều là số nguyên. Hệ thống dùng
 * {@link BigDecimal} để lưu trữ chính xác, nhưng quy định chỉ chấp nhận các giá trị có {@code scale
 * <= 0} sau khi loại bỏ số 0 cuối (ví dụ: {@code 1000}, {@code 1000.00} đều hợp lệ; {@code 1000.50}
 * thì không).
 *
 * <p>Lớp này là utility class — chỉ có phương thức static, không thể khởi tạo.
 */
public final class MoneyValidator {

  /** Ngăn khởi tạo — đây là utility class chỉ chứa phương thức static. */
  private MoneyValidator() {}

  /**
   * Kiểm tra {@code amount} là số tiền VND hợp lệ: khác null, dương và là số nguyên.
   *
   * <p>Dùng tại tất cả các endpoint nhận giá đặt hoặc giá khởi điểm để từ chối sớm các giá trị
   * không hợp lệ trước khi chạm tới tầng DB.
   *
   * @param amount số tiền cần kiểm tra
   * @param fieldName tên trường dùng trong thông điệp lỗi (ví dụ: "Starting price", "Bid amount")
   * @throws IllegalArgumentException nếu {@code amount} là null, không dương, hoặc có phần lẻ
   */
  public static void requirePositiveIntegerVnd(BigDecimal amount, String fieldName) {
    if (amount == null) {
      throw new IllegalArgumentException(fieldName + " is required");
    }
    if (amount.signum() <= 0) {
      throw new IllegalArgumentException(fieldName + " must be greater than 0 VND");
    }
    if (!isIntegerVnd(amount)) {
      throw new IllegalArgumentException(fieldName + " must be an integer VND amount");
    }
  }

  /**
   * Kiểm tra một {@link BigDecimal} có phải số nguyên VND không (không có phần thập phân).
   *
   * <p>Thực hiện bằng {@code stripTrailingZeros().scale() <= 0}: {@code 1000.00} → scale 0 ✅,
   * {@code 1000.5} → scale 1 ❌.
   *
   * @param amount số tiền cần kiểm tra (có thể null — trả về {@code false} nếu null)
   * @return {@code true} nếu {@code amount} khác null và là số nguyên
   */
  public static boolean isIntegerVnd(BigDecimal amount) {
    return amount != null && amount.stripTrailingZeros().scale() <= 0;
  }

  /**
   * Chuyển đổi {@link BigDecimal} sang {@code long} VND chính xác.
   *
   * <p>Ném exception thay vì làm tròn để tránh lỗi ngầm mất số lẻ trong giao dịch tài chính —
   * caller phải đảm bảo {@code amount} là số nguyên trước khi gọi method này.
   *
   * @param amount số tiền cần chuyển đổi
   * @param fieldName tên trường dùng trong thông điệp lỗi
   * @return giá trị {@code long} tương đương
   * @throws IllegalArgumentException nếu {@code amount} là null hoặc có phần lẻ
   * @throws ArithmeticException nếu giá trị vượt quá giới hạn {@code long}
   */
  public static long toIntegerVndExact(BigDecimal amount, String fieldName) {
    if (amount == null) {
      throw new IllegalArgumentException(fieldName + " is required");
    }
    if (!isIntegerVnd(amount)) {
      throw new IllegalArgumentException(fieldName + " must be an integer VND amount");
    }
    return amount.stripTrailingZeros().longValueExact();
  }
}
