package com.auction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Kiểm tra môi trường dự án sau khi thiết lập: JUnit 5 hoạt động và các tính năng Java 21 khả dụng.
 *
 * <p>Đây là "smoke test" cơ sở — nếu class này thất bại, môi trường build/test chưa được cấu hình
 * đúng.
 */
@DisplayName("Kiểm tra môi trường dự án — JUnit 5 và Java 21")
class SetupTest {

  @Test
  @DisplayName("JUnit 5 hoạt động đúng — framework test đã được cấu hình")
  void junitWorks() {
    assertTrue(true, "JUnit 5 should be configured correctly");
  }

  @Test
  @DisplayName("Tính năng Java 21 khả dụng — text block và pattern matching switch")
  void java21Works() {
    // Text block (Java 15+)
    String json =
        """
        {
          "status": "ok"
        }
        """;
    assertTrue(json.contains("ok"));

    // Pattern matching switch (Java 21)
    // Use a helper method so SpotBugs cannot track the concrete type
    Object value = getTestValue();
    String result =
        switch (value) {
          case String s -> s.toUpperCase();
          case Integer i -> String.valueOf(i);
          default -> "unknown";
        };
    assertEquals("HELLO", result);
  }

  /** Trả về giá trị có kiểu runtime cố tình không rõ với static analysis. */
  private Object getTestValue() {
    return "hello";
  }
}
