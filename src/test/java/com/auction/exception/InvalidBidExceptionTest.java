package com.auction.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Kiểm thử hợp đồng của {@link InvalidBidException} — ném ra khi giá đặt vi phạm quy tắc nghiệp vụ,
 * ví dụ thấp hơn giá hiện tại, thấp hơn increment tối thiểu, hoặc là số âm.
 *
 * <p><b>Hợp đồng được kiểm thử:</b>
 *
 * <ul>
 *   <li>Cả hai constructor (chỉ message và message + cause) truyền đúng tham số.
 *   <li>Lớp nằm đúng vị trí trong cây exception: {@code InvalidBidException → AuctionException →
 *       RuntimeException}.
 *   <li>{@code toString()} chứa cả tên lớp lẫn message — quan trọng khi log không in stack trace.
 * </ul>
 *
 * <p>Không cần kết nối DB — tất cả test khởi tạo exception trực tiếp (pure unit test).
 */
class InvalidBidExceptionTest {

  /** Kiểm tra constructor một tham số lưu đúng message và trả về qua {@code getMessage()}. */
  @Test
  void shouldCarryMessage() {
    InvalidBidException ex = new InvalidBidException("bid too low");
    assertEquals("bid too low", ex.getMessage());
  }

  /**
   * Kiểm tra constructor hai tham số: cause phải là chính xác object được truyền vào (identity
   * check qua {@code assertSame}), và message phải được bảo toàn độc lập.
   *
   * <p>Cause chaining hữu ích khi {@link InvalidBidException} bọc exception validation cấp thấp (ví
   * dụ: {@link IllegalArgumentException} từ bộ parse input), giữ nguyên context gốc để debug.
   */
  @Test
  void shouldChainCause() {
    Throwable root = new IllegalArgumentException("negative amount");
    InvalidBidException ex = new InvalidBidException("validation failed", root);
    assertSame(root, ex.getCause());
    assertEquals("validation failed", ex.getMessage());
  }

  /**
   * Kiểm tra {@link InvalidBidException} là subtype của {@link AuctionException}, cho phép bắt tất
   * cả exception nghiệp vụ bằng một khối {@code catch (AuctionException e)}.
   */
  @Test
  void shouldBeAnAuctionException() {
    InvalidBidException ex = new InvalidBidException("x");
    assertTrue(ex instanceof AuctionException);
  }

  /**
   * Kiểm tra {@link InvalidBidException} kế thừa {@link RuntimeException} — caller không bị buộc
   * khai báo trong {@code throws}. Đây là thiết kế có chủ ý cho exception nghiệp vụ.
   */
  @Test
  void shouldBeARuntimeException() {
    InvalidBidException ex = new InvalidBidException("x");
    assertTrue(ex instanceof RuntimeException);
  }

  /**
   * Kiểm tra {@code toString()} chứa cả tên lớp đầy đủ lẫn message — bảo vệ khỏi việc override
   * {@code toString()} vô tình làm mất thông tin khi log không in stack trace.
   */
  @Test
  void toStringShouldIncludeClassName() {
    InvalidBidException ex = new InvalidBidException("invalid");
    assertTrue(ex.toString().contains("InvalidBidException"));
    assertTrue(ex.toString().contains("invalid"));
  }
}
