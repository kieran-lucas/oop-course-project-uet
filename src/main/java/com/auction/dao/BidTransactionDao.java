package com.auction.dao;

import com.auction.model.BidTransaction;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * DAO (Data Access Object) cho bảng bid_transactions.
 *
 * <p>Class này chịu trách nhiệm ghi lại lịch sử đấu giá vào bảng bid_transactions.
 * BidTransaction là dữ liệu bất biến (append-only) — chỉ INSERT, không UPDATE hay DELETE.
 * Điều này đảm bảo tính minh bạch và khả năng kiểm toán (audit trail) của hệ thống.
 *
 * <h3>Vai trò trong hệ thống</h3>
 * <p>Mỗi khi có một bid thành công (thủ công hoặc auto-bid), hệ thống ghi lại:
 * <ul>
 *   <li>Ai đã bid (bidderId)</li>
 *   <li>Phiên nào (auctionId)</li>
 *   <li>Giá bao nhiêu (amount)</li>
 *   <li>Lúc nào (created_at)</li>
 *   <li>Là auto-bid hay thủ công (auto_bid flag)</li>
 * </ul>
 *
 * <h3>Ứng dụng của bid history</h3>
 * <ul>
 *   <li><b>Bid History Chart:</b> Lấy tất cả bid của một phiên để vẽ biểu đồ đường
 *       (trục X = thời gian, trục Y = giá)</li>
 *   <li><b>Xác định người thắng:</b> Tìm bid cuối cùng (giá cao nhất) của phiên đã kết thúc</li>
 *   <li><b>Audit trail:</b> Nếu có tranh chấp, có thể xem lại toàn bộ lịch sử</li>
 *   <li><b>Thống kê:</b> Đếm số bid của mỗi user, phân tích hành vi đấu giá</li>
 * </ul>
 *
 * <h3>Liên kết với các file khác</h3>
 * <ul>
 *   <li><b>BidTransaction.java</b> — model class, chứa dữ liệu một bid</li>
 *   <li><b>BidService.java</b> — gọi insert() sau mỗi bid thành công</li>
 *   <li><b>AuctionController.java</b> — gọi findByAuctionId() cho Bid History Chart</li>
 *   <li><b>V1__initial_schema.sql</b> — định nghĩa bảng bid_transactions</li>
 * </ul>
 */
public class BidTransactionDao {

  private static final Logger LOGGER = LoggerFactory.getLogger(BidTransactionDao.class);

  /** Danh sách cột SELECT dùng chung, tránh copy-paste. [FIX #10] */
  private static final String SELECT_COLUMNS =
      "id, auction_id, bidder_id, amount, auto_bid, created_at";

  private final Jdbi jdbi;

  public BidTransactionDao(Jdbi jdbi) {
    this.jdbi = jdbi;
  }

  /**
   * RowMapper chuyển ResultSet thành BidTransaction object.
   *
   * <p>Map các cột trong bảng bid_transactions:
   * <pre>
   * | id | auction_id | bidder_id | amount | auto_bid | created_at |
   * </pre>
   */
  private static class BidTransactionMapper implements RowMapper<BidTransaction> {
    @Override
    public BidTransaction map(ResultSet rs, StatementContext ctx) throws SQLException {
      return new BidTransaction(
          rs.getLong("id"),
          rs.getLong("auction_id"),
          rs.getLong("bidder_id"),
          rs.getBigDecimal("amount"),
          rs.getBoolean("auto_bid"),
          rs.getTimestamp("created_at").toLocalDateTime()
      );
    }
  }

  // ============================================================
  // CREATE (INSERT) — Chỉ có insert, không update/delete
  // ============================================================

  /**
   * Ghi lại một bid thành công vào database (phiên bản TRONG TRANSACTION).
   *
   * <p>Method này được gọi sau khi BidService đã validate và cập nhật auction.
   * Nó được gọi TRONG CÙNG TRANSACTION với auction update để đảm bảo consistency.
   *
   * <p>Ví dụ transaction trong BidService:
   * <pre>
   * jdbi.inTransaction(handle -> {
   *     Auction auction = auctionDao.findByIdForUpdate(handle, auctionId);
   *     // validate và update auction...
   *     auctionDao.updateInTransaction(handle, auction);
   *
   *     // Ghi bid transaction trong cùng transaction
   *     bidTransactionDao.insert(handle, new BidTransaction(...));
   *
   *     return auction;
   * });
   * </pre>
   *
   * @param handle Handle từ transaction đang mở (đã có connection)
   * @param transaction BidTransaction cần ghi (chưa có id)
   * @return BidTransaction đã được gán id từ database
   */
  public BidTransaction insert(org.jdbi.v3.core.Handle handle, BidTransaction transaction) {
    String sql = """
        INSERT INTO bid_transactions (auction_id, bidder_id, amount, auto_bid, created_at)
        VALUES (:auctionId, :bidderId, :amount, :autoBid, :createdAt)
        RETURNING id
        """;

    long id = handle.createQuery(sql)
        .bind("auctionId", transaction.getAuctionId())
        .bind("bidderId", transaction.getBidderId())
        .bind("amount", transaction.getAmount())
        .bind("autoBid", transaction.isAutoBid())
        .bind("createdAt", transaction.getCreatedAt())
        .mapTo(Long.class)
        .one();

    transaction.setId(id);
    LOGGER.debug("Inserted bid transaction: auction={}, bidder={}, amount={}, auto={}",
        transaction.getAuctionId(), transaction.getBidderId(),
        transaction.getAmount(), transaction.isAutoBid());

    return transaction;
  }

  /**
   * Ghi lại một bid thành công (phiên bản NGOÀI transaction, tạo connection riêng).
   *
   * <p>Dùng cho các trường hợp không cần transaction với auction update,
   * ví dụ: khi insert bid history cho mục đích logging không critical.
   *
   * @param transaction BidTransaction cần ghi
   * @return BidTransaction đã được gán id
   */
  public BidTransaction insert(BidTransaction transaction) {
    String sql = """
        INSERT INTO bid_transactions (auction_id, bidder_id, amount, auto_bid, created_at)
        VALUES (:auctionId, :bidderId, :amount, :autoBid, :createdAt)
        RETURNING id
        """;

    return jdbi.withHandle(handle -> {
      long id = handle.createQuery(sql)
          .bind("auctionId", transaction.getAuctionId())
          .bind("bidderId", transaction.getBidderId())
          .bind("amount", transaction.getAmount())
          .bind("autoBid", transaction.isAutoBid())
          .bind("createdAt", transaction.getCreatedAt())
          .mapTo(Long.class)
          .one();

      transaction.setId(id);
      LOGGER.debug("Inserted bid transaction: auction={}, bidder={}, amount={}",
          transaction.getAuctionId(), transaction.getBidderId(), transaction.getAmount());

      return transaction;
    });
  }

  // ============================================================
  // READ (SELECT)
  // ============================================================

  /**
   * Lấy tất cả bid transactions của một phiên đấu giá.
   *
   * <p><b>QUAN TRỌNG:</b> Method này được dùng cho Bid History Chart.
   * Kết quả trả về sắp xếp theo thời gian tăng dần (từ cũ đến mới)
   * để vẽ biểu đồ đường (line chart) với trục X = thời gian.
   *
   * @param auctionId ID của phiên đấu giá
   * @return List các BidTransaction sắp xếp theo thời gian (cũ → mới)
   */
  public List<BidTransaction> findByAuctionId(Long auctionId) {
    String sql = "SELECT " + SELECT_COLUMNS
        + " FROM bid_transactions WHERE auction_id = :auctionId ORDER BY created_at ASC";

    return jdbi.withHandle(handle ->
        handle.createQuery(sql)
            .bind("auctionId", auctionId)
            .map(new BidTransactionMapper())
            .list()
    );
  }

  /**
   * Lấy tất cả bid transactions của một người dùng.
   *
   * <p>Dùng cho:
   * <ul>
   *   <li>User profile: hiển thị lịch sử đấu giá của bidder</li>
   *   <li>Thống kê: đếm số lần bid của mỗi user</li>
   * </ul>
   *
   * @param bidderId ID của người đấu giá
   * @return List các BidTransaction sắp xếp theo thời gian (mới → cũ)
   */
  public List<BidTransaction> findByBidderId(Long bidderId) {
    String sql = "SELECT " + SELECT_COLUMNS
        + " FROM bid_transactions WHERE bidder_id = :bidderId ORDER BY created_at DESC";

    return jdbi.withHandle(handle ->
        handle.createQuery(sql)
            .bind("bidderId", bidderId)
            .map(new BidTransactionMapper())
            .list()
    );
  }

  /**
   * Tìm bid transaction theo ID.
   *
   * <p>Dùng để kiểm tra bid transaction cụ thể, phục vụ test và debug.
   *
   * @param id ID của bid transaction
   * @return Optional chứa BidTransaction nếu tìm thấy, Optional.empty() nếu không
   */
  public Optional<BidTransaction> findById(Long id) {
    String sql = "SELECT " + SELECT_COLUMNS + " FROM bid_transactions WHERE id = :id";

    return jdbi.withHandle(handle ->
        handle.createQuery(sql)
            .bind("id", id)
            .map(new BidTransactionMapper())
            .findOne()
    );
  }

  /**
   * Lấy bid cuối cùng (giá cao nhất) của một phiên đấu giá.
   *
   * <p>Dùng để xác định người thắng khi phiên kết thúc.
   *
   * <p>[FIX #7] Order by cả amount DESC lẫn created_at DESC.
   * Trước đây chỉ order by created_at → edge case khi auto-bid xử lý nhanh,
   * 2 bids có thể cùng created_at (precision đến millisecond).
   * Thêm amount DESC đảm bảo luôn lấy bid giá cao nhất.
   *
   * @param auctionId ID của phiên đấu giá
   * @return Optional chứa BidTransaction cuối cùng, Optional.empty() nếu chưa có bid nào
   */
  public Optional<BidTransaction> findLastBid(Long auctionId) {
    String sql = "SELECT " + SELECT_COLUMNS
        + " FROM bid_transactions WHERE auction_id = :auctionId"
        + " ORDER BY amount DESC, created_at DESC LIMIT 1";

    return jdbi.withHandle(handle ->
        handle.createQuery(sql)
            .bind("auctionId", auctionId)
            .map(new BidTransactionMapper())
            .findOne()
    );
  }

  /**
   * Lấy tất cả bid transactions của một phiên, kèm thông tin username của bidder.
   *
   * <p>Method này JOIN với bảng users để lấy username,
   * giúp client hiển thị tên người bid trên chart mà không cần gọi thêm API.
   *
   * @param auctionId ID của phiên đấu giá
   * @return List các BidHistoryEntry chứa BidTransaction + username
   */
  public List<BidHistoryEntry> findWithUsernames(Long auctionId) {
    String sql = """
        SELECT bt.id, bt.auction_id, bt.bidder_id, bt.amount, bt.auto_bid, bt.created_at,
               u.username
        FROM bid_transactions bt
        JOIN users u ON bt.bidder_id = u.id
        WHERE bt.auction_id = :auctionId
        ORDER BY bt.created_at ASC
        """;

    return jdbi.withHandle(handle ->
        handle.createQuery(sql)
            .bind("auctionId", auctionId)
            .map((rs, ctx) -> {
              BidTransaction tx = new BidTransaction(
                  rs.getLong("id"),
                  rs.getLong("auction_id"),
                  rs.getLong("bidder_id"),
                  rs.getBigDecimal("amount"),
                  rs.getBoolean("auto_bid"),
                  rs.getTimestamp("created_at").toLocalDateTime()
              );
              String username = rs.getString("username");
              return new BidHistoryEntry(tx, username);
            })
            .list()
    );
  }

  /**
   * Đếm số lượng bid trong một phiên.
   *
   * <p>Dùng để hiển thị thống kê (ví dụ: "Đã có 15 lượt đấu giá").
   *
   * @param auctionId ID của phiên đấu giá
   * @return số lượng bid transactions
   */
  public int countByAuctionId(Long auctionId) {
    String sql = "SELECT COUNT(*) FROM bid_transactions WHERE auction_id = :auctionId";

    return jdbi.withHandle(handle ->
        handle.createQuery(sql)
            .bind("auctionId", auctionId)
            .mapTo(Integer.class)
            .one()
    );
  }

  /**
   * Lấy giá cao nhất của một phiên (cũng chính là giá cuối cùng).
   *
   * <p>Dùng để kiểm tra nhanh mà không cần load toàn bộ auction.
   *
   * @param auctionId ID của phiên đấu giá
   * @return Optional chứa giá cao nhất, Optional.empty() nếu chưa có bid nào
   */
  public Optional<BigDecimal> getHighestPrice(Long auctionId) {
    String sql = """
        SELECT amount
        FROM bid_transactions
        WHERE auction_id = :auctionId
        ORDER BY amount DESC
        LIMIT 1
        """;

    return jdbi.withHandle(handle ->
        handle.createQuery(sql)
            .bind("auctionId", auctionId)
            .mapTo(BigDecimal.class)
            .findOne()
    );
  }

  // ============================================================
  // HELPER — BidHistoryEntry record
  // ============================================================

  /**
   * DTO helper cho bid history kèm username.
   *
   * <p>[FIX #12] Đổi từ inner class thành Java 21 record — gọn hơn, immutable,
   * tự sinh equals/hashCode/toString. Được dùng trong {@code findWithUsernames()}.
   *
   * <p><b>Cân nhắc:</b> Nếu muốn tách ra file riêng, đặt vào package dto/
   * (ví dụ: {@code dto/BidHistoryEntry.java}) vì nó thực chất là DTO trả về client.
   *
   * @param transaction giao dịch bid
   * @param username tên người đấu giá (lấy từ bảng users)
   */
  public record BidHistoryEntry(BidTransaction transaction, String username) {

    /** Shortcut: lấy ID phiên đấu giá. */
    public Long getAuctionId() {
      return transaction.getAuctionId();
    }

    /** Shortcut: lấy ID người đấu giá. */
    public Long getBidderId() {
      return transaction.getBidderId();
    }

    /** Shortcut: lấy giá bid. */
    public BigDecimal getAmount() {
      return transaction.getAmount();
    }

    /** Shortcut: kiểm tra có phải auto-bid không. */
    public boolean isAutoBid() {
      return transaction.isAutoBid();
    }

    /** Shortcut: lấy thời điểm bid. */
    public LocalDateTime getCreatedAt() {
      return transaction.getCreatedAt();
    }
  }

  // ============================================================
  // CLEANUP (Chỉ dùng cho test)
  // ============================================================

  /**
   * Xóa tất cả bid transactions của một phiên.
   *
   * <p><b>CHỈ DÙNG CHO TEST.</b> Không gọi method này trong production.
   *
   * @param auctionId ID của phiên đấu giá
   * @return số lượng dòng bị xóa
   */
  public int deleteByAuctionId(Long auctionId) {
    String sql = "DELETE FROM bid_transactions WHERE auction_id = :auctionId";

    return jdbi.withHandle(handle ->
        handle.createUpdate(sql)
            .bind("auctionId", auctionId)
            .execute()
    );
  }
}
