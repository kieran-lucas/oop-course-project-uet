package com.auction.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.auth0.jwt.interfaces.DecodedJWT;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Kiểm tra logic tính toán thời gian hết hạn token trong {@link AuctionWebSocketHandler}.
 *
 * <p>Phương thức {@code millisUntilExpiration()} quyết định khi nào đóng kết nối WebSocket sau khi
 * token hết hạn. Test dùng mock {@link com.auth0.jwt.interfaces.DecodedJWT} để kiểm soát thời gian
 * hết hạn mà không cần tạo token thật.
 */
@DisplayName("AuctionWebSocketHandler — tính toán thời gian hết hạn token")
class AuctionWebSocketHandlerTest {

  @Test
  @DisplayName("Token đã hết hạn → lên lịch đóng WebSocket ngay lập tức (delay = 0)")
  void expiredTokenSchedulesImmediateClose() {
    Instant now = Instant.parse("2026-05-15T00:00:00Z");
    DecodedJWT decoded = mock(DecodedJWT.class);
    when(decoded.getExpiresAtAsInstant()).thenReturn(now.minusSeconds(1));

    long delayMs = AuctionWebSocketHandler.millisUntilExpiration(decoded, now);

    assertEquals(0L, delayMs);
  }

  @Test
  @DisplayName("Token còn hạn → lên lịch đóng WebSocket đúng tại thời điểm hết hạn")
  void futureTokenSchedulesCloseAtExpiration() {
    Instant now = Instant.parse("2026-05-15T00:00:00Z");
    DecodedJWT decoded = mock(DecodedJWT.class);
    when(decoded.getExpiresAtAsInstant()).thenReturn(now.plusSeconds(30));

    long delayMs = AuctionWebSocketHandler.millisUntilExpiration(decoded, now);

    assertTrue(delayMs >= 30_000L);
    assertTrue(delayMs < 31_000L);
  }
}
