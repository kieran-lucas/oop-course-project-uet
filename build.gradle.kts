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
    java
    application
    checkstyle
    jacoco
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.diffplug.spotless") version "7.0.2"
    id("com.github.spotbugs") version "6.0.9"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.auction"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Environment variables
    implementation("io.github.cdimascio:dotenv-java:3.0.0")

    // ── DATABASE RUNTIME ────────────────────────────────────────────────────
    // Embedded PostgreSQL: default local/demo mode when DB_URL is not set.
    // External PostgreSQL: CI/advanced deployments can provide DB_URL, DB_USER,
    // and DB_PASSWORD; DatabaseConfig then skips embedded startup and connects to
    // that external database instead. In both modes, Flyway + HikariCP + JDBI are
    // still the migration/pool/DAO stack.
    // Runtime embedded data is stored under data/postgres.
    // First embedded run may download/cache an OS-specific PostgreSQL binary.
    implementation("io.zonky.test:embedded-postgres:2.0.7")

    // ── DATABASE MIGRATION ───────────────────────────────────────────────────
    implementation("org.flywaydb:flyway-core:10.15.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.15.0")

    // ── SERVER ──────────────────────────────────────────────────────────────
    implementation("io.javalin:javalin:6.4.0")

    // ── JSON ────────────────────────────────────────────────────────────────
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")

    // ── DATABASE ────────────────────────────────────────────────────────────
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("org.jdbi:jdbi3-core:3.45.4")
    implementation("org.jdbi:jdbi3-sqlobject:3.45.4")
    implementation("org.jdbi:jdbi3-postgres:3.45.4")

    // ── LOGGING ─────────────────────────────────────────────────────────────
    implementation("ch.qos.logback:logback-classic:1.5.15")

    // ── SECURITY ────────────────────────────────────────────────────────────
    implementation("at.favre.lib:bcrypt:0.10.2")
    implementation("com.auth0:java-jwt:4.4.0")

    // ── TESTING ─────────────────────────────────────────────────────────────
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.15.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.15.2")
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.web")
}

application {
    mainClass.set("com.auction.App")
}

tasks.register<JavaExec>("runClient") {
    group = "application"
    description = "Run the JavaFX auction client"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.auction.Launcher")
}

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

val jacocoExclusions = listOf(
    "com/auction/ui/**",
    "com/auction/App.class",
    "com/auction/ClientApp.class",
    "com/auction/Launcher.class",
    "com/auction/util/RestClient.class",
    "com/auction/util/WebSocketClient.class",
    "com/auction/util/BackgroundBidWatcher.class",
    "com/auction/util/UserBalanceWatcher.class",
    "com/auction/util/NotificationStore.class",
    "com/auction/controller/AuctionWebSocketHandler.class"
)

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        sourceSets.main.get().output.asFileTree.matching {
            exclude(jacocoExclusions)
        }
    )
    reports {
        html.required.set(true)
        xml.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        sourceSets.main.get().output.asFileTree.matching {
            exclude(jacocoExclusions)
        }
    )
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

checkstyle {
    toolVersion = "10.21.1"
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
}

spotless {
    java {
        googleJavaFormat("1.25.2")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

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
    onlyIf { file(".git").exists() }
    doLast {
        exec {
            commandLine("git", "config", "core.hooksPath", ".githooks")
        }
    }
}

tasks.shadowJar {
    archiveBaseName.set("auction-server")
    archiveVersion.set("1.0.0")
    archiveClassifier.set("")

    manifest {
        attributes["Main-Class"] = "com.auction.App"
    }

    mergeServiceFiles()

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

    exclude("org/checkerframework/**")
    exclude("org/intellij/**")
    exclude("org/jetbrains/**")

    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
}

tasks.register<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowClient") {
    archiveBaseName.set("auction-client")
    archiveVersion.set("1.0.0")
    archiveClassifier.set("")
    group = "shadow"
    description = "Build fat JAR client JavaFX"

    from(sourceSets["main"].output)
    configurations = listOf(project.configurations.runtimeClasspath.get())

    manifest {
        attributes["Main-Class"] = "com.auction.Launcher"
    }

    exclude("io/javalin/**")
    exclude("org/eclipse/jetty/**")
    exclude("io/zonky/**")
    exclude("org/postgresql/**")
    exclude("com/zaxxer/**")
    exclude("org/jdbi/**")
    exclude("db/migration/**")

    exclude("org/checkerframework/**")
    exclude("org/intellij/**")
    exclude("org/jetbrains/**")

    mergeServiceFiles()
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
}

tasks.register("buildJars") {
    group = "shadow"
    description = "Build server JAR và client JAR"
    dependsOn("shadowJar", "shadowClient")
    doLast {
        println("\nBuild hoan tat! Cac file JAR nam trong build/libs/:")
        println("   auction-server-1.0.0.jar <-- Chay server truoc: java -jar build/libs/auction-server-1.0.0.jar")
        println("   auction-client-1.0.0.jar <-- Roi chay client:  java -jar build/libs/auction-client-1.0.0.jar")
    }
}
