package com.auction.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for the three domain enums: AuctionStatus, AutoBidStatus, AutoBidFailureReason. Each enum
 * exposes a {@code from(String)} parser that is exercised here.
 */
@DisplayName("Domain enums")
class DomainEnumsTest {

  @Nested
  @DisplayName("AuctionStatus.from()")
  class AuctionStatusTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"OPEN", "open", "Open", " OPEN "})
    @DisplayName("parses OPEN case-insensitively (after trim where applicable)")
    void parsesOpen(String input) {
      // Note: from() does not trim — call sites are expected to pass clean values.
      // We trim here to align with the contract used by AuctionMapper.
      assertEquals(AuctionStatus.OPEN, AuctionStatus.from(input.trim()));
    }

    @Test
    @DisplayName("parses every concrete value")
    void parsesAllValues() {
      for (AuctionStatus expected : AuctionStatus.values()) {
        assertEquals(expected, AuctionStatus.from(expected.name()));
      }
    }

    @Test
    @DisplayName("rejects unknown value with IllegalArgumentException")
    void rejectsUnknown() {
      assertThrows(IllegalArgumentException.class, () -> AuctionStatus.from("UNKNOWN"));
    }
  }

  @Nested
  @DisplayName("AutoBidStatus.from()")
  class AutoBidStatusTest {

    @Test
    @DisplayName("returns ACTIVE for null and blank input")
    void defaultsToActive() {
      assertEquals(AutoBidStatus.ACTIVE, AutoBidStatus.from(null));
      assertEquals(AutoBidStatus.ACTIVE, AutoBidStatus.from(""));
      assertEquals(AutoBidStatus.ACTIVE, AutoBidStatus.from("   "));
    }

    @Test
    @DisplayName("parses every concrete status")
    void parsesAll() {
      for (AutoBidStatus expected : AutoBidStatus.values()) {
        assertEquals(expected, AutoBidStatus.from(expected.name()));
      }
    }

    @Test
    @DisplayName("rejects unknown status")
    void rejectsUnknown() {
      assertThrows(IllegalArgumentException.class, () -> AutoBidStatus.from("RUNNING"));
    }
  }

  @Nested
  @DisplayName("AutoBidFailureReason.from()")
  class FailureReasonTest {

    @Test
    @DisplayName("returns null for null and blank input")
    void returnsNullForBlank() {
      assertNull(AutoBidFailureReason.from(null));
      assertNull(AutoBidFailureReason.from(""));
      assertNull(AutoBidFailureReason.from("   "));
    }

    @Test
    @DisplayName("parses every concrete reason")
    void parsesAll() {
      for (AutoBidFailureReason expected : AutoBidFailureReason.values()) {
        assertEquals(expected, AutoBidFailureReason.from(expected.name()));
      }
    }

    @Test
    @DisplayName("rejects unknown reason")
    void rejectsUnknown() {
      assertThrows(IllegalArgumentException.class, () -> AutoBidFailureReason.from("UNKNOWN"));
    }
  }
}
