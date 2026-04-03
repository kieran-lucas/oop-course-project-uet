package com.auction.config;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatabaseConfigTest {

    private static Jdbi jdbi;

    @BeforeAll
    static void setup() {
        // Khởi tạo connection
        jdbi = DatabaseConfig.create();
    }

    @AfterAll
    static void tearDown() {
        // Đóng connection pool sau khi tất cả các test đã chạy xong
        DatabaseConfig.shutDown();
    }

    @Test
    @Order(1)
    @DisplayName("Database connection should work")
    void testConnection() {
        assertDoesNotThrow(() -> {
            boolean result = jdbi.withHandle(handle ->
                handle.createQuery("SELECT 1")
                    .mapTo(Integer.class)
                    .one() == 1
            );
            assertTrue(result);
        });
    }

    @Test
    @Order(2)
    @DisplayName("List all tables in public schema")
    void testListTables() {
        System.out.println("=== Liệt kê bảng trong database ===");
        List<String> tables = jdbi.withHandle(handle ->
            handle.createQuery(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public'"
            )
            .mapTo(String.class)
            .list()
        );
        
        System.out.println("Số bảng trong schema public: " + tables.size());
        tables.forEach(table -> System.out.println("  - " + table));
        
        assertTrue(tables.size() >= 5, "Cần có ít nhất 5 bảng.");
    }

    @Test
    @Order(3)
    @DisplayName("Check current schema")
    void testCurrentSchema() {
        String schema = jdbi.withHandle(handle ->
            handle.createQuery("SELECT current_schema()")
                .mapTo(String.class)
                .one()
        );
        assertEquals("public", schema);
    }

    @Test
    @Order(4)
    @DisplayName("Should have 5 required tables")
    void testRequiredTables() {
        List<String> requiredTables = List.of(
            "users", "items", "auctions", "bid_transactions", "auto_bid_configs"
        );
        
        List<String> existingTables = jdbi.withHandle(handle ->
            handle.createQuery("SELECT tablename FROM pg_tables WHERE schemaname = 'public'")
                .mapTo(String.class)
                .list()
        );
        
        long foundCount = requiredTables.stream()
                .filter(existingTables::contains)
                .count();
        
        assertEquals(5, foundCount, "Phải tìm thấy đủ 5 bảng bắt buộc.");
    }
}
