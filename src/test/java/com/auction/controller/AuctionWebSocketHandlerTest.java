package com.auction.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.auth0.jwt.interfaces.DecodedJWT;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AuctionWebSocketHandler token expiration")
class AuctionWebSocketHandlerTest {

  @Test
  @DisplayName("Expired token schedules immediate WebSocket close")
  void expiredTokenSchedulesImmediateClose() {
    Instant now = Instant.parse("2026-05-15T00:00:00Z");
    DecodedJWT decoded = mock(DecodedJWT.class);
    when(decoded.getExpiresAtAsInstant()).thenReturn(now.minusSeconds(1));

    long delayMs = AuctionWebSocketHandler.millisUntilExpiration(decoded, now);

    assertEquals(0L, delayMs);
  }

  @Test
  @DisplayName("Future token schedules close at expiration")
  void futureTokenSchedulesCloseAtExpiration() {
    Instant now = Instant.parse("2026-05-15T00:00:00Z");
    DecodedJWT decoded = mock(DecodedJWT.class);
    when(decoded.getExpiresAtAsInstant()).thenReturn(now.plusSeconds(30));

    long delayMs = AuctionWebSocketHandler.millisUntilExpiration(decoded, now);

    assertTrue(delayMs >= 30_000L);
    assertTrue(delayMs < 31_000L);
  }
}
