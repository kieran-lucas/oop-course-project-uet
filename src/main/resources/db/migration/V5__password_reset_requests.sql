-- ============================================================
-- Migration V5: Bảng password_reset_requests — Yêu cầu đặt lại mật khẩu
-- Mô tả : Lưu các yêu cầu đặt lại mật khẩu do admin xử lý thủ công.
--          Quy trình: user gửi yêu cầu → admin duyệt/từ chối
--          → nếu APPROVED thì hệ thống cấp mật khẩu mới cho user.
-- Vòng đời trạng thái: PENDING → APPROVED | REJECTED
-- Lưu ý : Không lưu token hay mật khẩu mới trong bảng này —
--          việc reset thực tế do application layer xử lý sau khi duyệt.
-- ============================================================

CREATE TABLE IF NOT EXISTS password_reset_requests (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),     -- Thời điểm user gửi yêu cầu
    reviewed_at TIMESTAMP                             -- Thời điểm admin xử lý (NULL nếu chưa duyệt)
);

-- Tăng tốc truy vấn lọc yêu cầu theo trạng thái (admin xem danh sách chờ duyệt)
CREATE INDEX IF NOT EXISTS idx_password_reset_requests_status ON password_reset_requests(status);

-- Tăng tốc truy vấn kiểm tra xem user đã có yêu cầu đang chờ chưa (tránh tạo trùng)
CREATE INDEX IF NOT EXISTS idx_password_reset_requests_user   ON password_reset_requests(user_id);
