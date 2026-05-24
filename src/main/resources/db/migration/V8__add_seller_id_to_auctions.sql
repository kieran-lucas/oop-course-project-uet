-- ============================================================
-- Migration V8: Thêm cột seller_id vào bảng auctions
-- Mô tả : Bảng auctions thiếu seller_id dẫn đến hai vấn đề:
--          1. Bảo mật: không thể ngăn seller tự đặt giá cho
--             phiên của chính mình mà không join bảng items.
--          2. Hiệu năng: mọi truy vấn phân quyền đều phải join
--             items để lấy seller_id, gây thêm overhead.
--         Migration này thêm cột, backfill từ items, rồi đặt NOT NULL.
-- ============================================================

-- Thêm cột seller_id (cho phép NULL tạm thời trong lúc backfill)
ALTER TABLE auctions ADD COLUMN IF NOT EXISTS seller_id BIGINT REFERENCES users(id);

-- Backfill seller_id từ bảng items cho toàn bộ phiên đấu giá hiện có
UPDATE auctions SET seller_id = (SELECT seller_id FROM items WHERE items.id = auctions.item_id);

-- Đặt NOT NULL sau khi backfill hoàn tất — bắt buộc với mọi phiên mới
ALTER TABLE auctions ALTER COLUMN seller_id SET NOT NULL;

-- Index tăng tốc truy vấn lấy tất cả phiên của một seller
CREATE INDEX IF NOT EXISTS idx_auctions_seller ON auctions(seller_id);
