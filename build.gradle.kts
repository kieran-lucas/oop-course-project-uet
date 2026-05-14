/*
 * ============================================================================
 * build.gradle.kts — Cấu hình Gradle cho hệ thống đấu giá trực tuyến
 * ============================================================================
 *
 * File này là "bộ não" của project. Nó khai báo:
 *   1. Plugins: khả năng mà Gradle thêm vào (biên dịch Java, chạy app, format code...)
 *   2. Dependencies: thư viện bên ngoài mà project sử dụng
 *   3. Cấu hình: JavaFX, test, coverage, coding convention
 *
 * Khi chạy bất kỳ lệnh Gradle nào (./gradlew build, test, run...),
 * Gradle đọc file này TRƯỚC TIÊN để biết phải làm gì.
 *
 * Liên kết:
 *   - settings.gradle.kts → đặt tên project
 *   - config/checkstyle/checkstyle.xml → quy tắc convention
 *   - .github/workflows/ci.yml → CI pipeline chạy các lệnh Gradle
 *   - src/main/java/com/auction/App.java → mainClass cho server
 *   - src/main/java/com/auction/ClientApp.java → mainClass cho client
 */

plugins {
    // Plugin java: cho phép Gradle biên dịch .java → .class
    // Thêm các task: compileJava, processResources, classes, jar
    java

    // Plugin application: cho phép chạy app bằng ./gradlew run
    // Thêm các task: run, startScripts, distZip
    application

    // Plugin checkstyle: kiểm tra code có tuân thủ coding convention không
    // Thêm task: checkstyleMain, checkstyleTest
    // Đọc quy tắc từ config/checkstyle/checkstyle.xml
    checkstyle

    // Plugin jacoco: đo test coverage (bao nhiêu % code được test chạy qua)
    // Thêm task: jacocoTestReport → tạo HTML report trong build/reports/jacoco/
    // CI pipeline upload report này lên GitHub Artifacts
    jacoco

    // Plugin javafxplugin: tự động tải JavaFX cho đúng hệ điều hành
    // Windows tải javafx-win, Mac tải javafx-mac, Linux tải javafx-linux
    // Không có plugin này, phải tải JavaFX tay và config module path — rất phức tạp
    id("org.openjfx.javafxplugin") version "0.1.0"

    // Plugin spotless: tự động format code theo Google Java Style
    // Thêm task: spotlessApply (tự sửa), spotlessCheck (chỉ kiểm tra, CI dùng)
    id("com.diffplug.spotless") version "7.0.2"

    // Plugin spotbugs: phát hiện bug patterns trong bytecode
    // Thêm task: spotbugsMain, spotbugsTest
    // Tìm: null dereference, resource leak, concurrency issues, bad practice
    // Report HTML tại build/reports/spotbugs/spotbugsMain.html
    id("com.github.spotbugs") version "6.0.9"

    // Plugin shadow: đóng gói fat JAR (uber JAR) chứa tất cả dependencies
    // Thêm task: shadowJar → tạo file .jar có thể chạy bằng java -jar
    // Liên kết: tasks shadowJar, shadowClient, buildJars ở cuối file
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

// Thông tin project — hiện trong output build và trong file .jar khi đóng gói
group = "com.auction"
version = "1.0.0"

// Yêu cầu Java 21 — đảm bảo mọi người dùng cùng version
// Nếu máy ai cài Java 17 hoặc thấp hơn, Gradle sẽ báo lỗi rõ ràng
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// Maven Central: kho thư viện online lớn nhất cho Java
// Khi khai báo dependency, Gradle tải file .jar từ đây
repositories {
    mavenCentral()
}

// ============================================================================
// DEPENDENCIES — Thư viện bên ngoài
// ============================================================================
//
// Mỗi dòng implementation("nhóm:tên:version") = 1 thư viện.
// Gradle tự tải từ Maven Central lần đầu, cache lại cho lần sau.
//
// implementation = dùng khi chạy app (đóng gói vào sản phẩm cuối)
// testImplementation = chỉ dùng khi chạy test (không đóng gói)
//
dependencies {
    // Environment variables
    implementation("io.github.cdimascio:dotenv-java:3.0.0")

    // ── EMBEDDED DATABASE ────────────────────────────────────────────────────
    // Embedded PostgreSQL: server luôn khởi động PostgreSQL riêng bên trong JVM.
    // Không đọc DB_URL và không cần cài PostgreSQL trên máy người dùng.
    // Runtime lưu dữ liệu bền tại data/postgres.
    // Lần đầu chạy: tải binary PostgreSQL phù hợp với OS (~15MB, tự cache).
    implementation("io.zonky.test:embedded-postgres:2.0.7")

    // ── DATABASE MIGRATION ───────────────────────────────────────────────────
    // Flyway: tự động chạy các file migration SQL khi khởi động server
    // Đảm bảo schema database luôn khớp với version code hiện tại.
    // Migration files: src/main/resources/db/migration/V*.sql
    // Liên kết: DatabaseConfig.java gọi Flyway.migrate() sau khi tạo dataSource
    implementation("org.flywaydb:flyway-core:10.15.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.15.0")

    // ── SERVER ──────────────────────────────────────────────────────────────
    // Javalin: HTTP server + WebSocket server
    // Đây là framework đã chọn (Mảng 5).
    // Bên trong Javalin dùng Jetty (web server) — ta không cần biết chi tiết Jetty,
    // chỉ làm việc với API của Javalin.
    // Liên kết: App.java tạo Javalin instance, đăng ký routes.
    implementation("io.javalin:javalin:6.4.0")

    // ── JSON ────────────────────────────────────────────────────────────────
    // Jackson: chuyển Java object ↔ JSON string
    // Javalin mặc định dùng Jackson khi gọi ctx.json() hoặc ctx.bodyAsClass()
    // jackson-databind: core — serialize/deserialize Java object
    // jackson-datatype-jsr310: hỗ trợ Java 8 time (LocalDateTime, Instant...)
    //   → Cần cho: Auction.startTime, Auction.endTime, BidTransaction.createdAt
    //   → Không có module này, Jackson ghi LocalDateTime thành mảng [2024,1,15,10,30]
    //     thay vì string "2024-01-15T10:30:00" — rất khó đọc và parse phía client
    // Liên kết: DTO classes dùng Jackson annotation nếu cần (@JsonIgnore, @JsonProperty)
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")

    // ── DATABASE ────────────────────────────────────────────────────────────
    // PostgreSQL JDBC driver: cho phép Java nói chuyện với PostgreSQL
    // Đây là "phiên dịch viên" giữa Java code và PostgreSQL server.
    // Không có driver này, Java không biết giao thức PostgreSQL.
    // Liên kết: DatabaseConfig.java dùng driver này khi tạo connection.
    implementation("org.postgresql:postgresql:42.7.4")

    // HikariCP: connection pool
    // Thay vì mỗi request mở connection mới (tốn 5-10ms), HikariCP giữ sẵn
    // pool ~10 connections đã mở. Request nào cần → lấy ra dùng → trả lại.
    // Nhanh hơn rất nhiều và tránh "hết connection" khi nhiều request cùng lúc.
    // Liên kết: DatabaseConfig.java tạo HikariDataSource → JDBI dùng pool này.
    implementation("com.zaxxer:HikariCP:6.2.1")

    // JDBI: SQL wrapper trên JDBC (Mảng 6)
    // jdbi3-core: API chính — Handle, Query, Update
    // jdbi3-sqlobject: cho phép viết DAO bằng interface + annotation
    // jdbi3-postgres: hỗ trợ kiểu dữ liệu riêng của PostgreSQL
    // Liên kết: tất cả DAO classes dùng JDBI để chạy SQL.
    implementation("org.jdbi:jdbi3-core:3.45.4")
    implementation("org.jdbi:jdbi3-sqlobject:3.45.4")
    implementation("org.jdbi:jdbi3-postgres:3.45.4")

    // ── LOGGING ─────────────────────────────────────────────────────────────
    // Logback: thư viện ghi log (implement SLF4J interface)
    // Javalin BẮT BUỘC cần SLF4J + 1 implementation → Logback là phổ biến nhất.
    // Không có → khởi động Javalin sẽ báo "SLF4J: No providers were found"
    // Cấu hình log level trong src/main/resources/logback.xml
    implementation("ch.qos.logback:logback-classic:1.5.15")

    // ── SECURITY ────────────────────────────────────────────────────────────
    // BCrypt: hash password một chiều
    // Khi user đăng ký, password được hash bằng BCrypt trước khi lưu database.
    // Khi login, hash password user gửi lên → so sánh với hash trong DB.
    // Ngay cả admin nhìn database cũng không biết password gốc.
    // Liên kết: UserService.java dùng khi register() và login().
    implementation("at.favre.lib:bcrypt:0.10.2")

    // JWT (JSON Web Token): xác thực user sau khi đăng nhập (Mảng 14)
    // Khi login thành công, server tạo JWT token chứa {userId, role, expiration},
    // ký bằng secret key, trả cho client.
    // Mỗi request sau, client gửi kèm token trong header Authorization.
    // Server verify chữ ký → biết đây là ai → KHÔNG cần tra database.
    // Liên kết:
    //   - config/JwtUtil.java: tạo token, verify token
    //   - controller/AuthController.java: trả token khi login
    //   - App.java: Javalin middleware kiểm tra token trước mỗi request
    implementation("com.auth0:java-jwt:4.4.0")

    // ── TESTING ─────────────────────────────────────────────────────────────
    // JUnit 5: framework chạy test (Mảng 8)
    // junit-bom: quản lý version thống nhất cho tất cả module JUnit
    // junit-jupiter: API viết test (@Test, @Nested, @DisplayName, assertEquals...)
    // junit-platform-launcher: engine chạy test (Gradle cần cái này)
    // Liên kết: tất cả file *Test.java trong src/test/ dùng JUnit 5.
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Mockito: tạo mock object để test từng lớp độc lập
    // Ví dụ: test BidService mà không cần PostgreSQL thật
    //   → Mock AuctionDao, BidDao, WebSocketHandler
    //   → Test LOGIC của BidService mà không lo database hay network
    // Liên kết: test service layer (BidServiceTest, AuctionServiceTest...)
    testImplementation("org.mockito:mockito-core:5.15.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.15.2")
}

// ============================================================================
// JAVAFX — UI Framework cho client
// ============================================================================
// javafx-controls: Button, Label, TextField, ListView...
// javafx-fxml: đọc .fxml file (UI được thiết kế trong Scene Builder)
// javafx-web: WebView component — nhúng browser vào JavaFX
// Khi compile, plugin tự tải các module JavaFX phù hợp với OS
javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.web")
}

// ============================================================================
// APPLICATION — Entry point
// ============================================================================
// Khi chạy ./gradlew run → Gradle sẽ chạy main() của class này
// Server mặc định: App.java
application {
    mainClass.set("com.auction.App")
}

tasks.register<JavaExec>("runClient") {
    group = "application"
    description = "Run the JavaFX auction client"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.auction.Launcher")
}

// ============================================================================
// TESTING — JUnit 5
// ============================================================================
// useJUnitPlatform(): bắt buộc để Gradle biết chạy JUnit 5 (không phải JUnit 4)
tasks.test {
    useJUnitPlatform()
    maxHeapSize = "512m"
    environment("JWT_SECRET", "test-only-jwt-secret-with-at-least-32-bytes")
    systemProperty("auction.db.dir", layout.buildDirectory.dir("embedded-postgres/test").get().asFile.absolutePath)
    systemProperty("auction.db.pid", layout.buildDirectory.file("embedded-postgres/test-postgres.pid").get().asFile.absolutePath)

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

// ============================================================================
// JACOCO — Test coverage
// ============================================================================
// Đo độ bao phủ test (bao nhiêu % code được chạy qua khi test)
// Chạy ./gradlew jacocoTestReport → tạo HTML report tại build/reports/jacoco/test/html/
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        html.required.set(true)
        xml.required.set(true)
        csv.required.set(false)
    }
}

// ============================================================================
// CHECKSTYLE — Kiểm tra coding convention
// ============================================================================
// Đọc quy tắc từ config/checkstyle/checkstyle.xml
// Chạy ./gradlew checkstyleMain → kiểm tra code trong src/main/
// Chạy ./gradlew checkstyleTest → kiểm tra code trong src/test/
// isIgnoreFailures = false → build FAIL nếu vi phạm convention
checkstyle {
    toolVersion = "10.21.1"
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
}

// ============================================================================
// SPOTLESS — Tự động format code
// ============================================================================
// googleJavaFormat: dùng formatter chính thức của Google
// Chạy ./gradlew spotlessApply → tự sửa toàn bộ code cho đúng convention
// Chạy ./gradlew spotlessCheck → chỉ kiểm tra, không sửa (CI dùng cái này)
// Liên kết: .editorconfig đảm bảo IDE format đúng indent/charset TRƯỚC KHI
// Spotless chạy → giảm số lượng thay đổi.
spotless {
    java {
        googleJavaFormat("1.25.2")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// ============================================================================
// SPOTBUGS — Phát hiện bug patterns trong bytecode
// ============================================================================
// SpotBugs phân tích bytecode (.class) sau khi compile để tìm:
//   - Null dereference: dùng biến có thể null mà không kiểm tra
//   - Resource leak: mở stream/connection mà không đóng
//   - Concurrency issues: race condition, incorrect synchronization
//   - Bad practice: equals() không nhất quán với hashCode(), v.v.
//
// effort = MAX: phân tích kỹ nhất (chậm hơn ~30s nhưng tìm được nhiều hơn)
// reportLevel = HIGH: chỉ báo HIGH confidence bugs, bỏ qua LOW/MEDIUM
//   → Giảm false positive — chỉ báo những gì thực sự đáng lo
//
// Chạy: ./gradlew spotbugsMain
// Report: build/reports/spotbugs/spotbugsMain.html (mở bằng browser)
//
// Nếu muốn tắt một cảnh báo cụ thể (khi chắc chắn không phải bug):
//   @SuppressFBWarnings("NP_NULL_ON_SOME_PATH")
//   private void myMethod() { ... }
spotbugs {
    effort = com.github.spotbugs.snom.Effort.MAX
    reportLevel = com.github.spotbugs.snom.Confidence.HIGH
    ignoreFailures = false
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.create("html") {
        required = true
        outputLocation =
            layout.buildDirectory.file("reports/spotbugs/${name}.html")
    }
}

tasks.compileJava {
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.register("installGitHooks") {
    description = "Configure git to use .githooks directory"
    doLast {
        exec {
            commandLine("git", "config", "core.hooksPath", ".githooks")
        }
    }
}

tasks.named("build") {
    dependsOn("installGitHooks")
}

// ============================================================================
// SHADOW — Đóng gói Fat JAR (uber JAR)
// ============================================================================
// Hai JAR được tạo ra:
//   auction-server.jar → chạy Javalin server + embedded PostgreSQL
//   auction-client.jar → chạy JavaFX client (cần server đang chạy trước)
//
// Chạy: ./gradlew buildJars
// ============================================================================

// ── 1. Fat JAR server ────────────────────────────────────────────────────────
// Entry point: App.java (Javalin + Database)
// Embedded PostgreSQL luôn tự khởi động và lưu dữ liệu tại data/postgres.
//
// Cách dùng:
//   java -jar build/libs/auction-server.jar
tasks.shadowJar {
    archiveBaseName.set("auction-server")
    archiveVersion.set("1.0.0")
    archiveClassifier.set("")

    manifest {
        attributes["Main-Class"] = "com.auction.App"
    }

    // Gộp file META-INF/services — quan trọng cho SLF4J, JDBI, Javalin hoạt động đúng
    mergeServiceFiles()

    // Loại bỏ JavaFX — chỉ cần ở client, server không có UI
    exclude("javafx/**")
    exclude("com/sun/javafx/**")
    exclude("com/sun/prism/**")
    exclude("com/sun/glass/**")
    exclude("com/sun/marlin/**")
    exclude("com/sun/scenario/**")
    exclude("com/sun/pisces/**")
    exclude("com/sun/openpisces/**")
    exclude("javafx_font.dll")
    exclude("javafx_iio.dll")
    exclude("javafx-swt.jar")
    exclude("javafx.properties")

    // Loại bỏ compile-time annotations — không cần khi chạy
    exclude("org/checkerframework/**")
    exclude("org/intellij/**")
    exclude("org/jetbrains/**")

    // Loại bỏ file chữ ký để tránh lỗi SecurityException khi chạy fat JAR
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
}

// ── 2. Fat JAR client JavaFX ─────────────────────────────────────────────────
// Entry point: Launcher.java → ClientApp.java
// Dùng Launcher thay vì ClientApp trực tiếp — bắt buộc vì fat JAR + JavaFX
// không cho phép class extends Application làm Main-Class.
//
// Lưu ý: Server phải đang chạy trước khi khởi động client.
//
// Cách dùng:
//   java -jar build/libs/auction-client.jar
tasks.register<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowClient") {
    archiveBaseName.set("auction-client")
    archiveVersion.set("1.0.0")
    archiveClassifier.set("")
    group = "shadow"
    description = "Build fat JAR client JavaFX"

    from(sourceSets["main"].output)
    configurations = listOf(project.configurations.runtimeClasspath.get())

    manifest {
        // Launcher.java là wrapper không extends Application — bắt buộc cho fat JAR JavaFX
        attributes["Main-Class"] = "com.auction.Launcher"
    }

    // Loại bỏ server-only dependencies — client chỉ cần giao tiếp HTTP, không chạy server
    exclude("io/javalin/**")        // Javalin HTTP server
    exclude("org/eclipse/jetty/**") // Jetty (web server bên trong Javalin)
    exclude("io/zonky/**")          // Embedded PostgreSQL
    exclude("org/postgresql/**")    // PostgreSQL JDBC driver
    exclude("com/zaxxer/**")        // HikariCP connection pool
    exclude("org/jdbi/**")          // JDBI SQL wrapper
    exclude("db/migration/**")      // Flyway migration SQL files

    // Loại bỏ compile-time annotations — không cần khi chạy
    exclude("org/checkerframework/**")
    exclude("org/intellij/**")
    exclude("org/jetbrains/**")

    mergeServiceFiles()
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
}

// ── Shortcut: build cả 2 JAR cùng lúc ──────────────────────────────────────
// Cách dùng:
//   ./gradlew buildJars
tasks.register("buildJars") {
    group = "shadow"
    description = "Build server JAR và client JAR"
    dependsOn("shadowJar", "shadowClient")
    doLast {
        println("\nBuild hoan tat! Cac file JAR nam trong build/libs/:")
        println("   auction-server.jar <-- Chay server truoc : java -jar build/libs/auction-server.jar")
        println("   auction-client.jar <-- Roi chay client   : java -jar build/libs/auction-client.jar")
    }
}
