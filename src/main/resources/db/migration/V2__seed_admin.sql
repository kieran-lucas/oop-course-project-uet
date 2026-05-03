-- V2: Seed tài khoản admin mặc định
-- Username: admin | Password: 123456

INSERT INTO users (username, password_hash, email, role, created_at)
VALUES (
    'admin',
    '$2a$12$HJXqXjs6OmZZo3vF.dPt/uLGV8BGcboPsPf2l0tVAfbkQT9uN9svC',
    'admin@auction.com',
    'ADMIN',
    NOW()
);
