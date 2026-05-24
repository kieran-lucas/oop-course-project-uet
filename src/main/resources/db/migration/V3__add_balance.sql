-- ============================================================
-- Migration V3: Thêm cột balance vào bảng users
-- Mô tả : Bổ sung số dư tài khoản cho người dùng.
--          Giá trị mặc định là 0 khi tạo tài khoản mới.
--          User nạp tiền qua deposit_requests (xem V4),
--          và số dư được dùng để đặt giá trong phiên đấu giá.
-- Idempotent: ADD COLUMN IF NOT EXISTS — an toàn khi chạy lại
--             trên database cũ đã tồn tại cột này.
-- ============================================================

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS balance DECIMAL(15,2) NOT NULL DEFAULT 0;
