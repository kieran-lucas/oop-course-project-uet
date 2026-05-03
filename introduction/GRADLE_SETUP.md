# Setup Gradle Project — Hướng dẫn cho GitHub Desktop

## Tổng quan file cần thêm vào repo

```
auction-system/              ← repo đã clone
├── build.gradle.kts         ← cấu hình Gradle (dependencies, plugins)
├── settings.gradle.kts      ← tên project
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties
├── config/
│   └── checkstyle/
│       └── checkstyle.xml   ← quy tắc coding convention
├── src/
│   ├── main/
│   │   ├── java/com/auction/
│   │   │   ├── App.java          ← server entry point
│   │   │   ├── ClientApp.java    ← client entry point
│   │   │   ├── model/            ← (folder rỗng, sẽ code sau)
│   │   │   ├── controller/
│   │   │   ├── service/
│   │   │   ├── dao/
│   │   │   ├── pattern/
│   │   │   │   ├── observer/
│   │   │   │   ├── factory/
│   │   │   │   ├── strategy/
│   │   │   │   └── state/
│   │   │   └── config/
│   │   └── resources/
│   │       ├── logback.xml       ← logging config
│   │       ├── db/
│   │       │   └── migration/
│   │       │       └── V1__initial_schema.sql  ← database schema
│   │       └── fxml/             ← (folder rỗng, JavaFX screens sau)
│   └── test/
│       └── java/com/auction/
│           └── SetupTest.java    ← test kiểm tra setup đúng
```

## Các bước thực hiện

### Bước 1: Copy file vào repo

Tất cả file đã được tạo sẵn. Copy chúng vào folder repo, giữ đúng cấu trúc.

Lưu ý: folder rỗng (model/, controller/, v.v.) sẽ không hiện trong
GitHub Desktop vì Git không track folder rỗng. Để Git track chúng,
tạo file .gitkeep rỗng trong mỗi folder:

Mở CMD trong folder repo, chạy:
```cmd
cd src\main\java\com\auction
for %d in (model controller service dao config) do copy nul %d\.gitkeep
cd pattern
for %d in (observer factory strategy state) do copy nul %d\.gitkeep
cd ..\..\..\..\..
cd src\main\resources\fxml
copy nul .gitkeep
```

### Bước 2: Tạo Gradle wrapper

Gradle wrapper cho phép chạy Gradle mà không cần cài Gradle global.
Cần tạo wrapper một lần:

**Cách 1 — Nếu đã cài Gradle trên máy:**
Mở CMD trong folder repo:
```cmd
gradle wrapper --gradle-version 8.12
```

**Cách 2 — Nếu chưa cài Gradle:**
1. Tải Gradle 8.12 từ https://gradle.org/releases/
2. Giải nén, thêm folder bin/ vào PATH
3. Mở CMD trong folder repo:
   ```cmd
   gradle wrapper --gradle-version 8.12
   ```

**Cách 3 — Dùng IntelliJ (đơn giản nhất):**
1. Mở IntelliJ → Open → chọn folder repo
2. IntelliJ sẽ thấy build.gradle.kts → hỏi "Import Gradle project?" → Yes
3. IntelliJ tự tải Gradle và tạo wrapper

Sau bước này, trong repo sẽ có thêm:
- gradlew (Linux/Mac)
- gradlew.bat (Windows)
- gradle/wrapper/gradle-wrapper.jar

### Bước 3: Kiểm tra build

Mở CMD/Terminal trong folder repo:

```cmd
.\gradlew build
```

Lần đầu sẽ tải dependencies (~2-3 phút). Nếu thấy "BUILD SUCCESSFUL" → OK.

### Bước 4: Chạy test

```cmd
.\gradlew test
```

Phải thấy:
```
SetupTest > JUnit 5 is working PASSED
SetupTest > Java 21 features are available PASSED

BUILD SUCCESSFUL
```

### Bước 5: Thử format code

```cmd
.\gradlew spotlessApply
```

Spotless sẽ auto-format tất cả Java files theo Google Java Style.

### Bước 6: Chạy server (test nhanh)

```cmd
.\gradlew run
```

Mở trình duyệt → http://localhost:8080/api/health
Phải thấy: {"status":"ok"}

Nhấn Ctrl+C trong CMD để tắt server.

### Bước 7: Commit và push

1. Mở GitHub Desktop → thấy rất nhiều file mới
2. Summary: `chore: setup Gradle project with all dependencies`
3. Commit to main → Push origin

⚠️ Nếu branch protection đã bật, bạn cần tạo branch:
1. Tạo branch: `chore/gradle-setup`
2. Commit vào branch đó
3. Push → tạo PR → merge

### Bước 8: Các thành viên khác pull và mở IntelliJ

1. GitHub Desktop → Fetch → Pull
2. Mở IntelliJ → Open → chọn folder repo
3. IntelliJ tự nhận Gradle project → tải dependencies
4. Thử chạy: .\gradlew test

---

## Lệnh Gradle thường dùng

| Lệnh | Chức năng |
|-------|-----------|
| `.\gradlew build` | Build toàn bộ project |
| `.\gradlew test` | Chạy tất cả test |
| `.\gradlew run` | Chạy server |
| `.\gradlew runClient` | Chạy JavaFX client |
| `.\gradlew spotlessApply` | Auto-format code |
| `.\gradlew spotlessCheck` | Kiểm tra format (CI dùng) |
| `.\gradlew checkstyleMain` | Kiểm tra coding convention |
| `.\gradlew jacocoTestReport` | Tạo báo cáo test coverage |
| `.\gradlew clean` | Xóa build cache |

## Mở project trong IntelliJ

1. File → Open → chọn folder repo (folder chứa build.gradle.kts)
2. Chọn "Open as Project"
3. IntelliJ tự phát hiện Gradle → tải dependencies
4. Đợi indexing xong (~1-2 phút lần đầu)
5. Thử: Run → Edit Configurations → thêm Application:
   - Name: Server
   - Main class: com.auction.App
   - Nhấn OK → Run ▶
