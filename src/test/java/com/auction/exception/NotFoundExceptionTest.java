package com.auction.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Kiểm thử hợp đồng của {@link NotFoundException} — ném ra khi tài nguyên được yêu cầu (user, item,
 * auction...) không tồn tại trong hệ thống; thường ánh xạ thành HTTP 404 ở tầng API.
 *
 * <p><b>Hợp đồng được kiểm thử:</b>
 *
 * <ul>
 *   <li>Cả hai constructor (chỉ message và message + cause) truyền đúng tham số.
 *   <li>Lớp nằm đúng vị trí trong cây exception: {@code NotFoundException → AuctionException →
 *       RuntimeException}.
 *   <li>{@code toString()} chứa cả tên lớp lẫn message — quan trọng khi log không in stack trace.
 * </ul>
 *
 * <p>Không cần kết nối DB — tất cả test khởi tạo exception trực tiếp (pure unit test).
 */
class NotFoundExceptionTest {

  /** Kiểm tra constructor một tham số lưu đúng message và trả về qua {@code getMessage()}. */
  @Test
  void shouldCarryMessage() {
    NotFoundException ex = new NotFoundException("user 42 not found");
    assertEquals("user 42 not found", ex.getMessage());
  }

  /**
   * Kiểm tra constructor hai tham số: cause phải là chính xác object được truyền vào (identity
   * check qua {@code assertSame}), và message phải được bảo toàn độc lập.
   *
   * <p>Cause chaining quan trọng vì {@link NotFoundException} có thể bắt nguồn từ lỗi kết nối DB
   * chứ không phải entity thực sự không tồn tại — bảo toàn exception gốc giúp tầng trên phân biệt.
   */
  @Test
  void shouldChainCause() {
    Throwable root = new RuntimeException("DB down");
    NotFoundException ex = new NotFoundException("lookup failed", root);
    assertSame(root, ex.getCause());
    assertEquals("lookup failed", ex.getMessage());
  }

  /**
   * Kiểm tra {@link NotFoundException} là subtype của {@link AuctionException}, cho phép bắt tất cả
   * exception nghiệp vụ bằng một khối {@code catch (AuctionException e)}.
   */
  @Test
  void shouldBeAnAuctionException() {
    NotFoundException ex = new NotFoundException("x");
    assertTrue(ex instanceof AuctionException);
  }

  /**
   * Kiểm tra {@link NotFoundException} kế thừa {@link RuntimeException} — caller không bị buộc khai
   * báo trong {@code throws}. Đây là thiết kế có chủ ý cho exception nghiệp vụ.
   */
  @Test
  void shouldBeARuntimeException() {
    NotFoundException ex = new NotFoundException("x");
    assertTrue(ex instanceof RuntimeException);
  }

  /**
   * Kiểm tra {@code toString()} chứa cả tên lớp đầy đủ lẫn message — bảo vệ khỏi việc override
   * {@code toString()} vô tình làm mất thông tin khi log không in stack trace.
   */
  @Test
  void toStringShouldIncludeClassName() {
    NotFoundException ex = new NotFoundException("entity gone");
    assertTrue(ex.toString().contains("NotFoundException"));
    assertTrue(ex.toString().contains("entity gone"));
  }
}
