-- ============================================================
-- Migration V12: Thêm cột reserved_balance vào users
-- Mô tả : Theo dõi số tiền đang bị tạm giữ (freeze) bởi các lượt
--          đặt giá đang dẫn đầu. Cơ chế này ngăn user dùng cùng
--          một số dư để dẫn đầu nhiều phiên đấu giá cùng lúc.
--
--         Luồng vận hành:
--           - Khi user dẫn đầu phiên: balance -= amount,
--             reserved_balance += amount (FREEZE)
--           - Khi bị vượt qua:         balance += amount,
--             reserved_balance -= amount (RELEASE)
--           - Khi thắng đấu giá:       reserved_balance -= amount
--             (WIN_CONSUME — tiền đã bị trừ ở bước FREEZE)
--
--         Bất biến được duy trì:
--           balance + reserved_balance = tổng số dư thực tế của user.
-- ============================================================

-- Thêm cột reserved_balance (idempotent — an toàn khi chạy lại)
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS reserved_balance DECIMAL(15,2) NOT NULL DEFAULT 0;
