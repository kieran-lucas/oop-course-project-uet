package com.auction.dao;

import com.auction.model.DepositRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

/**
 * DAO (Data Access Object) cho bảng deposit_requests.
 *
 * <p>Quản lý vòng đời của các yêu cầu nạp tiền: từ khi user tạo yêu cầu (PENDING), admin duyệt
 * (APPROVED) hoặc từ chối (REJECTED). Mỗi thay đổi trạng thái đều ghi nhận thời điểm review qua cột
 * {@code reviewed_at}.
 *
 * <h3>JOIN với bảng users</h3>
 *
 * <p>Một số query JOIN thêm bảng {@code users} để lấy {@code username} hiển thị trên giao diện
 * admin. Query chỉ dùng cho một user cụ thể (ví dụ {@code findByUserId}) thì không cần JOIN. {@code
 * DepositRecordMapper} xử lý cả 2 trường hợp — nếu cột {@code username} không có trong ResultSet
 * thì bỏ qua, không throw exception.
 *
 * <h3>Liên kết với các file khác</h3>
 *
 * <ul>
 *   <li><b>DepositRecord.java</b> — model class tương ứng
 *   <li><b>DepositService.java</b> — gọi insert() khi user tạo yêu cầu, updateStatus() khi admin
 *       duyệt/từ chối
 *   <li><b>V1__initial_schema.sql</b> — định nghĩa bảng deposit_requests
 * </ul>
 */
public class DepositRequestDao {

  private final Jdbi jdbi;

  public DepositRequestDao(Jdbi jdbi) {
    this.jdbi = jdbi;
  }

  /**
   * RowMapper chuyển ResultSet thành DepositRecord object.
   *
   * <p>Xử lý 2 trường hợp:
   *
   * <ul>
   *   <li>Query có JOIN với {@code users}: cột {@code username} có mặt → được set vào record
   *   <li>Query không JOIN: cột {@code username} không có → {@code SQLException} bị bỏ qua, {@code
   *       username} giữ nguyên {@code null}
   * </ul>
   *
   * <p>Cột {@code reviewed_at} có thể {@code null} (khi yêu cầu vẫn còn PENDING) nên cần kiểm tra
   * trước khi gọi {@code toLocalDateTime()}.
   */
  private static class DepositRecordMapper implements RowMapper<DepositRecord> {
    @Override
    public DepositRecord map(ResultSet rs, StatementContext ctx) throws SQLException {
      DepositRecord r = new DepositRecord();
      r.setId(rs.getLong("id"));
      r.setUserId(rs.getLong("user_id"));
      r.setAmount(rs.getBigDecimal("amount"));
      r.setStatus(rs.getString("status"));
      r.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
      var reviewed = rs.getTimestamp("reviewed_at");
      if (reviewed != null) {
        r.setReviewedAt(reviewed.toLocalDateTime());
      }
      // username từ JOIN với bảng users (có thể null nếu query không JOIN)
      try {
        r.setUsername(rs.getString("username"));
      } catch (SQLException ignored) {
      }
      return r;
    }
  }

  /**
   * Tạo yêu cầu nạp tiền mới.
   *
   * <p>Được gọi khi user submit form nạp tiền. Status mặc định là {@code PENDING}, chờ admin xử lý.
   * Cột {@code reviewed_at} chưa được set tại đây — sẽ được ghi khi admin duyệt/từ chối.
   *
   * @param record đối tượng DepositRecord (chưa có id, status = "PENDING")
   * @return DepositRecord đã được gán id từ database
   */
  public DepositRecord insert(DepositRecord record) {
    String sql =
        """
        INSERT INTO deposit_requests (user_id, amount, status, created_at)
        VALUES (:userId, :amount, :status, :createdAt)
        RETURNING id
        """;
    return jdbi.withHandle(
        handle -> {
          long id =
              handle
                  .createQuery(sql)
                  .bind("userId", record.getUserId())
                  .bind("amount", record.getAmount())
                  .bind("status", record.getStatus())
                  .bind("createdAt", record.getCreatedAt())
                  .mapTo(Long.class)
                  .one();
          record.setId(id);
          return record;
        });
  }

  /**
   * Tìm yêu cầu nạp tiền theo ID, kèm username của user.
   *
   * <p>JOIN với bảng {@code users} để lấy {@code username}, dùng cho màn hình admin xem chi tiết
   * một yêu cầu cụ thể.
   *
   * @param id ID của yêu cầu
   * @return Optional chứa DepositRecord nếu tìm thấy, Optional.empty() nếu không
   */
  public Optional<DepositRecord> findById(Long id) {
    String sql =
        """
        SELECT dr.id, dr.user_id, dr.amount, dr.status, dr.created_at, dr.reviewed_at,
               u.username
        FROM deposit_requests dr
        JOIN users u ON u.id = dr.user_id
        WHERE dr.id = :id
        """;
    return jdbi.withHandle(
        handle -> handle.createQuery(sql).bind("id", id).map(new DepositRecordMapper()).findOne());
  }

  public Optional<DepositRecord> findByIdForUpdate(org.jdbi.v3.core.Handle handle, Long id) {
    String sql =
        """
        SELECT dr.id, dr.user_id, dr.amount, dr.status, dr.created_at, dr.reviewed_at,
               u.username
        FROM deposit_requests dr
        JOIN users u ON u.id = dr.user_id
        WHERE dr.id = :id
        FOR UPDATE OF dr
        """;
    return handle.createQuery(sql).bind("id", id).map(new DepositRecordMapper()).findOne();
  }

  /**
   * Lấy tất cả yêu cầu theo status, JOIN với users để lấy username.
   *
   * <p>Dùng chủ yếu cho admin panel: lấy danh sách PENDING để xử lý, hoặc lịch sử APPROVED /
   * REJECTED. Kết quả sắp xếp theo {@code created_at} tăng dần — yêu cầu cũ nhất hiện trước để
   * admin xử lý theo thứ tự.
   *
   * @param status trạng thái cần lọc ("PENDING", "APPROVED", "REJECTED")
   * @return List các DepositRecord có status tương ứng, kèm username
   */
  public List<DepositRecord> findByStatus(String status) {
    String sql =
        """
        SELECT dr.id, dr.user_id, dr.amount, dr.status, dr.created_at, dr.reviewed_at,
               u.username
        FROM deposit_requests dr
        JOIN users u ON u.id = dr.user_id
        WHERE dr.status = :status
        ORDER BY dr.created_at ASC
        """;
    return jdbi.withHandle(
        handle ->
            handle.createQuery(sql).bind("status", status).map(new DepositRecordMapper()).list());
  }

  /**
   * Lấy tất cả yêu cầu nạp tiền của một user, mới nhất trước.
   *
   * <p>Dùng cho màn hình lịch sử nạp tiền của user. Không JOIN với {@code users} vì đã biết user,
   * không cần thêm {@code username}.
   *
   * @param userId ID của user
   * @return List các DepositRecord của user, sắp xếp theo created_at giảm dần
   */
  public List<DepositRecord> findByUserId(Long userId) {
    String sql =
        """
        SELECT id, user_id, amount, status, created_at, reviewed_at
        FROM deposit_requests
        WHERE user_id = :userId
        ORDER BY created_at DESC
        """;
    return jdbi.withHandle(
        handle ->
            handle.createQuery(sql).bind("userId", userId).map(new DepositRecordMapper()).list());
  }

  /**
   * Cập nhật trạng thái yêu cầu nạp tiền.
   *
   * <p>Được gọi khi admin duyệt (APPROVED) hoặc từ chối (REJECTED) một yêu cầu. Tự động ghi nhận
   * thời điểm review vào cột {@code reviewed_at = NOW()}.
   *
   * <p><b>Lưu ý:</b> method này chỉ cập nhật bảng {@code deposit_requests}. Logic cộng tiền vào
   * balance của user (khi APPROVED) được thực hiện ở tầng service, không phải tại đây.
   *
   * @param id ID của yêu cầu cần cập nhật
   * @param newStatus trạng thái mới ("APPROVED" hoặc "REJECTED")
   * @return true nếu cập nhật thành công, false nếu không tìm thấy yêu cầu
   */
  public boolean updateStatus(Long id, String newStatus) {
    String sql =
        """
        UPDATE deposit_requests
        SET status = :status, reviewed_at = NOW()
        WHERE id = :id
        """;
    int rows =
        jdbi.withHandle(
            handle -> handle.createUpdate(sql).bind("status", newStatus).bind("id", id).execute());
    return rows > 0;
  }

  /** Cập nhật trạng thái yêu cầu nạp tiền trong một transaction đang mở. */
  public void updateStatusInTransaction(org.jdbi.v3.core.Handle handle, Long id, String newStatus) {
    String sql =
        """
        UPDATE deposit_requests
        SET status = :status, reviewed_at = NOW()
        WHERE id = :id
        """;
    int rows = handle.createUpdate(sql).bind("status", newStatus).bind("id", id).execute();
    if (rows == 0) {
      throw new IllegalStateException("Không tìm thấy yêu cầu nạp tiền: " + id);
    }
  }

  public void transitionStatusInTransaction(
      org.jdbi.v3.core.Handle handle, Long id, String fromStatus, String toStatus) {
    String sql =
        """
        UPDATE deposit_requests
        SET status = :toStatus, reviewed_at = NOW()
        WHERE id = :id AND status = :fromStatus
        """;
    int rows =
        handle
            .createUpdate(sql)
            .bind("toStatus", toStatus)
            .bind("fromStatus", fromStatus)
            .bind("id", id)
            .execute();
    if (rows == 0) {
      throw new IllegalStateException("Yêu cầu nạp tiền này đã được xử lý rồi.");
    }
  }
}
