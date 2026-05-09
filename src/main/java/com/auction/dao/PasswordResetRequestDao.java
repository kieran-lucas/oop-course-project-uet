package com.auction.dao;

import com.auction.model.PasswordResetRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

/** DAO cho bảng password_reset_requests */
public class PasswordResetRequestDao {

  private final Jdbi jdbi;

  public PasswordResetRequestDao(Jdbi jdbi) {
    this.jdbi = jdbi;
  }

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

  /** Tạo yêu cầu mới với status PENDING */
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

  /** Tìm theo ID, JOIN users để lấy username và email */
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

  /** Lấy tất cả yêu cầu theo status, JOIN users để lấy username và email */
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

  /** Kiểm tra user đã có yêu cầu PENDING chưa */
  public boolean hasPendingRequest(Long userId) {
    String sql =
        "SELECT COUNT(*) FROM password_reset_requests WHERE user_id = :userId AND status = 'PENDING'";
    long count =
        jdbi.withHandle(
            handle -> handle.createQuery(sql).bind("userId", userId).mapTo(Long.class).one());
    return count > 0;
  }

  /** Cập nhật status và thời gian duyệt */
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
