-- ============================================================
-- Migration V14: Thêm cột status vào bảng items
-- Mô tả : Bổ sung vòng đời trạng thái cho sản phẩm đơn chiếc.
--          Trước migration này, không có cách nào biết sản phẩm
--          đang rảnh, đang đấu giá hay đã được bán qua DB.
--
--         Vòng đời trạng thái:
--           AVAILABLE → IN_AUCTION → SOLD
--                    ↘ REMOVED  (xóa mềm bởi admin/seller)
--
--         Giải thích các trạng thái:
--           AVAILABLE  — sản phẩm sẵn sàng được đưa vào đấu giá
--           IN_AUCTION — đang trong một phiên đấu giá đang chạy
--           SOLD       — đã được thanh toán, không thể đấu giá lại
--           REMOVED    — đã bị ẩn khỏi hệ thống (soft delete)
--
-- Migration này idempotent: ADD COLUMN IF NOT EXISTS + DO $$ kiểm tra
-- constraint trước khi thêm → an toàn khi chạy lại.
-- ============================================================

-- Thêm cột status với giá trị mặc định AVAILABLE
ALTER TABLE items
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE';

ALTER TABLE items
    ALTER COLUMN status SET DEFAULT 'AVAILABLE';

-- Backfill NULL nếu có (chạy trước khi đặt NOT NULL cho chắc chắn)
UPDATE items
SET status = 'AVAILABLE'
WHERE status IS NULL;

ALTER TABLE items
    ALTER COLUMN status SET NOT NULL;

-- Thêm CHECK constraint (bọc trong DO $$ để tránh lỗi trùng lặp trên schema mới)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'items_status_check'
    ) THEN
        ALTER TABLE items
            ADD CONSTRAINT items_status_check
            CHECK (status IN ('AVAILABLE', 'IN_AUCTION', 'SOLD', 'REMOVED'));
    END IF;
END $$;

-- Index tăng tốc truy vấn lọc sản phẩm theo trạng thái
CREATE INDEX IF NOT EXISTS idx_items_status ON items(status);
