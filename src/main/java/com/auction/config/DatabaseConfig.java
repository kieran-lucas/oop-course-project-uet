package com.auction.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseConfig {
  private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseConfig.class);
  private static volatile Jdbi jdbi;
  private static HikariDataSource dataSource;

  /** Khởi tạo hoặc trả về Jdbi instance duy nhất (Thread-safe) */
  public static Jdbi create() {
    if (jdbi == null) {
      synchronized (DatabaseConfig.class) {
        if (jdbi == null) {
          // Ưu tiên đọc biến môi trường hệ thống, nếu không có thì dùng cấu hình local mặc định
          String dbUrl = getEnvOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/auction_db");
          String dbUser = getEnvOrDefault("DB_USER", "postgres");
          String dbPass = getEnvOrDefault("DB_PASSWORD", "auction_pass");

          HikariConfig config = new HikariConfig();
          config.setJdbcUrl(dbUrl);
          config.setUsername(dbUser);
          config.setPassword(dbPass);

          // Cấu hình Connection Pool
          config.setMaximumPoolSize(10);
          config.setMinimumIdle(5);
          config.setConnectionTimeout(20000);
          config.setPoolName("Auction-Jdbi-Pool");
          config.addDataSourceProperty("currentSchema", "public");

          try {
            dataSource = new HikariDataSource(config);
            jdbi = Jdbi.create(dataSource);
            LOGGER.info("✅ Database connection pool initialized: {}", dbUrl);
          } catch (Exception e) {
            LOGGER.error("❌ Database initialization failed: {}", e.getMessage());
            throw new RuntimeException("Could not connect to database", e);
          }
        }
      }
    }
    return jdbi;
  }

  /** Đóng Connection Pool khi kết thúc chương trình hoặc toàn bộ test */
  public static void shutDown() {
    if (dataSource != null && !dataSource.isClosed()) {
      dataSource.close();
      jdbi = null;
      dataSource = null;
      LOGGER.info("🔌 Database connection pool has been shut down.");
    }
  }

  private static String getEnvOrDefault(String key, String defaultValue) {
    String value = System.getenv(key);
    return (value != null && !value.isEmpty()) ? value : defaultValue;
  }
}
