-- ============================================================
-- Migration V2: Seed dữ liệu mặc định — Tài khoản Admin
-- Mô tả : Tạo tài khoản quản trị viên mặc định cho hệ thống.
--          Chỉ chạy một lần khi khởi tạo môi trường mới.
-- Lưu ý : ĐỔI MẬT KHẨU ngay sau khi triển khai lên production!
-- Thông tin đăng nhập mặc định:
--   Username : admin
--   Password : 123456  (hash bcrypt, cost=12)
-- ============================================================

INSERT INTO users (username, password_hash, email, role, created_at)
VALUES (
    'admin',
    '$2a$12$HJXqXjs6OmZZo3vF.dPt/uLGV8BGcboPsPf2l0tVAfbkQT9uN9svC',  -- bcrypt("123456", cost=12)
    'admin@auction.com',
    'ADMIN',
    NOW()
)
ON CONFLICT (username) DO NOTHING;
