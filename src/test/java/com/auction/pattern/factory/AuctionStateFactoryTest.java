package com.auction.pattern.factory;

import static org.junit.jupiter.api.Assertions.*;

import com.auction.pattern.state.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("AuctionStateFactory")
class AuctionStateFactoryTest {

  @Test
  @DisplayName("returns OpenState singleton for 'OPEN'")
  void returnsOpenState() {
    AuctionState state = AuctionStateFactory.create("OPEN");
    assertSame(AuctionStates.OPEN, state);
    assertInstanceOf(OpenState.class, state);
  }

  @Test
  @DisplayName("returns RunningState singleton for 'RUNNING'")
  void returnsRunningState() {
    assertSame(AuctionStates.RUNNING, AuctionStateFactory.create("RUNNING"));
  }

  @Test
  @DisplayName("returns SettlingState singleton for 'SETTLING'")
  void returnsSettlingState() {
    assertSame(AuctionStates.SETTLING, AuctionStateFactory.create("SETTLING"));
  }

  @Test
  @DisplayName("returns FinishedState singleton for 'FINISHED'")
  void returnsFinishedState() {
    assertSame(AuctionStates.FINISHED, AuctionStateFactory.create("FINISHED"));
  }

  @Test
  @DisplayName("returns PaidState singleton for 'PAID'")
  void returnsPaidState() {
    assertSame(AuctionStates.PAID, AuctionStateFactory.create("PAID"));
  }

  @Test
  @DisplayName("returns CanceledState singleton for 'CANCELED'")
  void returnsCanceledState() {
    assertSame(AuctionStates.CANCELED, AuctionStateFactory.create("CANCELED"));
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {"open", "Open", "oPeN", "running"})
  @DisplayName("normalises input to uppercase before matching")
  void caseInsensitive(String input) {
    assertNotNull(AuctionStateFactory.create(input));
  }

  @Test
  @DisplayName("throws IllegalArgumentException for unknown status")
  void rejectsUnknownStatus() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> AuctionStateFactory.create("UNKNOWN_XYZ"));
    assertTrue(ex.getMessage().contains("UNKNOWN_XYZ"));
  }
}
