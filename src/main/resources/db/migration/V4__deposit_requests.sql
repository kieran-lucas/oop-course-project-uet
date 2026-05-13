-- ============================================================
-- Migration V4: Bảng deposit_requests — Yêu cầu nạp tiền
-- Mô tả : Lưu các yêu cầu nạp tiền vào tài khoản của người dùng.
--          Quy trình: user tạo yêu cầu → admin duyệt/từ chối
--          → nếu APPROVED thì cộng balance vào bảng users.
-- Vòng đời trạng thái: PENDING → APPROVED | REJECTED
-- ============================================================

CREATE TABLE IF NOT EXISTS deposit_requests (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount      DECIMAL(15,2) NOT NULL,               -- Số tiền yêu cầu nạp
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),     -- Thời điểm user gửi yêu cầu
    reviewed_at TIMESTAMP                             -- Thời điểm admin xử lý (NULL nếu chưa duyệt)
);

-- Tăng tốc truy vấn danh sách yêu cầu đang chờ duyệt (PENDING)
CREATE INDEX IF NOT EXISTS idx_deposit_requests_status ON deposit_requests(status);
