package com.auction.dto;

import java.time.LocalDateTime;

/**
 * DTO trả về khi server gặp lỗi — thay vì trả stacktrace Java (lộ nội bộ hệ thống), server trả
 * JSON chuẩn chứa mã lỗi và thông báo dễ hiểu.
 *
 * <p>Mapping giữa Custom Exception → HTTP status → ErrorResponse:
 * <table>
 *   <tr><th>Exception</th><th>HTTP Status</th><th>error field</th><th>Ví dụ message</th></tr>
 *   <tr><td>InvalidBidException</td><td>400 Bad Request</td><td>INVALID_BID</td>
 *       <td>Giá đặt phải cao hơn giá hiện tại 500,000đ</td></tr>
 *   <tr><td>AuctionClosedException</td><td>400 Bad Request</td><td>AUCTION_CLOSED</td>
 *       <td>Phiên đấu giá đã kết thúc</td></tr>
 *   <tr><td>UnauthorizedException</td><td>401 Unauthorized</td><td>UNAUTHORIZED</td>
 *       <td>Token không hợp lệ hoặc đã hết hạn</td></tr>
 *   <tr><td>NotFoundException</td><td>404 Not Found</td><td>NOT_FOUND</td>
 *       <td>Không tìm thấy phiên đấu giá với ID 99</td></tr>
 *   <tr><td>DuplicateException</td><td>409 Conflict</td><td>DUPLICATE</td>
 *       <td>Username "alice" đã tồn tại</td></tr>
 *   <tr><td>Exception (catch-all)</td><td>500 Internal</td><td>INTERNAL_ERROR</td>
 *       <td>Lỗi hệ thống, vui lòng thử lại sau</td></tr>
 * </table>
 *
 * <p>Cấu hình trong App.java (Javalin exception handler):
 * <pre>
 * app.exception(InvalidBidException.class, (e, ctx) -&gt; {
 *     ctx.status(400);
 *     ctx.json(ErrorResponse.of("INVALID_BID", e.getMessage()));
 * });
 * </pre>
 *
 * <p>Client nhận JSON lỗi → parse ErrorResponse → hiển thị message trên UI (ví dụ: Label đỏ
 * "Giá đặt phải cao hơn giá hiện tại").
 *
 * <p>Field timestamp giúp debug: khi user báo lỗi, có thể đối chiếu thời gian với server log.
 */
public class ErrorResponse {

  private String error;
  private String message;
  private LocalDateTime timestamp;

  public ErrorResponse() {}

  public ErrorResponse(String error, String message) {
    this.error = error;
    this.message = message;
    this.timestamp = LocalDateTime.now();
  }

  /**
   * Factory method tạo ErrorResponse nhanh — dùng trong Javalin exception handler.
   *
   * <p>Ví dụ: {@code ctx.json(ErrorResponse.of("INVALID_BID", "Giá phải cao hơn 500,000đ"));}
   *
   * @param error   mã lỗi ngắn gọn (client dùng để switch/case xử lý logic)
   * @param message thông báo chi tiết (hiển thị cho user đọc)
   * @return ErrorResponse với timestamp = thời điểm hiện tại
   */
  public static ErrorResponse of(String error, String message) {
    return new ErrorResponse(error, message);
  }

  // === Getters & Setters ===

  public String getError() {
    return error;
  }

  public void setError(String error) {
    this.error = error;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public LocalDateTime getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(LocalDateTime timestamp) {
    this.timestamp = timestamp;
  }

  @Override
  public String toString() {
    return "Error{" + error + ": " + message + "}";
  }
}
