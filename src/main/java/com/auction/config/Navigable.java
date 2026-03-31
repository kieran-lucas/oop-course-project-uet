package com.auction.config;

/**
 * Interface lifecycle cho các FXML Controller.
 * SceneManager gọi tự động khi chuyển màn hình.
 *
 * Dùng default method → controller chỉ override method nào cần,
 * không bắt buộc implement cả 3.
 */
public interface Navigable {

    /**
     * Gọi mỗi khi navigate TỚI màn hình này.
     * Dùng để refresh data, cập nhật UI.
     * Ví dụ: AuctionListController → gọi lại API lấy danh sách mới.
     */
    default void onNavigatedTo() {}

    /**
     * Nhận data từ màn hình trước truyền sang.
     * Ví dụ: auction-list truyền auctionId → auction-detail.
     *
     * @param data dữ liệu bất kỳ, controller tự cast về đúng kiểu
     */
    default void onDataReceived(Object data) {}

    /**
     * Gọi khi RỜI KHỎI màn hình (navigate đi chỗ khác).
     * Dùng để cleanup: đóng WebSocket, hủy Timer, dừng animation...
     */
    default void onNavigatedFrom() {}
}