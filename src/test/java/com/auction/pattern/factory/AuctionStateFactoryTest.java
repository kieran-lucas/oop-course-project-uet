package com.auction.pattern.factory;

import static org.junit.jupiter.api.Assertions.*;

import com.auction.pattern.state.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Kiểm thử {@link AuctionStateFactory} — factory ánh xạ chuỗi trạng thái sang singleton {@link
 * AuctionState} tương ứng.
 *
 * <p>Xác nhận rằng:
 *
 * <ul>
 *   <li>Mỗi chuỗi trạng thái hợp lệ trả về đúng singleton từ {@link AuctionStates}.
 *   <li>Factory không phân biệt hoa thường (case-insensitive).
 *   <li>Chuỗi không hợp lệ ném {@link IllegalArgumentException} có chứa giá trị đó.
 * </ul>
 *
 * <p>Không cần kết nối DB — pure unit test.
 */
@DisplayName("AuctionStateFactory")
class AuctionStateFactoryTest {

  @Test
  @DisplayName("trả về singleton OpenState cho 'OPEN'")
  void returnsOpenState() {
    AuctionState state = AuctionStateFactory.create("OPEN");
    assertSame(AuctionStates.OPEN, state);
    assertInstanceOf(OpenState.class, state);
  }

  @Test
  @DisplayName("trả về singleton RunningState cho 'RUNNING'")
  void returnsRunningState() {
    assertSame(AuctionStates.RUNNING, AuctionStateFactory.create("RUNNING"));
  }

  @Test
  @DisplayName("trả về singleton SettlingState cho 'SETTLING'")
  void returnsSettlingState() {
    assertSame(AuctionStates.SETTLING, AuctionStateFactory.create("SETTLING"));
  }

  @Test
  @DisplayName("trả về singleton FinishedState cho 'FINISHED'")
  void returnsFinishedState() {
    assertSame(AuctionStates.FINISHED, AuctionStateFactory.create("FINISHED"));
  }

  @Test
  @DisplayName("trả về singleton PaidState cho 'PAID'")
  void returnsPaidState() {
    assertSame(AuctionStates.PAID, AuctionStateFactory.create("PAID"));
  }

  @Test
  @DisplayName("trả về singleton CanceledState cho 'CANCELED'")
  void returnsCanceledState() {
    assertSame(AuctionStates.CANCELED, AuctionStateFactory.create("CANCELED"));
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {"open", "Open", "oPeN", "running"})
  @DisplayName("chuẩn hóa input sang uppercase trước khi so khớp (case-insensitive)")
  void caseInsensitive(String input) {
    assertNotNull(AuctionStateFactory.create(input));
  }

  @Test
  @DisplayName("ném IllegalArgumentException cho trạng thái không hợp lệ")
  void rejectsUnknownStatus() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> AuctionStateFactory.create("UNKNOWN_XYZ"));
    assertTrue(ex.getMessage().contains("UNKNOWN_XYZ"));
  }
}
