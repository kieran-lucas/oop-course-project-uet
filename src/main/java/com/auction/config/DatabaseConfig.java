package com.auction.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Khởi tạo và quản lý kết nối database (Singleton, thread-safe).
 *
 * <p>Luôn dùng Embedded PostgreSQL — không cần cài PostgreSQL trên máy. Người dùng chỉ cần chạy
 * {@code java -jar auction-server.jar} là đủ.
 *
 * <p>Data được lưu cố định tại {@code data/postgres/} trong thư mục chạy ứng dụng, đảm bảo không bị
 * reset giữa các lần chạy.
 *
 * <p>Flyway tự động chạy các migration SQL từ {@code src/main/resources/db/migration/} mỗi khi
 * server khởi động, đảm bảo schema database luôn khớp với version code hiện tại.
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

  // Thư mục lưu data PostgreSQL cố định — data được giữ lại giữa các lần chạy
  private static final File EMBEDDED_DATA_DIR = new File("data/postgres");

  /**
   * Khởi tạo hoặc trả về Jdbi instance duy nhất (thread-safe, double-checked locking).
   *
   * @return Jdbi instance đã được cấu hình
   */
  public static Jdbi create() {
    if (jdbi == null) {
      synchronized (DatabaseConfig.class) {
        if (jdbi == null) {
          initEmbeddedPostgres();
        }
      }
    }
    return jdbi;
  }

  /**
   * Khởi động Embedded PostgreSQL bên trong JVM. Lần đầu chạy sẽ tải binary PostgreSQL phù hợp với
   * OS (~15MB, tự cache lại). Các lần sau khởi động ngay lập tức.
   *
   * <p>Data được lưu cố định tại {@code data/postgres/} trong thư mục chạy ứng dụng, đảm bảo không
   * bị reset giữa các lần chạy.
   */
  private static void initEmbeddedPostgres() {
    LOGGER.info("🚀 Khởi động Embedded PostgreSQL...");
    LOGGER.info("   (Lần đầu chạy có thể mất 10-30 giây để tải PostgreSQL binary)");
    LOGGER.info("   Data directory: {}", EMBEDDED_DATA_DIR.getAbsolutePath());

    try {
      // Tạo thư mục nếu chưa tồn tại
      if (!EMBEDDED_DATA_DIR.exists()) {
        EMBEDDED_DATA_DIR.mkdirs();
        LOGGER.info("📁 Đã tạo data directory: {}", EMBEDDED_DATA_DIR.getAbsolutePath());
      }

      // Xóa postmaster.pid nếu còn sót — và kill process cũ nếu vẫn đang chạy
      File pidFile = new File(EMBEDDED_DATA_DIR, "postmaster.pid");
      if (pidFile.exists()) {
        try {
          String firstLine = Files.readAllLines(pidFile.toPath()).get(0).trim();
          long oldPid = Long.parseLong(firstLine);
          ProcessHandle.of(oldPid)
              .ifPresent(
                  p -> {
                    p.destroyForcibly();
                    LOGGER.warn("⚠️  Đã kill PostgreSQL process cũ (PID {})", oldPid);
                  });
        } catch (Exception ignored) {
          // Không đọc được PID → bỏ qua, xóa file rồi tiếp tục
        }
        pidFile.delete();
        LOGGER.warn("⚠️  Đã xóa postmaster.pid còn sót từ lần chạy trước");
      }

      embeddedPostgres =
          EmbeddedPostgres.builder()
              .setDataDirectory(EMBEDDED_DATA_DIR)
              .setCleanDataDirectory(false)
              .start();

      int port = embeddedPostgres.getPort();
      String dbUser = "postgres";
      String dbPass = "";

      LOGGER.info("✅ Embedded PostgreSQL đã khởi động tại port {}", port);

      // ── Tạo database auction_db nếu chưa tồn tại ─────────────────────
      // Phải connect vào DB mặc định "postgres" trước, không thể connect thẳng vào auction_db
      try (Connection conn = embeddedPostgres.getPostgresDatabase().getConnection();
          Statement stmt = conn.createStatement();
          PreparedStatement ps =
              conn.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
        ps.setString(1, EMBEDDED_DB_NAME);
        try (ResultSet rs = ps.executeQuery()) {
          if (!rs.next()) {
            stmt.execute("CREATE DATABASE " + EMBEDDED_DB_NAME);
            LOGGER.info("✅ Đã tạo database '{}'", EMBEDDED_DB_NAME);
          } else {
            LOGGER.info("ℹ️  Database '{}' đã tồn tại, bỏ qua bước tạo", EMBEDDED_DB_NAME);
          }
        }
      }

      String dbUrl = String.format("jdbc:postgresql://localhost:%d/%s", port, EMBEDDED_DB_NAME);
      HikariConfig config = buildHikariConfig(dbUrl, dbUser, dbPass);

      dataSource = new HikariDataSource(config);

      // ── CHẠY FLYWAY MIGRATION TRƯỚC KHI TẠO JDBI ──────────────────────
      runMigrations(dataSource);

      jdbi = Jdbi.create(dataSource);

      LOGGER.info("✅ Kết nối Embedded PostgreSQL thành công");

    } catch (Exception e) {
      LOGGER.error("❌ Không thể khởi động Embedded PostgreSQL: {}", e.getMessage());
      throw new RuntimeException("Could not start embedded database", e);
    }
  }

  /**
   * Chạy tất cả database migrations chưa được áp dụng.
   *
   * <p>Flyway tự động theo dõi version qua bảng {@code flyway_schema_history}, đảm bảo mỗi file
   * migration chỉ chạy đúng một lần duy nhất.
   *
   * <p>Migration files: {@code src/main/resources/db/migration/V*.sql}
   *
   * <p>{@code baselineOnMigrate(true)}: quan trọng cho database đã tồn tại sẵn — Flyway sẽ coi DB
   * hiện tại là baseline và chỉ chạy các migration mới hơn, không thực thi lại các file cũ.
   *
   * @param dataSource HikariCP DataSource đã được cấu hình
   */
  private static void runMigrations(HikariDataSource dataSource) {
    LOGGER.info("🔄 Đang chạy database migrations...");

    Flyway flyway =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true) // Cho phép migration trên DB đã tồn tại
            .validateOnMigrate(true) // Kiểm tra checksum để phát hiện file bị sửa
            .load();

    try {
      var result = flyway.migrate();
      if (result.migrationsExecuted == 0) {
        LOGGER.info("✅ Migration hoàn tất — không có migration mới cần áp dụng");
      } else {
        LOGGER.info("✅ Migration hoàn tất — {} file đã được áp dụng", result.migrationsExecuted);
      }
    } catch (Exception e) {
      LOGGER.error("❌ Migration thất bại: {}", e.getMessage(), e);
      throw new RuntimeException("Database migration failed", e);
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

  /** Đóng toàn bộ connection pool và embedded PostgreSQL. Gọi khi server tắt. */
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
}
