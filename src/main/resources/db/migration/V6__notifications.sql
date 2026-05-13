-- ============================================================
-- Migration V6: Bảng notifications — Thông báo người dùng
-- Mô tả : Lưu các thông báo gửi đến người dùng khi họ offline
--          (ví dụ: bị vượt giá, thắng đấu giá, yêu cầu được duyệt...).
--          Khi user online, thông báo được đẩy qua WebSocket;
--          bảng này đảm bảo user không bỏ sót thông báo khi offline.
-- notification_type: phân loại thông báo, ví dụ: OUTBID, AUCTION_WON,
--          DEPOSIT_APPROVED, PASSWORD_RESET_APPROVED, v.v.
-- ============================================================

CREATE TABLE IF NOT EXISTS notifications (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message             TEXT NOT NULL,
    notification_type   VARCHAR(50) NOT NULL,          -- Loại thông báo (xem mô tả ở trên)
    is_read             BOOLEAN DEFAULT FALSE,         -- FALSE: chưa đọc | TRUE: đã đọc
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Tăng tốc truy vấn lấy toàn bộ thông báo của một user
CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications(user_id);

-- Tăng tốc truy vấn đếm / lọc thông báo chưa đọc (is_read = FALSE)
CREATE INDEX IF NOT EXISTS idx_notifications_is_read ON notifications(is_read);
