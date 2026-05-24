-- ============================================================
-- Migration V15: Thêm trạng thái chi tiết cho cấu hình auto-bid
-- Mô tả : Thay thế cột boolean active (chỉ biết ON/OFF) bằng cột
--          status dạng chuỗi với 4 trạng thái rõ ràng hơn, cùng
--          với cột failure_reason để ghi lý do dừng khi có lỗi.
--
--         Các trạng thái status:
--           ACTIVE      — đang hoạt động, hệ thống sẽ auto-bid khi cần
--           STOPPED     — user chủ động tắt auto-bid
--           EXHAUSTED   — đã đạt max_bid, không còn ngân sách để tăng thêm
--           FAILED      — dừng do lỗi nghiệp vụ (xem failure_reason)
--
--         Các lý do dừng lỗi (failure_reason):
--           MAX_PRICE_TOO_LOW       — max_bid thấp hơn giá hiện tại
--           INSUFFICIENT_BALANCE    — số dư không đủ để freeze thêm
--           AUCTION_NOT_RUNNING     — phiên đã đóng khi hệ thống xử lý
--           BIDDER_ALREADY_HIGHEST  — user đã đang dẫn đầu, không cần bid
--           ACTIVE_AUTOBID_EXISTS   — đã có config ACTIVE khác cho phiên này
--
-- Idempotent: Dùng DO $$ để kiểm tra tên constraint trước khi thêm
--             → an toàn khi chạy lại trên database đã áp dụng migration.
-- ============================================================

-- Thêm cột status nếu chưa có
ALTER TABLE auto_bid_configs
    ADD COLUMN IF NOT EXISTS status VARCHAR(20);

-- Backfill từ cột active cũ: TRUE → 'ACTIVE', FALSE → 'STOPPED'
UPDATE auto_bid_configs
SET status = CASE WHEN active THEN 'ACTIVE' ELSE 'STOPPED' END
WHERE status IS NULL;

-- Đặt giá trị mặc định và NOT NULL cho các bản ghi mới
ALTER TABLE auto_bid_configs
    ALTER COLUMN status SET DEFAULT 'ACTIVE';

ALTER TABLE auto_bid_configs
    ALTER COLUMN status SET NOT NULL;

-- Thêm cột failure_reason để ghi lý do dừng khi auto-bid thất bại
ALTER TABLE auto_bid_configs
    ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(50);

-- CHECK constraint cho status (bọc trong DO $$ để idempotent)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'auto_bid_configs_status_check'
    ) THEN
        ALTER TABLE auto_bid_configs
            ADD CONSTRAINT auto_bid_configs_status_check
            CHECK (status IN ('ACTIVE', 'STOPPED', 'EXHAUSTED', 'FAILED'));
    END IF;
END $$;

-- CHECK constraint cho failure_reason (bọc trong DO $$ để idempotent)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'auto_bid_configs_failure_reason_check'
    ) THEN
        ALTER TABLE auto_bid_configs
            ADD CONSTRAINT auto_bid_configs_failure_reason_check
            CHECK (
                failure_reason IS NULL
                OR failure_reason IN (
                    'MAX_PRICE_TOO_LOW',
                    'INSUFFICIENT_BALANCE',
                    'AUCTION_NOT_RUNNING',
                    'BIDDER_ALREADY_HIGHEST',
                    'ACTIVE_AUTOBID_EXISTS'
                )
            );
    END IF;
END $$;

-- Index tăng tốc truy vấn lọc config theo trạng thái (ví dụ: tìm config ACTIVE)
CREATE INDEX IF NOT EXISTS idx_auto_bid_configs_status ON auto_bid_configs(status);
