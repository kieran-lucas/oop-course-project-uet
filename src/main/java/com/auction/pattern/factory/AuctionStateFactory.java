package com.auction.pattern.factory;

import com.auction.pattern.state.AuctionState;
import com.auction.pattern.state.AuctionStates;

/**
 * Factory tạo {@link AuctionState} từ chuỗi trạng thái — áp dụng Factory Pattern.
 *
 * <p>Thay vì mỗi nơi dùng {@code switch(status)} để lấy State, tập trung logic này vào một chỗ duy
 * nhất. Mọi thay đổi về mapping status → State chỉ cần sửa tại đây.
 *
 * <p>Uỷ quyền cho các singleton trong {@link AuctionStates} — không tạo đối tượng mới (zero
 * allocation) vì tất cả State đều bất biến và không có trạng thái riêng (stateless).
 *
 * <p>Lớp này là utility class — chỉ có phương thức static, không thể khởi tạo.
 */
public final class AuctionStateFactory {

  /** Ngăn khởi tạo — đây là utility class chỉ chứa phương thức static. */
  private AuctionStateFactory() {}

  /**
   * Trả về singleton {@link AuctionState} tương ứng với chuỗi trạng thái.
   *
   * <p>Chuỗi đầu vào được chuyển sang chữ hoa trước khi so khớp — không phân biệt hoa thường.
   *
   * @param status chuỗi trạng thái (ví dụ: "OPEN", "running", "Finished")
   * @return singleton State tương ứng từ {@link AuctionStates}
   * @throws IllegalArgumentException nếu {@code status} không khớp bất kỳ trạng thái nào
   */
  public static AuctionState create(String status) {
    return switch (status.toUpperCase()) {
      case "OPEN" -> AuctionStates.OPEN;
      case "RUNNING" -> AuctionStates.RUNNING;
      case "SETTLING" -> AuctionStates.SETTLING;
      case "FINISHED" -> AuctionStates.FINISHED;
      case "PAID" -> AuctionStates.PAID;
      case "CANCELED" -> AuctionStates.CANCELED;
      default -> throw new IllegalArgumentException("Unknown status: " + status);
    };
  }
}
