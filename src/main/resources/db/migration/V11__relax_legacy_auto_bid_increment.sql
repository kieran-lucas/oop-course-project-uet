-- ============================================================
-- Migration V11: Gỡ bỏ ràng buộc NOT NULL của cột increment cũ
-- Mô tả : Các database cũ có thể còn cột increment (tên cũ) bên cạnh
--          increment_amount (tên mới đang dùng). Ứng dụng chỉ populate
--          increment_amount; nếu cột increment vẫn có ràng buộc NOT NULL
--          thì mỗi lần INSERT sẽ thất bại với lỗi null constraint.
--
--          Migration này kiểm tra xem cột increment có tồn tại không,
--          và nếu có thì DROP NOT NULL để ứng dụng có thể insert bình
--          thường mà không cần quan tâm đến cột legacy đó.
--
-- An toàn: Dùng khối DO $$ để bọc logic điều kiện — không làm gì
--          nếu cột increment không tồn tại (tránh lỗi trên schema mới).
-- ============================================================

DO $$
BEGIN
    -- Chỉ thực hiện nếu cột increment cũ vẫn còn tồn tại trong bảng
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'auto_bid_configs'
          AND column_name = 'increment'
    ) THEN
        -- Gỡ bỏ NOT NULL để INSERT chỉ cần điền increment_amount
        ALTER TABLE auto_bid_configs ALTER COLUMN increment DROP NOT NULL;
    END IF;
END $$;
