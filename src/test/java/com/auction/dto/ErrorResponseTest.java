package com.auction.dto;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Kiểm thử {@link ErrorResponse} — DTO trả về lỗi từ API với error code, message và timestamp. */
@DisplayName("ErrorResponse")
class ErrorResponseTest {

  @Test
  @DisplayName("factory of() điền đúng error, message và timestamp hiện tại")
  void factoryPopulatesFields() {
    LocalDateTime before = LocalDateTime.now();
    ErrorResponse response = ErrorResponse.of("INVALID_BID", "Giá đặt quá thấp");
    LocalDateTime after = LocalDateTime.now();

    assertEquals("INVALID_BID", response.getError());
    assertEquals("Giá đặt quá thấp", response.getMessage());
    assertNotNull(response.getTimestamp());
    // timestamp phải nằm trong khoảng [before, after]
    assertFalse(response.getTimestamp().isBefore(before));
    assertFalse(response.getTimestamp().isAfter(after));
  }

  @Test
  @DisplayName("constructor v��i tham số đặt timestamp trong vòng dưới 2 giây")
  void constructorSetsTimestamp() {
    ErrorResponse response = new ErrorResponse("NOT_FOUND", "missing");
    Duration sinceCreation = Duration.between(response.getTimestamp(), LocalDateTime.now());
    assertTrue(sinceCreation.getSeconds() < 2);
  }

  @Test
  @DisplayName("constructor mặc định để tất cả trường là null")
  void defaultConstructorEmpty() {
    ErrorResponse response = new ErrorResponse();
    assertNull(response.getError());
    assertNull(response.getMessage());
    assertNull(response.getTimestamp());
  }
}
