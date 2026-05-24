package com.auction.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Kiểm thử hợp đồng của {@link UnauthorizedException} — ném ra khi caller không có quyền thực hiện
 * hành động, ví dụ đặt giá dưới danh tính người khác, sửa item không thuộc quyền sở hữu, hoặc trình
 * JWT hết hạn/không hợp lệ. Thường ánh xạ thành HTTP 401 hoặc 403 ở tầng API.
 *
 * <p><b>Hợp đồng được kiểm thử:</b>
 *
 * <ul>
 *   <li>Cả hai constructor (chỉ message và message + cause) truyền đúng tham số.
 *   <li>Lớp nằm đúng vị trí trong cây exception: {@code UnauthorizedException → AuctionException →
 *       RuntimeException}.
 *   <li>{@code toString()} chứa cả tên lớp lẫn message — quan trọng khi log không in stack trace.
 * </ul>
 *
 * <p>Không cần kết nối DB — tất cả test khởi tạo exception trực tiếp (pure unit test).
 */
class UnauthorizedExceptionTest {

  /** Kiểm tra constructor một tham số lưu đúng message và trả về qua {@code getMessage()}. */
  @Test
  void shouldCarryMessage() {
    UnauthorizedException ex = new UnauthorizedException("forbidden");
    assertEquals("forbidden", ex.getMessage());
  }

  /**
   * Kiểm tra constructor hai tham số: cause phải là chính xác object được truyền vào (identity
   * check qua {@code assertSame}), và message phải được bảo toàn độc lập.
   *
   * <p>Cause chaining đặc biệt liên quan ở đây vì {@link UnauthorizedException} thường phát sinh từ
   * lỗi xác thực JWT ({@link SecurityException} từ thư viện token) — bọc exception gốc giúp audit
   * bảo mật có thể truy ngược về nguồn gốc thực sự.
   */
  @Test
  void shouldChainCause() {
    Throwable root = new SecurityException("token expired");
    UnauthorizedException ex = new UnauthorizedException("auth failed", root);
    assertSame(root, ex.getCause());
    assertEquals("auth failed", ex.getMessage());
  }

  /**
   * Kiểm tra {@link UnauthorizedException} là subtype của {@link AuctionException}, cho phép bắt
   * tất cả exception nghiệp vụ bằng một khối {@code catch (AuctionException e)}.
   */
  @Test
  void shouldBeAnAuctionException() {
    UnauthorizedException ex = new UnauthorizedException("x");
    assertTrue(ex instanceof AuctionException);
  }

  /**
   * Kiểm tra {@link UnauthorizedException} kế thừa {@link RuntimeException} — caller không bị buộc
   * khai báo trong {@code throws}. Đây là thiết kế có chủ ý cho exception nghiệp vụ.
   */
  @Test
  void shouldBeARuntimeException() {
    UnauthorizedException ex = new UnauthorizedException("x");
    assertTrue(ex instanceof RuntimeException);
  }

  /**
   * Kiểm tra {@code toString()} chứa cả tên lớp đầy đủ lẫn message — bảo vệ khỏi việc override
   * {@code toString()} vô tình làm mất thông tin khi log không in stack trace.
   */
  @Test
  void toStringShouldIncludeClassName() {
    UnauthorizedException ex = new UnauthorizedException("denied");
    assertTrue(ex.toString().contains("UnauthorizedException"));
    assertTrue(ex.toString().contains("denied"));
  }
}
