package com.auction.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Kiểm thử {@link NotificationFormat} — tiện ích bao bọc username và tên phiên đấu giá bằng ký tự
 * phân cách để client JavaFX tô màu tương ứng.
 *
 * <p>Hai nhóm test:
 *
 * <ul>
 *   <li>{@code user()} — bao username trong guillemet {@code «»}
 *   <li>{@code auctionName()} — bao tên phiên trong dấu ngoặc vuông {@code []}
 * </ul>
 */
@DisplayName("NotificationFormat")
class NotificationFormatTest {

  @Test
  @DisplayName("bao username thường trong guillemet")
  void wrapsUsername() {
    assertEquals("«alice»", NotificationFormat.user("alice"));
  }

  @Test
  @DisplayName("bao username unicode (tiếng Việt) trong guillemet")
  void wrapsUnicodeUsername() {
    assertEquals("«người_dùng»", NotificationFormat.user("người_dùng"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "   ", "\t"})
  @DisplayName("fallback 'Người dùng' khi input null/blank")
  void fallsBackForBlank(String input) {
    assertEquals("«Người dùng»", NotificationFormat.user(input));
  }

  @Test
  @DisplayName("hằng số guillemet khớp với ký tự Unicode đã định nghĩa")
  void exposesGuillemetConstants() {
    assertEquals('«', NotificationFormat.USER_OPEN);
    assertEquals('»', NotificationFormat.USER_CLOSE);
  }

  @Test
  @DisplayName("auctionName dùng tên sản phẩm khi có")
  void auctionNameUsesItemName() {
    assertEquals("[iPhone 15]", NotificationFormat.auctionName(7L, "iPhone 15"));
  }

  @Test
  @DisplayName("auctionName cắt khoảng trắng thừa ở đầu/cuối tên sản phẩm")
  void auctionNameTrimsItemName() {
    assertEquals("[iPhone]", NotificationFormat.auctionName(7L, "  iPhone  "));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\n"})
  @DisplayName("auctionName fallback '[#id]' khi tên sản phẩm blank/null")
  void auctionNameFallsBackToId(String input) {
    assertEquals("[#42]", NotificationFormat.auctionName(42L, input));
  }
}
