package com.auction.config;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatabaseConfigTest {

    private static Jdbi jdbi;

    @BeforeAll
    static void setup() {
        jdbi = DatabaseConfig.create();
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
    @DisplayName("Should have 5 tables")
    void testTableCount() {
        int count = jdbi.withHandle(handle ->
            handle.createQuery(
                "SELECT COUNT(*) FROM information_schema.tables " +
                "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'"
            )
            .mapTo(Integer.class)
            .one()
        );
        assertEquals(5, count, "Should have 5 tables");
    }
}