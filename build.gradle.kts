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
    // Liên kết: tasks shadowJar, shadowServer, shadowClient, buildJars ở cuối file
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
    // Embedded PostgreSQL: tự động khởi động PostgreSQL bên trong JVM khi không
    // có biến môi trường DB_URL. Người dùng chỉ cần chạy java -jar mà không cần
    // cài PostgreSQL riêng.
    // Lần đầu chạy: tải binary PostgreSQL phù hợp với OS (~15MB, tự cache).
    // Liên kết: DatabaseConfig.java kiểm tra DB_URL → nếu không có → dùng cái này.
    implementation("io.zonky.test:embedded-postgres:2.0.7")

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
    //   → Mockito giả lập AuctionDao, trả về data giả
    //   → BidService không biết đang dùng DAO giả → test logic thuần túy
    // mockito-junit-jupiter: tích hợp Mockito với JUnit 5 (@ExtendWith, @Mock)
    // Liên kết: BidServiceTest.java, AuctionServiceTest.java dùng @Mock.
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")

    // SpotBugs annotations: dùng @SuppressFBWarnings để tắt cảnh báo cụ thể
    // khi bạn chắc chắn một đoạn code không có bug dù SpotBugs báo.
    // compileOnly = không đóng gói vào .jar, chỉ dùng khi compile.
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.8.6")
}

// ============================================================================
// JAVAFX — Cấu hình giao diện client
// ============================================================================
// Plugin tự tải JavaFX 21.0.5 cho đúng OS.
// javafx.controls: Button, Label, TableView, LineChart (dùng cho bid history chart)
// javafx.fxml: cho phép load file .fxml (giao diện tách riêng khỏi code)
// Liên kết: ClientApp.java, tất cả file trong src/main/resources/fxml/
javafx {
    version = "21.0.5"
    modules("javafx.controls", "javafx.fxml")
}

// ============================================================================
// APPLICATION — Entry points
// ============================================================================
// mainClass: class nào được chạy khi gõ ./gradlew run
// Ở đây là server (Javalin). Client có task riêng: ./gradlew runClient
application {
    mainClass.set("com.auction.App")
}

// Task chạy JavaFX client riêng biệt
// Trong thực tế khi dev: mở 2 terminal, 1 chạy server, 1 chạy client.
// Hoặc trong IntelliJ tạo 2 Run Configuration.
tasks.register<JavaExec>("runClient") {
    group = "application"
    description = "Chạy JavaFX client"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.auction.ClientApp")

    val os = System.getProperty("os.name").lowercase()
    val platform =
        when {
            os.contains("win") -> "win"
            os.contains("mac") -> "mac"
            else -> "linux"
        }

    val javafxLibPath =
        configurations.runtimeClasspath.get()
            .filter { it.name.contains("javafx") && it.name.contains(platform) }
            .joinToString(File.pathSeparator) { it.absolutePath }

    jvmArgs = listOf(
        "--module-path", javafxLibPath,
        "--add-modules", "javafx.controls,javafx.fxml"
    )
}

// ============================================================================
// TESTING — Cấu hình JUnit 5
// ============================================================================
// useJUnitPlatform(): bảo Gradle dùng JUnit 5 engine (không phải JUnit 4)
// Không có dòng này → Gradle mặc định JUnit 4 → không tìm thấy test nào
// vì annotation @Test của JUnit 4 (org.junit.Test) khác JUnit 5 (org.junit.jupiter.api.Test)
//
// testLogging với FULL exception format: hiện chi tiết lỗi SQL từ PostgreSQL
tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
        showStandardStreams = true
    }
}

// ============================================================================
// JACOCO — Đo test coverage
// ============================================================================
// Chạy test trước (dependsOn) → rồi tạo HTML report
// Report nằm trong build/reports/jacoco/test/html/index.html
// CI pipeline upload report này lên GitHub Artifacts → giám khảo xem được
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        html.required.set(true)
    }
}

// ============================================================================
// CHECKSTYLE — Kiểm tra coding convention
// ============================================================================
// Đọc quy tắc từ config/checkstyle/checkstyle.xml (Google Java Style)
// isIgnoreFailures = false: vi phạm → build FAIL → CI fail → không merge được
// Dùng trong CI (kiểm tra). Developer trên máy dùng Spotless (tự sửa).
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
// Ba JAR được tạo ra:
//   auction.jar        → chạy cả hệ thống (server + client) bằng 1 lệnh
//   auction-server.jar → chỉ server Javalin (tuỳ chọn)
//   auction-client.jar → chỉ client JavaFX (tuỳ chọn)
//
// Chạy: ./gradlew buildJars
// ============================================================================

// ── 1. Fat JAR chính: chạy toàn bộ hệ thống bằng 1 lệnh ────────────────────
// Entry point: Main.java
//   → khởi động server trong background thread
//   → poll /api/health chờ server ready
//   → khởi động JavaFX client trên main thread
//
// Cách dùng:
//   java -jar build/libs/auction.jar
tasks.shadowJar {
    archiveBaseName.set("auction")
    archiveVersion.set("1.0.0")
    archiveClassifier.set("")

    manifest {
        attributes["Main-Class"] = "com.auction.Main"
    }

    // Gộp file META-INF/services — quan trọng cho SLF4J, JDBI, Javalin hoạt động đúng
    mergeServiceFiles()

    // Loại bỏ file chữ ký để tránh lỗi SecurityException khi chạy fat JAR
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
}

// ── 2. Fat JAR chỉ server (tuỳ chọn) ───────────────────────────────────────
// Dùng khi muốn deploy server riêng (ví dụ: server trên máy chủ, client trên máy khác)
//
// Cách dùng:
//   java -jar build/libs/auction-server.jar
tasks.register<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowServer") {
    archiveBaseName.set("auction-server")
    archiveVersion.set("1.0.0")
    archiveClassifier.set("")
    group = "shadow"
    description = "Build fat JAR chỉ server (Javalin + Database)"

    from(sourceSets["main"].output)
    configurations = listOf(project.configurations.runtimeClasspath.get())

    manifest {
        attributes["Main-Class"] = "com.auction.App"
    }

    mergeServiceFiles()
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
}

// ── 3. Fat JAR chỉ client JavaFX (tuỳ chọn) ────────────────────────────────
// Dùng khi server đã chạy sẵn và chỉ cần phân phối client.
// Dùng Launcher thay vì ClientApp trực tiếp — bắt buộc vì fat JAR + JavaFX
// không cho phép class extends Application làm Main-Class.
//
// Cách dùng:
//   java -jar build/libs/auction-client.jar
tasks.register<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowClient") {
    archiveBaseName.set("auction-client")
    archiveVersion.set("1.0.0")
    archiveClassifier.set("")
    group = "shadow"
    description = "Build fat JAR chỉ client JavaFX"

    from(sourceSets["main"].output)
    configurations = listOf(project.configurations.runtimeClasspath.get())

    manifest {
        // Launcher.java là wrapper không extends Application — bắt buộc cho fat JAR JavaFX
        attributes["Main-Class"] = "com.auction.Launcher"
    }

    mergeServiceFiles()
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
}

// ── Shortcut: build cả 3 JAR cùng lúc ──────────────────────────────────────
// Cách dùng:
//   ./gradlew buildJars
tasks.register("buildJars") {
    group = "shadow"
    description = "Build tất cả fat JARs: auction.jar, auction-server.jar, auction-client.jar"
    dependsOn("shadowJar", "shadowServer", "shadowClient")
    doLast {
        println("\n✅ Build hoàn tất! Các file JAR nằm trong build/libs/:")
        println("   auction.jar        ← Chạy cả hệ thống : java -jar build/libs/auction.jar")
        println("   auction-server.jar ← Chỉ server       : java -jar build/libs/auction-server.jar")
        println("   auction-client.jar ← Chỉ client       : java -jar build/libs/auction-client.jar")
    }
}