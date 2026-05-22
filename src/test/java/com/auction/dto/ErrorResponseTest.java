package com.auction.dto;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ErrorResponse")
class ErrorResponseTest {

  @Test
  @DisplayName("of() factory populates error, message, and current timestamp")
  void factoryPopulatesFields() {
    LocalDateTime before = LocalDateTime.now();
    ErrorResponse response = ErrorResponse.of("INVALID_BID", "Giá đặt quá thấp");
    LocalDateTime after = LocalDateTime.now();

    assertEquals("INVALID_BID", response.getError());
    assertEquals("Giá đặt quá thấp", response.getMessage());
    assertNotNull(response.getTimestamp());
    assertFalse(response.getTimestamp().isBefore(before));
    assertFalse(response.getTimestamp().isAfter(after));
  }

  @Test
  @DisplayName("constructor with args sets timestamp within the last second")
  void constructorSetsTimestamp() {
    ErrorResponse response = new ErrorResponse("NOT_FOUND", "missing");
    Duration sinceCreation = Duration.between(response.getTimestamp(), LocalDateTime.now());
    assertTrue(sinceCreation.getSeconds() < 2);
  }

  @Test
  @DisplayName("default constructor leaves all fields null")
  void defaultConstructorEmpty() {
    ErrorResponse response = new ErrorResponse();
    assertNull(response.getError());
    assertNull(response.getMessage());
    assertNull(response.getTimestamp());
  }
}
