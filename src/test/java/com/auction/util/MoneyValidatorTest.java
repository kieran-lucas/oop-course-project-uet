package com.auction.util;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Kiểm thử {@link MoneyValidator} — tiện ích xác thực số tiền VND là số nguyên dương.
 *
 * <p>Ba nhóm test tương ứng ba phương thức public:
 *
 * <ul>
 *   <li>{@code requirePositiveIntegerVnd()} — ném exception nếu vi phạm
 *   <li>{@code isIntegerVnd()} — kiểm tra boolean
 *   <li>{@code toIntegerVndExact()} — chuyển đổi chính xác sang long
 * </ul>
 */
@DisplayName("MoneyValidator")
class MoneyValidatorTest {

  @Nested
  @DisplayName("requirePositiveIntegerVnd()")
  class RequirePositiveIntegerVnd {

    @Test
    @DisplayName("chấp nhận số tiền nguyên dương")
    void acceptsPositiveInteger() {
      assertDoesNotThrow(
          () -> MoneyValidator.requirePositiveIntegerVnd(new BigDecimal("100000"), "Amount"));
    }

    @Test
    @DisplayName("từ chối null — message chứa tên trường")
    void rejectsNull() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> MoneyValidator.requirePositiveIntegerVnd(null, "Bid amount"));
      assertTrue(ex.getMessage().contains("Bid amount"));
    }

    @Test
    @DisplayName("từ chối số 0")
    void rejectsZero() {
      assertThrows(
          IllegalArgumentException.class,
          () -> MoneyValidator.requirePositiveIntegerVnd(BigDecimal.ZERO, "Amount"));
    }

    @Test
    @DisplayName("từ chối số âm")
    void rejectsNegative() {
      assertThrows(
          IllegalArgumentException.class,
          () -> MoneyValidator.requirePositiveIntegerVnd(new BigDecimal("-1"), "Amount"));
    }

    @Test
    @DisplayName("từ chối số có phần thập phân (1000.5)")
    void rejectsFractional() {
      assertThrows(
          IllegalArgumentException.class,
          () -> MoneyValidator.requirePositiveIntegerVnd(new BigDecimal("1000.5"), "Amount"));
    }

    @Test
    @DisplayName("chấp nhận số nguyên biểu diễn với trailing zero (100000.00)")
    void acceptsIntegerWithTrailingZeros() {
      assertDoesNotThrow(
          () -> MoneyValidator.requirePositiveIntegerVnd(new BigDecimal("100000.00"), "Amount"));
    }
  }

  @Nested
  @DisplayName("isIntegerVnd()")
  class IsIntegerVnd {

    @Test
    @DisplayName("trả về false cho null")
    void rejectsNull() {
      assertFalse(MoneyValidator.isIntegerVnd(null));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"1", "100", "1000000", "100.00", "0", "-5"})
    @DisplayName("trả về true cho số nguyên (kể cả 0 và âm)")
    void acceptsInteger(String value) {
      assertTrue(MoneyValidator.isIntegerVnd(new BigDecimal(value)));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"0.5", "1000.50", "0.0001"})
    @DisplayName("trả về false cho số có phần lẻ")
    void rejectsFractional(String value) {
      assertFalse(MoneyValidator.isIntegerVnd(new BigDecimal(value)));
    }
  }

  @Nested
  @DisplayName("toIntegerVndExact()")
  class ToIntegerVndExact {

    @Test
    @DisplayName("chuyển đổi số nguyên sang long chính xác")
    void convertsToLong() {
      assertEquals(500_000L, MoneyValidator.toIntegerVndExact(new BigDecimal("500000"), "Amount"));
    }

    @Test
    @DisplayName("từ chối null")
    void rejectsNull() {
      assertThrows(
          IllegalArgumentException.class, () -> MoneyValidator.toIntegerVndExact(null, "Amount"));
    }

    @Test
    @DisplayName("từ chối số có phần lẻ")
    void rejectsFractional() {
      assertThrows(
          IllegalArgumentException.class,
          () -> MoneyValidator.toIntegerVndExact(new BigDecimal("1000.5"), "Amount"));
    }
  }
}
