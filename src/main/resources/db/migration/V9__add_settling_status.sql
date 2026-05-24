-- ============================================================
-- Migration V9: Thêm trạng thái SETTLING vào auctions
-- Mô tả : SETTLING là trạng thái trung gian ngắn ngủi được dùng
--          để khóa phiên đấu giá trong lúc hệ thống chốt kết quả.
--          Khi AuctionScheduler phát hiện phiên hết giờ, nó chuyển
--          phiên sang SETTLING trước khi thực hiện các bước settlement
--          (xác định winner, chuyển tiền, cập nhật item status).
--
--          Cơ chế này tránh race condition trong môi trường multi-thread:
--          chỉ thread nào chuyển được phiên sang SETTLING thành công
--          (thông qua UPDATE với WHERE status = 'RUNNING') mới tiếp tục
--          xử lý — các thread còn lại tự động bỏ qua.
--
--         Vòng đời đầy đủ sau migration này:
--           OPEN → RUNNING → SETTLING → FINISHED → PAID
--                                    ↘ CANCELED
-- ============================================================

-- Bỏ constraint cũ chưa có SETTLING rồi tạo lại với đầy đủ 6 trạng thái
ALTER TABLE auctions DROP CONSTRAINT IF EXISTS auctions_status_check;
ALTER TABLE auctions ADD CONSTRAINT auctions_status_check
    CHECK (status IN ('OPEN', 'RUNNING', 'SETTLING', 'FINISHED', 'PAID', 'CANCELED'));
