# Hướng dẫn cài đặt và chạy

Tài liệu này giữ **tiếng Việt** để người chấm và người chạy dự án thao tác đúng, nhanh, ít nhầm nhất. Cách ưu tiên khi chấm bài là dùng **hai file JAR dựng sẵn** trong GitHub Release.

Thứ tự bắt buộc:

```text
Chạy Server trước -> chạy Client sau
```

---

## 1. Cách khuyến nghị cho người chấm: dùng JAR dựng sẵn

Tải hai file trong release `v1.0.0`:

| File | Vai trò |
|---|---|
| `auction-server-1.0.0.jar` | Backend Javalin, WebSocket, Embedded PostgreSQL, PostgreSQL JDBC, HikariCP, Flyway, JDBI |
| `auction-client-1.0.0.jar` | JavaFX desktop client |

Release page:

```text
https://github.com/kieran-labs/oop-course-project-uet/releases/tag/v1.0.0
```

Đặt cả hai file trong cùng một thư mục:

```text
auction-server-1.0.0.jar
auction-client-1.0.0.jar
```

---

## 2. Yêu cầu môi trường

| Thành phần | Yêu cầu |
|---|---|
| JDK | Java 21 trở lên, khuyến nghị JDK 21 |
| Port | `8080` phải rảnh |
| PostgreSQL | Không cần cài riêng khi dùng chế độ mặc định |
| Gradle | Không cần cài riêng nếu chỉ chạy JAR release |

Kiểm tra Java:

```cmd
java -version
```

Server mặc định tự chạy Embedded PostgreSQL. Lần đầu chạy có thể lâu hơn vì embedded-postgres cần tải/cache binary PostgreSQL tương ứng hệ điều hành.

---

## 3. Chạy Server

Mở **Terminal 1** trong thư mục chứa hai file JAR.

### Windows PowerShell

```powershell
$env:JWT_SECRET='auction-demo-secret-1234567890-abcdef-32bytes'; java -jar .\auction-server-1.0.0.jar
```

### macOS / Linux

```bash
JWT_SECRET='auction-demo-secret-1234567890-abcdef-32bytes' java -jar ./auction-server-1.0.0.jar
```

Đợi server lên tại:

```text
http://localhost:8080
```

Không đóng Terminal 1 trong lúc dùng app. Đóng Terminal 1 là dừng server.

---

## 4. Chạy Client

Mở **Terminal 2** trong cùng thư mục.

### Windows PowerShell

```powershell
java -jar .\auction-client-1.0.0.jar
```

### macOS / Linux

```bash
java -jar ./auction-client-1.0.0.jar
```

Muốn demo nhiều client thì mở thêm terminal và chạy lại lệnh client.

---

## 5. Tài khoản admin mặc định

Khi server khởi động, `AdminSeeder` tạo admin nếu chưa có admin trong database.

| Role | Username | Password |
|---|---|---|
| ADMIN | `admin` | `123456` |

Tài khoản `SELLER` và `BIDDER` được tạo trong màn hình đăng ký của client.

---

## 6. Luồng demo khuyến nghị

1. Chạy server.
2. Chạy ít nhất hai client.
3. Đăng nhập admin.
4. Tạo seller và bidder bằng màn hình đăng ký.
5. Bidder gửi yêu cầu nạp tiền.
6. Admin duyệt yêu cầu nạp tiền.
7. Seller tạo item và auction.
8. Hai bidder cùng vào một auction và đặt giá.
9. Bật auto-bid cho một bidder.
10. Quan sát realtime update, chart, notification, balance update và anti-sniping extension.

---

## 7. Chạy từ source cho developer

Chỉ dùng phần này nếu cần clone repo và tự build/chạy source.

Clone repo:

```cmd
git clone https://github.com/kieran-labs/oop-course-project-uet.git
cd oop-course-project-uet
```

Set biến môi trường bắt buộc cho terminal đang chạy server. Secret phải dài ít nhất 32 bytes UTF-8.

### Windows cmd.exe

```cmd
set JWT_SECRET=auction-demo-secret-1234567890-abcdef-32bytes
```

### Windows PowerShell

```powershell
$env:JWT_SECRET='auction-demo-secret-1234567890-abcdef-32bytes'
```

### macOS / Linux

```bash
export JWT_SECRET='auction-demo-secret-1234567890-abcdef-32bytes'
```

Chạy server từ source:

```cmd
gradlew.bat run
```

Chạy client từ source trong terminal khác:

```cmd
gradlew.bat runClient
```

macOS/Linux thay `gradlew.bat` bằng `./gradlew`.

---

## 8. Build JAR từ source

Build cả server và client JAR:

```cmd
gradlew.bat clean buildJars
```

macOS/Linux:

```bash
./gradlew clean buildJars
```

Output:

```text
build/libs/auction-server-1.0.0.jar
build/libs/auction-client-1.0.0.jar
```

Build riêng:

```cmd
gradlew.bat shadowJar
gradlew.bat shadowClient
```

---

## 9. Database runtime

Stack database mặc định:

```text
Embedded PostgreSQL
  -> PostgreSQL JDBC
  -> HikariCP connection pool
  -> Flyway migrations
  -> JDBI DAOs
```

Ý nghĩa:

- Không cần cài PostgreSQL riêng khi demo/evaluation.
- Dữ liệu local nằm trong `data/postgres/`.
- Flyway chạy migrations trong `src/main/resources/db/migration`.
- `DatabaseConfig` tạo `HikariDataSource`, chạy migrations, rồi tạo JDBI.
- Có thể dùng PostgreSQL ngoài bằng `DB_URL`, `DB_USER`, `DB_PASSWORD` nếu cần.

---

## 10. Script Windows có sẵn

| Script | Mục đích |
|---|---|
| `server-start.bat` | Start server nền, ghi log vào `logs/`. |
| `server-stop.bat` | Dừng server local. |
| `server-status.bat` | Kiểm tra server. |
| `db-reset.bat` | Xóa state local generated để reset demo. |

---

## 11. Test và quality checks

```cmd
gradlew.bat spotlessCheck
gradlew.bat test
gradlew.bat check
gradlew.bat jacocoTestReport
```

Một số report:

```text
build/reports/jacoco/test/html/index.html
build/reports/spotbugs/spotbugsMain.html
build/reports/problems/problems-report.html
```

---

## 12. Lỗi thường gặp

### Thiếu `JWT_SECRET`

Dùng lại lệnh server ở mục 3 hoặc set biến môi trường này trong terminal chạy server.

### Port `8080` bị chiếm

```cmd
server-status.bat
server-stop.bat
```

Hoặc kiểm tra thủ công:

```cmd
netstat -ano | findstr :8080
```

### Embedded PostgreSQL bị kẹt sau khi kill process

Nếu chấp nhận xóa dữ liệu demo local:

```cmd
db-reset.bat
```

Hoặc:

```cmd
rmdir /s /q data logs
```

### Client JavaFX không mở

Kiểm tra Java:

```cmd
java -version
gradlew.bat --version
```

Sau đó thử:

```cmd
gradlew.bat clean runClient
```

---

## 13. Thứ tự thao tác an toàn nhất

```text
1. Tải Server JAR và Client JAR.
2. Terminal 1: chạy server bằng lệnh có JWT_SECRET.
3. Đợi server lên localhost:8080.
4. Terminal 2: chạy client.
5. Mở thêm client nếu cần demo realtime bidding.
```
