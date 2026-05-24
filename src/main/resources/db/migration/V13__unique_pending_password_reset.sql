-- ============================================================
-- Migration V13: Đảm bảo mỗi user chỉ có 1 yêu cầu đặt lại MK đang chờ
-- Mô tả : Trước migration này, service layer kiểm tra "đã có yêu cầu
--          PENDING chưa" nhưng không có ràng buộc DB — hai request
--          đồng thời có thể bypass kiểm tra đó (TOCTOU race).
--
--          Migration này:
--          1. Dọn dẹp các bản ghi PENDING trùng lặp hiện có
--             (giữ lại bản ghi mới nhất, REJECT các bản còn lại).
--          2. Tạo partial unique index chỉ trên các hàng PENDING
--             để DB tự enforce ràng buộc 1-per-user ở tầng thấp nhất.
--
-- Partial index (WHERE status = 'PENDING') được chọn thay vì
-- full unique index vì một user hợp lệ có thể có nhiều yêu cầu
-- REJECTED/APPROVED trong lịch sử — chỉ giới hạn ở trạng thái đang chờ.
-- ============================================================

-- Bước 1: Loại bỏ trùng lặp PENDING — giữ bản ghi mới nhất, reject phần còn lại
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY created_at DESC, id DESC) AS rn
    FROM password_reset_requests
    WHERE status = 'PENDING'
)
UPDATE password_reset_requests
SET status = 'REJECTED',
    reviewed_at = NOW()
WHERE id IN (SELECT id FROM ranked WHERE rn > 1);

-- Bước 2: Tạo partial unique index — chỉ áp dụng cho hàng PENDING
CREATE UNIQUE INDEX IF NOT EXISTS ux_password_reset_one_pending_per_user
    ON password_reset_requests(user_id)
    WHERE status = 'PENDING';
