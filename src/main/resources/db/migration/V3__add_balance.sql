-- V3: Thêm cột balance (số dư tài khoản) cho người dùng
ALTER TABLE users ADD COLUMN balance DECIMAL(15,2) NOT NULL DEFAULT 0;
