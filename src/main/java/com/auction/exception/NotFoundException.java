package com.auction.exception;

/**
 * Thrown khi entity được yêu cầu (User, Item, Auction, Bid, ...) không tồn tại trong data source.
 *
 * <p>Cách dùng điển hình:
 *
 * <pre>{@code
 * Auction auction = auctionDao.findById(id)
 *     .orElseThrow(() -> new NotFoundException("Auction not found with id: " + id));
 * }</pre>
 *
 * @see AuctionException
 */
public class NotFoundException extends AuctionException {

  private static final long serialVersionUID = 1L;

  /**
   * Khởi tạo NotFoundException với message mô tả entity không tìm thấy.
   *
   * @param message mô tả entity nào không tìm thấy
   */
  public NotFoundException(String message) {
    super(message);
  }

  /**
   * Khởi tạo NotFoundException với message và nguyên nhân gốc.
   *
   * @param message mô tả entity nào không tìm thấy
   * @param cause exception gốc dẫn đến lỗi này (ví dụ: SQLException)
   */
  public NotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
