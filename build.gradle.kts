plugins {
    java
    application
    checkstyle
    jacoco
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.diffplug.spotless") version "7.0.2"
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

// === Dependencies ===
dependencies {
    // Javalin (REST + WebSocket server)
    implementation("io.javalin:javalin:6.4.0")

    // Jackson (JSON serialization - Javalin default)
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")

    // PostgreSQL driver
    implementation("org.postgresql:postgresql:42.7.4")

    // HikariCP (connection pool)
    implementation("com.zaxxer:HikariCP:6.2.1")

    // JDBI (database access)
    implementation("org.jdbi:jdbi3-core:3.45.4")
    implementation("org.jdbi:jdbi3-sqlobject:3.45.4")
    implementation("org.jdbi:jdbi3-postgres:3.45.4")

    // SLF4J + Logback (logging - required by Javalin)
    implementation("ch.qos.logback:logback-classic:1.5.15")

    // BCrypt (password hashing)
    implementation("at.favre.lib:bcrypt:0.10.2")

    // JUnit 5 (testing)
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Mockito (mocking in tests)
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
}

// === JavaFX ===
javafx {
    version = "21.0.5"
    modules("javafx.controls", "javafx.fxml")
}

// === Application ===
application {
    mainClass.set("com.auction.App")
}

// Register a task to run the JavaFX client separately
tasks.register<JavaExec>("runClient") {
    group = "application"
    description = "Run the JavaFX client"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.auction.ClientApp")
}

// === Testing ===
tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

// === JaCoCo (test coverage) ===
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        html.required.set(true)
    }
}

// === Checkstyle (Google Java Style) ===
checkstyle {
    toolVersion = "10.21.1"
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
}

// === Spotless (auto-format) ===
spotless {
    java {
        googleJavaFormat("1.25.2")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
