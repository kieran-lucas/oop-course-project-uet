/**
 * Hệ thống exception tùy chỉnh cho miền nghiệp vụ đấu giá
 *
 * <p>Tất cả các exception đều kế thừa từ {@link com.auction.exception.AuctionException} để đảm bảo
 * việc xử lý lỗi theo domain được thống nhất
 *
 * <p><b>Các loại exception:</b>
 *
 * <ul>
 *   <li>{@link com.auction.exception.NotFoundException} — lỗi không tìm thấy thực thể
 *   <li>{@link com.auction.exception.DuplicateException} — vi phạm ràng buộc duy nhất
 *   <li>{@link com.auction.exception.InvalidBidException} — lỗi kiểm tra giá thầu
 *   <li>{@link com.auction.exception.AuctionClosedException} — thao tác trên phiên đấu giá đã đóng
 *   <li>{@link com.auction.exception.UnauthorizedException} — từ chối quyền truy cập
 * </ul>
 *
 * <p><b>Mẫu sử dụng — bắt toàn bộ lỗi đấu giá:</b>
 *
 * <pre>{@code
 * try {
 *     auctionService.createAuction(request, userId, role);
 * } catch (AuctionException e) {
 *     // Một catch duy nhất xử lý cả 5 loại exception
 *     return ApiResponse.error(e.getMessage());
 * }
 * }</pre>
 *
 * <p><b>Lý do dùng RuntimeException:</b> Tất cả exception trong đấu giá đều là unchecked vì chúng
 * thường đại diện cho vi phạm logic nghiệp vụ hoặc lỗi lập trình. Việc bắt buộc khai báo {@code
 * throws} sẽ làm code rườm rà mà không mang lại lợi ích thực tế
 */
package com.auction.exception;
