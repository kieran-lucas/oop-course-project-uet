package com.auction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Project setup verification")
class SetupTest {

  @Test
  @DisplayName("JUnit 5 is working")
  void junitWorks() {
    assertTrue(true, "JUnit 5 should be configured correctly");
  }

  @Test
  @DisplayName("Java 21 features are available")
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

  /** Returns a value whose runtime type is intentionally opaque to static analysis. */
  private Object getTestValue() {
    return "hello";
  }
}
