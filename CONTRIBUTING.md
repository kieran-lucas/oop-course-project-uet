# Hướng dẫn đóng góp (Contributing Guidelines)

Cảm ơn bạn đã tham gia phát triển Online Auction System! Để đảm bảo chất lượng dự án, vui lòng tuân thủ các quy tắc sau:

## 1. Quy tắc đặt tên Branch
* Tính năng mới: `feat/<tên-tính-năng>` (VD: `feat/add-auto-bid`)
* Sửa lỗi: `fix/<tên-lỗi>` (VD: `fix/login-crash`)
* Tài liệu/Cấu hình: `docs/<tên-tài-liệu>` hoặc `chore/<tên-công-việc>`

## 2. Quy tắc Commit (Conventional Commits)
* `feat:` Thêm tính năng mới
* `fix:` Sửa lỗi
* `docs:` Cập nhật tài liệu
* `style:` Format code (không ảnh hưởng logic)
* `refactor:` Tái cấu trúc code

## 3. Quy trình Pull Request (PR)
1. Chạy `./gradlew spotlessApply` trước khi commit.
2. Đảm bảo mọi Unit Test đều pass (`./gradlew test`).
3. Tạo PR và request ít nhất 1 thành viên review trước khi merge.
