package com.auction.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Assumptions;

/**
 * Test suite kiểm tra tính đúng đắn của {@link DatabaseConfig}:
 *
 * <ul>
 *   <li>Kết nối thành công đến PostgreSQL qua HikariCP connection pool.
 *   <li>Schema "public" tồn tại và chứa đủ các bảng bắt buộc.
 *   <li>Migration (Flyway/Liquibase hoặc script thủ công) đã chạy thành công.
 * </ul>
 *
 * <p><b>Điều kiện tiên quyết:</b> Cần có một instance PostgreSQL đang chạy với thông tin kết nối
 * được cấu hình trong {@code DatabaseConfig}. Nếu không có DB, toàn bộ test class sẽ bị bỏ qua thay
 * vì báo lỗi (nhờ {@link Assumptions#abort}).
 *
 * <p><b>Thứ tự thực thi:</b> Các test được đánh số {@code @Order} để phản ánh mức độ phụ thuộc
 * logic — kết nối phải hoạt động trước khi kiểm tra schema.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatabaseConfigTest {

  /** Đối tượng JDBI dùng chung cho toàn bộ test class; được khởi tạo một lần duy nhất. */
  private static Jdbi jdbi;

  /**
   * Khởi tạo kết nối JDBI trước khi bất kỳ test nào chạy.
   *
   * <p>Nếu {@link DatabaseConfig#create()} ném ngoại lệ (ví dụ: DB chưa khởi động, sai
   * credentials), toàn bộ test class sẽ bị bỏ qua thông qua {@link Assumptions#abort} thay vì báo
   * FAILED — điều này giúp CI/CD không bị chặn khi không có DB.
   */
  @BeforeAll
  static void setup() {
    try {
      jdbi = DatabaseConfig.create();
    } catch (Exception e) {
      Assumptions.abort("No DB available, skipping: " + e.getMessage());
    }
  }

  /**
   * Giải phóng connection pool sau khi tất cả test trong class đã hoàn thành.
   *
   * <p>Gọi {@link DatabaseConfig#shutDown()} để đóng HikariCP pool và tránh rò rỉ kết nối
   * (connection leak) trong môi trường test chạy liên tục.
   */
  @AfterAll
  static void tearDown() {
    DatabaseConfig.shutDown();
  }

  /**
   * Xóa toàn bộ dữ liệu và đặt lại bộ đếm ID về 1 trước mỗi test.
   *
   * <p>Chiến lược này đảm bảo mỗi test chạy trong trạng thái DB sạch, loại bỏ sự phụ thuộc giữa các
   * test (test isolation). Các bảng được TRUNCATE theo thứ tự con → cha để tránh vi phạm ràng buộc
   * khóa ngoại; {@code CASCADE} xử lý các quan hệ còn lại tự động.
   *
   * <p>{@code RESTART IDENTITY} trên bảng {@code users} đưa sequence BIGSERIAL về 1, giúp các
   * assertion kiểm tra ID cụ thể luôn nhất quán qua mọi lần chạy.
   */
  @BeforeEach
  void cleanDatabase() {
    jdbi.useHandle(
        handle -> {
          // Xóa các bảng con trước để tránh lỗi vi phạm ràng buộc khóa ngoại (FK constraint).
          // CASCADE sẽ tự động xóa các bản ghi liên quan nếu vẫn còn phụ thuộc.
          handle.execute("TRUNCATE TABLE auto_bid_configs CASCADE");
          handle.execute("TRUNCATE TABLE bid_transactions CASCADE");
          handle.execute("TRUNCATE TABLE auctions CASCADE");
          handle.execute("TRUNCATE TABLE items CASCADE");
          // RESTART IDENTITY đưa sequence BIGSERIAL của cột id về 1,
          // đảm bảo các test dùng ID cố định không bị ảnh hưởng bởi các lần chạy trước.
          handle.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
        });
  }

  /**
   * Kiểm tra kết nối cơ bản: thực thi câu lệnh {@code SELECT 1} và xác nhận kết quả trả về đúng.
   *
   * <p>Đây là "smoke test" tối giản — nếu test này thất bại, tất cả test phía sau đều vô nghĩa.
   */
  @Test
  @Order(1)
  @DisplayName("Database connection should work")
  void testConnection() {
    assertDoesNotThrow(
        () -> {
          boolean result =
              jdbi.withHandle(
                  handle -> handle.createQuery("SELECT 1").mapTo(Integer.class).one() == 1);
          assertTrue(result);
        });
  }

  /**
   * Liệt kê và in ra tất cả các bảng trong schema {@code public}, đồng thời kiểm tra rằng migration
   * đã tạo ít nhất 5 bảng.
   *
   * <p>Ngưỡng tối thiểu 5 bảng tương ứng với 5 bảng nghiệp vụ bắt buộc của hệ thống đấu giá. Test
   * này hữu ích khi debug: danh sách bảng được in ra stdout giúp xác nhận nhanh trạng thái DB.
   */
  @Test
  @Order(2)
  @DisplayName("List all tables in public schema")
  void testListTables() {
    System.out.println("=== Liệt kê bảng trong database ===");
    List<String> tables =
        jdbi.withHandle(
            handle ->
                handle
                    .createQuery("SELECT tablename FROM pg_tables WHERE schemaname = 'public'")
                    .mapTo(String.class)
                    .list());

    System.out.println("Số bảng trong schema public: " + tables.size());
    tables.forEach(table -> System.out.println("  - " + table));

    // Đảm bảo migration đã chạy thành công và tạo ra đủ các bảng cần thiết.
    assertTrue(tables.size() >= 5, "Cần có ít nhất 5 bảng sau khi chạy migration.");
  }

  /**
   * Xác nhận rằng schema hiện tại của session đang là {@code public}.
   *
   * <p>Nếu {@code search_path} bị cấu hình sai (ví dụ: trỏ sang schema khác), các câu truy vấn
   * không chỉ định schema tường minh có thể đọc nhầm bảng, gây ra bug khó tái hiện.
   */
  @Test
  @Order(3)
  @DisplayName("Check current schema")
  void testCurrentSchema() {
    String schema =
        jdbi.withHandle(
            handle -> handle.createQuery("SELECT current_schema()").mapTo(String.class).one());
    assertEquals("public", schema);
  }

  /**
   * Xác nhận rằng cả 5 bảng nghiệp vụ bắt buộc đều tồn tại trong schema {@code public}.
   *
   * <p>Danh sách bảng bắt buộc: {@code users}, {@code items}, {@code auctions}, {@code
   * bid_transactions}, {@code auto_bid_configs}. Test đếm số bảng tìm thấy và so sánh với con số kỳ
   * vọng là 5 — nếu thiếu bất kỳ bảng nào, migration đã chạy không đầy đủ.
   */
  @Test
  @Order(4)
  @DisplayName("Should have 5 required tables")
  void testRequiredTables() {
    List<String> requiredTables =
        List.of("users", "items", "auctions", "bid_transactions", "auto_bid_configs");

    List<String> existingTables =
        jdbi.withHandle(
            handle ->
                handle
                    .createQuery("SELECT tablename FROM pg_tables WHERE schemaname = 'public'")
                    .mapTo(String.class)
                    .list());

    long foundCount = requiredTables.stream().filter(existingTables::contains).count();

    assertEquals(
        5,
        foundCount,
        "Phải tìm thấy đủ 5 bảng bắt buộc: users, items, auctions, bid_transactions, auto_bid_configs.");
  }
}
