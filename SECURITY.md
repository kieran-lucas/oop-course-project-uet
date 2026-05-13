# Security Policy

## Báo cáo lỗ hổng bảo mật

Nếu bạn phát hiện lỗ hổng bảo mật, vui lòng:
1. KHÔNG tạo GitHub Issue công khai.
2. Gửi email mô tả: loại lỗ hổng, file/endpoint bị ảnh hưởng, steps to reproduce.

## Phiên bản được hỗ trợ

| Phiên bản | Trạng thái     |
| --------- | -------------- |
| 1.0.x     | ✅ Được hỗ trợ |

## Thông tin bảo mật

- Mật khẩu được hash bằng **BCrypt** (cost factor = 12).
- JWT token có thời hạn **24 giờ**, ký bằng HMAC-256.
- Đặt biến môi trường `JWT_SECRET` trước khi deploy production.
- Mật khẩu admin mặc định phải được đổi ngay sau lần đầu đăng nhập.
