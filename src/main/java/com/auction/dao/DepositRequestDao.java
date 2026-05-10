package com.auction.dao;

import com.auction.model.DepositRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

/** DAO cho bảng deposit_requests */
public class DepositRequestDao {

  private final Jdbi jdbi;

  public DepositRequestDao(Jdbi jdbi) {
    this.jdbi = jdbi;
  }

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

  /** Lấy tất cả yêu cầu theo status, JOIN với users để lấy username */
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

  /** Lấy tất cả yêu cầu của một user, mới nhất trước */
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
}
