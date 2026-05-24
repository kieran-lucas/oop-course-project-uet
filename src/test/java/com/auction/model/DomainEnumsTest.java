package com.auction.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Kiểm thử ba enum nghiệp vụ: {@link AuctionStatus}, {@link AutoBidStatus}, {@link
 * AutoBidFailureReason}. Mỗi enum cung cấp phương thức {@code from(String)} để parse từ chuỗi trong
 * DB — được ki���m thử kỹ ở đây.
 */
@DisplayName("Domain enums")
class DomainEnumsTest {

  @Nested
  @DisplayName("AuctionStatus.from()")
  class AuctionStatusTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"OPEN", "open", "Open", " OPEN "})
    @DisplayName("parse OPEN không phân biệt hoa thường (sau khi trim)")
    void parsesOpen(String input) {
      // from() không tự trim — caller phải trim trước (như AuctionMapper làm)
      assertEquals(AuctionStatus.OPEN, AuctionStatus.from(input.trim()));
    }

    @Test
    @DisplayName("parse được mọi giá trị enum")
    void parsesAllValues() {
      for (AuctionStatus expected : AuctionStatus.values()) {
        assertEquals(expected, AuctionStatus.from(expected.name()));
      }
    }

    @Test
    @DisplayName("ném IllegalArgumentException cho giá trị không biết")
    void rejectsUnknown() {
      assertThrows(IllegalArgumentException.class, () -> AuctionStatus.from("UNKNOWN"));
    }
  }

  @Nested
  @DisplayName("AutoBidStatus.from()")
  class AutoBidStatusTest {

    @Test
    @DisplayName("trả về ACTIVE cho null và chuỗi trống/blank")
    void defaultsToActive() {
      assertEquals(AutoBidStatus.ACTIVE, AutoBidStatus.from(null));
      assertEquals(AutoBidStatus.ACTIVE, AutoBidStatus.from(""));
      assertEquals(AutoBidStatus.ACTIVE, AutoBidStatus.from("   "));
    }

    @Test
    @DisplayName("parse được mọi trạng thái")
    void parsesAll() {
      for (AutoBidStatus expected : AutoBidStatus.values()) {
        assertEquals(expected, AutoBidStatus.from(expected.name()));
      }
    }

    @Test
    @DisplayName("ném IllegalArgumentException cho trạng thái không biết")
    void rejectsUnknown() {
      assertThrows(IllegalArgumentException.class, () -> AutoBidStatus.from("RUNNING"));
    }
  }

  @Nested
  @DisplayName("AutoBidFailureReason.from()")
  class FailureReasonTest {

    @Test
    @DisplayName("trả về null cho null và chuỗi trống/blank")
    void returnsNullForBlank() {
      assertNull(AutoBidFailureReason.from(null));
      assertNull(AutoBidFailureReason.from(""));
      assertNull(AutoBidFailureReason.from("   "));
    }

    @Test
    @DisplayName("parse được mọi lý do thất bại")
    void parsesAll() {
      for (AutoBidFailureReason expected : AutoBidFailureReason.values()) {
        assertEquals(expected, AutoBidFailureReason.from(expected.name()));
      }
    }

    @Test
    @DisplayName("ném IllegalArgumentException cho lý do không biết")
    void rejectsUnknown() {
      assertThrows(IllegalArgumentException.class, () -> AutoBidFailureReason.from("UNKNOWN"));
    }
  }
}
