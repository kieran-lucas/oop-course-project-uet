package com.auction.exception;

/**
 * Base class cho tất cả custom exception trong auction domain.
 *
 * <p>Tất cả exception nghiệp vụ ({@link InvalidBidException}, {@link AuctionClosedException},
 * {@link NotFoundException}, {@link DuplicateException}, {@link UnauthorizedException}) đều kế thừa
 * class này, cho phép caller bắt toàn bộ auction exception chỉ với một catch block:
 *
 * <pre>{@code
 * try {
 *     auctionService.placeBid(bid);
 * } catch (AuctionException e) {
 *     // bắt được tất cả custom exception trong com.auction.exception
 *     log.error("Auction operation failed: {}", e.getMessage(), e);
 *     return errorResponse(e);
 * }
 * }</pre>
 *
 * <p>Class này là {@code abstract} — chỉ khởi tạo các subclass cụ thể của nó.
 *
 * <p>Kế thừa {@link RuntimeException} (thay vì checked {@link Exception}) là có chủ đích: các
 * auction exception thường biểu thị vi phạm nghiệp vụ hoặc lỗi lập trình mà caller không thể xử lý
 * hợp lý tại mọi call site — bắt buộc khai báo {@code throws} khắp nơi chỉ thêm nhiễu mà không mang
 * lại giá trị thực tế.
 */
public abstract class AuctionException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Khởi tạo AuctionException với message mô tả lỗi.
   *
   * @param message mô tả business rule bị vi phạm
   */
  protected AuctionException(String message) {
    super(message);
  }

  /**
   * Khởi tạo AuctionException với message và nguyên nhân gốc.
   *
   * @param message mô tả business rule bị vi phạm
   * @param cause exception gốc dẫn đến lỗi này (ví dụ: SQLException)
   */
  protected AuctionException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Trả về chuỗi mô tả phù hợp để ghi log: {@code [TênClass] message}
   *
   * @return chuỗi mô tả exception
   */
  @Override
  public String toString() {
    return "[" + getClass().getSimpleName() + "] " + (getMessage() == null ? "" : getMessage());
  }
}
