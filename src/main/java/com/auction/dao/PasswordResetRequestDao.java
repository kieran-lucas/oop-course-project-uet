package com.auction.dao;

import com.auction.model.PasswordResetRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

/**
 * DAO (Data Access Object) cho bảng password_reset_requests.
 *
 * <p>Quản lý vòng đời của các yêu cầu reset mật khẩu: user tạo yêu cầu (PENDING), admin duyệt
 * (APPROVED) hoặc từ chối (REJECTED). Thiết kế tương tự {@link DepositRequestDao} — admin review
 * thủ công thay vì tự động hóa qua email token.
 *
 * <h3>Ràng buộc một yêu cầu PENDING mỗi lúc</h3>
 *
 * <p>Mỗi user chỉ được có tối đa một yêu cầu PENDING tại một thời điểm. Kiểm tra này được thực hiện
 * ở service layer bằng cách gọi {@link #hasPendingRequest(Long)} trước khi insert. DAO không
 * enforce constraint này ở tầng database.
 *
 * <h3>JOIN với bảng users</h3>
 *
 * <p>Các query dành cho admin ({@code findById}, {@code findByStatus}) JOIN thêm bảng {@code users}
 * để lấy {@code username} và {@code email} hiển thị trên giao diện. {@code Mapper} xử lý cả 2
 * trường hợp có hoặc không có JOIN — nếu cột không tồn tại trong ResultSet thì bỏ qua.
 *
 * <h3>Liên kết với các file khác</h3>
 *
 * <ul>
 *   <li><b>PasswordResetRecord.java</b> — model class tương ứng
 *   <li><b>PasswordResetService.java</b> — gọi hasPendingRequest() + insert() khi user tạo yêu cầu,
 *       updateStatus() khi admin duyệt/từ chối
 *   <li><b>V1__initial_schema.sql</b> — định nghĩa bảng password_reset_requests
 * </ul>
 */
public class PasswordResetRequestDao {

  private final Jdbi jdbi;

  public PasswordResetRequestDao(Jdbi jdbi) {
    this.jdbi = jdbi;
  }

  /**
   * RowMapper chuyển ResultSet thành PasswordResetRecord object.
   *
   * <p>Xử lý 2 trường hợp:
   *
   * <ul>
   *   <li>Query có JOIN với {@code users}: cột {@code username} và {@code email} có mặt → được set
   *       vào record
   *   <li>Query không JOIN: cột không có → {@code SQLException} bị bỏ qua, 2 field giữ nguyên
   *       {@code null}
   * </ul>
   *
   * <p>Cột {@code reviewed_at} có thể {@code null} khi yêu cầu vẫn còn PENDING nên cần kiểm tra
   * trước khi gọi {@code toLocalDateTime()}.
   */
  private static class Mapper implements RowMapper<PasswordResetRecord> {
    @Override
    public PasswordResetRecord map(ResultSet rs, StatementContext ctx) throws SQLException {
      PasswordResetRecord r = new PasswordResetRecord();
      r.setId(rs.getLong("id"));
      r.setUserId(rs.getLong("user_id"));
      r.setStatus(rs.getString("status"));
      r.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
      var reviewed = rs.getTimestamp("reviewed_at");
      if (reviewed != null) {
        r.setReviewedAt(reviewed.toLocalDateTime());
      }
      try {
        r.setUsername(rs.getString("username"));
        r.setEmail(rs.getString("email"));
      } catch (SQLException ignored) {
      }
      return r;
    }
  }

  /**
   * Tạo yêu cầu reset mật khẩu mới.
   *
   * <p>Được gọi sau khi service đã xác nhận user chưa có yêu cầu PENDING nào ({@link
   * #hasPendingRequest(Long)}). Status mặc định là {@code PENDING}, {@code reviewed_at} chưa được
   * set — sẽ được ghi khi admin xử lý.
   *
   * @param record đối tượng PasswordResetRecord (chưa có id, status = "PENDING")
   * @return PasswordResetRecord đã được gán id từ database
   */
  public PasswordResetRecord insert(PasswordResetRecord record) {
    String sql =
        """
        INSERT INTO password_reset_requests (user_id, status, created_at)
        VALUES (:userId, :status, :createdAt)
        RETURNING id
        """;
    return jdbi.withHandle(
        handle -> {
          long id =
              handle
                  .createQuery(sql)
                  .bind("userId", record.getUserId())
                  .bind("status", record.getStatus())
                  .bind("createdAt", record.getCreatedAt())
                  .mapTo(Long.class)
                  .one();
          record.setId(id);
          return record;
        });
  }

  /**
   * Tìm yêu cầu reset mật khẩu theo ID, kèm username và email của user.
   *
   * <p>JOIN với bảng {@code users} để lấy thông tin hiển thị trên màn hình admin xem chi tiết.
   *
   * @param id ID của yêu cầu
   * @return Optional chứa PasswordResetRecord nếu tìm thấy, Optional.empty() nếu không
   */
  public Optional<PasswordResetRecord> findById(Long id) {
    String sql =
        """
        SELECT pr.id, pr.user_id, pr.status, pr.created_at, pr.reviewed_at,
               u.username, u.email
        FROM password_reset_requests pr
        JOIN users u ON u.id = pr.user_id
        WHERE pr.id = :id
        """;
    return jdbi.withHandle(
        handle -> handle.createQuery(sql).bind("id", id).map(new Mapper()).findOne());
  }

  /**
   * Lấy tất cả yêu cầu theo status, kèm username và email của từng user.
   *
   * <p>Dùng chủ yếu cho admin panel: lấy danh sách PENDING để xử lý, hoặc lịch sử APPROVED /
   * REJECTED. Kết quả sắp xếp theo {@code created_at} tăng dần — yêu cầu cũ nhất hiện trước để
   * admin xử lý theo thứ tự.
   *
   * @param status trạng thái cần lọc ("PENDING", "APPROVED", "REJECTED")
   * @return List các PasswordResetRecord có status tương ứng, kèm username và email
   */
  public List<PasswordResetRecord> findByStatus(String status) {
    String sql =
        """
        SELECT pr.id, pr.user_id, pr.status, pr.created_at, pr.reviewed_at,
               u.username, u.email
        FROM password_reset_requests pr
        JOIN users u ON u.id = pr.user_id
        WHERE pr.status = :status
        ORDER BY pr.created_at ASC
        """;
    return jdbi.withHandle(
        handle -> handle.createQuery(sql).bind("status", status).map(new Mapper()).list());
  }

  /**
   * Kiểm tra user đã có yêu cầu PENDING chưa.
   *
   * <p>Được gọi ở service layer trước khi tạo yêu cầu mới, đảm bảo mỗi user chỉ có tối đa một yêu
   * cầu đang chờ xử lý tại một thời điểm.
   *
   * @param userId ID của user cần kiểm tra
   * @return true nếu đã có yêu cầu PENDING, false nếu chưa
   */
  public boolean hasPendingRequest(Long userId) {
    String sql =
        "SELECT COUNT(*) FROM password_reset_requests WHERE user_id = :userId AND status = 'PENDING'";
    long count =
        jdbi.withHandle(
            handle -> handle.createQuery(sql).bind("userId", userId).mapTo(Long.class).one());
    return count > 0;
  }

  /**
   * Cập nhật trạng thái yêu cầu reset mật khẩu.
   *
   * <p>Được gọi khi admin duyệt (APPROVED) hoặc từ chối (REJECTED) một yêu cầu. Tự động ghi nhận
   * thời điểm review vào cột {@code reviewed_at = NOW()}.
   *
   * <p><b>Lưu ý:</b> method này chỉ cập nhật trạng thái trong bảng {@code password_reset_requests}.
   * Logic thực sự reset mật khẩu của user (khi APPROVED) được thực hiện ở tầng service.
   *
   * @param id ID của yêu cầu cần cập nhật
   * @param newStatus trạng thái mới ("APPROVED" hoặc "REJECTED")
   * @return true nếu cập nhật thành công, false nếu không tìm thấy yêu cầu
   */
  public boolean updateStatus(Long id, String newStatus) {
    String sql =
        """
        UPDATE password_reset_requests
        SET status = :status, reviewed_at = NOW()
        WHERE id = :id
        """;
    int rows =
        jdbi.withHandle(
            handle -> handle.createUpdate(sql).bind("status", newStatus).bind("id", id).execute());
    return rows > 0;
  }
}
