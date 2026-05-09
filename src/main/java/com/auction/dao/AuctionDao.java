package com.auction.dao;

import com.auction.model.Auction;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DAO (Data Access Object) cho bảng auctions
 *
 * <p>Class này chịu trách nhiệm giao tiếp với bảng auctions trong database. Đây là DAO quan trọng
 * nhất vì nó xử lý các thao tác đấu giá cốt lõi, bao gồm cả việc khóa row để tránh race condition
 * (SELECT FOR UPDATE)
 *
 * <h3>Vai trò trong hệ thống</h3>
 *
 * <p>AuctionDao nằm ở tầng dưới cùng, được AuctionService và BidService gọi. Mọi thao tác đọc/ghi
 * trên bảng auctions đều đi qua DAO này
 *
 * <h3>Concurrency - SELECT FOR UPDATE</h3>
 *
 * <p>Khi nhiều user bid cùng lúc, cần đảm bảo không có lost update. Giải pháp: 2 tầng bảo vệ
 *
 * <ol>
 *   <li><b>Tầng application:</b> synchronized(auction) trong BidService
 *   <li><b>Tầng database:</b> SELECT ... FOR UPDATE khóa row khi đọc
 * </ol>
 *
 * <p>Luồng xử lý bid an toàn:
 *
 * <pre>
 * jdbi.inTransaction(handle -> {
 *     // 1. Khóa row - các transaction khác phải chờ
 *     Auction auction = auctionDao.findByIdForUpdate(handle, auctionId);
 *
 *     // 2. Validate và update trong Java
 *     auction.setCurrentPrice(newPrice);
 *     auction.setLeadingBidderId(userId);
 *
 *     // 3. Update database - lock được giải phóng khi commit
 *     auctionDao.updateInTransaction(handle, auction);
 *
 *     // 4. Commit transaction
 *     return auction;
 * });
 * </pre>
 *
 * <h3>State pattern liên kết</h3>
 *
 * <p>Trạng thái của Auction (OPEN, RUNNING, FINISHED, PAID, CANCELED) được quản lý bởi State
 * pattern trong service layer. DAO chỉ đơn thuần đọc/ghi trạng thái, không quyết định logic chuyển
 * trạng thái.
 *
 * <h3>Liên kết với các file khác</h3>
 *
 * <ul>
 *   <li><b>Auction.java</b> — model class, chứa dữ liệu phiên đấu giá
 *   <li><b>BidService.java</b> — gọi findByIdForUpdate() và updateInTransaction()
 *   <li><b>AuctionService.java</b> — CRUD thông thường cho auction
 *   <li><b>AuctionScheduler.java</b> — cập nhật trạng thái khi hết giờ
 *   <li><b>V1__initial_schema.sql</b> — định nghĩa bảng auctions
 * </ul>
 */
public class AuctionDao {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuctionDao.class);

  /**
   * Danh sách cột SELECT dùng chung, tránh copy-paste. [FIX #10]
   *
   * <p>Bao gồm cả updated_at [FIX #6] — trước đây SELECT có updated_at nhưng Mapper không đọc, gây
   * mất thông tin cập nhật cuối cùng.
   */
  private static final String SELECT_COLUMNS =
      "id, item_id, starting_price, current_price, leading_bidder_id, "
          + "start_time, end_time, status, created_at, updated_at";

  private final Jdbi jdbi;

  public AuctionDao(Jdbi jdbi) {
    this.jdbi = jdbi;
  }

  /**
   * RowMapper chuyển ResultSet thành Auction object.
   *
   * <p>[FIX #6] Mapper giờ đọc cả cột updated_at và set vào Auction object. Trước đây cột này được
   * SELECT nhưng bị bỏ qua → mất dữ liệu "cập nhật lần cuối".
   *
   * <p><b>LƯU Ý:</b> Cần thêm field {@code updatedAt} (kiểu LocalDateTime) và setter {@code
   * setUpdatedAt()} vào model {@code Auction.java} nếu chưa có.
   *
   * <p>Map các cột trong bảng auctions:
   *
   * <pre>
   * | id | item_id | starting_price | current_price | leading_bidder_id |
   * | start_time | end_time | status | created_at | updated_at |
   * </pre>
   */
  private static class AuctionMapper implements RowMapper<Auction> {
    @Override
    public Auction map(ResultSet rs, StatementContext ctx) throws SQLException {
      Auction auction =
          new Auction(
              rs.getLong("id"),
              rs.getLong("item_id"),
              rs.getBigDecimal("starting_price"),
              rs.getBigDecimal("current_price"),
              getLongOrNull(rs, "leading_bidder_id"),
              rs.getTimestamp("start_time").toLocalDateTime(),
              rs.getTimestamp("end_time").toLocalDateTime(),
              rs.getString("status"),
              rs.getTimestamp("created_at").toLocalDateTime());

      // [FIX #6] Đọc updated_at và set vào Auction
      var updatedAtTs = rs.getTimestamp("updated_at");
      if (updatedAtTs != null) {
        auction.setUpdatedAt(updatedAtTs.toLocalDateTime());
      }

      return auction;
    }

    private Long getLongOrNull(ResultSet rs, String column) throws SQLException {
      long value = rs.getLong(column);
      return rs.wasNull() ? null : value;
    }
  }

  // ============================================================
  // CREATE (INSERT)
  // ============================================================

  /**
   * Tạo phiên đấu giá mới.
   *
   * <p>Được gọi khi Seller tạo phiên đấu giá mới (AuctionService.createAuction()).
   *
   * @param auction đối tượng Auction (chưa có id, status = "OPEN")
   * @return Auction đã được gán id từ database
   */
  public Auction insert(Auction auction) {
    String sql =
        """
        INSERT INTO auctions (
            item_id, starting_price, current_price,
            leading_bidder_id, start_time, end_time,
            status, created_at, updated_at
        ) VALUES (
            :itemId, :startingPrice, :currentPrice,
            :leadingBidderId, :startTime, :endTime,
            :status, :createdAt, :updatedAt
        )
        RETURNING id
        """;

    return jdbi.withHandle(
        handle -> {
          long id =
              handle
                  .createQuery(sql)
                  .bind("itemId", auction.getItemId())
                  .bind("startingPrice", auction.getStartingPrice())
                  .bind("currentPrice", auction.getCurrentPrice())
                  .bind("leadingBidderId", auction.getLeadingBidderId())
                  .bind("startTime", auction.getStartTime())
                  .bind("endTime", auction.getEndTime())
                  .bind("status", auction.getStatus())
                  .bind("createdAt", auction.getCreatedAt())
                  .bind("updatedAt", auction.getCreatedAt()) // mới tạo, updatedAt = createdAt
                  .mapTo(Long.class)
                  .one();

          auction.setId(id);
          LOGGER.debug("Inserted auction: id={}, itemId={}", id, auction.getItemId());
          return auction;
        });
  }

  // ============================================================
  // READ (SELECT)
  // ============================================================

  /**
   * Tìm phiên đấu giá theo ID (SELECT thường, không khóa).
   *
   * <p>Dùng cho các thao tác đọc thông thường như:
   *
   * <ul>
   *   <li>GET /api/auctions/{id} — xem chi tiết phiên
   *   <li>Kiểm tra phiên tồn tại trước khi xóa
   * </ul>
   *
   * @param id ID của phiên đấu giá
   * @return Optional chứa Auction nếu tìm thấy, Optional.empty() nếu không
   */
  public Optional<Auction> findById(Long id) {
    String sql = "SELECT " + SELECT_COLUMNS + " FROM auctions WHERE id = :id";

    return jdbi.withHandle(
        handle -> handle.createQuery(sql).bind("id", id).map(new AuctionMapper()).findOne());
  }

  /**
   * Tìm phiên đấu giá với khóa row (SELECT FOR UPDATE).
   *
   * <p><b>QUAN TRỌNG:</b> Method này phải được gọi TRONG TRANSACTION. Row bị khóa cho đến khi
   * transaction COMMIT hoặc ROLLBACK. Các transaction khác cố gắng đọc row này sẽ phải chờ.
   *
   * <p>Đây là tầng bảo vệ thứ 2 cho concurrency (sau synchronized trong BidService).
   *
   * <p><b>Ví dụ sử dụng trong transaction:</b>
   *
   * <pre>
   * jdbi.inTransaction(handle -> {
   *     Auction auction = auctionDao.findByIdForUpdate(handle, auctionId);
   *     // xử lý bid...
   *     auctionDao.updateInTransaction(handle, auction);
   *     return auction;
   * });
   * </pre>
   *
   * @param handle Handle từ JDBI transaction (đã mở transaction)
   * @param id ID của phiên đấu giá
   * @return Auction với row đã bị khóa (FOR UPDATE)
   * @throws org.jdbi.v3.core.statement.UnableToExecuteStatementException nếu không tìm thấy
   */
  public Auction findByIdForUpdate(org.jdbi.v3.core.Handle handle, Long id) {
    String sql = "SELECT " + SELECT_COLUMNS + " FROM auctions WHERE id = :id FOR UPDATE";

    return handle
        .createQuery(sql)
        .bind("id", id)
        .map(new AuctionMapper())
        .one(); // throw exception nếu không tìm thấy
  }

  /**
   * Lấy tất cả phiên đấu giá.
   *
   * <p>Dùng cho màn hình danh sách phiên (auction-list.fxml).
   *
   * @return List chứa tất cả Auction (sắp xếp theo end_time gần nhất trước)
   */
  public List<Auction> findAll() {
    String sql = "SELECT " + SELECT_COLUMNS + " FROM auctions ORDER BY end_time ASC";

    return jdbi.withHandle(handle -> handle.createQuery(sql).map(new AuctionMapper()).list());
  }

  /**
   * Lấy các phiên đấu giá theo trạng thái.
   *
   * <p>Dùng cho:
   *
   * <ul>
   *   <li>AuctionScheduler: tìm các phiên RUNNING sắp hết giờ
   *   <li>Admin Panel: lọc phiên theo trạng thái
   *   <li>Seller: xem phiên đang chạy của mình
   * </ul>
   *
   * @param status trạng thái cần tìm (OPEN, RUNNING, FINISHED, PAID, CANCELED)
   * @return List các Auction có status tương ứng
   */
  public List<Auction> findByStatus(String status) {
    String sql =
        "SELECT " + SELECT_COLUMNS + " FROM auctions WHERE status = :status ORDER BY end_time ASC";

    return jdbi.withHandle(
        handle -> handle.createQuery(sql).bind("status", status).map(new AuctionMapper()).list());
  }

  /**
   * Lấy các phiên đấu giá của một item (sản phẩm có thể được đấu giá nhiều lần).
   *
   * <p>Theo logic, mỗi item chỉ nên có 1 phiên đấu giá, nhưng để an toàn vẫn hỗ trợ lấy danh sách
   * (trong trường hợp item được đem đấu giá lại).
   *
   * @param itemId ID của sản phẩm
   * @return List các Auction của item đó
   */
  public List<Auction> findByItemId(Long itemId) {
    String sql =
        "SELECT "
            + SELECT_COLUMNS
            + " FROM auctions WHERE item_id = :itemId ORDER BY created_at DESC";

    return jdbi.withHandle(
        handle -> handle.createQuery(sql).bind("itemId", itemId).map(new AuctionMapper()).list());
  }

  /**
   * Lấy các phiên đang diễn ra (RUNNING) cho màn hình chính.
   *
   * @return List các Auction có status = "RUNNING" và chưa hết giờ
   */
  public List<Auction> findActiveAuctions() {
    String sql =
        "SELECT "
            + SELECT_COLUMNS
            + " FROM auctions WHERE status = 'RUNNING' AND end_time > NOW()"
            + " ORDER BY end_time ASC";

    return jdbi.withHandle(handle -> handle.createQuery(sql).map(new AuctionMapper()).list());
  }

  // ============================================================
  // UPDATE
  // ============================================================

  /**
   * Cập nhật phiên đấu giá — phiên bản dùng trong TRANSACTION.
   *
   * <p>[FIX #5] Đổi tên từ {@code update(Handle, Auction)} thành {@code updateInTransaction(Handle,
   * Auction)} để phân biệt rõ hành vi:
   *
   * <ul>
   *   <li>{@code updateInTransaction} — <b>throw exception</b> nếu không tìm thấy (fail-fast trong
   *       transaction, rollback toàn bộ)
   *   <li>{@code update} — <b>return false</b> nếu không tìm thấy (caller tự xử lý)
   * </ul>
   *
   * <p>Method này được dùng trong transaction khi có findByIdForUpdate() trước đó.
   *
   * @param handle Handle từ transaction đang mở
   * @param auction Auction đã được cập nhật (phải có id)
   * @throws IllegalStateException nếu không tìm thấy auction → rollback transaction
   */
  public void updateInTransaction(org.jdbi.v3.core.Handle handle, Auction auction) {
    String sql =
        """
        UPDATE auctions
        SET current_price = :currentPrice,
            leading_bidder_id = :leadingBidderId,
            end_time = :endTime,
            status = :status,
            updated_at = :updatedAt
        WHERE id = :id
        """;

    int rowsAffected =
        handle
            .createUpdate(sql)
            .bind("currentPrice", auction.getCurrentPrice())
            .bind("leadingBidderId", auction.getLeadingBidderId())
            .bind("endTime", auction.getEndTime())
            .bind("status", auction.getStatus())
            .bind("updatedAt", LocalDateTime.now())
            .bind("id", auction.getId())
            .execute();

    if (rowsAffected == 0) {
      LOGGER.warn("Auction not found for update in transaction: id={}", auction.getId());
      throw new IllegalStateException("Auction not found: " + auction.getId());
    }
  }

  /**
   * Cập nhật phiên đấu giá — phiên bản NGOÀI transaction.
   *
   * <p>Dùng cho các thao tác update đơn giản không cần transaction, ví dụ: Admin sửa trạng thái,
   * AuctionScheduler đóng phiên.
   *
   * @param auction Auction đã được cập nhật (phải có id)
   */
  public void update(Auction auction) {
    String sql =
        """
        UPDATE auctions
        SET current_price = :currentPrice,
            leading_bidder_id = :leadingBidderId,
            end_time = :endTime,
            status = :status,
            updated_at = :updatedAt
        WHERE id = :id
        """;

    int rowsAffected =
        jdbi.withHandle(
            handle ->
                handle
                    .createUpdate(sql)
                    .bind("currentPrice", auction.getCurrentPrice())
                    .bind("leadingBidderId", auction.getLeadingBidderId())
                    .bind("endTime", auction.getEndTime())
                    .bind("status", auction.getStatus())
                    .bind("updatedAt", LocalDateTime.now())
                    .bind("id", auction.getId())
                    .execute());

    if (rowsAffected > 0) {
      LOGGER.debug(
          "Updated auction: id={}, status={}, price={}",
          auction.getId(),
          auction.getStatus(),
          auction.getCurrentPrice());
    } else {
      LOGGER.warn("Auction not found for update: id={}", auction.getId());
    }
  }

  // ============================================================
  // DELETE
  // ============================================================

  /**
   * Xóa phiên đấu giá.
   *
   * <p>Chỉ Admin hoặc Seller của item mới có quyền xóa phiên chưa bắt đầu.
   *
   * @param id ID của phiên cần xóa
   * @return true nếu xóa thành công, false nếu không tìm thấy
   */
  public void delete(Long id) {
    String sql = "DELETE FROM auctions WHERE id = :id";

    int rowsAffected = jdbi.withHandle(handle -> handle.createUpdate(sql).bind("id", id).execute());

    if (rowsAffected > 0) {
      LOGGER.info("Deleted auction: id={}", id);
    } else {
      LOGGER.warn("Auction not found for deletion: id={}", id);
    }
  }

  /**
   * Xóa cứng phiên đấu giá cùng toàn bộ dữ liệu liên quan (bid_transactions, auto_bid_configs).
   * Thực hiện trong 1 transaction để đảm bảo tính toàn vẹn dữ liệu.
   */
  public void hardDelete(Long id) {
    jdbi.useTransaction(
        handle -> {
          handle
              .createUpdate("DELETE FROM auto_bid_configs WHERE auction_id = :id")
              .bind("id", id)
              .execute();
          handle
              .createUpdate("DELETE FROM bid_transactions WHERE auction_id = :id")
              .bind("id", id)
              .execute();
          handle.createUpdate("DELETE FROM auctions WHERE id = :id").bind("id", id).execute();
        });
    LOGGER.info("Hard-deleted auction: id={}", id);
  }

  // ============================================================
  // HELPER METHODS
  // ============================================================

  /**
   * Đóng các phiên đấu giá đã hết hạn.
   *
   * <p>Được gọi bởi AuctionScheduler mỗi phút để chuyển các phiên hết hạn từ trạng thái RUNNING
   * sang FINISHED.
   *
   * @return số lượng phiên đã được đóng
   */
  public int closeExpiredAuctions() {
    String sql =
        """
        UPDATE auctions
        SET status = 'FINISHED', updated_at = NOW()
        WHERE status = 'RUNNING' AND end_time <= NOW()
        """;

    int rowsAffected = jdbi.withHandle(handle -> handle.createUpdate(sql).execute());

    if (rowsAffected > 0) {
      LOGGER.info("Closed {} expired auctions", rowsAffected);
    }

    return rowsAffected;
  }

  /**
   * Chuyển các phiên từ OPEN sang RUNNING khi đến giờ bắt đầu.
   *
   * <p>Được gọi bởi AuctionScheduler mỗi phút.
   *
   * @return số lượng phiên đã được chuyển sang RUNNING
   */
  public int startScheduledAuctions() {
    String sql =
        """
        UPDATE auctions
        SET status = 'RUNNING', updated_at = NOW()
        WHERE status = 'OPEN' AND start_time <= NOW()
        """;

    int rowsAffected = jdbi.withHandle(handle -> handle.createUpdate(sql).execute());

    if (rowsAffected > 0) {
      LOGGER.info("Started {} scheduled auctions", rowsAffected);
    }

    return rowsAffected;
  }

  /**
   * Kiểm tra phiên đấu giá có tồn tại không.
   *
   * @param id ID cần kiểm tra
   * @return true nếu tồn tại, false nếu không
   */
  public boolean existsById(Long id) {
    String sql = "SELECT COUNT(*) FROM auctions WHERE id = :id";

    long count =
        jdbi.withHandle(handle -> handle.createQuery(sql).bind("id", id).mapTo(Long.class).one());

    return count > 0;
  }

  /**
   * Lấy giá hiện tại của phiên đấu giá.
   *
   * <p>Dùng để kiểm tra trước khi bid mà không cần load toàn bộ Auction.
   *
   * @param id ID của phiên đấu giá
   * @return Optional chứa giá hiện tại, Optional.empty() nếu không tìm thấy
   */
  public Optional<BigDecimal> getCurrentPrice(Long id) {
    String sql = "SELECT current_price FROM auctions WHERE id = :id";

    return jdbi.withHandle(
        handle -> handle.createQuery(sql).bind("id", id).mapTo(BigDecimal.class).findOne());
  }
}
