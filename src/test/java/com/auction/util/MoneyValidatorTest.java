package com.auction.util;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("MoneyValidator")
class MoneyValidatorTest {

  @Nested
  @DisplayName("requirePositiveIntegerVnd()")
  class RequirePositiveIntegerVnd {

    @Test
    @DisplayName("accepts a positive integer amount")
    void acceptsPositiveInteger() {
      assertDoesNotThrow(
          () -> MoneyValidator.requirePositiveIntegerVnd(new BigDecimal("100000"), "Amount"));
    }

    @Test
    @DisplayName("rejects null amount with field name in message")
    void rejectsNull() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> MoneyValidator.requirePositiveIntegerVnd(null, "Bid amount"));
      assertTrue(ex.getMessage().contains("Bid amount"));
    }

    @Test
    @DisplayName("rejects zero amount")
    void rejectsZero() {
      assertThrows(
          IllegalArgumentException.class,
          () -> MoneyValidator.requirePositiveIntegerVnd(BigDecimal.ZERO, "Amount"));
    }

    @Test
    @DisplayName("rejects negative amount")
    void rejectsNegative() {
      assertThrows(
          IllegalArgumentException.class,
          () -> MoneyValidator.requirePositiveIntegerVnd(new BigDecimal("-1"), "Amount"));
    }

    @Test
    @DisplayName("rejects fractional amount (1000.5)")
    void rejectsFractional() {
      assertThrows(
          IllegalArgumentException.class,
          () -> MoneyValidator.requirePositiveIntegerVnd(new BigDecimal("1000.5"), "Amount"));
    }

    @Test
    @DisplayName("accepts integer expressed with trailing zeros (100000.00)")
    void acceptsIntegerWithTrailingZeros() {
      assertDoesNotThrow(
          () -> MoneyValidator.requirePositiveIntegerVnd(new BigDecimal("100000.00"), "Amount"));
    }
  }

  @Nested
  @DisplayName("isIntegerVnd()")
  class IsIntegerVnd {

    @Test
    @DisplayName("returns false for null")
    void rejectsNull() {
      assertFalse(MoneyValidator.isIntegerVnd(null));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"1", "100", "1000000", "100.00", "0", "-5"})
    @DisplayName("returns true for integer-valued amounts")
    void acceptsInteger(String value) {
      assertTrue(MoneyValidator.isIntegerVnd(new BigDecimal(value)));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"0.5", "1000.50", "0.0001"})
    @DisplayName("returns false for fractional amounts")
    void rejectsFractional(String value) {
      assertFalse(MoneyValidator.isIntegerVnd(new BigDecimal(value)));
    }
  }

  @Nested
  @DisplayName("toIntegerVndExact()")
  class ToIntegerVndExact {

    @Test
    @DisplayName("converts integer amount to long")
    void convertsToLong() {
      assertEquals(500_000L, MoneyValidator.toIntegerVndExact(new BigDecimal("500000"), "Amount"));
    }

    @Test
    @DisplayName("rejects null")
    void rejectsNull() {
      assertThrows(
          IllegalArgumentException.class, () -> MoneyValidator.toIntegerVndExact(null, "Amount"));
    }

    @Test
    @DisplayName("rejects fractional amount")
    void rejectsFractional() {
      assertThrows(
          IllegalArgumentException.class,
          () -> MoneyValidator.toIntegerVndExact(new BigDecimal("1000.5"), "Amount"));
    }
  }
}
