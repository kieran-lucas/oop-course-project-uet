package com.auction.pattern.state;

import com.auction.exception.AuctionClosedException;
import com.auction.model.Auction;
import java.math.BigDecimal;

/**
 * Trạng thái FINISHED — phiên đấu giá đã kết thúc, đang chờ thanh toán.
 *
 * <p>Đây là trạng thái <em>trung gian</em> nằm giữa {@link RunningState} và
 * {@link PaidState}. Tại thời điểm chuyển sang FINISHED, phiên đã xác định được người
 * thắng cuộc thông qua trường {@code leadingBidderId} của {@link Auction}, nhưng giao
 * dịch tài chính giữa người thắng và người bán vẫn chưa hoàn tất.
 *
 * <h2>Hành động được phép / bị từ chối</h2>
 *
 * <table border="1">
 *   <caption>Quy tắc xử lý hành động trong FinishedState</caption>
 *   <tr><th>Hành động</th><th>Kết quả</th></tr>
 *   <tr><td>{@code placeBid()}</td><td>❌ "Phiên đã kết thúc"</td></tr>
 *   <tr><td>{@code edit()}</td><td>❌ "Phiên đã kết thúc"</td></tr>
 *   <tr><td>{@code extend()}</td><td>❌ "Phiên đã kết thúc"</td></tr>
 *   <tr><td>{@code close()}</td><td>✅ Cho phép — chuyển sang PAID</td></tr>
 * </table>
 *
 * <p><b>Chuyển trạng thái:</b> FINISHED → PAID khi seller xác nhận đã nhận được khoản
 * thanh toán từ người thắng cuộc.
 */
public class FinishedState implements AuctionState {

    /** Mẫu thông điệp lỗi dùng chung; tham số thứ hai là tên hành động bị chặn. */
    private static final String ERROR_MSG_TEMPLATE =
        "Phiên đấu giá #%d đã kết thúc. Không thể %s.";

    /**
     * {@inheritDoc}
     *
     * @throws AuctionClosedException luôn luôn ném — phiên đã đóng, không nhận giá mới
     */
    @Override
    public void placeBid(Auction auction, BigDecimal amount, Long bidderId) {
        throw new AuctionClosedException(
            String.format(ERROR_MSG_TEMPLATE, auction.getId(), "đặt giá")
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p>Cho phép chuyển trạng thái sang {@code PAID} sau khi quá trình thanh toán đã
     * hoàn tất. Method này chỉ đóng vai trò validate; việc cập nhật trạng thái thực sự
     * do {@code AuctionService} đảm nhiệm.
     */
    @Override
    public void close(Auction auction) {
        // FinishedState cho phép close → PAID. AuctionService sẽ set status = "PAID".
    }

    /**
     * {@inheritDoc}
     *
     * @throws AuctionClosedException luôn luôn ném — thông tin phiên đã chốt sau khi kết thúc
     */
    @Override
    public void edit(Auction auction) {
        throw new AuctionClosedException(
            String.format(ERROR_MSG_TEMPLATE, auction.getId(), "chỉnh sửa")
        );
    }

    /**
     * {@inheritDoc}
     *
     * @throws AuctionClosedException luôn luôn ném — anti-sniping không áp dụng sau kết thúc
     */
    @Override
    public void extend(Auction auction, long extraSeconds) {
        throw new AuctionClosedException(
            String.format(ERROR_MSG_TEMPLATE, auction.getId(), "gia hạn")
        );
    }
}
