package com.auction.dao;

import java.math.BigDecimal;
import org.jdbi.v3.core.Handle;

/**
 * DAO ghi sổ cái ví người dùng — append-only (chỉ thêm, không sửa/xóa).
 *
 * <p>Mỗi lần số dư hoặc reserved_balance thay đổi đều phải ghi một bản ghi vào bảng {@code
 * wallet_transactions}. Thiết kế append-only đảm bảo audit trail đầy đủ: bất kỳ tranh chấp nào về
 * số dư đều có thể được tái hiện bằng cách replay toàn bộ lịch sử giao dịch.
 *
 * <p>Các loại giao dịch ({@code kind}):
 *
 * <ul>
 *   <li>{@code DEPOSIT} — nạp tiền vào tài khoản (admin duyệt).
 *   <li>{@code FREEZE} — khóa tiền khi bidder dẫn đầu một phiên đấu giá.
 *   <li>{@code RELEASE} — giải phóng tiền đã khóa khi bidder bị vượt giá.
 *   <li>{@code WIN_CONSUME} — trừ tiền khi bidder thắng và hoàn tất thanh toán.
 *   <li>{@code SELLER_PAYOUT} — cộng tiền cho seller sau khi phiên được thanh toán.
 *   <li>{@code CANCEL_RELEASE}— giải phóng tiền đã khóa khi phiên bị hủy.
 * </ul>
 *
 * <p>Lớp này là utility class — chỉ có phương thức static, không thể khởi tạo.
 */
public final class WalletTransactionDao {

  /** Ngăn khởi tạo — đây là utility class chỉ chứa phương thức static. */
  private WalletTransactionDao() {}

  /**
   * Ghi một bản ghi giao dịch ví vào DB trong transaction đang mở.
   *
   * <p>Phương thức này luôn được gọi bên trong một transaction lớn hơn (ví dụ: đặt giá, thanh toán,
   * hủy phiên) để đảm bảo tính nhất quán: thay đổi số dư và bản ghi lịch sử được commit hoặc
   * rollback cùng nhau.
   *
   * @param handle handle của transaction hiện tại
   * @param userId người dùng liên quan đến giao dịch
   * @param auctionId phiên đấu giá liên quan (có thể null nếu là DEPOSIT)
   * @param bidTransactionId lượt đặt giá liên quan (có thể null nếu không phải FREEZE/RELEASE)
   * @param kind loại giao dịch (DEPOSIT, FREEZE, RELEASE, WIN_CONSUME, ...)
   * @param amount số tiền — phải dương, được kiểm tra bởi DB constraint
   * @param referenceInfo thông tin tham chiếu tùy chọn để debug (ví dụ: "auction_cancel:42")
   */
  public static void insert(
      Handle handle,
      Long userId,
      Long auctionId,
      Long bidTransactionId,
      String kind,
      BigDecimal amount,
      String referenceInfo) {
    handle.execute(
        """
        INSERT INTO wallet_transactions (
            user_id, auction_id, bid_transaction_id, kind, amount, reference_info
        )
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        userId,
        auctionId,
        bidTransactionId,
        kind,
        amount,
        referenceInfo);
  }
}
