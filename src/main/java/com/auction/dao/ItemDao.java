package com.auction.dao;

import com.auction.model.Item;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DAO (Data Access Object) cho bảng items.
 *
 * <p>Quản lý các thao tác CRUD cho sản phẩm. Đã được đơn giản hóa để sử dụng model Item phẳng,
 * không còn xử lý polymorphism tại tầng DAO.
 */
public class ItemDao {

  private static final Logger LOGGER = LoggerFactory.getLogger(ItemDao.class);

  private static final String SELECT_COLUMNS =
      "id, name, description, seller_id, category, status, brand, artist, year, created_at,"
          + " updated_at";

  private final Jdbi jdbi;

  public ItemDao(Jdbi jdbi) {
    this.jdbi = jdbi;
  }

  /** RowMapper chuyển ResultSet thành Item object. */
  private static class ItemMapper implements RowMapper<Item> {
    @Override
    public Item map(ResultSet rs, StatementContext ctx) throws SQLException {
      Item item = new Item();
      item.setId(rs.getLong("id"));
      item.setName(rs.getString("name"));
      item.setDescription(rs.getString("description"));
      item.setSellerId(rs.getLong("seller_id"));
      item.setCategory(rs.getString("category"));
      item.setStatus(rs.getString("status"));
      item.setBrand(rs.getString("brand"));
      item.setArtist(rs.getString("artist"));
      int year = rs.getInt("year");
      if (!rs.wasNull()) {
        item.setYear(year);
      }
      item.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
      return item;
    }
  }

  public Item insert(Item item) {
    String sql =
        """
        INSERT INTO items (name, description, seller_id, category,
                           status, brand, artist, year, created_at, updated_at)
        VALUES (:name, :description, :sellerId, :category,
                :status, :brand, :artist, :year, :createdAt, :updatedAt)
        RETURNING id
        """;

    return jdbi.withHandle(
        handle -> {
          long id =
              handle
                  .createQuery(sql)
                  .bind("name", item.getName())
                  .bind("description", item.getDescription())
                  .bind("sellerId", item.getSellerId())
                  .bind("category", item.getCategory())
                  .bind("status", item.getStatus())
                  .bind("brand", item.getBrand())
                  .bind("artist", item.getArtist())
                  .bind("year", item.getYear())
                  .bind("createdAt", item.getCreatedAt())
                  .bind("updatedAt", item.getCreatedAt())
                  .mapTo(Long.class)
                  .one();

          item.setId(id);
          return item;
        });
  }

  public Optional<Item> findById(Long id) {
    String sql = "SELECT " + SELECT_COLUMNS + " FROM items WHERE id = :id";
    return jdbi.withHandle(
        handle -> handle.createQuery(sql).bind("id", id).map(new ItemMapper()).findOne());
  }

  public Optional<Item> findByIdForUpdate(Handle handle, Long id) {
    String sql = "SELECT " + SELECT_COLUMNS + " FROM items WHERE id = :id FOR UPDATE";
    return handle.createQuery(sql).bind("id", id).map(new ItemMapper()).findOne();
  }

  public List<Item> findBySellerId(Long sellerId) {
    String sql =
        "SELECT "
            + SELECT_COLUMNS
            + " FROM items WHERE seller_id = :sellerId ORDER BY created_at DESC";
    return jdbi.withHandle(
        handle -> handle.createQuery(sql).bind("sellerId", sellerId).map(new ItemMapper()).list());
  }

  public List<Item> findAll() {
    String sql = "SELECT " + SELECT_COLUMNS + " FROM items ORDER BY created_at DESC";
    return jdbi.withHandle(handle -> handle.createQuery(sql).map(new ItemMapper()).list());
  }

  public List<Item> findByCategory(String category) {
    String sql =
        "SELECT "
            + SELECT_COLUMNS
            + " FROM items WHERE category = :category ORDER BY created_at DESC";
    return jdbi.withHandle(
        handle -> handle.createQuery(sql).bind("category", category).map(new ItemMapper()).list());
  }

  public List<Item> searchByName(String keyword) {
    String sql =
        "SELECT "
            + SELECT_COLUMNS
            + " FROM items WHERE LOWER(name) LIKE LOWER(:keyword) ESCAPE '\\'"
            + " ORDER BY created_at DESC";

    String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    String searchPattern = "%" + escaped + "%";

    return jdbi.withHandle(
        handle ->
            handle.createQuery(sql).bind("keyword", searchPattern).map(new ItemMapper()).list());
  }

  public boolean update(Item item) {
    String sql =
        """
        UPDATE items
        SET name = :name,
            description = :description,
            status = :status,
            brand = :brand,
            artist = :artist,
            year = :year,
            updated_at = :updatedAt
        WHERE id = :id
        """;

    int rowsAffected =
        jdbi.withHandle(
            handle ->
                handle
                    .createUpdate(sql)
                    .bind("name", item.getName())
                    .bind("description", item.getDescription())
                    .bind("status", item.getStatus())
                    .bind("brand", item.getBrand())
                    .bind("artist", item.getArtist())
                    .bind("year", item.getYear())
                    .bind("updatedAt", LocalDateTime.now())
                    .bind("id", item.getId())
                    .execute());

    return rowsAffected > 0;
  }

  public void updateStatusInTransaction(Handle handle, Long id, String status) {
    int rowsAffected =
        handle
            .createUpdate("UPDATE items SET status = :status, updated_at = NOW() WHERE id = :id")
            .bind("status", status)
            .bind("id", id)
            .execute();

    if (rowsAffected == 0) {
      throw new IllegalStateException("Không tìm thấy sản phẩm #" + id + " để cập nhật trạng thái");
    }
  }

  public boolean delete(Long id) {
    String sql = "UPDATE items SET status = 'REMOVED', updated_at = NOW() WHERE id = :id";
    int rowsAffected = jdbi.withHandle(handle -> handle.createUpdate(sql).bind("id", id).execute());
    return rowsAffected > 0;
  }

  public boolean existsById(Long id) {
    String sql = "SELECT COUNT(*) FROM items WHERE id = :id";
    long count =
        jdbi.withHandle(handle -> handle.createQuery(sql).bind("id", id).mapTo(Long.class).one());
    return count > 0;
  }

  public boolean belongsToSeller(Long itemId, Long sellerId) {
    String sql = "SELECT COUNT(*) FROM items WHERE id = :itemId AND seller_id = :sellerId";
    long count =
        jdbi.withHandle(
            handle ->
                handle
                    .createQuery(sql)
                    .bind("itemId", itemId)
                    .bind("sellerId", sellerId)
                    .mapTo(Long.class)
                    .one());
    return count > 0;
  }
}
