package com.auction.dao;

import com.auction.model.Admin;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import com.auction.model.User;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DAO (Data Access Object) cho bảng users
 *
 * <p>Class này chịu trách nhiệm giao tiếp với database, thực hiện các thao tác CRUD (Create, Read,
 * Update, Delete) trên bảng users. DAO layer nằm giữa Service layer và Database, giúp tách biệt
 * logic nghiệp vụ (Service) khỏi logic truy vấn SQL
 *
 * <p>Luồng dữ liệu:
 *
 * <pre>
 * Service (UserService)
 *   → gọi userDao.findByUsername(...)
 *     → JDBI thực thi SQL query
 *       → RowMapper chuyển ResultSet → User object (Bidder/Seller/Admin)
 *         → trả về Optional&lt;User&gt;
 * </pre>
 *
 * <p>Điểm quan trọng: RowMapper phải đọc cột "role" để quyết định tạo đúng subclass:
 *
 * <ul>
 *   <li>role = "BIDDER" → new Bidder(...)
 *   <li>role = "SELLER" → new Seller(...)
 *   <li>role = "ADMIN" → new Admin(...)
 * </ul>
 *
 * <p>Điều này thể hiện POLYMORPHISM: cùng một câu query SELECT * FROM users, RowMapper tự động tạo
 * đúng loại User mà không cần code if-else rải rác.
 *
 * <p>Liên kết với các file khác:
 *
 * <ul>
 *   <li><b>User.java, Bidder.java, Seller.java, Admin.java</b> — model classes
 *   <li><b>DatabaseConfig.java</b> — cung cấp Jdbi instance
 *   <li><b>UserService.java</b> — gọi các method của DAO này
 *   <li><b>V1__initial_schema.sql</b> — định nghĩa bảng users
 * </ul>
 */
public class UserDao {

  private static final Logger LOGGER = LoggerFactory.getLogger(UserDao.class);

  /** Danh sách cột SELECT dùng chung, tránh copy-paste */
  private static final String SELECT_COLUMNS =
      "id, username, password_hash, email, role, created_at, balance, reserved_balance, "
          + "token_version";

  private final Jdbi jdbi;

  /**
   * Constructor — nhận Jdbi từ DatabaseConfig
   *
   * <p>Trong App.java:
   *
   * <pre>
   * Jdbi jdbi = DatabaseConfig.create();
   * UserDao userDao = new UserDao(jdbi);
   * </pre>
   *
   * @param jdbi instance từ DatabaseConfig, dùng để tạo connection và thực thi SQL
   */
  public UserDao(Jdbi jdbi) {
    this.jdbi = jdbi;
  }

  /**
   * RowMapper chuyển ResultSet (1 dòng từ bảng users) thành User object
   *
   * <p>JDBI tự động gọi mapper này cho mỗi dòng trong ResultSet. Mapper đọc giá trị các cột, dựa
   * vào cột "role" để tạo subclass phù hợp
   *
   * <p>Ví dụ:
   *
   * <pre>
   * | id | username | password_hash | email   | role   | created_at |
   * | 1  | alice    | $2a$10$...    | a@x.com | BIDDER | 2024-01-01 |
   * → new Bidder(1L, "alice", "$2a$10$...", "a@x.com", createdAt)
   * </pre>
   *
   * <p>[FIX #4] Throw exception thay vì return null khi gặp role không hợp lệ.
   *
   * <p>[FIX #9] Dùng enhanced switch expression (Java 21) — gọn hơn, compiler bắt thiếu case.
   */
  private static class UserMapper implements RowMapper<User> {
    @Override
    public User map(ResultSet rs, StatementContext ctx) throws SQLException {
      Long id = rs.getLong("id");
      String username = rs.getString("username");
      String passwordHash = rs.getString("password_hash");
      String email = rs.getString("email");
      String role = rs.getString("role");
      var createdAt = rs.getTimestamp("created_at").toLocalDateTime();

      // POLYMORPHISM: tạo đúng subclass dựa vào role
      User user =
          switch (role) {
            case "BIDDER" -> new Bidder(id, username, passwordHash, email, createdAt);
            case "SELLER" -> new Seller(id, username, passwordHash, email, createdAt);
            case "ADMIN" -> new Admin(id, username, passwordHash, email, createdAt);
            default ->
                throw new IllegalStateException(
                    "Role không hợp lệ trong database: '"
                        + role
                        + "' (user id="
                        + id
                        + ", username="
                        + username
                        + "). "
                        + "Kiểm tra dữ liệu bảng users hoặc thêm case mới vào UserMapper.");
          };

      BigDecimal balance = rs.getBigDecimal("balance");
      user.setBalance(balance != null ? balance : BigDecimal.ZERO);
      BigDecimal reservedBalance = rs.getBigDecimal("reserved_balance");
      user.setReservedBalance(reservedBalance != null ? reservedBalance : BigDecimal.ZERO);
      user.setTokenVersion(rs.getInt("token_version"));
      return user;
    }
  }

  // ============================================================
  // CREATE (INSERT)
  // ============================================================

  /**
   * Thêm user mới vào database
   *
   * <p>Method này được gọi khi người dùng đăng ký thành công (UserService.register()).
   *
   * <p>Luồng xử lý:
   *
   * <ol>
   *   <li>UserService đã hash password bằng BCrypt
   *   <li>Gọi userDao.insert(user) với user có id = null (chưa có)
   *   <li>JDBI thực thi INSERT, sử dụng RETURNING id để lấy ID database sinh ra
   *   <li>Cập nhật id vào user object (setId)
   *   <li>Trả về user đã có id
   * </ol>
   *
   * @param user đối tượng User (chưa có id, đã có passwordHash)
   * @return User đã được gán id từ database
   * @throws org.jdbi.v3.core.statement.UnableToExecuteStatementException nếu INSERT thất bại (ví
   *     dụ: duplicate username do UNIQUE constraint)
   */
  public User insert(User user) {
    String sql =
        """
        INSERT INTO users (username, password_hash, email, role, created_at, balance, token_version)
        VALUES (:username, :passwordHash, :email, :role, :createdAt, :balance, :tokenVersion)
        RETURNING id
        """;

    return jdbi.withHandle(
        handle -> {
          long id =
              handle
                  .createQuery(sql)
                  .bind("username", user.getUsername())
                  .bind("passwordHash", user.getPasswordHash())
                  .bind("email", user.getEmail())
                  .bind("role", user.getRole())
                  .bind("createdAt", user.getCreatedAt())
                  .bind(
                      "balance",
                      user.getBalance() != null ? user.getBalance() : java.math.BigDecimal.ZERO)
                  .bind("tokenVersion", user.getTokenVersion())
                  .mapTo(Long.class)
                  .one();

          user.setId(id);
          LOGGER.debug(
              "Inserted user: {} (id={}, role={})", user.getUsername(), id, user.getRole());
          return user;
        });
  }

  // ============================================================
  // READ (SELECT)
  // ============================================================

  /**
   * Tìm user theo ID.
   *
   * <p>Dùng khi cần lấy thông tin user từ JWT token (userId nằm trong token) hoặc khi cần kiểm tra
   * owner của item/auction.
   *
   * @param id ID của user cần tìm
   * @return Optional chứa User nếu tìm thấy, Optional.empty() nếu không
   */
  public Optional<User> findById(Long id) {
    String sql = "SELECT " + SELECT_COLUMNS + " FROM users WHERE id = :id";

    return jdbi.withHandle(
        handle -> handle.createQuery(sql).bind("id", id).map(new UserMapper()).findOne());
  }

  /**
   * Tìm user với khóa row (SELECT FOR UPDATE). Dùng trong transaction để đảm bảo số dư không bị
   * thay đổi bởi luồng khác.
   */
  public User findByIdForUpdate(org.jdbi.v3.core.Handle handle, Long id) {
    String sql = "SELECT " + SELECT_COLUMNS + " FROM users WHERE id = :id FOR UPDATE";

    return handle.createQuery(sql).bind("id", id).map(new UserMapper()).one();
  }

  /**
   * Tìm user theo username (dùng cho đăng nhập).
   *
   * <p>Đây là method quan trọng nhất của UserDao, được gọi trong login flow.
   *
   * <p>Luồng đăng nhập:
   *
   * <ol>
   *   <li>Client gửi username/password
   *   <li>UserService.login() gọi findByUsername(username)
   *   <li>Nếu tìm thấy user, dùng BCrypt.verify() so sánh password
   *   <li>Nếu đúng → tạo JWT token và trả về
   * </ol>
   *
   * @param username tên đăng nhập
   * @return Optional chứa User nếu tìm thấy, Optional.empty() nếu không
   */
  public Optional<User> findByUsername(String username) {
    String sql = "SELECT " + SELECT_COLUMNS + " FROM users WHERE username = :username";

    return jdbi.withHandle(
        handle ->
            handle.createQuery(sql).bind("username", username).map(new UserMapper()).findOne());
  }

  /**
   * Tìm user theo email (dùng để kiểm tra email đã tồn tại khi đăng ký).
   *
   * @param email email cần kiểm tra
   * @return Optional chứa User nếu tìm thấy, Optional.empty() nếu không
   */
  public Optional<User> findByEmail(String email) {
    String sql = "SELECT " + SELECT_COLUMNS + " FROM users WHERE email = :email";

    return jdbi.withHandle(
        handle -> handle.createQuery(sql).bind("email", email).map(new UserMapper()).findOne());
  }

  /**
   * Lấy tất cả user trong hệ thống (dành cho Admin).
   *
   * <p>Admin Panel sẽ gọi method này để hiển thị danh sách người dùng, cho phép admin xóa hoặc vô
   * hiệu hóa tài khoản vi phạm.
   *
   * @return List chứa tất cả User (có thể rỗng nếu chưa có user nào)
   */
  public List<User> findAll() {
    String sql = "SELECT " + SELECT_COLUMNS + " FROM users ORDER BY id";

    return jdbi.withHandle(handle -> handle.createQuery(sql).map(new UserMapper()).list());
  }

  /**
   * Lấy tất cả user với một role cụ thể.
   *
   * <p>Ví dụ: lấy tất cả Seller để hiển thị trong Admin Panel, hoặc lấy tất cả Bidder để thống kê.
   *
   * @param role "BIDDER", "SELLER", hoặc "ADMIN"
   * @return List chứa các user có role tương ứng
   */
  public List<User> findByRole(String role) {
    String sql = "SELECT " + SELECT_COLUMNS + " FROM users WHERE role = :role ORDER BY id";

    return jdbi.withHandle(
        handle -> handle.createQuery(sql).bind("role", role).map(new UserMapper()).list());
  }

  // ============================================================
  // UPDATE
  // ============================================================

  /**
   * Cập nhật thông tin user.
   *
   * <p>Cho phép user thay đổi email, password. Không cho phép thay đổi username (username là unique
   * identifier, không nên thay đổi sau khi tạo).
   *
   * @param user đối tượng User đã được cập nhật thông tin (phải có id)
   * @return true nếu cập nhật thành công, false nếu không tìm thấy user
   */
  public boolean update(User user) {
    String sql =
        """
        UPDATE users
        SET password_hash = :passwordHash,
            email = :email,
            balance = :balance,
            token_version = :tokenVersion
        WHERE id = :id
        """;

    int rowsAffected =
        jdbi.withHandle(
            handle ->
                handle
                    .createUpdate(sql)
                    .bind("passwordHash", user.getPasswordHash())
                    .bind("email", user.getEmail())
                    .bind(
                        "balance",
                        user.getBalance() != null ? user.getBalance() : java.math.BigDecimal.ZERO)
                    .bind("tokenVersion", user.getTokenVersion())
                    .bind("id", user.getId())
                    .execute());

    if (rowsAffected > 0) {
      LOGGER.debug("Updated user: id={}, username={}", user.getId(), user.getUsername());
      return true;
    }

    LOGGER.warn("User not found for update: id={}", user.getId());
    return false;
  }

  /**
   * Cập nhật số dư bằng một giá trị delta (cộng hoặc trừ) theo cách atomic — tránh race condition
   * khi nhiều phiên kết thúc cùng lúc với cùng một bidder.
   *
   * @param userId ID người dùng
   * @param delta số tiền thay đổi (dương = cộng, âm = trừ)
   */
  public void updateBalance(Long userId, BigDecimal delta) {
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate("UPDATE users SET balance = balance + :delta WHERE id = :userId")
                .bind("delta", delta)
                .bind("userId", userId)
                .execute());
  }

  public void updateReservedBalanceInTransaction(
      org.jdbi.v3.core.Handle handle, Long userId, BigDecimal delta) {
    int rows =
        handle
            .createUpdate(
                """
                UPDATE users
                SET reserved_balance = reserved_balance + :delta
                WHERE id = :userId
                  AND reserved_balance + :delta >= 0
                """)
            .bind("delta", delta)
            .bind("userId", userId)
            .execute();
    if (rows == 0) {
      throw new IllegalStateException("Không thể cập nhật tiền giữ chỗ cho user: " + userId);
    }
  }

  public void releaseReservedBalanceInTransaction(
      org.jdbi.v3.core.Handle handle, Long userId, BigDecimal amount) {
    int rows =
        handle
            .createUpdate(
                """
                UPDATE users
                SET reserved_balance = reserved_balance - :amount
                WHERE id = :userId
                  AND reserved_balance >= :amount
                """)
            .bind("amount", amount)
            .bind("userId", userId)
            .execute();
    if (rows == 0) {
      throw new IllegalStateException(
          "Không thể release tiền giữ chỗ cho user "
              + userId
              + ": reserved_balance không đủ hoặc user không tồn tại");
    }
  }

  // ============================================================
  // DELETE
  // ============================================================

  /**
   * Xóa user khỏi database.
   *
   * <p>Lưu ý: do có ON DELETE CASCADE trong bảng items và auctions, khi xóa user, tất cả sản phẩm
   * và phiên đấu giá của user đó cũng bị xóa.
   *
   * <p>Chỉ Admin mới có quyền gọi method này.
   *
   * @param id ID của user cần xóa
   * @return true nếu xóa thành công, false nếu không tìm thấy user
   */
  public boolean delete(Long id) {
    String sql = "DELETE FROM users WHERE id = :id";

    int rowsAffected = jdbi.withHandle(handle -> handle.createUpdate(sql).bind("id", id).execute());

    if (rowsAffected > 0) {
      LOGGER.info("Deleted user with id: {}", id);
      return true;
    }

    LOGGER.warn("User not found for deletion: id={}", id);
    return false;
  }

  // ============================================================
  // HELPER METHODS
  // ============================================================

  /**
   * Kiểm tra username đã tồn tại chưa (dùng trong register để tránh duplicate).
   *
   * @param username tên đăng nhập cần kiểm tra
   * @return true nếu username đã tồn tại, false nếu chưa
   */
  public boolean existsByUsername(String username) {
    String sql = "SELECT COUNT(*) FROM users WHERE username = :username";

    long count =
        jdbi.withHandle(
            handle -> handle.createQuery(sql).bind("username", username).mapTo(Long.class).one());

    return count > 0;
  }

  /**
   * Kiểm tra email đã tồn tại chưa.
   *
   * @param email email cần kiểm tra
   * @return true nếu email đã tồn tại, false nếu chưa
   */
  public boolean existsByEmail(String email) {
    String sql = "SELECT COUNT(*) FROM users WHERE email = :email";

    long count =
        jdbi.withHandle(
            handle -> handle.createQuery(sql).bind("email", email).mapTo(Long.class).one());

    return count > 0;
  }
}
