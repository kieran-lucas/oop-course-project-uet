package com.auction.dao;

import com.auction.model.Art;
import com.auction.model.Electronics;
import com.auction.model.Item;
import com.auction.model.Vehicle;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

/** DAO for the items table. Maps each row back to the concrete Item subtype by category. */
public class ItemDao {

  private static final String SELECT_COLUMNS =
      "id, name, description, seller_id, category, status, brand, artist, year, created_at,"
          + " updated_at";

  private final Jdbi jdbi;

  public ItemDao(Jdbi jdbi) {
    this.jdbi = jdbi;
  }

  private static class ItemMapper implements RowMapper<Item> {
    @Override
    public Item map(ResultSet rs, StatementContext ctx) throws SQLException {
      Long id = rs.getLong("id");
      String name = rs.getString("name");
      String description = rs.getString("description");
      Long sellerId = rs.getLong("seller_id");
      String category = rs.getString("category");
      String status = rs.getString("status");
      LocalDateTime createdAt = rs.getTimestamp("created_at").toLocalDateTime();

      return switch (category) {
        case "ELECTRONICS" ->
            new Electronics(
                id, name, description, sellerId, status, rs.getString("brand"), createdAt);
        case "ART" ->
            new Art(id, name, description, sellerId, status, rs.getString("artist"), createdAt);
        case "VEHICLE" ->
            new Vehicle(id, name, description, sellerId, status, nullableYear(rs), createdAt);
        default ->
            throw new IllegalStateException(
                "Invalid item category in database: " + category + " for item #" + id);
      };
    }

    private Integer nullableYear(ResultSet rs) throws SQLException {
      int year = rs.getInt("year");
      return rs.wasNull() ? null : year;
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
                  .bind("brand", getBrand(item))
                  .bind("artist", getArtist(item))
                  .bind("year", getYear(item))
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
                    .bind("brand", getBrand(item))
                    .bind("artist", getArtist(item))
                    .bind("year", getYear(item))
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

  private String getBrand(Item item) {
    if (item instanceof Electronics electronics) {
      return electronics.getBrand();
    }
    return null;
  }

  private String getArtist(Item item) {
    if (item instanceof Art art) {
      return art.getArtist();
    }
    return null;
  }

  private Integer getYear(Item item) {
    if (item instanceof Vehicle vehicle) {
      return vehicle.getYear();
    }
    return null;
  }
}
