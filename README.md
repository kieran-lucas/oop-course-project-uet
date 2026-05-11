<div align="center">

<!-- Replace with your actual logo/banner -->
<img src="assets/app-screenshot.png" alt="Online Auction System" width="600"/>

# Online Auction System

*A real-time, multi-client auction platform built with Java 21 — featuring concurrent bidding, auto-bid, and anti-sniping.*

[![CI](https://img.shields.io/badge/CI-passing-brightgreen?style=for-the-badge&logo=githubactions&logoColor=white)](https://github.com/kieran-labs/oop-course-project-uet/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/JAVA-21-orange?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Coverage](https://img.shields.io/badge/COVERAGE-JaCoCo-brightgreen?style=for-the-badge)](https://github.com/kieran-labs/oop-course-project-uet/actions)
[![SpotBugs](https://img.shields.io/badge/SPOTBUGS-passing-yellow?style=for-the-badge)](https://github.com/kieran-labs/oop-course-project-uet/actions)

[![Javalin](https://img.shields.io/badge/JAVALIN-6.4.0-black?style=for-the-badge)](https://javalin.io)
[![PostgreSQL](https://img.shields.io/badge/POSTGRESQL-embedded-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)](https://github.com/zonkyio/embedded-postgres)
[![License](https://img.shields.io/badge/LICENSE-MIT-blue?style=for-the-badge)](LICENSE)

</div>

---

## 📌 Table of Contents

- [Overview](#-overview)
- [Demo](#-demo)
- [Features](#-features)
- [Architecture](#-architecture)
- [Tech Stack](#-tech-stack)
- [Project Structure](#-project-structure)
- [Getting Started](#-getting-started)
- [Reports](#-reports)

---

## 🧩 Overview

A full-stack desktop auction platform implementing a **JavaFX client** communicating with a **Javalin HTTP/WebSocket server** backed by an embedded PostgreSQL database. The system supports multiple concurrent bidders with real-time price updates pushed over WebSocket, automated bidding logic, and anti-sniping protection.

The project scope covers three user roles (Admin, Seller, Bidder), three item categories (Electronics, Art, Vehicle), and a complete auction lifecycle from creation through payment.

---

## 🎬 Demo

> 📹 **[Watch Demo Video](#)** — *(update link before submission)*
>
> 📄 **[View PDF Report](#)** — *(update link before submission)*

---

## ✅ Features

### Core Auction Flow
- **User authentication** — Register/Login with BCrypt-hashed passwords and JWT session tokens
- **Role-based access** — Admin, Seller, and Bidder roles with endpoint-level enforcement
- **Item management** — Sellers create items across three categories (Electronics, Art, Vehicle)
- **Auction lifecycle** — Full state machine: `OPEN → RUNNING → FINISHED → PAID / CANCELED`
- **Manual bidding** — Real-time bid validation against current price, persisted atomically

### Advanced Features
- **⚡ Real-time updates** — WebSocket push for every bid update, time extension, and auction end event
- **🤖 Auto-bidding** — Configurable max-bid and increment; PriorityQueue ensures fair FIFO resolution among competing auto-bidders
- **🛡️ Anti-sniping** — Bids placed within 30 seconds of deadline automatically extend the auction by 60 seconds
- **📈 Bid history chart** — Live JavaFX LineChart populated from WebSocket stream; no polling required
- **🔒 Concurrent bidding safety** — Two-layer protection: `synchronized` block at application level + `SELECT FOR UPDATE` at database level
- **👤 Deposit & balance system** — Bidders maintain account balance; deposits managed by Admin

### Quality & DevOps
- **CI/CD pipeline** — GitHub Actions: format check → convention check → tests → coverage report
- **Automated formatting** — Spotless (Google Java Style) + Checkstyle + EditorConfig
- **Static analysis** — SpotBugs at MAX effort scanning for null dereference, resource leaks, race conditions
- **Test coverage** — JUnit 5 with Mockito; integration tests run against real embedded PostgreSQL

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────┐
│                CLIENT (JavaFX)                  │
│                                                 │
│   View (.fxml)  ←→  Controller  ←→  Model (DTO) │
│                                                 │
│   ┌──────────────┐    ┌───────────────────┐     │
│   │ REST Client  │    │ WebSocket Client  │     │
│   │ (HTTP calls) │    │ (Event listener)  │     │
│   │ + JWT header │    │ + JWT auth        │     │
│   └──────┬───────┘    └────────┬──────────┘     │
└──────────┼─────────────────────┼────────────────┘
           │ HTTP REST           │ WebSocket
           ▼                    ▼
┌─────────────────────────────────────────────────┐
│                SERVER (Javalin)                 │
│                                                 │
│   ┌──────────────────────────────────────┐      │
│   │  JWT Middleware (verify every request)│      │
│   └──────────────┬───────────────────────┘      │
│   ┌──────────────┐    ┌───────────────────┐     │
│   │ REST Routes  │    │ WebSocket Handler │     │
│   │ (Controller) │    │ (Observer Manager)│     │
│   └──────┬───────┘    └────────┬──────────┘     │
│          │                     │                │
│          ▼                     ▼                │
│   ┌──────────────────────────────────────┐      │
│   │           Service Layer              │      │
│   │   (Business logic + synchronized)    │      │
│   └──────────────┬───────────────────────┘      │
│                  │                              │
│   ┌──────────────▼───────────────────────┐      │
│   │          DAO Layer (JDBI)            │      │
│   └──────────────┬───────────────────────┘      │
└─────────────────-┼──────────────────────────────┘
                   │ SQL (HikariCP pool)
                   ▼
           ┌───────────────┐
           │  PostgreSQL   │
           │  (Embedded)   │
           └───────────────┘
```

### Design Patterns

| Pattern | Where applied | Purpose |
|---|---|---|
| **Observer** | `AuctionEventManager` → `WebSocketObserver` | Push bid/time/end events to all connected clients |
| **Factory Method** | `ItemFactory.create(category, data)` | Instantiate correct `Item` subclass from JSON input |
| **Strategy** | `ManualBidStrategy`, `AutoBidStrategy` | Swap bidding logic without changing `BidService` |
| **State** | `OpenState`, `RunningState`, `FinishedState`, `PaidState`, `CanceledState` | Enforce valid transitions; illegal operations throw exceptions |
| **DAO** | `AuctionDao`, `UserDao`, `BidTransactionDao`, `ItemDao`, `AutoBidConfigDao` | Isolate all SQL from business logic |

### Concurrency Model

Two-layer protection against lost updates under concurrent bids:

```java
// Layer 1 — Application (JVM)
synchronized (auction) {
    // validate → update → persist → notify
}
```

```sql
-- Layer 2 — Database (PostgreSQL)
BEGIN;
SELECT * FROM auctions WHERE id = ? FOR UPDATE;
UPDATE auctions SET current_price = ?, leading_bidder_id = ? WHERE id = ?;
INSERT INTO bid_transactions (...) VALUES (...);
COMMIT;
```

---

## 🛠️ Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 21 (LTS) |
| GUI | JavaFX + FXML | 21 |
| HTTP / WebSocket Server | Javalin | 6.4.0 |
| Database | PostgreSQL (Embedded) | — |
| Connection Pool | HikariCP | 6.2.1 |
| SQL Mapper | JDBI 3 | 3.45.4 |
| JSON | Jackson + JSR310 | 2.18.2 |
| Authentication | JWT (Auth0) | 4.4.0 |
| Password Hashing | BCrypt | 0.10.2 |
| Logging | Logback / SLF4J | 1.5.15 |
| Testing | JUnit 5 + Mockito | 5.11.4 |
| Coverage | JaCoCo | — |
| Build | Gradle (Kotlin DSL) | 8.12.1 |
| Code Style | Checkstyle + Spotless | Google Java Style |
| Static Analysis | SpotBugs | 6.0.9 |
| CI/CD | GitHub Actions | — |

**Runtime requirement:** Java 21+ (no other installation needed — PostgreSQL is embedded)

---

## 📁 Project Structure

```
oop-course-project-uet/
├── src/main/java/com/auction/
│   ├── App.java                    # Server entry point (Javalin + DB)
│   ├── ClientApp.java              # JavaFX client entry point
│   ├── Launcher.java               # Fat JAR wrapper for JavaFX
│   ├── config/                     # DatabaseConfig, JwtUtil
│   ├── controller/                 # REST routes + WebSocket handler
│   ├── service/                    # Business logic (BidService, AuctionService...)
│   ├── dao/                        # SQL access via JDBI
│   ├── model/                      # Domain classes (Entity → User/Item/Auction...)
│   ├── dto/                        # Request/Response transfer objects
│   ├── pattern/
│   │   ├── observer/               # AuctionEventManager, WebSocketObserver
│   │   ├── factory/                # ItemFactory
│   │   ├── strategy/               # ManualBidStrategy, AutoBidStrategy
│   │   └── state/                  # OpenState, RunningState, FinishedState...
│   ├── exception/                  # Domain exceptions (InvalidBid, NotFound...)
│   ├── middleware/                 # JwtMiddleware
│   └── ui/
│       ├── controller/             # JavaFX screen controllers
│       └── util/                   # SceneManager, Navigable
├── src/main/resources/
│   ├── db/migration/               # Flyway SQL: V1–V5
│   ├── ui/fxml/                    # FXML screen definitions
│   ├── css/style.css
│   └── fonts/                      # Lexend font family
├── src/test/                       # JUnit 5 test suites
├── build/libs/
│   ├── auction-server-1.0.0.jar   # ← Fat JAR server
│   └── auction-client-1.0.0.jar   # ← Fat JAR client
├── .github/workflows/ci.yml        # GitHub Actions pipeline
├── config/checkstyle/              # Google Java Style rules
└── build.gradle.kts                # Full build configuration
```

---

## 🚀 Getting Started

### Prerequisites

- **Java 21+** — [Download Adoptium](https://adoptium.net/)
- No other dependencies — PostgreSQL is embedded and starts automatically

Verify your Java version:
```bash
java -version
# Expected: openjdk version "21.x.x"
```

### Option A — Run from prebuilt JARs *(recommended for graders)*

The JARs are already built and committed at `build/libs/`.

**Step 1 — Start the server:**
```bash
java -jar build/libs/auction-server-1.0.0.jar
```
Wait for: `Javalin started in X ms` before launching any client.

**Step 2 — Start one or more clients** (each in a new terminal):
```bash
java -jar build/libs/auction-client-1.0.0.jar
```

### Option B — Build from source

#### macOS / Linux
```bash
git clone https://github.com/kieran-labs/oop-course-project-uet.git
cd oop-course-project-uet

./gradlew buildJars

java -jar build/libs/auction-server-1.0.0.jar

# Run client (new terminal)
java -jar build/libs/auction-client-1.0.0.jar

### Common Gradle Commands

| Command | Description |
|---|---|
| `./gradlew buildJars` | Build server + client fat JARs |
| `./gradlew test` | Run all tests |
| `./gradlew jacocoTestReport` | Generate coverage report |
| `./gradlew spotlessApply` | Auto-format code |
| `./gradlew spotbugsMain` | Run static analysis |
| `./gradlew clean` | Clean build artifacts |

### Troubleshooting

**`initdb: directory "data/postgres" exists but is not empty`**
```bash
# Windows
rmdir /s /q data\postgres

# Linux / Mac
rm -rf data/postgres
```
Then rerun the server. This happens when a previous run did not shut down cleanly.

---

## 📄 Reports

| Resource | Link |
|---|---|
| 📹 Demo Video | [Watch on YouTube / Drive](#) *(update before submission)* |
| 📄 PDF Report | [View Report](#) *(update before submission)* |
| 📊 CI Pipeline | [GitHub Actions](https://github.com/kieran-labs/oop-course-project-uet/actions) |
| 📈 Coverage Report | Available as artifact in each CI run |

---

## 📜 License

Distributed under the MIT License. See [`LICENSE`](LICENSE) for details.

---

<div align="center">
<sub>Built for Advanced Programming (LTNC) — University of Engineering and Technology, VNU Hanoi</sub>
</div>
