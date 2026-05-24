-- ============================================================
-- Migration V10: Sửa các cột thiếu trong auto_bid_configs
-- Mô tả : Các database được tạo từ trước khi schema V1 có cột
--          active và registered_at sẽ thiếu hai cột này.
--          Migration này backfill chúng một cách an toàn (idempotent).
--
--          active        = TRUE mặc định — cấu hình auto-bid luôn
--                          bắt đầu ở trạng thái hoạt động.
--          registered_at = NOW() mặc định — dùng để ghi lại thời điểm
--                          đăng ký auto-bid (hữu ích cho audit/debug).
-- ============================================================

-- Thêm cột active nếu chưa tồn tại
ALTER TABLE auto_bid_configs
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

-- Thêm cột registered_at nếu chưa tồn tại
ALTER TABLE auto_bid_configs
    ADD COLUMN IF NOT EXISTS registered_at TIMESTAMP NOT NULL DEFAULT NOW();
