package com.auction.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("NotificationFormat")
class NotificationFormatTest {

  @Test
  @DisplayName("wraps a normal username in guillemets")
  void wrapsUsername() {
    assertEquals("«alice»", NotificationFormat.user("alice"));
  }

  @Test
  @DisplayName("preserves unicode username inside guillemets")
  void wrapsUnicodeUsername() {
    assertEquals("«người_dùng»", NotificationFormat.user("người_dùng"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "   ", "\t"})
  @DisplayName("falls back to 'Người dùng' for null/blank input")
  void fallsBackForBlank(String input) {
    assertEquals("«Người dùng»", NotificationFormat.user(input));
  }

  @Test
  @DisplayName("guillemet constants match documented unicode characters")
  void exposesGuillemetConstants() {
    assertEquals('\u00AB', NotificationFormat.USER_OPEN);
    assertEquals('\u00BB', NotificationFormat.USER_CLOSE);
  }

  @Test
  @DisplayName("auctionName uses item name when present")
  void auctionNameUsesItemName() {
    assertEquals("[iPhone 15]", NotificationFormat.auctionName(7L, "iPhone 15"));
  }

  @Test
  @DisplayName("auctionName trims surrounding whitespace from item name")
  void auctionNameTrimsItemName() {
    assertEquals("[iPhone]", NotificationFormat.auctionName(7L, "  iPhone  "));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\n"})
  @DisplayName("auctionName falls back to '[#id]' when item name is blank")
  void auctionNameFallsBackToId(String input) {
    assertEquals("[#42]", NotificationFormat.auctionName(42L, input));
  }
}
