# MÔ TẢ CHI TIẾT CÁC LUỒNG UI — HỆ THỐNG ĐẤU GIÁ TRỰC TUYẾN

> **Ghi chú:** Tài liệu này mô tả chi tiết từng sự kiện xảy ra trên giao diện người dùng (UI), theo trình tự thời gian, bao gồm cả các sự kiện bất đồng bộ (WebSocket), lỗi, và phản hồi hệ thống. UI được thiết kế bằng SceneBuilder (FXML) và quản lý database bằng pgAdmin 4.
>
> **Palette:** Navy (#0A1628) + Gold (#C9A96E) dark theme.
>
> **Navigation:** SceneManager (Single Scene + StackPane swap + lazy load + cache).

---

## MỤC LỤC CÁC LUỒNG

1. [LUỒNG 0 — Khởi động ứng dụng](#luồng-0--khởi-động-ứng-dụng)
2. [LUỒNG 1 — Đăng ký tài khoản](#luồng-1--đăng-ký-tài-khoản)
3. [LUỒNG 2 — Đăng nhập](#luồng-2--đăng-nhập)
4. [LUỒNG 3 — Quên mật khẩu](#luồng-3--quên-mật-khẩu)
5. [LUỒNG 4 — Đổi mật khẩu](#luồng-4--đổi-mật-khẩu)
6. [LUỒNG 5 — Nạp tiền (Deposit / Top-up)](#luồng-5--nạp-tiền-deposit--top-up)
7. [LUỒNG 6 — Xem danh sách phiên đấu giá (Bidder)](#luồng-6--xem-danh-sách-phiên-đấu-giá-bidder)
8. [LUỒNG 7 — Đấu giá thủ công (Manual Bid)](#luồng-7--đấu-giá-thủ-công-manual-bid)
9. [LUỒNG 8 — Đấu giá tự động (Auto-Bid)](#luồng-8--đấu-giá-tự-động-auto-bid)
10. [LUỒNG 9 — Anti-sniping (góc nhìn UI)](#luồng-9--anti-sniping-góc-nhìn-ui)
11. [LUỒNG 10 — Phiên kết thúc (Auction Ended)](#luồng-10--phiên-kết-thúc-auction-ended)
12. [LUỒNG 11 — Tạo sản phẩm (Seller)](#luồng-11--tạo-sản-phẩm-seller)
13. [LUỒNG 12 — Tạo phiên đấu giá (Seller)](#luồng-12--tạo-phiên-đấu-giá-seller)
14. [LUỒNG 13 — Quản trị hệ thống (Admin Panel)](#luồng-13--quản-trị-hệ-thống-admin-panel)
15. [LUỒNG 14 — Xử lý lỗi & ngoại lệ trên UI](#luồng-14--xử-lý-lỗi--ngoại-lệ-trên-ui)
16. [LUỒNG 15 — WebSocket Reconnect](#luồng-15--websocket-reconnect)

---

## LUỒNG 0 — Khởi động ứng dụng

**Màn hình:** Không có FXML cụ thể — `ClientApp.java` là entry point.

### Trình tự sự kiện

| Bước | Sự kiện | Mô tả chi tiết |
|------|---------|-----------------|
| 0.1 | **User double-click** file .jar hoặc chạy `./gradlew run` | JVM khởi tạo, JavaFX Application Thread được tạo. |
| 0.2 | **`ClientApp.start(Stage)`** được gọi | JavaFX gọi method `start()` trên Application Thread. |
| 0.3 | **SceneManager khởi tạo** | `SceneManager` tạo một `Scene` duy nhất với root là `StackPane`. Scene được gắn CSS theme (`style.css`) với palette Navy+Gold. Stage được set title "Online Auction System", kích thước mặc định 800×600. |
| 0.4 | **Load `login.fxml`** | SceneManager gọi `FXMLLoader.load()` để tải `login.fxml` — đây là màn hình đầu tiên. FXML được parse, các `@FXML` fields trong `LoginController` được inject. Controller implement `Navigable` interface. |
| 0.5 | **Màn hình Login hiển thị** | StackPane swap: login pane được push lên trên. User thấy: form đăng nhập với 2 ô input (username/password), nút "Đăng nhập", link "Đăng ký" ở dưới, link "Quên mật khẩu?" ở dưới cùng. Background: `#0A1628`, card: `#112240`, text: `#E8D5A3`, accent nút: `#C9A96E`. |
| 0.6 | **Stage.show()** | Cửa sổ ứng dụng hiện lên trên desktop. Focus tự động vào ô "Username". |

### Trạng thái UI sau khởi động

- Ô Username: rỗng, có placeholder "Nhập tên đăng nhập"
- Ô Password: rỗng, có placeholder "Nhập mật khẩu", kiểu `PasswordField` (che ký tự)
- Nút "Đăng nhập": enabled, màu gold `#C9A96E`, text `#0A1628`
- Link "Chưa có tài khoản? Đăng ký": dạng `Hyperlink`, màu `#8899AA`
- Link "Quên mật khẩu?": dạng `Hyperlink`, màu `#8899AA`
- Label lỗi: ẩn (invisible), sẵn sàng hiện khi có lỗi

---

## LUỒNG 1 — Đăng ký tài khoản

**Màn hình:** `register.fxml` — `RegisterController.java`

### Điểm khởi đầu

User đang ở màn hình Login, nhấn link "Chưa có tài khoản? Đăng ký".

### Trình tự sự kiện

| Bước | Sự kiện UI | Tầng nào xử lý | Mô tả chi tiết |
|------|-----------|----------------|-----------------|
| 1.1 | **Click "Đăng ký"** | Client (JavaFX) | `LoginController` bắt sự kiện `onAction` của Hyperlink. Gọi `SceneManager.navigateTo("register")`. |
| 1.2 | **Chuyển màn hình** | Client (SceneManager) | SceneManager kiểm tra cache: nếu `register.fxml` chưa load → gọi `FXMLLoader.load()` → cache kết quả. StackPane swap: login pane bị ẩn (hoặc remove), register pane hiện lên với animation fade-in. |
| 1.3 | **Hiển thị form đăng ký** | Client (FXML render) | User thấy form gồm: ô Username, ô Email, ô Password, ô Confirm Password, DatePicker "Ngày sinh", ComboBox "Vai trò" (BIDDER / SELLER), nút "Đăng ký", link "Đã có tài khoản? Đăng nhập". |
| 1.4 | **User điền thông tin** | Client (User input) | User gõ từng ô. Mỗi ô có placeholder text màu `#8899AA`. Khi user focus vào ô → border đổi sang gold `#C9A96E`. Khi user rời ô → border trở lại `#1B3A5C`. |
| 1.5 | **User chọn vai trò** | Client (ComboBox) | ComboBox dropdown hiện 2 option: "Người đấu giá (Bidder)" và "Người bán (Seller)". Admin KHÔNG có trong danh sách — admin chỉ được tạo qua database trực tiếp (pgAdmin 4). |
| 1.6 | **User chọn ngày sinh** | Client (DatePicker) | DatePicker popup hiện lịch. User chọn ngày tháng năm. DatePicker validate format tự động. |
| 1.7 | **User nhấn "Đăng ký"** | Client (Controller) | `RegisterController.handleRegister()` được gọi. |

### Bước 1.7 — Xử lý chi tiết khi nhấn "Đăng ký"

| Sub-bước | Sự kiện | Mô tả |
|----------|---------|-------|
| 1.7.1 | **Client-side validation** | Controller kiểm tra: (a) tất cả ô đã điền? (b) email có đúng format `xxx@xxx.xxx`? (c) password ≥ 8 ký tự? (d) password == confirm password? (e) đã chọn role? (f) đã chọn ngày sinh? Nếu bất kỳ điều kiện nào sai → hiện Label lỗi màu đỏ `#D4383B` ngay dưới ô bị lỗi, ví dụ "Mật khẩu phải có ít nhất 8 ký tự". Dừng tại đây, KHÔNG gửi request. |
| 1.7.2 | **Hiện loading** | Nút "Đăng ký" bị disable (opacity giảm xuống 0.6). Hiện `ProgressIndicator` spinning cạnh nút (hoặc thay text nút thành "Đang xử lý..."). Mục đích: ngăn user click nhiều lần (double submit). |
| 1.7.3 | **Tạo DTO** | Controller tạo `RegisterRequest{username, password, email, role, dateOfBirth}`. |
| 1.7.4 | **Gửi HTTP POST** | REST Client (chạy trên background thread, KHÔNG phải JavaFX Application Thread) gửi: `POST http://localhost:7070/api/auth/register` với body JSON từ DTO. Header: `Content-Type: application/json`. Không cần JWT vì đây là endpoint public. |
| 1.7.5a | **Server trả 201 Created** | Server nhận → `AuthController.register()` → `UserService.register()` → BCrypt hash password → `UserDao.insert()` vào PostgreSQL → trả response 201 với message thành công. |
| 1.7.5b | **HOẶC Server trả 409 Conflict** | `DuplicateException` — username hoặc email đã tồn tại → server trả `ErrorResponse{error: "DUPLICATE", message: "Username đã tồn tại"}`. |
| 1.7.5c | **HOẶC Server trả 400 Bad Request** | Validation phía server fail (thêm một lớp bảo vệ) → trả `ErrorResponse`. |
| 1.7.6a | **Thành công → cập nhật UI** | `Platform.runLater()` chạy trên JavaFX Application Thread: ẩn ProgressIndicator, hiện Alert dialog hoặc notification bar màu xanh `#2ECC71` với text "Đăng ký thành công! Vui lòng đăng nhập." Sau 2 giây (hoặc khi user nhấn OK) → SceneManager chuyển về `login.fxml`. Ô username ở màn hình login được tự động điền sẵn username vừa đăng ký. |
| 1.7.6b | **Lỗi Duplicate → cập nhật UI** | `Platform.runLater()`: ẩn ProgressIndicator, enable lại nút "Đăng ký". Label lỗi hiện: "Username đã tồn tại" (hoặc "Email đã được sử dụng") — màu `#D4383B`. Ô username được highlight border đỏ. |
| 1.7.6c | **Lỗi khác → cập nhật UI** | Tương tự 1.7.6b nhưng message lỗi tùy thuộc vào `ErrorResponse.message` từ server. |
| 1.7.6d | **Lỗi mạng (timeout/unreachable)** | `Platform.runLater()`: ẩn ProgressIndicator, enable lại nút. Label lỗi: "Không thể kết nối đến server. Vui lòng thử lại." |

### Link quay lại Login

| Bước | Sự kiện | Mô tả |
|------|---------|-------|
| 1.8 | **Click "Đã có tài khoản? Đăng nhập"** | SceneManager swap StackPane → hiện lại `login.fxml` (lấy từ cache). Fade-in animation. |

---

## LUỒNG 2 — Đăng nhập

**Màn hình:** `login.fxml` — `LoginController.java`

### Trình tự sự kiện

| Bước | Sự kiện UI | Tầng xử lý | Mô tả chi tiết |
|------|-----------|------------|-----------------|
| 2.1 | **User nhập username** | Client | User gõ vào ô TextField. Mỗi keypress: ký tự hiện ra. Placeholder ẩn khi có text. |
| 2.2 | **User nhập password** | Client | User gõ vào ô PasswordField. Mỗi keypress: hiện dấu `•` thay vì ký tự thật. |
| 2.3 | **User nhấn Enter hoặc click "Đăng nhập"** | Client (Controller) | `LoginController.handleLogin()` được gọi. Nút "Đăng nhập" cũng phản ứng với phím Enter (defaultButton=true trong FXML). |

### Bước 2.3 — Xử lý chi tiết

| Sub-bước | Sự kiện | Mô tả |
|----------|---------|-------|
| 2.3.1 | **Client-side validation** | Kiểm tra: username không rỗng, password không rỗng. Nếu rỗng → Label lỗi: "Vui lòng nhập đầy đủ thông tin". |
| 2.3.2 | **Hiện loading** | Disable nút "Đăng nhập". ProgressIndicator spinning xuất hiện. |
| 2.3.3 | **Tạo LoginRequest** | `LoginRequest{usernameOrEmail: "alice", password: "mypass123"}`. Field `usernameOrEmail` hỗ trợ cả username lẫn email đăng nhập. |
| 2.3.4 | **Gửi HTTP POST** | Background thread: `POST /api/auth/login` với JSON body. |
| 2.3.5 | **Server xử lý** | `AuthController.login()` → `LoginRateLimiter` kiểm tra rate limit (tối đa 20 lần / 5 phút cho mỗi IP hoặc username). Nếu vượt → throw `TooManyAttemptsException` → HTTP 429. Nếu OK → `UserService.login()` → `UserDao.findByUsernameOrEmail()` → BCrypt.verify() → nếu đúng → `JwtUtil.generateToken(userId, role)` → trả 200 với `{token: "eyJhbGciOi...", role: "BIDDER", username: "alice"}`. |

### Sau khi server trả response

| Sub-bước | Trường hợp | Sự kiện UI |
|----------|-----------|-----------|
| 2.3.6a | **200 OK — Login thành công** | `Platform.runLater()`: Ẩn loading. Client lưu JWT token vào bộ nhớ (static field hoặc singleton). Client đọc `role` từ response. **Phân luồng theo role:** |
| | → role = **BIDDER** | SceneManager chuyển sang `auction-list.fxml`. Bidder thấy danh sách phiên đấu giá. Thanh navigation bar trên cùng hiện: "Danh sách đấu giá \| Nạp tiền \| Đổi mật khẩu \| Đăng xuất". Hiện tên user + balance ở góc phải. |
| | → role = **SELLER** | SceneManager chuyển sang `auction-list.fxml` NHƯNG navigation bar khác: "Sản phẩm của tôi \| Tạo sản phẩm \| Tạo phiên \| Đổi mật khẩu \| Đăng xuất". Seller thấy các phiên đấu giá của mình + danh sách chung. |
| | → role = **ADMIN** | SceneManager chuyển sang `admin-panel.fxml`. Admin thấy dashboard: quản lý users, quản lý auctions, duyệt yêu cầu nạp tiền, thống kê hệ thống. |
| 2.3.6b | **401 Unauthorized** | Label lỗi: "Sai tên đăng nhập hoặc mật khẩu" — màu `#D4383B`. Ô password bị xóa trắng. Focus trả về ô username. Enable lại nút. |
| 2.3.6c | **429 Too Many Requests** | Label lỗi: "Bạn đã thử quá nhiều lần. Vui lòng đợi 5 phút." — màu `#D4383B`. Nút "Đăng nhập" bị disable trong 5 phút (Timer countdown hiện trên nút: "Thử lại sau 4:59"). `LoginController` dùng `Timeline` để đếm ngược mỗi giây, khi hết → enable lại nút. |
| 2.3.6d | **Lỗi mạng** | Label lỗi: "Không thể kết nối server." Enable lại nút. |

---

## LUỒNG 3 — Quên mật khẩu

**Màn hình:** `forgot-password.fxml` — `ForgotPasswordController.java`

### Điểm khởi đầu

User đang ở màn hình Login, nhấn link "Quên mật khẩu?"

### Bước 1 — Nhập email

| Bước | Sự kiện | Mô tả |
|------|---------|-------|
| 3.1 | **Click "Quên mật khẩu?"** | SceneManager swap sang `forgot-password.fxml`. |
| 3.2 | **Hiển thị form** | User thấy: Label hướng dẫn "Nhập email đã đăng ký để nhận mã xác nhận", ô Email, nút "Gửi mã xác nhận", link "Quay lại đăng nhập". |
| 3.3 | **User nhập email và nhấn "Gửi mã xác nhận"** | Client validation: email format đúng? Nếu sai → lỗi. Nếu đúng → disable nút, hiện loading. |
| 3.4 | **Gửi POST /api/auth/forgot-password** | Body: `{email: "alice@example.com"}`. |
| 3.5 | **Server xử lý** | `AuthController.forgotPassword()` → `UserService.forgotPassword()` → `UserDao.findByEmail()` → nếu tìm thấy → tạo `PasswordResetToken` (random UUID, hash bằng SHA-256, expiry = now + 15 phút) → `PasswordResetTokenDao.insert()` lưu vào bảng `password_reset_tokens` → `EmailService.sendResetEmail()` gửi email qua Gmail SMTP (Jakarta Mail) chứa mã 6 số (hoặc link reset). Server LUÔN trả 200 OK dù email có tồn tại hay không (bảo mật: không leak thông tin email nào đã đăng ký). |
| 3.6 | **UI cập nhật** | `Platform.runLater()`: Ẩn form nhập email. Hiện form mới: "Nhập mã xác nhận đã gửi đến email của bạn", ô "Mã xác nhận" (6 ký tự), ô "Mật khẩu mới", ô "Xác nhận mật khẩu mới", nút "Đặt lại mật khẩu". Hiện countdown "Mã hết hạn sau: 14:59" (đếm ngược 15 phút). |

### Bước 2 — Nhập mã và đặt mật khẩu mới

| Bước | Sự kiện | Mô tả |
|------|---------|-------|
| 3.7 | **User nhập mã + mật khẩu mới** | Client validation: mã không rỗng, password ≥ 8 ký tự, password == confirm. |
| 3.8 | **Nhấn "Đặt lại mật khẩu"** | Loading hiện. Gửi `POST /api/auth/reset-password` với `{email, token, newPassword}`. |
| 3.9a | **200 OK** | Server: verify token hash + check chưa hết hạn → BCrypt hash mật khẩu mới → `UserDao.update()` → xóa token đã dùng. Client: hiện notification xanh "Đặt lại mật khẩu thành công!" → chuyển về `login.fxml`. |
| 3.9b | **400 Bad Request — Token hết hạn** | Label lỗi: "Mã xác nhận đã hết hạn. Vui lòng gửi lại." Hiện nút "Gửi lại mã" để user quay lại bước 3.3. |
| 3.9c | **400 Bad Request — Token sai** | Label lỗi: "Mã xác nhận không đúng. Vui lòng kiểm tra lại." |

---

## LUỒNG 4 — Đổi mật khẩu

**Màn hình:** `change-password.fxml` — `ChangePasswordController.java`

### Điểm khởi đầu

User đã đăng nhập (bất kỳ role nào), nhấn nút "Đổi mật khẩu" trên navigation bar.

### Trình tự sự kiện

| Bước | Sự kiện | Mô tả |
|------|---------|-------|
| 4.1 | **Click "Đổi mật khẩu"** | SceneManager swap sang `change-password.fxml`. Giữ nguyên navigation bar. |
| 4.2 | **Hiển thị form** | 3 ô: "Mật khẩu hiện tại", "Mật khẩu mới", "Xác nhận mật khẩu mới". Nút "Lưu thay đổi". |
| 4.3 | **User điền và nhấn "Lưu thay đổi"** | Client validation: mật khẩu mới ≥ 8 ký tự, mật khẩu mới == confirm, mật khẩu mới ≠ mật khẩu hiện tại. Nếu fail → label lỗi tương ứng. |
| 4.4 | **Gửi POST /api/auth/change-password** | Header: `Authorization: Bearer <JWT>`. Body: `ChangePasswordRequest{currentPassword, newPassword}`. |
| 4.5a | **200 OK** | Server: verify JWT → lấy userId → `UserDao.findById()` → BCrypt.verify(currentPassword) → BCrypt.hash(newPassword) → `UserDao.update()`. Client: notification xanh "Đổi mật khẩu thành công!". Form được reset (xóa trắng 3 ô). |
| 4.5b | **401 — Sai mật khẩu hiện tại** | Label lỗi: "Mật khẩu hiện tại không đúng" — dưới ô "Mật khẩu hiện tại", border đỏ. |

---

## LUỒNG 5 — Nạp tiền (Deposit / Top-up)

**Màn hình:** `deposit.fxml` — `DepositController.java` (client)

### 5A — User (Bidder) gửi yêu cầu nạp tiền

| Bước | Sự kiện | Mô tả |
|------|---------|-------|
| 5A.1 | **Click "Nạp tiền" trên nav bar** | SceneManager swap sang `deposit.fxml`. |
| 5A.2 | **Hiển thị màn hình nạp tiền** | Trên cùng: hiện số dư hiện tại "Số dư: 500,000 VNĐ" (lấy từ `User.balance`). Ô nhập "Số tiền muốn nạp" (kiểu số, format tự động thêm dấu phẩy ngăn cách hàng nghìn). Nút "Gửi yêu cầu nạp tiền". Bảng lịch sử yêu cầu nạp tiền (TableView): cột Ngày, Số tiền, Trạng thái (PENDING/APPROVED/REJECTED). |
| 5A.3 | **Load lịch sử** | Background thread: `GET /api/deposits?userId=<myId>` (hoặc server tự lấy từ JWT). Response: list `DepositRequest`. Mỗi row hiện tag trạng thái: PENDING = vàng `#C9A96E`, APPROVED = xanh `#2ECC71`, REJECTED = đỏ `#D4383B`. |
| 5A.4 | **User nhập số tiền** | Nhập ví dụ "1000000". Ô tự format thành "1,000,000". Client validate: số > 0, số ≤ giới hạn (ví dụ 100,000,000). |
| 5A.5 | **Nhấn "Gửi yêu cầu"** | Loading. `POST /api/deposits` với `{amount: 1000000}`. Server tạo `DepositRequest` với status = PENDING → lưu DB. |
| 5A.6a | **201 Created** | Notification xanh: "Yêu cầu nạp 1,000,000 VNĐ đã được gửi. Vui lòng chờ Admin duyệt." Bảng lịch sử tự refresh: row mới xuất hiện ở đầu bảng với tag PENDING. |
| 5A.6b | **Lỗi** | Label lỗi tương ứng. |

### 5B — Admin duyệt yêu cầu nạp tiền (trong Admin Panel)

| Bước | Sự kiện | Mô tả |
|------|---------|-------|
| 5B.1 | **Admin vào tab "Duyệt nạp tiền"** | Admin Panel có tab/menu item "Duyệt nạp tiền". Click → load danh sách `DepositRequest` có status = PENDING. |
| 5B.2 | **Hiển thị bảng yêu cầu** | TableView: cột Username, Số tiền, Ngày gửi, Nút "Duyệt" (xanh), Nút "Từ chối" (đỏ). |
| 5B.3a | **Admin nhấn "Duyệt"** | Confirm dialog: "Bạn có chắc muốn duyệt nạp 1,000,000 VNĐ cho user alice?" → nhấn "Xác nhận". |
| 5B.4a | **Gửi PUT /api/deposits/{id}/approve** | Server: `DepositService.approve()` → transaction atomic: (1) `SELECT deposit_requests WHERE id=? FOR UPDATE` (2) update status = APPROVED (3) `UPDATE users SET balance = balance + amount WHERE id = userId` (4) COMMIT. Nếu bất kỳ bước nào fail → ROLLBACK. |
| 5B.5a | **200 OK** | Row biến mất khỏi bảng PENDING (hoặc chuyển tag thành APPROVED, opacity giảm). Notification xanh: "Đã duyệt nạp tiền cho alice." |
| 5B.3b | **Admin nhấn "Từ chối"** | Confirm dialog: "Lý do từ chối?" (optional text field) → nhấn "Xác nhận". |
| 5B.4b | **Gửi PUT /api/deposits/{id}/reject** | Server: update status = REJECTED. |
| 5B.5b | **200 OK** | Row chuyển tag REJECTED. |

### 5C — Bidder thấy kết quả

| Bước | Sự kiện | Mô tả |
|------|---------|-------|
| 5C.1 | **Bidder refresh trang nạp tiền** | (Hoặc polling mỗi 30 giây) → `GET /api/deposits` → bảng lịch sử cập nhật: row đổi trạng thái từ PENDING → APPROVED (xanh) hoặc REJECTED (đỏ). |
| 5C.2 | **Nếu APPROVED** | Số dư hiển thị trên header cập nhật: "Số dư: 1,500,000 VNĐ". Bidder có thể dùng balance này để đấu giá. |

---

## LUỒNG 6 — Xem danh sách phiên đấu giá (Bidder)

**Màn hình:** `auction-list.fxml` — `AuctionListController.java`

### Trình tự sự kiện sau khi login

| Bước | Sự kiện | Mô tả |
|------|---------|-------|
| 6.1 | **Màn hình load** | Controller gọi `onNavigatedTo()` (Navigable interface). Hiện ProgressIndicator ở giữa màn hình: "Đang tải danh sách..." |
| 6.2 | **Gọi API** | Background thread: `GET /api/auctions` với JWT header. Server: `AuctionController.getAll()` → `AuctionDao.findAll()` → trả list `AuctionResponse` (chứa: id, itemName, itemCategory, currentPrice, startTime, endTime, status, leadingBidderUsername, bidCount). |
| 6.3 | **Render danh sách** | `Platform.runLater()`: Ẩn ProgressIndicator. Mỗi auction hiện dạng **Card** trong ScrollPane (hoặc ListView): |
| | **Card layout** | Card nền `#112240`, border `#1B3A5C`, border-radius 8px. Gồm: (1) **Tên sản phẩm** — font lớn, màu `#E8D5A3`, bold. (2) **Danh mục** — badge nhỏ: Electronics = xanh dương, Art = tím, Vehicle = cam. (3) **Giá hiện tại** — font lớn nhất, màu gold `#C9A96E`, ví dụ "500,000 VNĐ". (4) **Trạng thái** — badge: OPEN = vàng, RUNNING = xanh lá `#2ECC71`, FINISHED = xám `#8899AA`. (5) **Thời gian còn lại** — nếu RUNNING: countdown "Còn 2h 15m 32s" (cập nhật mỗi giây bằng JavaFX Timeline). Nếu OPEN: "Bắt đầu lúc: 01/04/2026 14:00". (6) **Người dẫn đầu** — "Người dẫn đầu: alice" (hoặc "Chưa có bid" nếu chưa ai bid). (7) **Badge "Popular"** — nếu bidCount ≥ 5: badge gold nhỏ ở góc trên phải card, text "Hot" hoặc "Popular". |
| 6.4 | **Filter/Search** | Thanh filter ở trên: (1) **TextField tìm kiếm** — gõ tên sản phẩm → filter realtime (debounce 300ms, gọi `GET /api/auctions?search=keyword`). (2) **ComboBox trạng thái** — "Tất cả", "Đang mở", "Đang diễn ra", "Đã kết thúc". (3) **ComboBox danh mục** — "Tất cả", "Electronics", "Art", "Vehicle". (4) **Nút "Refresh"** — gọi lại API, reload toàn bộ. |
| 6.5 | **Click vào một Card** | Card được highlight (border đổi sang gold `#C9A96E`). SceneManager chuyển sang `auction-detail.fxml` với parameter `auctionId`. |

### Countdown Timer — cách hoạt động trên client

| Sự kiện | Mô tả |
|---------|-------|
| **Khởi tạo** | Khi card hiển thị auction RUNNING, controller tạo `Timeline` cho mỗi card, `KeyFrame` chạy mỗi 1 giây. |
| **Mỗi giây** | Tính `remaining = endTime - now()`. Format thành "Xh Ym Zs". Cập nhật Label trên card. |
| **Khi remaining ≤ 60s** | Text đổi màu đỏ `#D4383B`, font bold, có thể thêm animation nhấp nháy (pulse). |
| **Khi remaining ≤ 0** | Text hiện "Đã kết thúc". Badge status đổi thành FINISHED (xám). Timeline dừng. |
| **Khi nhận TIME_EXTENDED** (WebSocket) | `endTime` được cập nhật → remaining tính lại → countdown tiếp tục. |

---

## LUỒNG 7 — Đấu giá thủ công (Manual Bid)

**Màn hình:** `auction-detail.fxml` — `AuctionDetailController.java`

Đây là luồng phức tạp nhất của hệ thống, kết hợp REST + WebSocket + realtime UI.

### 7A — Vào màn hình chi tiết

| Bước | Sự kiện | Mô tả |
|------|---------|-------|
| 7A.1 | **SceneManager load `auction-detail.fxml`** | FXMLLoader parse FXML. `AuctionDetailController` được khởi tạo. Nhận `auctionId` qua parameter. |
| 7A.2 | **Load dữ liệu ban đầu (REST)** | Background thread gọi 2 API song song: (1) `GET /api/auctions/{id}` → chi tiết auction. (2) `GET /api/auctions/{id}/bids` → lịch sử bid (cho chart). |
| 7A.3 | **Render chi tiết** | `Platform.runLater()`: Layout chia 2 phần — **Bên trái (60%):** Thông tin sản phẩm (tên, mô tả, danh mục, ảnh nếu có), thông tin phiên (giá khởi điểm, người bán). **Bên phải (40%):** Panel đấu giá gồm: |
| | **Panel đấu giá** | (1) **Giá hiện tại** — font cực lớn (28px), bold, màu gold `#C9A96E`. Ví dụ "500,000 VNĐ". (2) **Người dẫn đầu** — "alice" (hoặc "Bạn đang dẫn đầu!" nếu chính user này). Nếu user đang dẫn đầu → text xanh `#2ECC71`. Nếu bị vượt → text đỏ `#D4383B` "Bạn đã bị vượt!". (3) **Countdown timer** — "Còn 1h 23m 45s", cập nhật mỗi giây. Khi ≤ 60s → đỏ, nhấp nháy. (4) **Ô nhập giá bid** — TextField kiểu số. Placeholder: "Nhập giá ≥ 510,000" (currentPrice + minimumIncrement). (5) **Nút "Đặt giá"** — lớn, nổi bật, gold background. (6) **Nút "Bật Auto-bid"** — secondary style, border gold. (7) **Label lỗi** — ẩn, sẵn sàng hiện. |
| 7A.4 | **Render Bid History Chart** | JavaFX `LineChart` ở phía dưới panel đấu giá. Trục X = timestamp, trục Y = giá (VNĐ). Load initial data từ response bids: mỗi `BidTransaction` = 1 data point. Chart style: line màu gold, fill area dưới line gradient từ `#C9A96E` xuống transparent. |
| 7A.5 | **Mở WebSocket connection** | Client tạo WebSocket connection: `ws://localhost:7070/ws/auction/{id}?token=<JWT>`. Server: `AuctionWebSocketHandler.onConnect()` → verify JWT → lấy userId → tạo `WebSocketObserver` → add vào `AuctionEventManager` cho auction này. Connection mở thành công → icon nhỏ "Live" màu xanh lá nhấp nháy ở góc trên phải. |

### 7B — User đặt giá (Manual Bid)

| Bước | Sự kiện | Mô tả |
|------|---------|-------|
| 7B.1 | **User nhập giá** | Gõ vào ô, ví dụ "550000". Ô tự format "550,000". |
| 7B.2 | **Client-side validation** | Kiểm tra: (a) giá > currentPrice (b) giá là số hợp lệ (c) phiên đang RUNNING. Nếu giá ≤ currentPrice → label lỗi: "Giá phải cao hơn 500,000 VNĐ". (d) **Optional:** kiểm tra balance ≥ giá bid (nếu tính năng deposit đã enable). Nếu không đủ → "Số dư không đủ. Vui lòng nạp thêm tiền." |
| 7B.3 | **Nhấn "Đặt giá"** | Confirm dialog (optional UX): "Bạn có chắc muốn đặt 550,000 VNĐ?" → "Xác nhận" / "Hủy". |
| 7B.4 | **Gửi request** | Disable nút, loading. `POST /api/auctions/{id}/bid` với `BidRequest{auctionId: 5, amount: 550000}` + JWT header. |
| 7B.5 | **Server xử lý — đây là phần quan trọng nhất** | |
| 7B.5.1 | JWT Middleware | Verify token → extract userId, role. Nếu role ≠ BIDDER → throw `UnauthorizedException`. |
| 7B.5.2 | BidController | Parse `BidRequest` từ JSON body → gọi `bidService.placeBid(request, userId)`. |
| 7B.5.3 | BidService (synchronized) | `synchronized(auctionLock)` → vào critical section. |
| 7B.5.4 | State Pattern check | Gọi `auction.getState().placeBid()`. Nếu state = OPEN → throw `AuctionClosedException("Phiên chưa bắt đầu")`. Nếu state = FINISHED → throw `AuctionClosedException("Phiên đã kết thúc")`. Nếu state = RUNNING → cho phép tiếp tục. |
| 7B.5.5 | Strategy Pattern | `ManualBidStrategy.execute()`: validate giá > currentPrice. Nếu không → throw `InvalidBidException`. |
| 7B.5.6 | Anti-sniping check | `long remaining = Duration.between(now, endTime).getSeconds()`. Nếu remaining < 30 → `endTime = endTime.plusSeconds(60)` → sẽ broadcast TIME_EXTENDED. |
| 7B.5.7 | Database transaction | DAO: `BEGIN` → `SELECT * FROM auctions WHERE id=? FOR UPDATE` (lock row) → `UPDATE auctions SET current_price=550000, leading_bidder_id=userId` → `INSERT INTO bid_transactions(...)` → `COMMIT`. |
| 7B.5.8 | Observer notify | `eventManager.notify(BID_UPDATE, bidUpdateMessage)` → duyệt tất cả `WebSocketObserver` cho auction này → mỗi observer gửi JSON qua WebSocket. Nếu có anti-sniping → notify thêm `TIME_EXTENDED`. |
| 7B.5.9 | Auto-bid trigger | Sau khi manual bid thành công, BidService kiểm tra: có ai đã cấu hình auto-bid cho auction này không? → `AutoBidStrategy.execute()` → duyệt PriorityQueue (sort by registeredAt) → tìm auto-bidder có maxBid > currentPrice → tự động bid cho họ → lặp lại notify. (Chi tiết ở Luồng 8.) |

### 7C — TẤT CẢ client nhận WebSocket message

Đây là phần realtime update — xảy ra đồng thời trên MỌI client đang xem cùng phiên.

| Bước | Message type | Sự kiện trên client |
|------|-------------|---------------------|
| 7C.1 | **BID_UPDATE** | Client nhận JSON: `{type: "BID_UPDATE", currentPrice: 550000, leadingBidderUsername: "bob", ...}`. `Platform.runLater()` chạy trên JavaFX Application Thread: (1) Label giá đổi: "500,000" → "550,000 VNĐ" (có thể animation số cuộn lên). (2) Label người dẫn đầu đổi: "alice" → "bob". (3) Nếu user đang xem chính là "alice" (bị vượt) → text đỏ "Bạn đã bị vượt!". Nếu user là "bob" (vừa bid) → text xanh "Bạn đang dẫn đầu!". (4) Placeholder ô bid cập nhật: "Nhập giá ≥ 560,000". (5) Bid History Chart: thêm data point mới (timestamp, 550000) → line chart animation mượt. |
| 7C.2 | **TIME_EXTENDED** | Nhận JSON: `{type: "TIME_EXTENDED", endTime: "2026-04-01T21:01:00"}`. (1) Countdown timer cập nhật: endTime mới. (2) Notification nhỏ slide-in: "Phiên đã được gia hạn thêm 60 giây!" (màu vàng `#C9A96E`, tự ẩn sau 3 giây). |
| 7C.3 | **AUCTION_ENDED** | Nhận JSON: `{type: "AUCTION_ENDED", leadingBidderUsername: "bob", currentPrice: 750000}`. (1) Countdown timer: "Đã kết thúc". (2) Nút "Đặt giá" bị disable, opacity 0.4. (3) Ô nhập giá bị disable. (4) Nút "Bật Auto-bid" bị disable. (5) Overlay hiện: "Phiên đấu giá đã kết thúc!" + "Người thắng: bob" + "Giá cuối: 750,000 VNĐ". Nếu user là bob → overlay viền xanh, text "Chúc mừng! Bạn đã thắng!". Nếu user là alice (thua) → overlay bình thường. (6) WebSocket connection đóng (server close). |

### 7D — Response trả về cho người vừa bid

| Trường hợp | HTTP Status | Sự kiện UI |
|-----------|------------|-----------|
| **Bid thành công** | 200 OK | Enable lại nút. Ô bid xóa trắng. Notification xanh ngắn "Đặt giá thành công!" (tự ẩn 2 giây). *Lưu ý: UI đã cập nhật qua WebSocket trước khi REST response về.* |
| **InvalidBidException** | 400 | Label lỗi: "Giá phải cao hơn giá hiện tại (550,000 VNĐ)". Có thể xảy ra khi: user A nhìn thấy 500k → gõ 510k → nhưng user B đã bid 550k trong lúc đó → server reject. |
| **AuctionClosedException** | 409 | Label lỗi: "Phiên đấu giá đã kết thúc." Disable toàn bộ form bid. |
| **UnauthorizedException** | 401 | Label lỗi: "Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại." → chuyển về login.fxml. |
| **Lỗi mạng** | — | Label lỗi: "Mất kết nối. Vui lòng thử lại." |

---

## LUỒNG 8 — Đấu giá tự động (Auto-Bid)

**Màn hình:** Vẫn trên `auction-detail.fxml`, panel riêng.

### 8A — User cấu hình Auto-bid

| Bước | Sự kiện | Mô tả |
|------|---------|-------|
| 8A.1 | **Nhấn "Bật Auto-bid"** | Panel mở rộng (accordion expand hoặc modal popup). Hiện 2 ô: (1) "Giá tối đa (maxBid)" — placeholder "Ví dụ: 1,000,000". (2) "Bước giá (increment)" — placeholder "Ví dụ: 10,000". Nút "Kích hoạt". Giải thích: "Hệ thống sẽ tự động đặt giá cho bạn mỗi khi có người vượt, cho đến khi đạt giá tối đa." |
| 8A.2 | **User nhập và nhấn "Kích hoạt"** | Client validate: maxBid > currentPrice, increment > 0, maxBid là số hợp lệ. |
| 8A.3 | **Gửi POST /api/auctions/{id}/auto-bid** | Body: `AutoBidRequest{auctionId, maxBid: 1000000, increment: 10000}` + JWT. |
| 8A.4 | **Server xử lý** | `BidController.registerAutoBid()` → tạo `AutoBidConfig{auctionId, bidderId, maxBid, increment, active: true, registeredAt: now()}` → `AutoBidConfigDao.insert()`. |
| 8A.5 | **200 OK** | UI cập nhật: panel auto-bid đổi trạng thái → hiện badge "Auto-bid: ĐANG HOẠT ĐỘNG" màu xanh `#2ECC71`. Hiện thông tin: "Giá tối đa: 1,000,000 VNĐ \| Bước giá: 10,000 VNĐ". Nút đổi thành "Hủy Auto-bid". |

### 8B — Auto-bid tự động kích hoạt (server-side)

| Bước | Sự kiện | Mô tả |
|------|---------|-------|
| 8B.1 | **Có bid mới từ user khác** | Ví dụ: user C bid 600,000. BidService xử lý thành công → broadcast BID_UPDATE. |
| 8B.2 | **BidService trigger auto-bid** | Sau khi bid thủ công thành công, BidService chạy `AutoBidStrategy.execute()`: |
| 8B.2.1 | Load configs | `AutoBidConfigDao.findByAuctionId(auctionId)` → lấy tất cả config active. |
| 8B.2.2 | Filter & Sort | Loại bỏ: config của người vừa bid (C), config đã maxBid ≤ currentPrice. Sort PriorityQueue theo `registeredAt` (ai đăng ký trước ưu tiên). |
| 8B.2.3 | Chọn auto-bidder | Lấy người đầu tiên trong queue (ví dụ user A). Tính giá bid: `min(currentPrice + increment, maxBid)` = min(600000 + 10000, 1000000) = 610,000. |
| 8B.2.4 | Bid tự động | Tạo `BidTransaction{bidderId=A, amount=610000, autoBid=true}` → lưu DB → cập nhật auction → notify. |
| 8B.3 | **Broadcast AUTO_BID_TRIGGERED + BID_UPDATE** | Tất cả client nhận: (1) **BID_UPDATE**: giá đổi 600k → 610k, leadingBidder = A, autoBid = true. (2) **AUTO_BID_TRIGGERED**: thông tin auto-bid đã kích hoạt. |
| 8B.4 | **Trên client của user A** | Notification đặc biệt: "Auto-bid đã đặt 610,000 VNĐ cho bạn!" (icon robot nhỏ, màu xanh). Label "Bạn đang dẫn đầu!" hiện xanh. |
| 8B.5 | **Trên client của user C** | Giá cập nhật 610k. Label "Bạn đã bị vượt!" đỏ. Text nhỏ bên dưới: "(bởi auto-bid)". |
| 8B.6 | **Cascade auto-bid** | Nếu user C CŨNG có auto-bid → server tiếp tục vòng lặp: C auto-bid 620k → A auto-bid 630k → ... cho đến khi một bên hết maxBid. Mỗi vòng đều broadcast BID_UPDATE → chart cập nhật liên tục → cascade effect hiện rõ trên LineChart. |

### 8C — Hủy Auto-bid

| Bước | Sự kiện | Mô tả |
|------|---------|-------|
| 8C.1 | **Nhấn "Hủy Auto-bid"** | Confirm dialog: "Hủy auto-bid? Hệ thống sẽ không tự động đặt giá nữa." |
| 8C.2 | **Gửi DELETE /api/auctions/{id}/auto-bid** | Server: `AutoBidConfigDao.updateActive(configId, false)`. |
| 8C.3 | **200 OK** | Badge đổi: "Auto-bid: ĐÃ HỦY" (xám). Nút đổi lại "Bật Auto-bid". |

---

## LUỒNG 9 — Anti-sniping (góc nhìn UI)

Anti-sniping xảy ra tự động phía server, nhưng ảnh hưởng trực tiếp đến UI.

### Kịch bản: User A bid trong 30 giây cuối

| Thời điểm | Sự kiện | UI trên tất cả client |
|-----------|---------|----------------------|
| T = endTime - 25s | User A nhấn "Đặt giá" | Countdown đang hiện: "Còn 0h 0m 25s" — **ĐỎ, nhấp nháy**. |
| T = endTime - 25s + RTT | Server nhận bid | BidService detect: remaining (25s) < 30s → trigger anti-sniping: `endTime += 60s`. |
| T + RTT | **WebSocket push** | Tất cả client nhận 2 message: (1) `BID_UPDATE` — giá mới. (2) `TIME_EXTENDED` — endTime mới. |
| | **Countdown reset** | Timer đổi: "Còn 0h 0m 25s" → "Còn 0h 1m 25s". Màu từ đỏ → trở lại trắng/cream. Notification slide-in: "⏰ Phiên đã gia hạn 60 giây do có bid phút cuối!" |
| | **Phản ứng của user khác** | User B thấy giá tăng + thời gian gia hạn → quyết định bid lại → lại trigger anti-sniping → vòng lặp tiếp tục cho đến khi 30s trôi qua mà không ai bid. |

---

## LUỒNG 10 — Phiên kết thúc (Auction Ended)

### 10A — Tự động kết thúc (AuctionScheduler)

| Bước | Sự kiện | Mô tả |
|------|---------|-------|
| 10A.1 | **Server: AuctionScheduler detect** | `ScheduledExecutorService` chạy mỗi 1 giây, kiểm tra: auction nào có `endTime < now()` && status = RUNNING. |
| 10A.2 | **Chuyển trạng thái** | `AuctionService.closeAuction()` → State pattern: `RunningState.close()` → set status = FINISHED → `AuctionDao.update()`. |
| 10A.3 | **Broadcast AUCTION_ENDED** | `eventManager.notify(AUCTION_ENDED, msg)` → tất cả client nhận. |

### 10B — UI sau khi phiên kết thúc

| Client | Sự kiện UI |
|--------|-----------|
| **Người thắng (leading bidder)** | Overlay xanh lá: "🎉 Chúc mừng! Bạn đã thắng phiên đấu giá!" + "Sản phẩm: iPhone 15 Pro" + "Giá cuối: 750,000 VNĐ" + "Vui lòng thanh toán trong 48 giờ". Nút "Thanh toán" (future feature). Countdown timer: "Đã kết thúc". Tất cả ô input bị disable. |
| **Người thua** | Overlay bình thường: "Phiên đấu giá đã kết thúc" + "Người thắng: bob — 750,000 VNĐ". Nút "Quay lại danh sách". |
| **Seller (chủ phiên)** | Notification: "Phiên đấu giá #5 đã kết thúc! Người thắng: bob — 750,000 VNĐ". |
| **Tất cả** | WebSocket connection đóng (server-initiated close). Nút bid/auto-bid disable. Chart vẫn hiển thị (readonly). |

---

## LUỒNG 11 — Tạo sản phẩm (Seller)

**Màn hình:** `create-item.fxml` — `CreateItemController.java`

### Trình tự sự kiện

| Bước | Sự kiện | Mô tả |
|------|---------|-------|
| 11.1 | **Seller nhấn "Tạo sản phẩm" trên nav** | SceneManager swap sang `create-item.fxml`. |
| 11.2 | **Hiển thị form** | Các ô: (1) "Tên sản phẩm" — TextField. (2) "Mô tả" — TextArea (nhiều dòng). (3) **ComboBox "Danh mục"** — 3 option: Electronics, Art, Vehicle. (4) **Ô động theo danh mục** — xuất hiện khi chọn danh mục: |
| | → **Electronics** | Thêm ô: "Thương hiệu (Brand)" — ví dụ "Apple", "Samsung". |
| | → **Art** | Thêm ô: "Nghệ sĩ (Artist)" — ví dụ "Nguyễn Gia Trí". |
| | → **Vehicle** | Thêm ô: "Năm sản xuất (Year)" — Spinner số, ví dụ 2020. |
| 11.3 | **Chọn danh mục → ô động** | Khi user đổi ComboBox: ô đặc thù của danh mục cũ biến mất (fade-out), ô mới hiện ra (fade-in). Ví dụ: chọn Electronics → ô Brand hiện. Đổi sang Vehicle → ô Brand ẩn, ô Year hiện. |
| 11.4 | **Nhấn "Tạo sản phẩm"** | Client validate: tên không rỗng, mô tả không rỗng, danh mục đã chọn, ô đặc thù đã điền. |
| 11.5 | **Gửi POST /api/items** | JWT header (role = SELLER). Body: `CreateItemRequest{name, description, category: "ELECTRONICS", brand: "Apple"}`. Server: `ItemController.createItem()` → `ItemService.createItem()` → **Factory Pattern**: `ItemFactory.create("ELECTRONICS", data)` → tạo `new Electronics(name, desc, sellerId, brand)` → `ItemDao.insert()`. |
| 11.6a | **201 Created** | Notification xanh: "Sản phẩm 'iPhone 15 Pro' đã được tạo!". Form reset. Option: nút "Tạo phiên đấu giá cho sản phẩm này" xuất hiện → click → chuyển sang `create-auction.fxml` với itemId pre-filled. |
| 11.6b | **Lỗi** | Label lỗi tương ứng. |

---

## LUỒNG 12 — Tạo phiên đấu giá (Seller)

**Màn hình:** `create-auction.fxml` — `CreateAuctionController.java`

### Trình tự sự kiện

| Bước | Sự kiện | Mô tả |
|------|---------|-------|
| 12.1 | **Seller nhấn "Tạo phiên" trên nav** | SceneManager swap sang `create-auction.fxml`. |
| 12.2 | **Hiển thị form** | (1) **ComboBox "Chọn sản phẩm"** — dropdown liệt kê sản phẩm của seller (load từ `GET /api/items?sellerId=me`). Mỗi option hiện: "[Electronics] iPhone 15 Pro". Nếu đến từ luồng 11 → pre-selected. (2) **"Giá khởi điểm"** — TextField số, placeholder "100,000". (3) **"Thời gian bắt đầu"** — DateTimePicker (DatePicker + giờ:phút). (4) **"Thời gian kết thúc"** — DateTimePicker. (5) Nút "Tạo phiên". |
| 12.3 | **Client validation** | Sản phẩm đã chọn? Giá > 0? Thời gian bắt đầu > now? Thời gian kết thúc > thời gian bắt đầu? Thời lượng ≥ 1 giờ (recommended)? |
| 12.4 | **Gửi POST /api/auctions** | Body: `CreateAuctionRequest{itemId, startingPrice, startTime, endTime}` + JWT (SELLER). Server: `AuctionController.create()` → `AuctionService.createAuction()` → tạo `Auction` với status = OPEN, currentPrice = startingPrice → `AuctionDao.insert()`. **State pattern:** phiên mới tạo ở `OpenState`. |
| 12.5a | **201 Created** | Notification: "Phiên đấu giá đã được tạo! Trạng thái: OPEN. Phiên sẽ tự động chuyển sang RUNNING khi đến giờ bắt đầu." |
| 12.6 | **Khi đến startTime** | Server: `AuctionScheduler` detect → `AuctionService.startAuction()` → State: OPEN → RUNNING. Phiên xuất hiện trên `auction-list.fxml` với badge RUNNING. |

### Seller quản lý phiên đang OPEN

| Sự kiện | Mô tả |
|---------|-------|
| **Sửa phiên** | Seller có thể sửa phiên đang OPEN (chưa RUNNING): đổi giá khởi điểm, đổi thời gian. Gửi `PUT /api/auctions/{id}` → State pattern: `OpenState.edit()` → cho phép. Nếu phiên đã RUNNING → `RunningState.edit()` → throw exception → UI: "Không thể sửa phiên đang diễn ra." |
| **Xóa phiên** | Seller hoặc Admin xóa: `DELETE /api/auctions/{id}`. Confirm dialog: "Bạn có chắc muốn xóa phiên này? Hành động không thể hoàn tác." Server: kiểm tra status = OPEN hoặc FINISHED → cho phép. Nếu RUNNING → "Không thể xóa phiên đang diễn ra." |

---

## LUỒNG 13 — Quản trị hệ thống (Admin Panel)

**Màn hình:** `admin-panel.fxml` — `AdminPanelController.java`

### Layout Admin Panel

Admin Panel chia thành nhiều tab/section:

| Tab | Nội dung | API liên quan |
|-----|---------|---------------|
| **Quản lý người dùng** | TableView: ID, Username, Email, Role, Ngày tạo, Nút "Xóa"/"Vô hiệu hóa" | `GET /api/admin/users`, `DELETE /api/admin/users/{id}` |
| **Quản lý phiên đấu giá** | TableView: ID, Sản phẩm, Giá hiện tại, Trạng thái, Seller, Nút "Xóa" | `GET /api/auctions`, `DELETE /api/auctions/{id}` |
| **Duyệt nạp tiền** | TableView: Username, Số tiền, Ngày gửi, Nút "Duyệt"/"Từ chối" | `GET /api/admin/deposits?status=PENDING`, `PUT /api/deposits/{id}/approve\|reject` |
| **Thống kê** | Tổng user, tổng phiên, tổng giao dịch, biểu đồ (optional) | `GET /api/admin/stats` |

### Luồng xóa user

| Bước | Sự kiện | Mô tả |
|------|---------|-------|
| 13.1 | **Admin click "Xóa" cạnh user** | Confirm dialog: "Xóa user 'alice'? Tất cả bid và phiên của user này cũng sẽ bị ảnh hưởng." |
| 13.2 | **Xác nhận → gửi DELETE** | `DELETE /api/admin/users/{id}` + JWT (ADMIN). Server: kiểm tra user không phải admin đang login. DB: cascade delete hoặc soft delete. |
| 13.3 | **200 OK** | Row biến mất khỏi bảng (animation fade-out). Notification: "Đã xóa user alice." |

---

## LUỒNG 14 — Xử lý lỗi & ngoại lệ trên UI

### Mapping Exception → HTTP Status → UI Message

| Exception (Server) | HTTP Status | Error Code | UI Message | Vị trí hiển thị |
|--------------------|-------------|-----------|-----------|-----------------|
| `InvalidBidException` | 400 | INVALID_BID | "Giá phải cao hơn giá hiện tại" | Label lỗi dưới ô bid, màu `#D4383B` |
| `AuctionClosedException` | 409 | AUCTION_CLOSED | "Phiên đấu giá đã kết thúc" hoặc "Phiên chưa bắt đầu" | Label lỗi + disable form |
| `UnauthorizedException` | 401 | UNAUTHORIZED | "Phiên đăng nhập hết hạn" | Alert dialog → redirect login |
| `NotFoundException` | 404 | NOT_FOUND | "Không tìm thấy phiên đấu giá" | Alert dialog → quay lại list |
| `DuplicateException` | 409 | DUPLICATE | "Username/email đã tồn tại" | Label lỗi dưới ô tương ứng |
| `TooManyAttemptsException` | 429 | RATE_LIMITED | "Quá nhiều lần thử. Đợi 5 phút." | Label lỗi + countdown trên nút |
| `InsufficientBalanceException` | 400 | INSUFFICIENT_BALANCE | "Số dư không đủ để đặt giá" | Label lỗi + link "Nạp tiền" |
| Network error (client) | — | — | "Không thể kết nối server" | Label lỗi, cho phép retry |
| Server 500 | 500 | INTERNAL_ERROR | "Lỗi hệ thống. Vui lòng thử lại." | Alert dialog |

### Cách hiển thị lỗi trên UI

| Kiểu lỗi | Component | Style |
|-----------|----------|-------|
| **Inline error** (validation) | Label ngay dưới ô input | Text đỏ `#D4383B`, font 12px. Border ô input đổi đỏ. |
| **Notification bar** | Bar trượt vào từ trên | Background đỏ `#D4383B`, text trắng, icon cảnh báo, tự ẩn sau 5 giây. |
| **Success notification** | Bar trượt vào từ trên | Background xanh `#2ECC71`, text trắng, icon check, tự ẩn sau 3 giây. |
| **Alert dialog** | Modal popup giữa màn hình | Card `#112240`, border gold, nút OK. Block interaction cho đến khi đóng. |
| **Confirm dialog** | Modal popup giữa màn hình | Card `#112240`, 2 nút: "Xác nhận" (gold) + "Hủy" (xám). |

---

## LUỒNG 15 — WebSocket Reconnect

### Khi mất kết nối WebSocket

| Bước | Sự kiện | Mô tả |
|------|---------|-------|
| 15.1 | **Connection drop** | Network issue hoặc server restart. `onClose` callback được gọi trên client. |
| 15.2 | **UI hiện cảnh báo** | Icon "Live" đổi từ xanh → đỏ. Text: "Mất kết nối — đang thử kết nối lại..." Banner nhỏ vàng ở trên cùng: "⚠️ Kết nối realtime bị ngắt. Dữ liệu có thể không cập nhật." |
| 15.3 | **Auto-reconnect** | Client thử kết nối lại với exponential backoff: 1s → 2s → 4s → 8s → max 30s. Mỗi lần thử: log "Attempting reconnect #N...". |
| 15.4a | **Reconnect thành công** | Icon "Live" đổi lại xanh. Banner ẩn. Client gọi REST `GET /api/auctions/{id}` để sync state mới nhất (có thể đã miss vài BID_UPDATE khi offline). UI cập nhật lại giá, leadingBidder, countdown. |
| 15.4b | **Reconnect thất bại sau N lần** | Banner chuyển đỏ: "Không thể kết nối lại. Vui lòng refresh." Nút "Thử lại" (manual reconnect). |

---

## TỔNG HỢP: SƠ ĐỒ ĐIỀU HƯỚNG GIỮA CÁC MÀN HÌNH

```
                    ┌─────────────────────┐
                    │   ClientApp.start()  │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │    login.fxml        │◄──────────────────────┐
                    │                     │                       │
                    │  [Đăng nhập]        │                       │
                    │  [Đăng ký] ─────────┼──► register.fxml      │
                    │  [Quên mật khẩu] ───┼──► forgot-password.fxml│
                    └──────────┬──────────┘                       │
                               │ Login OK                         │
                    ┌──────────┼──────────┐                       │
                    │          │          │                       │
                    ▼          ▼          ▼                       │
              ┌──────────┐ ┌──────────┐ ┌──────────┐             │
              │ BIDDER   │ │ SELLER   │ │  ADMIN   │             │
              │          │ │          │ │          │             │
              │ auction  │ │ auction  │ │ admin    │             │
              │ -list    │ │ -list    │ │ -panel   │             │
              └────┬─────┘ └────┬─────┘ └──────────┘             │
                   │            │                                │
                   ▼            ├──► create-item.fxml             │
              auction-detail    ├──► create-auction.fxml          │
              .fxml             │                                │
                                │                                │
              (Tất cả roles)    │                                │
              ├──► change-password.fxml                           │
              ├──► deposit.fxml (Bidder only)                     │
              └──► [Đăng xuất] ──────────────────────────────────┘
```

---

## PHỤ LỤC: MAPPING FXML → CONTROLLER → API

| FXML | Controller (Client) | API Endpoint | Controller (Server) | Service |
|------|-------------------|-------------|-------------------|---------|
| `login.fxml` | `LoginController` | `POST /api/auth/login` | `AuthController` | `UserService.login()` |
| `register.fxml` | `RegisterController` | `POST /api/auth/register` | `AuthController` | `UserService.register()` |
| `forgot-password.fxml` | `ForgotPasswordController` | `POST /api/auth/forgot-password`, `POST /api/auth/reset-password` | `AuthController` | `UserService.forgotPassword()`, `EmailService` |
| `change-password.fxml` | `ChangePasswordController` | `POST /api/auth/change-password` | `AuthController` | `UserService.changePassword()` |
| `auction-list.fxml` | `AuctionListController` | `GET /api/auctions` | `AuctionController` | `AuctionService` |
| `auction-detail.fxml` | `AuctionDetailController` | `GET /api/auctions/{id}`, `GET /api/auctions/{id}/bids`, `POST /api/auctions/{id}/bid`, `POST /api/auctions/{id}/auto-bid`, `WS /ws/auction/{id}` | `AuctionController`, `BidController`, `AuctionWebSocketHandler` | `BidService`, `AuctionService` |
| `create-item.fxml` | `CreateItemController` | `POST /api/items`, `GET /api/items` | `ItemController` | `ItemService`, `ItemFactory` |
| `create-auction.fxml` | `CreateAuctionController` | `POST /api/auctions`, `GET /api/items?sellerId=me` | `AuctionController`, `ItemController` | `AuctionService` |
| `admin-panel.fxml` | `AdminPanelController` | `GET /api/admin/users`, `DELETE /api/admin/users/{id}`, `GET /api/admin/deposits`, `PUT /api/deposits/{id}/approve\|reject` | Admin endpoints | `UserService`, `DepositService` |
| `deposit.fxml` | `DepositController` (client) | `POST /api/deposits`, `GET /api/deposits` | `DepositController` (server) | `DepositService` |
