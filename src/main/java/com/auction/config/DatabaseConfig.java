package com.auction.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Khởi tạo và quản lý kết nối database (Singleton, thread-safe).
 *
 * <p>Logic chọn database:
 *
 * <ul>
 *   <li>Nếu biến môi trường {@code DB_URL} được set → kết nối PostgreSQL ngoài.
 *   <li>Nếu không có {@code DB_URL} → tự động khởi động Embedded PostgreSQL bên trong JVM.
 * </ul>
 *
 * <p>Nhờ đó người dùng có thể chạy {@code java -jar auction.jar} mà không cần cài PostgreSQL.
 */
public class DatabaseConfig {

  private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseConfig.class);

  // Singleton instances
  private static volatile Jdbi jdbi;
  private static HikariDataSource dataSource;

  // Giữ tham chiếu để có thể đóng khi shutdown
  private static EmbeddedPostgres embeddedPostgres;

  // Tên database dùng cho embedded mode
  private static final String EMBEDDED_DB_NAME = "auction_db";

  /**
   * Khởi tạo hoặc trả về Jdbi instance duy nhất (thread-safe, double-checked locking).
   *
   * @return Jdbi instance đã được cấu hình
   */
  public static Jdbi create() {
    if (jdbi == null) {
      synchronized (DatabaseConfig.class) {
        if (jdbi == null) {
          String dbUrl = System.getenv("DB_URL");

          if (dbUrl != null && !dbUrl.isBlank()) {
            // ── Trường hợp 1: Dùng PostgreSQL ngoài ──────────────────────
            initExternalPostgres(dbUrl);
          } else {
            // ── Trường hợp 2: Tự khởi động Embedded PostgreSQL ───────────
            initEmbeddedPostgres();
          }
        }
      }
    }
    return jdbi;
  }

  /** Kết nối tới PostgreSQL ngoài qua biến môi trường DB_URL, DB_USER, DB_PASSWORD. */
  private static void initExternalPostgres(String dbUrl) {
    String dbUser = getEnvOrDefault("DB_USER", "postgres");
    String dbPass = getEnvOrDefault("DB_PASSWORD", "auction_pass");

    LOGGER.info("🔗 Kết nối PostgreSQL ngoài: {}", dbUrl);

    HikariConfig config = buildHikariConfig(dbUrl, dbUser, dbPass);

    try {
      dataSource = new HikariDataSource(config);
      jdbi = Jdbi.create(dataSource);
      LOGGER.info("✅ Kết nối PostgreSQL ngoài thành công");
    } catch (Exception e) {
      LOGGER.error("❌ Không thể kết nối PostgreSQL ngoài: {}", e.getMessage());
      throw new RuntimeException("Could not connect to external database", e);
    }
  }

  /**
   * Khởi động Embedded PostgreSQL bên trong JVM. Lần đầu chạy sẽ tải binary PostgreSQL phù hợp với
   * OS (~15MB, tự cache lại). Các lần sau khởi động ngay lập tức.
   */
  private static void initEmbeddedPostgres() {
    LOGGER.info("🚀 Không tìm thấy DB_URL — khởi động Embedded PostgreSQL...");
    LOGGER.info("   (Lần đầu chạy có thể mất 10-30 giây để tải PostgreSQL binary)");

    try {
      embeddedPostgres = EmbeddedPostgres.builder().start();

      int port = embeddedPostgres.getPort();
      String dbUrl = String.format("jdbc:postgresql://localhost:%d/%s", port, EMBEDDED_DB_NAME);

      // Embedded PostgreSQL mặc định: user=postgres, password=""
      String dbUser = "postgres";
      String dbPass = "";

      LOGGER.info("✅ Embedded PostgreSQL đã khởi động tại port {}", port);

      HikariConfig config = buildHikariConfig(dbUrl, dbUser, dbPass);

      dataSource = new HikariDataSource(config);
      jdbi = Jdbi.create(dataSource);

      LOGGER.info("✅ Kết nối Embedded PostgreSQL thành công");

    } catch (Exception e) {
      LOGGER.error("❌ Không thể khởi động Embedded PostgreSQL: {}", e.getMessage());
      throw new RuntimeException("Could not start embedded database", e);
    }
  }

  /** Tạo HikariConfig với các thông số chung. */
  private static HikariConfig buildHikariConfig(String url, String user, String pass) {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(url);
    config.setUsername(user);
    config.setPassword(pass);
    config.setMaximumPoolSize(10);
    config.setMinimumIdle(5);
    config.setConnectionTimeout(30000);
    config.setPoolName("Auction-Jdbi-Pool");
    config.addDataSourceProperty("currentSchema", "public");
    return config;
  }

  /** Đóng toàn bộ connection pool và embedded PostgreSQL (nếu có). Gọi khi server tắt. */
  public static void shutDown() {
    if (dataSource != null && !dataSource.isClosed()) {
      dataSource.close();
      jdbi = null;
      dataSource = null;
      LOGGER.info("🔌 Database connection pool đã đóng.");
    }

    if (embeddedPostgres != null) {
      try {
        embeddedPostgres.close();
        embeddedPostgres = null;
        LOGGER.info("🔌 Embedded PostgreSQL đã tắt.");
      } catch (Exception e) {
        LOGGER.warn("Lỗi khi tắt Embedded PostgreSQL: {}", e.getMessage());
      }
    }
  }

  private static String getEnvOrDefault(String key, String defaultValue) {
    String value = System.getenv(key);
    return (value != null && !value.isEmpty()) ? value : defaultValue;
  }
}
