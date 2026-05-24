-- ============================================================
-- Migration V16: Thêm cột token_version vào users
-- Mô tả : Hỗ trợ cơ chế vô hiệu hoá JWT khi đổi/reset mật khẩu.
--
--          Vấn đề: JWT là stateless — một khi đã cấp, không có cách
--          nào thu hồi trước khi hết hạn mà không có server-side state.
--          Nếu mật khẩu bị reset nhưng token cũ vẫn còn hiệu lực,
--          kẻ tấn công có thể tiếp tục dùng token đó.
--
--          Giải pháp: Mỗi JWT được cấp sẽ mang theo token_version tại
--          thời điểm đó. Mỗi request, server so sánh token_version trong
--          JWT với giá trị hiện tại trong DB. Nếu không khớp → JWT đã
--          bị vô hiệu hoá (token cũ sau khi đổi mật khẩu).
--
--          Khi đổi/reset mật khẩu: tăng token_version lên 1 → tất cả
--          JWT cũ tự động không còn hợp lệ.
-- ============================================================

-- Thêm cột token_version, mặc định 0 cho toàn bộ user hiện có
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS token_version INTEGER NOT NULL DEFAULT 0;
