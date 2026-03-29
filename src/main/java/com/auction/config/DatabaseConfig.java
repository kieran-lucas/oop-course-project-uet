package com.auction.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.Slf4JSqlLogger;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cấu hình kết nối database cho toàn bộ hệ thống.
 *
 * <p>File này là "cầu nối" giữa Java code và PostgreSQL. Nó tạo ra 2 thứ:
 *
 * <ol>
 *   <li><b>HikariDataSource</b> — connection pool: giữ sẵn ~10 kết nối đến PostgreSQL. Khi DAO cần
 *       chạy SQL, nó lấy 1 connection từ pool (nhanh, ~0.5ms) thay vì mở connection mới (chậm,
 *       ~5-10ms). Dùng xong trả lại pool.
 *   <li><b>Jdbi</b> — SQL wrapper: thay vì viết JDBC thủ công (PreparedStatement, ResultSet,
 *       close...), JDBI cung cấp API gọn hơn. DAO classes dùng Jdbi để chạy query.
 * </ol>
 *
 * <h3>Luồng dữ liệu:</h3>
 *
 * <pre>
 *   App.java khởi động
 *     → gọi DatabaseConfig.create()
 *       → tạo HikariDataSource (connection pool)
 *         → tạo Jdbi instance (dùng pool đó)
 *           → truyền Jdbi vào tất cả DAO
 *             → DAO dùng Jdbi để chạy SQL
 * </pre>
 *
 * <h3>Cấu hình từ biến môi trường:</h3>
 *
 * <p>Database URL, username, password đọc từ biến môi trường (System.getenv), KHÔNG hardcode trong
 * code. Lý do:
 *
 * <ul>
 *   <li>Mỗi người trong nhóm có thể dùng password khác nhau
 *   <li>CI pipeline dùng database test riêng (xem ci.yml: DB_URL, DB_USER, DB_PASSWORD)
 *   <li>Không bao giờ commit password lên GitHub (security best practice)
 * </ul>
 *
 * <p>Developer tạo file .env trên máy mình (file này nằm trong .gitignore, không commit):
 *
 * <pre>
 *   DB_URL=jdbc:postgresql://localhost:5432/auction_db
 *   DB_USER=auction_user
 *   DB_PASSWORD=auction_pass
 * </pre>
 *
 * <h3>Liên kết với các file khác:</h3>
 *
 * <ul>
 *   <li><b>App.java</b>: gọi DatabaseConfig.create() khi server khởi động
 *   <li><b>Tất cả DAO</b> (UserDao, AuctionDao...): nhận Jdbi từ config này
 *   <li><b>ci.yml</b>: truyền DB_URL, DB_USER, DB_PASSWORD qua environment variables
 *   <li><b>V1__initial_schema.sql</b>: tạo bảng trong database mà config này kết nối đến
 *   <li><b>build.gradle.kts</b>: khai báo dependency postgresql, HikariCP, jdbi3
 * </ul>
 */
public class DatabaseConfig {

  private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseConfig.class);

  // ============================================================================
  // Giá trị mặc định — dùng khi biến môi trường không được set
  // Phù hợp cho development trên máy local.
  // Trong CI (GitHub Actions), ci.yml set biến môi trường riêng → override giá trị này.
  // ============================================================================
  private static final String DEFAULT_URL = "jdbc:postgresql://localhost:5432/auction_db";
  private static final String DEFAULT_USER = "auction_user";
  private static final String DEFAULT_PASSWORD = "auction_pass";

  /** Không cho phép tạo instance — class này chỉ có static methods. */
  private DatabaseConfig() {}

  /**
   * Tạo Jdbi instance kết nối đến PostgreSQL.
   *
   * <p>Đây là method duy nhất mà App.java cần gọi. Nó thực hiện 3 bước:
   *
   * <ol>
   *   <li>Tạo HikariDataSource (connection pool)
   *   <li>Tạo Jdbi instance từ DataSource đó
   *   <li>Cài đặt plugins (PostgreSQL support, SqlObject support, SQL logging)
   * </ol>
   *
   * @return Jdbi instance sẵn sàng dùng cho DAO
   */
  public static Jdbi create() {
    DataSource dataSource = createDataSource();
    Jdbi jdbi = Jdbi.create(dataSource);

    // ── Cài đặt JDBI plugins ──

    // PostgresPlugin: hỗ trợ kiểu dữ liệu riêng của PostgreSQL
    // Ví dụ: UUID, ARRAY, hstore... Không có plugin này, JDBI chỉ hiểu
    // kiểu dữ liệu chuẩn SQL (VARCHAR, INTEGER, TIMESTAMP...)
    jdbi.installPlugin(new PostgresPlugin());

    // SqlObjectPlugin: cho phép viết DAO bằng interface + annotation
    // Thay vì viết jdbi.withHandle(h -> h.createQuery("SELECT...")...),
    // có thể viết interface AuctionDao { @SqlQuery("SELECT...") Auction findById(long id); }
    // JDBI tự tạo implementation class khi runtime.
    jdbi.installPlugin(new SqlObjectPlugin());

    // SQL Logger: ghi lại mọi SQL query vào log (mức DEBUG)
    // Rất hữu ích khi debug: xem chính xác query nào đang chạy, tham số gì.
    // Trong production sẽ tắt (đổi log level trong logback.xml).
    // Liên kết: logback.xml cấu hình com.auction = DEBUG → thấy SQL log.
    jdbi.setSqlLogger(new Slf4JSqlLogger());

    LOGGER.info("Đã kết nối database thành công");
    return jdbi;
  }

  /**
   * Tạo HikariCP DataSource — connection pool.
   *
   * <p>Connection pool hoạt động như sau:
   *
   * <ol>
   *   <li>Khi khởi động, HikariCP mở sẵn {@code minimumIdle} connections đến PostgreSQL
   *   <li>Khi DAO cần chạy SQL, nó "mượn" 1 connection từ pool (~0.5ms)
   *   <li>Chạy SQL xong, connection được "trả lại" pool (không đóng thật)
   *   <li>Nếu tất cả connections đang bận, request mới phải chờ (tối đa {@code connectionTimeout}
   *       ms)
   *   <li>Nếu chờ quá lâu → throw SQLException → BidService bắt lỗi → trả error cho client
   * </ol>
   *
   * <h4>Tại sao cần connection pool?</h4>
   *
   * <p>Mở connection đến PostgreSQL tốn ~5-10ms (TCP handshake + authentication). Nếu mỗi request
   * mở connection mới → 100 request/giây = 100 lần handshake = chậm. Pool giữ sẵn connections đã mở
   * → lấy ra dùng ngay → nhanh gấp 10-20 lần.
   *
   * <h4>Tại sao maximumPoolSize = 10?</h4>
   *
   * <p>PostgreSQL mặc định cho phép tối đa 100 connections. Với 10 connections trong pool, hệ thống
   * xử lý được ~10 SQL queries đồng thời. Với bài tập lớn (vài chục user), con số này dư thừa. Nếu
   * cần tăng sau này, chỉ đổi số này.
   *
   * @return HikariDataSource đã cấu hình
   */
  private static DataSource createDataSource() {
    // Đọc cấu hình từ biến môi trường, fallback về giá trị mặc định
    String url = getEnv("DB_URL", DEFAULT_URL);
    String user = getEnv("DB_USER", DEFAULT_USER);
    String password = getEnv("DB_PASSWORD", DEFAULT_PASSWORD);

    HikariConfig config = new HikariConfig();

    // ── Kết nối cơ bản ──
    // jdbcUrl: địa chỉ PostgreSQL server
    //   Format: jdbc:postgresql://host:port/database_name
    //   localhost:5432 = PostgreSQL chạy trên máy này, port mặc định
    //   auction_db = tên database (tạo bằng lệnh CREATE DATABASE auction_db)
    config.setJdbcUrl(url);
    config.setUsername(user);
    config.setPassword(password);

    // ── Pool size ──
    // maximumPoolSize: tối đa bao nhiêu connections cùng lúc
    // minimumIdle: giữ sẵn ít nhất bao nhiêu connections (dù không ai dùng)
    //   → Khi server mới khởi động, đã có 5 connections sẵn sàng → request đầu tiên nhanh
    config.setMaximumPoolSize(10);
    config.setMinimumIdle(5);

    // ── Timeout ──
    // connectionTimeout: nếu pool hết connection, request chờ tối đa 30 giây
    //   → Quá 30 giây → throw SQLException → server trả lỗi 503 cho client
    // idleTimeout: connection không ai dùng trong 10 phút → đóng bớt (tiết kiệm tài nguyên)
    // maxLifetime: mỗi connection sống tối đa 30 phút → rồi đóng, mở connection mới
    //   → Tránh lỗi "connection bị PostgreSQL server đóng vì quá cũ"
    config.setConnectionTimeout(30000); // 30 giây
    config.setIdleTimeout(600000); // 10 phút
    config.setMaxLifetime(1800000); // 30 phút

    // ── Pool name ──
    // Hiện trong log: "HikariPool-AuctionPool - Starting..."
    // Dễ phân biệt nếu sau này có nhiều pool (ví dụ pool riêng cho read replica)
    config.setPoolName("AuctionPool");

    LOGGER.info("Khởi tạo connection pool: {} (max={}, min={})", url, 10, 5);
    return new HikariDataSource(config);
  }

  /**
   * Đọc biến môi trường, trả về giá trị mặc định nếu không tìm thấy.
   *
   * <p>Thứ tự ưu tiên:
   *
   * <ol>
   *   <li>Biến môi trường hệ thống (System.getenv) — CI pipeline set ở đây
   *   <li>System property (System.getProperty) — có thể truyền qua -D flag khi chạy Gradle
   *   <li>Giá trị mặc định (defaultValue) — cho development local
   * </ol>
   *
   * @param name tên biến môi trường (ví dụ: "DB_URL")
   * @param defaultValue giá trị mặc định nếu biến không tồn tại
   * @return giá trị biến môi trường hoặc giá trị mặc định
   */
  private static String getEnv(String name, String defaultValue) {
    // Ưu tiên 1: biến môi trường OS (CI pipeline, Docker, .env loader)
    String value = System.getenv(name);
    if (value != null && !value.isBlank()) {
      return value;
    }

    // Ưu tiên 2: system property (java -DDB_URL=jdbc:... khi chạy)
    value = System.getProperty(name);
    if (value != null && !value.isBlank()) {
      return value;
    }

    // Ưu tiên 3: giá trị mặc định
    return defaultValue;
  }
}
