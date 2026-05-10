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

*Yêu cầu môi trường:

-Máy tính đã cài đặt sẵn Java JDK 21+.

-Máy tính đã cài đặt PostgreSQL 16.

-Cấu hình một Database trắng trong PostgreSQL với tên auction_db.

3. Cấu trúc thư mục (Các module chính)

-Dự án được cấu trúc theo chuẩn phân tầng để đảm bảo tính dễ bảo trì và khả năng mở rộng:

src/main/java/com/auction/model/: Chứa các thực thể cốt lõi và các Design Patterns (Factory, State, Strategy, Observer).

src/main/java/com/auction/dao/: Data Access Object, chịu trách nhiệm tương tác trực tiếp với PostgreSQL.

src/main/java/com/auction/service/: Business Logic Layer, xử lý các nghiệp vụ phức tạp của phiên đấu giá.

src/main/java/com/auction/controller/: Điều khiển giao diện JavaFX và kết nối đến các Service.

src/main/java/com/auction/network/: Chứa các lớp xử lý giao tiếp giữa Client và Server (RestClient, WebSocketClient).

src/main/resources/: Chứa tài nguyên tĩnh (giao diện .fxml, file cấu hình application.properties, file SQL khởi tạo db/migration).