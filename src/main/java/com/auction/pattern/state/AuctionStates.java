package com.auction.pattern.state;

/**
 * Kho singleton toàn cục cho tất cả các implementation của {@link AuctionState}.
 *
 * <p>Tất cả State đều bất biến (immutable) và không có trạng thái riêng (stateless) — chúng chỉ đọc
 * dữ liệu từ đối tượng {@link com.auction.model.Auction} được truyền vào, không giữ lại bất cứ thứ
 * gì. Do đó, mỗi State chỉ cần tồn tại một bản duy nhất trong suốt vòng đời ứng dụng: không tốn
 * thêm bộ nhớ, không rủi ro race condition.
 *
 * <p>Cách sử dụng: {@link com.auction.pattern.factory.AuctionStateFactory} tra cứu đây để trả về
 * đúng State singleton tương ứng với chuỗi trạng thái từ DB (ví dụ: {@code "RUNNING"} → {@link
 * #RUNNING}).
 *
 * <p>Lớp này là utility class — không thể khởi tạo.
 */
public final class AuctionStates {

  /** Ngăn khởi tạo — đây là holder class chỉ chứa hằng số static. */
  private AuctionStates() {}

  /** Singleton trạng thái OPEN — phiên đã tạo, chưa đến giờ bắt đầu. */
  public static final AuctionState OPEN = new OpenState();

  /** Singleton trạng thái RUNNING — phiên đang diễn ra, đang nhận giá đặt. */
  public static final AuctionState RUNNING = new RunningState();

  /** Singleton trạng thái SETTLING — phiên đang được khóa để chốt kết quả. */
  public static final AuctionState SETTLING = new SettlingState();

  /** Singleton trạng thái FINISHED — phiên đã kết thúc, chờ thanh toán. */
  public static final AuctionState FINISHED = new FinishedState();

  /** Singleton trạng thái PAID — phiên đã thanh toán hoàn tất (trạng thái cuối tích cực). */
  public static final AuctionState PAID = new PaidState();

  /** Singleton trạng thái CANCELED — phiên đã bị hủy (trạng thái cuối tiêu cực). */
  public static final AuctionState CANCELED = new CanceledState();
}
