package com.auction.util;

/**
 * Tiện ích định dạng các tham chiếu động (username, tên phiên đấu giá) trong nội dung thông báo.
 *
 * <p>Client JavaFX đọc thông điệp thông báo và tô màu một số đoạn văn bản dựa trên ký tự phân cách
 * đặc biệt:
 *
 * <ul>
 *   <li>Username được bao bởi {@code «} và {@code »} (guillemet) → hiển thị màu xanh dương.
 *   <li>Tên phiên đấu giá được bao bởi {@code [} và {@code ]} → hiển thị màu nâu.
 * </ul>
 *
 * <p><b>Lý do chọn ký tự phân cách:</b> Guillemet ({@code «»}) không xuất hiện trong tên người dùng
 * hay văn bản sản phẩm tiếng Việt (đã được validate tại thời điểm đăng ký/tạo sản phẩm). Dấu ngoặc
 * vuông ({@code []}) cũng không xuất hiện trong các trường này. Cả hai ký tự đều đủ phân biệt để
 * client parse mà không cần định dạng chuỗi phức tạp hơn.
 *
 * <p>Lớp này là utility class — chỉ có phương thức static, không thể khởi tạo.
 */
public final class NotificationFormat {

  /** Ký tự mở guillemet — đánh dấu bắt đầu một đoạn tên người dùng. */
  public static final char USER_OPEN = '«';

  /** Ký tự đóng guillemet — đánh dấu kết thúc một đoạn tên người dùng. */
  public static final char USER_CLOSE = '»';

  /** Ngăn khởi tạo — đây là utility class chỉ chứa phương thức static. */
  private NotificationFormat() {}

  /**
   * Bao một username trong guillemet để client hiển thị màu xanh dương.
   *
   * <p>Nếu {@code username} null hoặc rỗng, trả về chuỗi fallback {@code «Người dùng»} thay vì để
   * thông báo bị trống.
   *
   * @param username tên người dùng cần định dạng
   * @return chuỗi dạng {@code «username»}
   */
  public static String user(String username) {
    String safe = username != null && !username.isBlank() ? username : "Người dùng";
    return USER_OPEN + safe + USER_CLOSE;
  }

  /**
   * Bao tên hiển thị của phiên đấu giá trong dấu ngoặc vuông để client hiển thị màu nâu.
   *
   * <p><b>Hợp đồng UI quan trọng:</b> Tên phiên đấu giá luôn được bao trong {@code [...]}. Nếu tên
   * sản phẩm chưa biết, fallback ID vẫn được bao, ví dụ {@code [#4]}. Không bao giờ trả về {@code
   * #4} bare — nếu không renderer thông báo có thể nhầm {@code #4 VND} là số tiền.
   *
   * <p>Token {@code #<id>} trong DB (ví dụ: {@code "Auction #4 was canceled"}) sẽ được {@code
   * AuctionListController.replaceAuctionIdWithName()} thay thế bằng {@code "[Item Name]"} lúc
   * render — đây là hai con đường khác nhau để tạo ra cùng định dạng {@code [...]}.
   *
   * @param auctionId ID phiên đấu giá — dùng làm fallback nếu {@code itemName} null/rỗng
   * @param itemName tên sản phẩm của phiên (có thể null nếu không truy vấn được)
   * @return chuỗi dạng {@code [Item Name]} hoặc {@code [#auctionId]} nếu không có tên
   */
  public static String auctionName(Long auctionId, String itemName) {
    String safe = itemName != null && !itemName.isBlank() ? itemName.trim() : "#" + auctionId;
    return "[" + safe + "]";
  }
}
