package com.auction.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Kiểm thử hợp đồng của {@link DuplicateException} — ném ra khi thao tác cố tạo tài nguyên đã tồn
 * tại, ví dụ đăng ký username hoặc email vi phạm ràng buộc UNIQUE trong DB.
 *
 * <p><b>Hợp đồng được kiểm thử:</b>
 *
 * <ul>
 *   <li>Cả hai constructor (chỉ message và message + cause) truyền đúng tham số.
 *   <li>Lớp nằm đúng vị trí trong cây exception: {@code DuplicateException → AuctionException →
 *       RuntimeException}.
 *   <li>{@code toString()} chứa cả tên lớp lẫn message — quan trọng khi log không in stack trace.
 * </ul>
 *
 * <p>Không cần kết nối DB — tất cả test khởi tạo exception trực tiếp (pure unit test).
 */
class DuplicateExceptionTest {

  /** Kiểm tra constructor một tham số lưu đúng message và trả về qua {@code getMessage()}. */
  @Test
  void shouldCarryMessage() {
    DuplicateException ex = new DuplicateException("email exists");
    assertEquals("email exists", ex.getMessage());
  }

  /**
   * Kiểm tra constructor hai tham số: cause phải là chính xác object được truyền vào (identity
   * check qua {@code assertSame}), và message phải được bảo toàn độc lập.
   *
   * <p>Cause chaining quan trọng vì {@link DuplicateException} thường bắt nguồn từ lỗi JDBC
   * constraint violation ở tầng thấp — bọc exception gốc giúp tầng trên có thể kiểm tra nguyên
   * nhân.
   */
  @Test
  void shouldChainCause() {
    Throwable root = new RuntimeException("constraint violation");
    DuplicateException ex = new DuplicateException("duplicate detected", root);
    assertSame(root, ex.getCause());
    assertEquals("duplicate detected", ex.getMessage());
  }

  /**
   * Kiểm tra {@link DuplicateException} là subtype của {@link AuctionException}, cho phép bắt tất
   * cả exception nghiệp vụ bằng một khối {@code catch (AuctionException e)}.
   */
  @Test
  void shouldBeAnAuctionException() {
    DuplicateException ex = new DuplicateException("x");
    assertTrue(ex instanceof AuctionException);
  }

  /**
   * Kiểm tra {@link DuplicateException} kế thừa {@link RuntimeException} — caller không bị buộc
   * khai báo trong {@code throws}. Đây là thiết kế có chủ ý cho exception nghiệp vụ.
   */
  @Test
  void shouldBeARuntimeException() {
    DuplicateException ex = new DuplicateException("x");
    assertTrue(ex instanceof RuntimeException);
  }

  /**
   * Kiểm tra {@code toString()} chứa cả tên lớp đầy đủ lẫn message — bảo vệ khỏi việc override
   * {@code toString()} vô tình làm mất thông tin khi log không in stack trace.
   */
  @Test
  void toStringShouldIncludeClassName() {
    DuplicateException ex = new DuplicateException("conflict");
    assertTrue(ex.toString().contains("DuplicateException"));
    assertTrue(ex.toString().contains("conflict"));
  }
}
