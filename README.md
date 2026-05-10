Hệ Thống Đấu Giá Trực Tuyến (Online Auction System)

1. Mô tả bài toán và phạm vi hệ thống

*Dự án là một hệ thống đấu giá trực tuyến được thiết kế theo mô hình Client-Server (có giao diện Desktop). Hệ thống cung cấp môi trường minh bạch cho người dùng (Bidder) tham gia đấu giá các sản phẩm từ người bán (Seller).

Phạm vi hệ thống bao gồm:

-Quản lý thông tin tài khoản (Admin, Seller, Bidder).

-Đăng tải và phân loại sản phẩm (Art, Vehicle, Electronics).

-Quản lý các phiên đấu giá theo thời gian thực (State Pattern).

-Hỗ trợ đấu giá thủ công và tự động (Auto-bid Strategy).

-Quản lý ví điện tử, nạp tiền (có sự phê duyệt của Admin).

2. Công nghệ sử dụng và Yêu cầu cài đặt

-Ngôn ngữ lập trình: Java 21

-Giao diện: JavaFX (Client)

-Kiến trúc: Phân tầng (Layered Architecture), MVC

-Hệ quản trị CSDL: PostgreSQL 16

-Kết nối CSDL: Jdbi3, HikariCP (Connection Pooling)

-Giao thức kết nối: HTTP (REST) & WebSocket (Real-time updates)

-Công cụ Build & CI/CD: Gradle, GitHub Actions