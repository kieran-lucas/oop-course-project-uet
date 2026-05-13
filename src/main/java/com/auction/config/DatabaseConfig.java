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
import java.util.Locale;
import java.util.concurrent.TimeUnit;
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
  private static final File EMBEDDED_DATA_DIR =
      new File(System.getProperty("auction.db.dir", "data/postgres"));

  // File lưu PID của PostgreSQL process để kill khi khởi động lại
  private static final File POSTGRES_PID_FILE =
      new File(System.getProperty("auction.db.pid", "data/postgres.pid"));

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

      // Kill PostgreSQL process cũ nếu còn sống (từ lần chạy trước bị đóng đột ngột)
      killOrphanPostgresProcess();

      // Xóa postmaster.pid nếu còn sót
      File pidFile = new File(EMBEDDED_DATA_DIR, "postmaster.pid");
      if (pidFile.exists()) {
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

      // Lưu PID vào file riêng để lần sau có thể kill nếu JVM bị đóng đột ngột
      savePostgresPid();

      // Đăng ký shutdown hook ngay tại đây để đảm bảo luôn được gọi
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    LOGGER.info("Shutdown hook: đang tắt Embedded PostgreSQL...");
                    shutDown();
                  },
                  "postgres-shutdown-hook"));

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

  /** Lưu PID của PostgreSQL process vào file để có thể kill khi khởi động lại. */
  private static void savePostgresPid() {
    try {
      // EmbeddedPostgres không expose PID trực tiếp, nhưng postmaster.pid chứa nó
      File postmasterPid = new File(EMBEDDED_DATA_DIR, "postmaster.pid");
      if (postmasterPid.exists()) {
        String firstLine = Files.readAllLines(postmasterPid.toPath()).get(0).trim();
        Files.writeString(POSTGRES_PID_FILE.toPath(), firstLine);
        LOGGER.debug("Đã lưu PostgreSQL PID: {}", firstLine);
      }
    } catch (Exception e) {
      LOGGER.warn("Không thể lưu PostgreSQL PID: {}", e.getMessage());
    }
  }

  /** Kill PostgreSQL process cũ từ lần chạy trước nếu vẫn còn sống. */
  private static void killOrphanPostgresProcess() {
    killPostgresFromPidFile(POSTGRES_PID_FILE);
    killPostgresFromPidFile(new File(EMBEDDED_DATA_DIR, "postmaster.pid"));
    killPostgresUsingDataDir();

    if (POSTGRES_PID_FILE.exists()) {
      POSTGRES_PID_FILE.delete();
    }
  }

  private static void killPostgresFromPidFile(File pidFile) {
    if (!pidFile.exists()) {
      return;
    }

    try {
      String pidStr = Files.readAllLines(pidFile.toPath()).get(0).trim();
      long oldPid = Long.parseLong(pidStr);
      ProcessHandle.of(oldPid).ifPresent(process -> stopPostgresProcess(process, oldPid));
    } catch (Exception e) {
      LOGGER.warn("Không thể đọc PostgreSQL PID từ {}: {}", pidFile.getPath(), e.getMessage());
    }
  }

  private static void killPostgresUsingDataDir() {
    String relativeDataDir =
        EMBEDDED_DATA_DIR.getPath().replace('\\', '/').toLowerCase(Locale.ROOT);
    String absoluteDataDir =
        EMBEDDED_DATA_DIR.getAbsolutePath().replace('\\', '/').toLowerCase(Locale.ROOT);

    ProcessHandle.allProcesses()
        .filter(
            process ->
                process
                    .info()
                    .commandLine()
                    .map(command -> command.replace('\\', '/').toLowerCase(Locale.ROOT))
                    .map(
                        command ->
                            command.contains("postgres")
                                && (command.contains(relativeDataDir)
                                    || command.contains(absoluteDataDir)))
                    .orElse(false))
        .forEach(process -> stopPostgresProcess(process, process.pid()));
  }

  private static void stopPostgresProcess(ProcessHandle process, long pid) {
    if (!process.isAlive()) {
      return;
    }

    try {
      process.destroy();
      process.onExit().get(2, TimeUnit.SECONDS);
    } catch (Exception ignored) {
      process.destroyForcibly();
    }

    LOGGER.warn("⚠️  Đã kill PostgreSQL process cũ (PID {}) còn sót từ lần trước", pid);
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
      } finally {
        // Xoa PID file vi postgres da duoc tat dung cach
        if (POSTGRES_PID_FILE.exists()) {
          POSTGRES_PID_FILE.delete();
        }
      }
    }
  }
}
