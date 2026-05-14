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
import java.util.concurrent.TimeoutException;
import org.flywaydb.core.Flyway;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Khởi tạo và quản lý kết nối database (Singleton, thread-safe).
 *
 * <p>Mặc định dùng Embedded PostgreSQL — không cần cài PostgreSQL trên máy. CI/test có thể truyền
 * {@code DB_URL}, {@code DB_USER}, {@code DB_PASSWORD} để dùng PostgreSQL service bên ngoài.
 *
 * <p>Data được lưu cố định tại {@code data/postgres/} trong thư mục chạy ứng dụng, đảm bảo không bị
 * reset giữa các lần chạy.
 *
 * <p>Flyway tự động chạy các migration SQL từ {@code src/main/resources/db/migration/} mỗi khi
 * server khởi động, đảm bảo schema database luôn khớp với version code hiện tại.
 */
public class DatabaseConfig {

  private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseConfig.class);
  private static final Object SHUTDOWN_LOCK = new Object();

  // Singleton instances
  private static volatile Jdbi jdbi;
  private static HikariDataSource dataSource;

  // Giữ tham chiếu để có thể đóng khi shutdown
  private static EmbeddedPostgres embeddedPostgres;
  private static Long embeddedPostgresPid;
  private static boolean shutdownHookRegistered;

  // Tên database dùng cho embedded mode
  private static final String EMBEDDED_DB_NAME = "auction_db";

  // Thư mục lưu data PostgreSQL cố định — data được giữ lại giữa các lần chạy
  private static final File EMBEDDED_DATA_DIR =
      new File(System.getProperty("auction.db.dir", "data/postgres"));

  // File lưu PID của PostgreSQL process để kill khi khởi động lại
  private static final File POSTGRES_PID_FILE =
      new File(System.getProperty("auction.db.pid", "data/postgres.pid"));
  private static final File POSTMASTER_PID_FILE = new File(EMBEDDED_DATA_DIR, "postmaster.pid");
  private static final File POSTMASTER_OPTS_FILE = new File(EMBEDDED_DATA_DIR, "postmaster.opts");

  /**
   * Khởi tạo hoặc trả về Jdbi instance duy nhất (thread-safe, double-checked locking).
   *
   * @return Jdbi instance đã được cấu hình
   */
  public static Jdbi create() {
    if (jdbi == null) {
      synchronized (DatabaseConfig.class) {
        if (jdbi == null) {
          String externalDbUrl = System.getenv("DB_URL");
          if (externalDbUrl != null && !externalDbUrl.isBlank()) {
            initExternalPostgres(externalDbUrl);
          } else {
            initEmbeddedPostgres();
          }
        }
      }
    }
    return jdbi;
  }

  /** Connect to a caller-provided PostgreSQL database and let Flyway prepare the schema. */
  private static void initExternalPostgres(String dbUrl) {
    LOGGER.info("Connecting to external PostgreSQL database: {}", dbUrl);

    String dbUser = System.getenv().getOrDefault("DB_USER", "postgres");
    String dbPass = System.getenv().getOrDefault("DB_PASSWORD", "");

    try {
      HikariConfig config = buildHikariConfig(dbUrl, dbUser, dbPass);
      dataSource = new HikariDataSource(config);

      runMigrations(dataSource);

      jdbi = Jdbi.create(dataSource);
      LOGGER.info("External PostgreSQL connection initialized successfully");
    } catch (Exception e) {
      LOGGER.error("Could not initialize external PostgreSQL: {}", e.getMessage(), e);
      throw new RuntimeException("Could not initialize external database", e);
    }
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

      // Stop PostgreSQL process cũ nếu server trước đó bị đóng đột ngột bằng nút X.
      // Không bao giờ xóa data directory ở đây; data/postgres chỉ reset khi người dùng tự rmdir.
      stopPreviousPostgresIfNeeded();

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
      embeddedPostgresPid = savePostgresPid();

      // Đăng ký shutdown hook ngay tại đây để đảm bảo luôn được gọi
      registerShutdownHook();

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
  private static Long savePostgresPid() {
    try {
      Long pid = readPid(POSTMASTER_PID_FILE);
      if (pid != null) {
        Files.writeString(POSTGRES_PID_FILE.toPath(), Long.toString(pid));
        LOGGER.debug("Saved PostgreSQL PID: {}", pid);
        return pid;
      }
    } catch (Exception e) {
      LOGGER.warn("Khong the luu PostgreSQL PID: {}", e.getMessage());
    }
    return null;
  }

  private static void registerShutdownHook() {
    synchronized (SHUTDOWN_LOCK) {
      if (shutdownHookRegistered) {
        return;
      }
      Runtime.getRuntime()
          .addShutdownHook(new Thread(DatabaseConfig::shutDown, "postgres-shutdown-hook"));
      shutdownHookRegistered = true;
    }
  }

  /** Stop PostgreSQL process cũ từ lần chạy trước nếu vẫn còn sống. */
  private static void stopPreviousPostgresIfNeeded() throws Exception {
    boolean stopped = true;
    stopped &= stopPostgresWithPgCtl();
    stopped &= killPostgresFromPidFile(POSTGRES_PID_FILE);
    stopped &= killPostgresFromPidFile(POSTMASTER_PID_FILE);
    stopped &= killPostgresUsingDataDir();

    if (stopped) {
      deleteStalePidFiles();
      return;
    }

    throw new IllegalStateException(
        "PostgreSQL from a previous run is still using "
            + EMBEDDED_DATA_DIR.getAbsolutePath()
            + ". Stop postgres.exe from Task Manager or restart Windows, then start the server again.");
  }

  private static boolean stopPostgresWithPgCtl() {
    if (!POSTMASTER_PID_FILE.exists()) {
      return true;
    }

    File pgCtl = findPgCtlExecutable();
    if (pgCtl == null || !pgCtl.exists()) {
      LOGGER.warn("Could not find pg_ctl.exe for previous PostgreSQL process");
      return false;
    }

    try {
      Process process =
          new ProcessBuilder(
                  pgCtl.getAbsolutePath(),
                  "-D",
                  EMBEDDED_DATA_DIR.getPath(),
                  "-m",
                  "fast",
                  "-w",
                  "stop")
              .redirectErrorStream(true)
              .start();
      boolean exited = process.waitFor(30, TimeUnit.SECONDS);
      if (!exited) {
        process.destroyForcibly();
        return false;
      }

      Long pid = readPid(POSTMASTER_PID_FILE);
      boolean stopped =
          process.exitValue() == 0
              || pid == null
              || ProcessHandle.of(pid).map(handle -> !handle.isAlive()).orElse(true);
      if (stopped) {
        LOGGER.warn("Stopped previous PostgreSQL server with pg_ctl");
      } else {
        LOGGER.warn("pg_ctl could not stop previous PostgreSQL server");
      }
      return stopped;
    } catch (Exception e) {
      LOGGER.warn("Could not stop previous PostgreSQL with pg_ctl: {}", e.getMessage());
      return false;
    }
  }

  private static File findPgCtlExecutable() {
    if (!POSTMASTER_OPTS_FILE.exists()) {
      return null;
    }

    try {
      String options = Files.readString(POSTMASTER_OPTS_FILE.toPath()).trim();
      String lowerOptions = options.toLowerCase(Locale.ROOT);
      int postgresExeIndex = lowerOptions.indexOf("postgres.exe");
      if (postgresExeIndex < 0) {
        return null;
      }

      String postgresPath =
          options.substring(0, postgresExeIndex + "postgres.exe".length()).replace("\"", "").trim();
      File postgresExe = new File(postgresPath);
      File binDir = postgresExe.getParentFile();
      return binDir == null ? null : new File(binDir, "pg_ctl.exe");
    } catch (Exception e) {
      LOGGER.warn("Could not read PostgreSQL startup options: {}", e.getMessage());
      return null;
    }
  }

  private static void deleteStalePidFiles() throws Exception {
    Files.deleteIfExists(POSTGRES_PID_FILE.toPath());

    Long postmasterPid = readPid(POSTMASTER_PID_FILE);
    boolean postmasterStopped =
        postmasterPid == null
            || ProcessHandle.of(postmasterPid).map(process -> !process.isAlive()).orElse(true);
    if (postmasterStopped) {
      Files.deleteIfExists(POSTMASTER_PID_FILE.toPath());
    }
  }

  private static boolean killPostgresFromPidFile(File pidFile) {
    Long oldPid = readPid(pidFile);
    return oldPid == null
        || ProcessHandle.of(oldPid)
            .map(process -> stopPostgresProcess(process, oldPid))
            .orElse(true);
  }

  private static boolean killPostgresUsingDataDir() {
    String relativeDataDir =
        EMBEDDED_DATA_DIR.getPath().replace('\\', '/').toLowerCase(Locale.ROOT);
    String absoluteDataDir =
        EMBEDDED_DATA_DIR.getAbsolutePath().replace('\\', '/').toLowerCase(Locale.ROOT);

    return ProcessHandle.allProcesses()
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
        .map(process -> stopPostgresProcess(process, process.pid()))
        .reduce(true, (left, right) -> left && right);
  }

  private static boolean stopPostgresProcess(ProcessHandle process, long pid) {
    if (!process.isAlive()) {
      return true;
    }

    boolean stopped = stopProcessGracefully(process, 10);
    if (!stopped) {
      process.destroyForcibly();
      stopped = waitForProcessExit(process, 10);
    }

    if (stopped || !process.isAlive()) {
      LOGGER.warn("Stopped orphan PostgreSQL process (PID {}) from previous run", pid);
    } else {
      LOGGER.warn("Could not stop orphan PostgreSQL process (PID {})", pid);
    }
    return stopped || !process.isAlive();
  }

  private static boolean stopProcessGracefully(ProcessHandle process, int timeoutSeconds) {
    try {
      process.destroy();
      return waitForProcessExit(process, timeoutSeconds);
    } catch (Exception e) {
      LOGGER.debug("Could not send stop signal to process: {}", e.getMessage());
      return false;
    }
  }

  private static boolean waitForProcessExit(ProcessHandle process, int timeoutSeconds) {
    if (!process.isAlive()) {
      return true;
    }

    try {
      process.onExit().get(timeoutSeconds, TimeUnit.SECONDS);
      return true;
    } catch (TimeoutException e) {
      return false;
    } catch (Exception e) {
      LOGGER.debug("Could not wait for process exit: {}", e.getMessage());
      return !process.isAlive();
    }
  }

  private static Long readPid(File pidFile) {
    if (!pidFile.exists()) {
      return null;
    }

    try {
      String pidStr = Files.readAllLines(pidFile.toPath()).get(0).trim();
      return Long.parseLong(pidStr);
    } catch (Exception e) {
      LOGGER.warn("Could not read PostgreSQL PID from {}: {}", pidFile.getPath(), e.getMessage());
      return null;
    }
  }

  /** Close the connection pool and embedded PostgreSQL. */
  public static void shutDown() {
    synchronized (SHUTDOWN_LOCK) {
      if (dataSource != null && !dataSource.isClosed()) {
        dataSource.close();
        LOGGER.info("Database connection pool closed.");
      }
      jdbi = null;
      dataSource = null;

      if (embeddedPostgres == null) {
        return;
      }

      Long pid = embeddedPostgresPid;
      if (pid == null) {
        pid = readPid(POSTGRES_PID_FILE);
      }
      if (pid == null) {
        pid = readPid(POSTMASTER_PID_FILE);
      }

      try {
        embeddedPostgres.close();
        LOGGER.info("Embedded PostgreSQL stopped.");
      } catch (Exception e) {
        LOGGER.warn("Error while stopping Embedded PostgreSQL: {}", e.getMessage());
      } finally {
        embeddedPostgres = null;
        waitForPostgresToReleaseFiles(pid);
        embeddedPostgresPid = null;
        deletePidFileIfStopped(pid);
      }
    }
  }

  private static void waitForPostgresToReleaseFiles(Long pid) {
    if (pid == null) {
      return;
    }

    ProcessHandle.of(pid)
        .ifPresent(
            process -> {
              if (waitForProcessExit(process, 10)) {
                return;
              }

              LOGGER.warn("PostgreSQL PID {} still running after close, stopping it again", pid);
              if (!stopProcessGracefully(process, 5)) {
                process.destroyForcibly();
                waitForProcessExit(process, 5);
              }
            });
  }

  private static void deletePidFileIfStopped(Long pid) {
    boolean processStopped =
        pid == null || ProcessHandle.of(pid).map(process -> !process.isAlive()).orElse(true);
    if (!processStopped) {
      LOGGER.warn("Keeping PostgreSQL PID file because PID {} is still running", pid);
      return;
    }

    try {
      Files.deleteIfExists(POSTGRES_PID_FILE.toPath());
    } catch (Exception e) {
      LOGGER.warn("Could not delete PostgreSQL PID file: {}", e.getMessage());
    }
  }
}
