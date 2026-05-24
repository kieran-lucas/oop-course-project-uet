package com.auction.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Kiểm thử hợp đồng của {@link AuctionClosedException} — ném ra khi đặt giá vào phiên đã kết thúc
 * hoặc bị đóng bởi scheduler.
 *
 * <p><b>Hợp đồng được kiểm thử:</b>
 *
 * <ul>
 *   <li>Cả hai constructor (chỉ message và message + cause) truyền đúng tham số.
 *   <li>Lớp nằm đúng vị trí trong cây exception: {@code AuctionClosedException → AuctionException →
 *       RuntimeException}.
 *   <li>{@code toString()} chứa cả tên lớp lẫn message — quan trọng khi log không in stack trace.
 * </ul>
 *
 * <p>Không cần kết nối DB — tất cả test khởi tạo exception trực tiếp (pure unit test).
 */
class AuctionClosedExceptionTest {

  /** Kiểm tra constructor một tham số lưu đúng message và trả về qua {@code getMessage()}. */
  @Test
  void shouldCarryMessage() {
    AuctionClosedException ex = new AuctionClosedException("auction 1 closed");
    assertEquals("auction 1 closed", ex.getMessage());
  }

  /**
   * Kiểm tra constructor hai tham số: cause phải là chính xác object được truyền vào (identity
   * check qua {@code assertSame}), và message phải được bảo toàn độc lập với cause.
   *
   * <p>Cause chaining quan trọng vì nếu không có nó, lỗi gốc từ scheduler sẽ bị nuốt mất khi {@code
   * AuctionClosedException} được bắt ở tầng trên.
   */
  @Test
  void shouldChainCause() {
    Throwable root = new RuntimeException("scheduler error");
    AuctionClosedException ex = new AuctionClosedException("close failed", root);
    assertSame(root, ex.getCause());
    assertEquals("close failed", ex.getMessage());
  }

  /**
   * Kiểm tra {@link AuctionClosedException} là subtype của {@link AuctionException}, cho phép bắt
   * tất cả exception nghiệp vụ bằng một khối {@code catch (AuctionException e)}.
   */
  @Test
  void shouldBeAnAuctionException() {
    AuctionClosedException ex = new AuctionClosedException("x");
    assertTrue(ex instanceof AuctionException);
  }

  /**
   * Kiểm tra {@link AuctionClosedException} kế thừa {@link RuntimeException} — caller không bị buộc
   * khai báo trong {@code throws}. Đây là thiết kế có chủ ý cho exception nghiệp vụ.
   */
  @Test
  void shouldBeARuntimeException() {
    AuctionClosedException ex = new AuctionClosedException("x");
    assertTrue(ex instanceof RuntimeException);
  }

  /**
   * Kiểm tra {@code toString()} chứa cả tên lớp đầy đủ lẫn message — bảo vệ khỏi việc override
   * {@code toString()} vô tình làm mất thông tin khi log không in stack trace.
   */
  @Test
  void toStringShouldIncludeClassName() {
    AuctionClosedException ex = new AuctionClosedException("ended");
    assertTrue(ex.toString().contains("AuctionClosedException"));
    assertTrue(ex.toString().contains("ended"));
  }
}
