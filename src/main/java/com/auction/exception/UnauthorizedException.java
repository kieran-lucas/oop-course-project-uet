package com.auction.exception;

/**
 * Được ném ra khi người dùng thực hiện một thao tác mà không có đủ quyền:
 *
 * <ul>
 *   <li>Một {@code BIDDER} truy cập các endpoint chỉ dành cho {@code SELLER} hoặc {@code ADMIN}
 *   <li>Người bán cố gắng chỉnh sửa phiên đấu giá không thuộc về họ
 *   <li>Người dùng chưa xác thực truy cập tài nguyên được bảo vệ
 *   <li>Xác thực token thất bại (hết hạn, chữ ký không hợp lệ)
 * </ul>
 *
 * <p>Ví dụ sử dụng:
 *
 * <pre>{@code
 * if (!item.getSellerId().equals(userId)) {
 *     throw new UnauthorizedException("Bạn chỉ có thể chỉnh sửa sản phẩm của mình");
 * }
 * }</pre>
 *
 * @see AuctionException
 */
public class UnauthorizedException extends AuctionException {

  private static final long serialVersionUID = 1L;

  /**
   * Tạo một UnauthorizedException mới với thông điệp được chỉ định
   *
   * @param message mô tả vi phạm quyền truy cập
   */
  public UnauthorizedException(String message) {
    super(message);
  }

  /**
   * Tạo một UnauthorizedException mới với thông điệp và nguyên nhân
   *
   * @param message mô tả vi phạm quyền truy cập
   * @param cause ngoại lệ gốc gây ra lỗi
   */
  public UnauthorizedException(String message, Throwable cause) {
    super(message, cause);
  }
}
