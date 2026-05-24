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

/**
 * DAO quản lý bảng {@code items} — ánh xạ mỗi hàng DB về đúng lớp con của {@link Item} dựa trên cột
 * {@code category}.
 *
 * <p>Thiết kế single-table inheritance: ba loại sản phẩm (ELECTRONICS, ART, VEHICLE) dùng chung một
 * bảng. Các cột đặc thù (brand, artist, year) là NULL cho các loại không dùng đến chúng.
 *
 * <p>Tất cả truy vấn đọc đều dùng {@link ItemMapper} để tự động chọn subclass đúng. Các phương thức
 * ghi (insert/update) trích xuất cột đặc thù qua các helper {@code getBrand}, {@code getArtist},
 * {@code getYear}.
 */
public class ItemDao {

  /** Danh sách cột được SELECT trong mọi truy vấn đọc — đồng bộ với ItemMapper. */
  private static final String SELECT_COLUMNS =
      "id, name, description, seller_id, category, status, brand, artist, year, created_at,"
          + " updated_at";

  private final Jdbi jdbi;

  public ItemDao(Jdbi jdbi) {
    this.jdbi = jdbi;
  }

  /**
   * RowMapper nội bộ: ánh xạ một hàng ResultSet về đúng subclass Item theo giá trị cột {@code
   * category}. Đây là điểm duy nhất cần cập nhật khi thêm loại sản phẩm mới.
   */
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

      // Phân nhánh theo category để trả về đúng subclass — switch expression đảm bảo
      // compiler báo lỗi nếu thiếu nhánh khi thêm loại mới.
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

    /** Đọc cột year — trả về null thay vì 0 khi DB lưu NULL (Vehicle không rõ năm). */
    private Integer nullableYear(ResultSet rs) throws SQLException {
      int year = rs.getInt("year");
      return rs.wasNull() ? null : year;
    }
  }

  /**
   * Thêm sản phẩm mới vào DB và gán ID vừa sinh cho đối tượng đầu vào.
   *
   * <p>Dùng {@code RETURNING id} để lấy ID do database tự sinh (BIGSERIAL), tránh truy vấn thêm.
   *
   * @param item sản phẩm cần lưu (chưa có id)
   * @return cùng đối tượng {@code item} sau khi đã được gán id
   */
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

  /**
   * Tìm sản phẩm theo ID — trả về {@link Optional#empty()} nếu không tìm thấy.
   *
   * @param id khóa chính của sản phẩm
   */
  public Optional<Item> findById(Long id) {
    String sql = "SELECT " + SELECT_COLUMNS + " FROM items WHERE id = :id";
    return jdbi.withHandle(
        handle -> handle.createQuery(sql).bind("id", id).map(new ItemMapper()).findOne());
  }

  /**
   * Tìm sản phẩm theo ID trong một transaction đang mở, với khóa hàng {@code FOR UPDATE}.
   *
   * <p>Dùng khi cần đảm bảo tính atomicity: ví dụ kiểm tra trạng thái AVAILABLE trước khi tạo phiên
   * đấu giá, ngăn hai phiên được tạo đồng thời cho cùng một sản phẩm.
   *
   * @param handle handle của transaction hiện tại
   * @param id khóa chính của sản phẩm
   */
  public Optional<Item> findByIdForUpdate(Handle handle, Long id) {
    String sql = "SELECT " + SELECT_COLUMNS + " FROM items WHERE id = :id FOR UPDATE";
    return handle.createQuery(sql).bind("id", id).map(new ItemMapper()).findOne();
  }

  /**
   * Lấy danh sách tất cả sản phẩm của một seller, sắp xếp mới nhất trước.
   *
   * @param sellerId ID của seller cần truy vấn
   */
  public List<Item> findBySellerId(Long sellerId) {
    String sql =
        "SELECT "
            + SELECT_COLUMNS
            + " FROM items WHERE seller_id = :sellerId ORDER BY created_at DESC";
    return jdbi.withHandle(
        handle -> handle.createQuery(sql).bind("sellerId", sellerId).map(new ItemMapper()).list());
  }

  /**
   * Lấy toàn bộ sản phẩm trong hệ thống, sắp xếp mới nhất trước. Dùng chủ yếu cho Admin hoặc trang
   * danh sách công khai.
   */
  public List<Item> findAll() {
    String sql = "SELECT " + SELECT_COLUMNS + " FROM items ORDER BY created_at DESC";
    return jdbi.withHandle(handle -> handle.createQuery(sql).map(new ItemMapper()).list());
  }

  /**
   * Lọc sản phẩm theo category (ELECTRONICS, ART, VEHICLE), sắp xếp mới nhất trước.
   *
   * @param category giá trị category cần lọc — phân biệt hoa thường theo DB
   */
  public List<Item> findByCategory(String category) {
    String sql =
        "SELECT "
            + SELECT_COLUMNS
            + " FROM items WHERE category = :category ORDER BY created_at DESC";
    return jdbi.withHandle(
        handle -> handle.createQuery(sql).bind("category", category).map(new ItemMapper()).list());
  }

  /**
   * Tìm kiếm sản phẩm theo từ khóa trong tên, không phân biệt hoa thường.
   *
   * <p>Ký tự đặc biệt trong keyword ({@code %}, {@code _}, {@code \}) được escape trước khi truyền
   * vào LIKE để tránh SQL injection logic và kết quả ngoài ý muốn.
   *
   * @param keyword chuỗi tìm kiếm do người dùng nhập
   */
  public List<Item> searchByName(String keyword) {
    String sql =
        "SELECT "
            + SELECT_COLUMNS
            + " FROM items WHERE LOWER(name) LIKE LOWER(:keyword) ESCAPE '\\'"
            + " ORDER BY created_at DESC";

    // Escape ký tự đặc biệt của LIKE để keyword được hiểu là chuỗi literal
    String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    String searchPattern = "%" + escaped + "%";

    return jdbi.withHandle(
        handle ->
            handle.createQuery(sql).bind("keyword", searchPattern).map(new ItemMapper()).list());
  }

  /**
   * Cập nhật thông tin sản phẩm (tên, mô tả, trạng thái, brand/artist/year).
   *
   * <p>Không cập nhật {@code category} hay {@code seller_id} để tránh thay đổi phân loại sau khi
   * sản phẩm đã tồn tại.
   *
   * @param item đối tượng sản phẩm với thông tin đã được chỉnh sửa (phải có id hợp lệ)
   * @return {@code true} nếu có ít nhất 1 hàng bị ảnh hưởng; {@code false} nếu id không tồn tại
   */
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

  /**
   * Cập nhật trạng thái sản phẩm trong một transaction đang mở.
   *
   * <p>Dùng khi cần đảm bảo tính nhất quán — ví dụ: chuyển AVAILABLE → IN_AUCTION khi tạo phiên đấu
   * giá, hoặc IN_AUCTION → SOLD khi thanh toán thành công.
   *
   * @param handle handle của transaction hiện tại
   * @param id ID sản phẩm cần cập nhật
   * @param status trạng thái mới (AVAILABLE, IN_AUCTION, SOLD, REMOVED)
   * @throws IllegalStateException nếu không tìm thấy sản phẩm với id đã cho
   */
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

  /**
   * Xóa mềm sản phẩm bằng cách đổi trạng thái sang {@code REMOVED} thay vì xóa hàng khỏi DB.
   *
   * <p>Sản phẩm đã bị gắn REMOVED vẫn hiển thị trong lịch sử đấu giá và bid_transactions.
   *
   * @param id ID sản phẩm cần xóa
   * @return {@code true} nếu xóa thành công; {@code false} nếu id không tồn tại
   */
  public boolean delete(Long id) {
    String sql = "UPDATE items SET status = 'REMOVED', updated_at = NOW() WHERE id = :id";
    int rowsAffected = jdbi.withHandle(handle -> handle.createUpdate(sql).bind("id", id).execute());
    return rowsAffected > 0;
  }

  /**
   * Kiểm tra nhanh sự tồn tại của sản phẩm theo ID (không tải toàn bộ dữ liệu).
   *
   * @param id ID cần kiểm tra
   * @return {@code true} nếu sản phẩm tồn tại trong DB
   */
  public boolean existsById(Long id) {
    String sql = "SELECT COUNT(*) FROM items WHERE id = :id";
    long count =
        jdbi.withHandle(handle -> handle.createQuery(sql).bind("id", id).mapTo(Long.class).one());
    return count > 0;
  }

  /**
   * Xác minh sản phẩm có thuộc về seller đã cho không.
   *
   * <p>Dùng để phân quyền: chỉ seller sở hữu mới được tạo phiên đấu giá hoặc sửa sản phẩm.
   *
   * @param itemId ID sản phẩm cần kiểm tra
   * @param sellerId ID seller muốn thực hiện thao tác
   * @return {@code true} nếu sản phẩm thuộc seller; {@code false} nếu không thuộc hoặc không tồn
   *     tại
   */
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

  // ── Các helper trích xuất field đặc thù theo loại sản phẩm ──

  /** Lấy thương hiệu nếu là Electronics; trả về null cho các loại khác. */
  private String getBrand(Item item) {
    if (item instanceof Electronics electronics) {
      return electronics.getBrand();
    }
    return null;
  }

  /** Lấy tên nghệ sĩ nếu là Art; trả về null cho các loại khác. */
  private String getArtist(Item item) {
    if (item instanceof Art art) {
      return art.getArtist();
    }
    return null;
  }

  /** Lấy năm sản xuất nếu là Vehicle; trả về null cho các loại khác. */
  private Integer getYear(Item item) {
    if (item instanceof Vehicle vehicle) {
      return vehicle.getYear();
    }
    return null;
  }
}
