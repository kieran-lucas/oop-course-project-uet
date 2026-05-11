<div align="center">

<img src="assets/app-screenshot.png" alt="Online Auction System Screenshot" width="700"/>

# Online Auction System

*Nền tảng đấu giá desktop thời gian thực — JavaFX client · Javalin server · PostgreSQL · WebSocket*

[![CI](https://img.shields.io/badge/CI-passing-brightgreen?style=for-the-badge&logo=githubactions&logoColor=white)](https://github.com/kieran-labs/oop-course-project-uet/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/JAVA-21-orange?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Coverage](https://img.shields.io/badge/COVERAGE-JaCoCo-brightgreen?style=for-the-badge)](https://github.com/kieran-labs/oop-course-project-uet/actions)
[![SpotBugs](https://img.shields.io/badge/SPOTBUGS-passing-yellow?style=for-the-badge)](https://github.com/kieran-labs/oop-course-project-uet/actions)
[![Javalin](https://img.shields.io/badge/JAVALIN-6.4.0-black?style=for-the-badge)](https://javalin.io)
[![PostgreSQL](https://img.shields.io/badge/POSTGRESQL-16-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/LICENSE-MIT-blue?style=for-the-badge)](LICENSE)

</div>

---

## 📌 Table of Contents

- [Overview](#-overview)
- [Demo](#-demo)
- [Features](#-features)
- [Architecture](#-architecture)
- [Data Flow — End-to-End](#-data-flow--end-to-end)
- [API Reference](#-api-reference)
- [Design Patterns](#-design-patterns)
- [Class Hierarchy](#-class-hierarchy)
- [Tech Stack & Why We Chose It](#-tech-stack--why-we-chose-it)
- [Project Structure](#-project-structure)
- [Getting Started](#-getting-started)
- [Technical Decisions](#-technical-decisions)
- [Known Limitations](#-known-limitations)
- [Team](#-team)
- [Rubric Coverage](#-rubric-coverage)
- [Reports](#-reports)

---

## 🧩 Overview

A full-stack **desktop auction platform** built with Java 21. A **JavaFX client** communicates with a **Javalin HTTP/WebSocket server** backed by a PostgreSQL database (embedded, zero-install). Multiple clients can bid simultaneously with real-time price updates pushed over WebSocket — no polling, no stale data.

**What makes this project non-trivial:**

- Concurrent bid safety through **two independent layers** of locking (JVM `synchronized` + SQL `SELECT FOR UPDATE`)
- **Anti-sniping** protection: bids in the final 30 seconds automatically extend the deadline by 60 seconds
- **Auto-bidding engine** using a `PriorityQueue` with FIFO tie-breaking, capable of chaining multiple auto-bids in a single transaction
- A complete **5-state auction lifecycle** enforced by the State pattern — illegal operations throw typed exceptions, not silent failures
- **12 JavaFX screens** with a dark Navy+Gold theme, fade transitions, and a live `LineChart` fed directly from WebSocket events

The project scope covers **3 user roles** (Admin, Seller, Bidder), **3 item categories** (Electronics, Art, Vehicle), and a complete lifecycle from item creation through payment and password management — **~99 Java files**, 17 test files, 5 database migrations.

---

## 🎬 Demo

> 📹 **[Watch Demo Video](#)** — *(update link before submission)*
>
> 📄 **[View PDF Report](#)** — *(update link before submission)*

---

## ✅ Features

### Core Auction Flow

- **User authentication** — Register/Login with BCrypt-hashed passwords (cost 12) and JWT session tokens
- **Role-based access** — Admin, Seller, Bidder; enforcement at both middleware and service layer
- **Item management** — Sellers create items across 3 categories with category-specific fields (brand / artist / year)
- **Auction lifecycle** — Full state machine: `OPEN → RUNNING → FINISHED → PAID / CANCELED`
- **Manual bidding** — Real-time bid validation against current price, persisted atomically to `bid_transactions`

### Advanced Features

| Feature | Implementation highlight |
|---|---|
| ⚡ **Real-time updates** | WebSocket push for `BID_UPDATE`, `TIME_EXTENDED`, `AUCTION_ENDED`, `AUTO_BID_TRIGGERED` |
| 🤖 **Auto-bidding** | Configurable max-bid + increment; `PriorityQueue` sorted by `registeredAt` ensures fair FIFO |
| 🛡️ **Anti-sniping** | Bids within 30s of deadline → extend by 60s → broadcast `TIME_EXTENDED` to all clients |
| 📈 **Bid history chart** | JavaFX `LineChart` populated live from WebSocket; no REST polling required |
| 🔒 **Concurrency safety** | `synchronized` (app) + `SELECT FOR UPDATE` (DB) — two independent layers |
| 💰 **Deposit system** | Bidder submits deposit request → Admin approves → `balance` credited atomically |
| 🔑 **Password reset** | Admin-reviewed flow: user requests → Admin approves → password reset to default |
| ⏱️ **Auction scheduler** | `ScheduledExecutorService` auto-transitions `OPEN→RUNNING→FINISHED` on time |

### Quality & DevOps

- **CI pipeline** — GitHub Actions: `spotlessCheck → checkstyleMain → test → jacocoTestReport`
- **Pre-commit hook** — `.githooks/pre-commit` auto-runs `spotlessApply` before every commit
- **Static analysis** — SpotBugs at MAX effort (null dereference, resource leaks, race conditions)
- **Code style** — Spotless + Checkstyle enforcing Google Java Style; Checkstyle warnings = 0
- **17 test files** — Unit tests with Mockito + Integration tests against real PostgreSQL (embedded)

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                       CLIENT (JavaFX)                           │
│                                                                 │
│  View (FXML) ←→ ui/controller/ ←→ DTO / Model                  │
│                                                                 │
│  ┌────────────────┐    ┌─────────────────────────┐              │
│  │  RestClient    │    │    WebSocketClient      │              │
│  │ (HTTP calls)   │    │  (Event listener)       │              │
│  │ + JWT header   │    │  + Platform.runLater()  │              │
│  └───────┬────────┘    └───────────┬─────────────┘              │
│          │                        │                            │
│  ┌───────┴────────────────────────┴──────────────────────┐     │
│  │  Utilities: SceneManager · NotificationStore          │     │
│  │             BackgroundBidWatcher · Navigable          │     │
│  └───────────────────────────────────────────────────────┘     │
└──────────────┬──────────────────────────┬───────────────────────┘
               │ HTTP REST                │ WebSocket
               ▼                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                       SERVER (Javalin)                          │
│                                                                 │
│  ┌────────────────────────────────────────────────────────┐     │
│  │       JWT Middleware (verify every /api/* request)     │     │
│  └──────────────────────────┬─────────────────────────────┘     │
│                             │                                   │
│  ┌──────────────────┐    ┌──┴─────────────────────────┐        │
│  │  REST Controllers│    │    AuctionWebSocketHandler │        │
│  │  (Auth/Auction/  │    │    (Observer Manager)      │        │
│  │   Bid/Item)      │    │                            │        │
│  └────────┬─────────┘    └──────────────┬─────────────┘        │
│           │                             │                      │
│           ▼                             ▼                      │
│  ┌─────────────────────────────────────────────────────┐       │
│  │                    Service Layer                    │       │
│  │         (Business logic + synchronized locks)       │       │
│  └──────────────────────────┬──────────────────────────┘       │
│                             │                                  │
│  ┌──────────────────────────▼──────────────────────────┐       │
│  │                    DAO Layer (JDBI)                 │       │
│  └──────────────────────────┬──────────────────────────┘       │
└─────────────────────────────┼───────────────────────────────────┘
                              │ SQL (HikariCP pool)
                              ▼
                    ┌──────────────────┐
                    │   PostgreSQL     │
                    │   (Embedded)     │
                    │   7 tables       │
                    │   5 migrations   │
                    └──────────────────┘
```

### Concurrency — Two Independent Layers

```java
// Layer 1 — Application (JVM): prevent lost updates within the same server instance
synchronized (auction) {
    state.placeBid();               // State pattern: throws if FINISHED/CANCELED
    manualBidStrategy.execute();    // Validate amount > currentPrice
    if (remaining < 30s) extend();  // Anti-sniping
    auctionDao.updateWithLock();    // Layer 2 below
    eventManager.notify();          // Observer: broadcast to WebSocket clients
    autoBidStrategy.execute();      // Chain auto-bids
}
```

```sql
-- Layer 2 — Database: prevent lost updates from multiple server instances
BEGIN;
SELECT * FROM auctions WHERE id = ? FOR UPDATE;  -- row-level lock
UPDATE auctions SET current_price = ?, leading_bidder_id = ? WHERE id = ?;
INSERT INTO bid_transactions (auction_id, bidder_id, amount, auto_bid) VALUES (?, ?, ?, ?);
COMMIT;
```

---

## 🔄 Data Flow — End-to-End

*Kịch bản: Bidder đặt giá 500.000 VNĐ từ JavaFX client*

```
1. AuctionDetailController (JavaFX)
   └─► POST /api/auctions/{id}/bid  { amount: 500000 }  + Authorization: Bearer <JWT>

2. JwtMiddleware
   └─► verifyToken() → extract { userId, username, role }
   └─► BidController.placeBid(ctx)

3. BidService.placeBid()          ← synchronized(auction) {
   ├─► RunningState.placeBid()    ← State: phiên đang chạy, cho phép bid
   ├─► ManualBidStrategy.execute()← validate: 500000 > currentPrice ✓
   ├─► if (remaining < 30s)       ← Anti-sniping check
   │       extend by 60s + notify TIME_EXTENDED
   ├─► AuctionDao.updateForUpdate()← SELECT FOR UPDATE → UPDATE → INSERT
   ├─► eventManager.notify(BID_UPDATE)
   └─► AutoBidStrategy.execute()  ← trigger auto-bid chain if any registered
                                  }

4. AuctionEventManager
   └─► WebSocketObserver.onBidUpdate()
   └─► broadcast BidUpdateMessage (JSON) → all connected clients on this auction

5. All AuctionDetailControllers (all open clients)
   └─► Platform.runLater():
       ├─► update currentPrice label
       ├─► append point to LineChart
       └─► reset countdown timer
```

---

## 📡 API Reference

### REST Endpoints

| Method | Path | Auth | Role | Description |
|---|---|---|---|---|
| `POST` | `/api/auth/register` | ❌ | — | Register (BIDDER / SELLER) |
| `POST` | `/api/auth/login` | ❌ | — | Login → JWT token |
| `POST` | `/api/auth/change-password` | ✅ | Any | Change password |
| `POST` | `/api/auth/forgot-password` | ✅ | Any | Request password reset |
| `GET` | `/api/items` | ✅ | Any | List all items |
| `POST` | `/api/items` | ✅ | SELLER | Create item |
| `GET` | `/api/auctions` | ✅ | Any | List auctions (`?status=`) |
| `GET` | `/api/auctions/{id}` | ✅ | Any | Auction detail (enriched) |
| `POST` | `/api/auctions` | ✅ | SELLER | Create auction |
| `PUT` | `/api/auctions/{id}` | ✅ | SELLER | Edit auction (OPEN only) |
| `DELETE` | `/api/auctions/{id}` | ✅ | SELLER/ADMIN | Cancel auction |
| `POST` | `/api/auctions/{id}/bid` | ✅ | BIDDER | Place manual bid |
| `POST` | `/api/auctions/{id}/auto-bid` | ✅ | BIDDER | Register auto-bid |
| `POST` | `/api/users/me/deposit` | ✅ | BIDDER | Submit deposit request |
| Admin endpoints | `/api/admin/*` | ✅ | ADMIN | Manage users, deposit, reset |

All errors return `ErrorResponse { error: String, message: String }` with the appropriate HTTP status code (400/401/404/409).

### WebSocket Protocol

```
Endpoint: /ws/auction/{auctionId}?token=<JWT>
```

| Direction | `type` field | Payload |
|---|---|---|
| Server → Client | `BID_UPDATE` | `{ currentPrice, leadingBidderUsername, timestamp }` |
| Server → Client | `TIME_EXTENDED` | `{ newEndTime }` |
| Server → Client | `AUCTION_ENDED` | `{ winner, finalPrice }` |
| Server → Client | `AUTO_BID_TRIGGERED` | `{ bidderId, amount }` |

---

## 🧠 Design Patterns

### 1. Observer — Real-time Push

```
AuctionEventManager (Subject)
  └─► Map<auctionId, List<AuctionEventListener>>

AuctionEventListener (Observer interface)
  ├── onBidUpdate(auctionId, price, bidder)
  ├── onTimeExtended(auctionId, newEndTime)
  └── onAuctionEnd(auctionId, winner, finalPrice)

WebSocketObserver (Concrete Observer)
  └─► serializes to BidUpdateMessage JSON → sends over WebSocket
```

**Trigger:** `BidService.placeBid()` succeeds → `eventManager.notify(BID_UPDATE)` → all open `AuctionDetailController` windows update instantly.

### 2. Factory Method — Item Creation

```
ItemFactory.create(CreateItemRequest, sellerId)
  ├── "ELECTRONICS" → new Electronics(name, desc, brand)
  ├── "ART"         → new Art(name, desc, artist)
  └── "VEHICLE"     → new Vehicle(name, desc, year)
```

`ItemService` calls `ItemFactory` and never knows which subclass was instantiated. Invalid category → `InvalidBidException`.

### 3. Strategy — Bid Execution

```
BidStrategy (interface)
  └── execute(auction, bidderId, amount, isAutoBid)

ManualBidStrategy
  └── validate amount > currentPrice → update auction

AutoBidStrategy
  └── PriorityQueue<AutoBidConfig> sorted by registeredAt (FIFO tie-breaking)
  └── while (nextBidder.maxBid > currentPrice): place bid → re-sort → repeat
```

`BidService` selects the strategy at runtime — `synchronized` block wraps both.

### 4. State — Auction Lifecycle

```
AuctionState (interface): placeBid(), close(), edit(), extend()

OpenState     → can edit, cannot bid
RunningState  → can bid + extend, cannot edit
FinishedState → throws on all operations
PaidState     → throws on all operations (terminal)
CanceledState → throws on all operations (terminal)
```

State transitions are driven by `AuctionScheduler` (time-based) and Admin actions. Attempting `placeBid()` on a `FinishedState` throws `AuctionClosedException` → `HTTP 409`.

### 5. DAO — Database Isolation

Each table has exactly one DAO class using JDBI 3. `AuctionDao` uniquely holds `SELECT ... FOR UPDATE` logic — the only SQL that touches concurrency. All DAOs are injected via constructor (testable with embedded PostgreSQL in integration tests).

---

## 🌳 Class Hierarchy

```
Entity (abstract)           ← id: Long, createdAt: LocalDateTime
│
├── User (abstract)         ← username, email, balance: BigDecimal, getRole()
│   ├── Bidder              ← getRole() = "BIDDER"
│   ├── Seller              ← getRole() = "SELLER"
│   └── Admin               ← getRole() = "ADMIN"
│
├── Item (abstract)         ← name, description, sellerId, getCategory()
│   ├── Electronics         ← getCategory() = "ELECTRONICS", + brand: String
│   ├── Art                 ← getCategory() = "ART",         + artist: String
│   └── Vehicle             ← getCategory() = "VEHICLE",     + year: int
│
├── Auction                 ← startingPrice/currentPrice: BigDecimal (not double)
│                              status: OPEN/RUNNING/FINISHED/PAID/CANCELED
│
├── BidTransaction          ← auctionId, bidderId, amount, autoBid: boolean
├── AutoBidConfig           ← maxBid, increment, registeredAt (PriorityQueue key)
├── DepositRecord           ← amount, status: PENDING/APPROVED/REJECTED
└── PasswordResetRecord     ← status: PENDING/APPROVED/REJECTED
```

`BigDecimal` is used throughout for monetary amounts — never `double` or `float`.

---

## 🛠️ Tech Stack & Why We Chose It

| Layer | Technology | Version | Why |
|---|---|---|---|
| Language | Java | 21 (LTS) | Records, sealed classes, pattern matching; long-term support |
| GUI | JavaFX + FXML | 21 | Native desktop UI; declarative FXML separates View from Controller |
| HTTP + WebSocket | Javalin | 6.4.0 | Lightweight (~1 MB); no DI container overhead unlike Spring Boot |
| Database | PostgreSQL (Embedded) | 16 | ACID + `SELECT FOR UPDATE` support; embedded = zero install for graders |
| Connection Pool | HikariCP | 6.2.1 | Lowest-latency JDBC pool available |
| SQL Mapper | JDBI 3 | 3.45.4 | SQL-first (vs ORM magic); explicit queries are easier to reason about |
| JSON | Jackson + JSR310 | 2.18.2 | De-facto standard; JSR310 module handles `LocalDateTime` natively |
| Authentication | JWT (Auth0) | 4.4.0 | Stateless — server holds no session state |
| Password Hashing | BCrypt | 0.10.2 | One-way with salt; cost factor 12 |
| Logging | Logback / SLF4J | 1.5.15 | Industry standard; structured output |
| Testing | JUnit 5 + Mockito | 5.11.4 | Parameterized tests + mock injection |
| Coverage | JaCoCo | — | GitHub Actions artifact; > 60% service layer |
| Build | Gradle (Kotlin DSL) | 8.12.1 | Type-safe build scripts; faster than Maven |
| Code Style | Checkstyle + Spotless | — | Google Java Style enforced in CI and pre-commit hook |
| Static Analysis | SpotBugs | 6.0.9 | MAX effort; catches null dereference and race conditions |
| CI/CD | GitHub Actions | — | Free for public repos; matrix-ready |

---

## 📁 Project Structure

```
auction-system/
│
├── .github/
│   ├── workflows/
│   │   ├── ci.yml                        ← Pipeline: format → lint → test → coverage
│   │   └── commit-graph.yml              ← Commit activity graph
│   └── pull_request_template.md          ← PR checklist
│
├── .githooks/
│   └── pre-commit                        ← Auto spotlessApply before commit
│
├── build.gradle.kts                      ← All dependencies + plugins
├── config/checkstyle/checkstyle.xml      ← Google Java Style rules
│
└── src/
    ├── main/java/com/auction/
    │   ├── App.java                      ← Server entry: Javalin + routes + exception handlers
    │   ├── ClientApp.java                ← Client entry: JavaFX Application
    │   ├── Launcher.java                 ← Fat JAR wrapper (bypasses JavaFX module path)
    │   │
    │   ├── model/                        ← 14 domain classes (Entity hierarchy)
    │   ├── dto/                          ← 13 request/response transfer objects
    │   │
    │   ├── controller/                   ← 5 server-side handlers (REST + WebSocket)
    │   │   ├── AuthController.java
    │   │   ├── AuctionController.java
    │   │   ├── BidController.java
    │   │   ├── ItemController.java
    │   │   └── AuctionWebSocketHandler.java
    │   │
    │   ├── service/                      ← 6 business logic classes
    │   │   ├── BidService.java           ← ★ Core: synchronized + anti-sniping + auto-bid
    │   │   ├── AuctionService.java
    │   │   ├── UserService.java
    │   │   ├── ItemService.java
    │   │   ├── AuctionScheduler.java     ← ScheduledExecutorService for state transitions
    │   │   └── PasswordResetService.java
    │   │
    │   ├── dao/                          ← 7 DAO classes (one per table, JDBI 3)
    │   │
    │   ├── pattern/
    │   │   ├── observer/                 ← AuctionEventManager, WebSocketObserver (3 files)
    │   │   ├── factory/                  ← ItemFactory (1 file)
    │   │   ├── strategy/                 ← ManualBidStrategy, AutoBidStrategy (3 files)
    │   │   └── state/                    ← Open/Running/Finished/Paid/Canceled (6 files)
    │   │
    │   ├── exception/                    ← AuctionException (abstract) + 5 custom exceptions
    │   ├── middleware/                   ← JwtMiddleware
    │   ├── config/                       ← DatabaseConfig (HikariCP + JDBI), JwtUtil
    │   │
    │   ├── ui/
    │   │   ├── controller/               ← 12 JavaFX screen controllers
    │   │   │   └── AuctionDetailController.java  ← ★ WebSocket + LineChart + countdown
    │   │   └── util/                     ← SceneManager (singleton), Navigable (interface)
    │   │
    │   └── util/                         ← RestClient, WebSocketClient,
    │                                        BackgroundBidWatcher, NotificationStore
    │
    ├── main/resources/
    │   ├── db/migration/                 ← V1–V5 Flyway SQL migrations (7 tables)
    │   ├── ui/fxml/                      ← 12 FXML screen definitions
    │   ├── css/style.css                 ← Dark theme: Navy #0A1628 + Gold #C9A96E (~450 lines)
    │   ├── fonts/                        ← Lexend font family (9 weights)
    │   ├── icons/                        ← App icons (6 PNG files)
    │   └── logback.xml                   ← SLF4J logging config
    │
    └── test/java/com/auction/            ← 17 test files (unit + integration)
        ├── service/                      ← UserServiceTest, BidServiceTest ★, AuctionServiceTest
        ├── dao/                          ← 5 integration tests against real PostgreSQL
        ├── exception/                    ← 5 exception hierarchy tests
        └── config/                       ← DatabaseConfigTest, JwtUtilTest
```

---

## 🚀 Getting Started

### Prerequisites

**Java 21+** is the only requirement — PostgreSQL is embedded and starts automatically.

```bash
java -version
# Expected: openjdk version "21.x.x"
```

### Option A — Run from prebuilt JARs *(recommended for graders)*

```bash
# Step 1: Start the server (wait for "Javalin started in X ms")
java -jar build/libs/auction-server-1.0.0.jar

# Step 2: Start one or more clients (each in a new terminal)
java -jar build/libs/auction-client-1.0.0.jar
```

The default Admin account is seeded automatically via `V2__seed_admin.sql`.

### Option B — Build from source

```bash
git clone https://github.com/kieran-labs/oop-course-project-uet.git
cd oop-course-project-uet

# macOS / Linux
./gradlew buildJars

# Windows
gradlew.bat buildJars

# Then run as Option A above
```

### Common Gradle Commands

| Command | Description |
|---|---|
| `./gradlew buildJars` | Build server + client fat JARs |
| `./gradlew test` | Run all 17 test files |
| `./gradlew jacocoTestReport` | Generate coverage report → `build/reports/jacoco/` |
| `./gradlew spotlessApply` | Auto-format all Java code |
| `./gradlew spotbugsMain` | Run static analysis |
| `./gradlew clean` | Clean build artifacts |

### Troubleshooting

**`initdb: directory "data/postgres" exists but is not empty`**

The embedded PostgreSQL data directory was not cleaned up from a previous run:

```bash
# Linux / macOS
rm -rf data/postgres

# Windows
rmdir /s /q data\postgres
```

Then restart the server.

---

## 🤔 Technical Decisions

These are the non-obvious choices made during development and the reasoning behind them.

**Javalin over Spring Boot**
Spring Boot adds significant startup time, annotation-based DI, and abstraction layers that obscure what's actually happening at the network layer. For a course project where understanding the full stack matters, Javalin's explicit `app.post("/path", handler)` pattern makes the routing immediately visible. It also produces a ~50 MB fat JAR vs 100+ MB for Spring Boot.

**Embedded PostgreSQL over H2**
H2's in-memory mode does not support `SELECT FOR UPDATE` with the same semantics as real PostgreSQL. Since concurrency is a core graded requirement, using H2 would mean our integration tests would not actually verify the database-level locking. Embedded PostgreSQL runs the real engine, so tests are meaningful.

**JDBI 3 over Hibernate/JPA**
ORM magic (lazy loading, session lifecycle, proxy objects) adds complexity that is hard to debug and reason about under concurrency. With JDBI, every SQL statement is explicit — it is easy to see exactly what hits the database and in what order. The DAO pattern also makes it straightforward to inject real or test connections.

**Admin-reviewed password reset over email SMTP**
An SMTP-based flow requires environment configuration (email credentials, SMTP server) that complicates the setup for graders. The Admin-reviewed approach — where Admin approves a reset request via the panel — achieves the same security property (a trusted party authorizes the reset) without any external service dependency.

**`BigDecimal` for all monetary amounts**
`double` and `float` arithmetic on monetary values produces floating-point errors (e.g., `0.1 + 0.2 ≠ 0.3`). Every bid amount, balance, and starting price in this system uses `BigDecimal` with explicit scale, stored as `NUMERIC` in PostgreSQL.

**`PriorityQueue` with `registeredAt` for auto-bid fairness**
When two bidders both have auto-bid configured, the one who registered first gets priority. Using `registeredAt: LocalDateTime` as the sort key on a `PriorityQueue<AutoBidConfig>` gives deterministic, fair ordering without any additional logic.

---

## ⚠️ Known Limitations

These are honest trade-offs made given the project scope:

- **Single-server only** — The `synchronized` block at the JVM level only works within a single server instance. A horizontally scaled deployment (multiple Javalin nodes) would need distributed locking (e.g., Redis `SETNX` or PostgreSQL advisory locks) — the DB-level `SELECT FOR UPDATE` remains valid, but the JVM lock would not.

- **No payment gateway** — The `PAID` state exists in the state machine, but actual payment processing is mocked. A real system would integrate a gateway here.

- **Embedded PostgreSQL data directory** — On unclean shutdown, the `data/postgres/` directory may need manual cleanup (see Troubleshooting). A production deployment would use a managed PostgreSQL instance instead.

- **WebSocket reconnection is basic** — `WebSocketClient` attempts reconnection on disconnect, but does not implement exponential backoff. In a flaky network environment, a missed `TIME_EXTENDED` event could leave a client's countdown out of sync until next refresh.

- **Admin password reset resets to `"123456"`** — Sufficient for a course project; a production system would generate a one-time token.

---

## 👥 Team

| Member | Role | Primary Responsibility |
|---|---|---|
| **A** | Backend Lead | Javalin server, 5 REST controllers, WebSocket handler, 7 DAOs, 5 SQL migrations |
| **B** | Frontend Lead | 12 JavaFX UI controllers, 12 FXML screens, SceneManager, Dark CSS theme |
| **C** | Business Logic | 6 service classes, 4 design patterns (13 files), exception hierarchy, JWT/BCrypt |
| **D** | DevOps & QA | CI/CD pipeline, 17 test files, Gradle config, Git workflow, documentation |

All members share ownership of `model/` (14 files), `dto/` (13 files), and `README.md`.

---

## 📊 Rubric Coverage

*For graders — direct mapping from rubric items to source files.*

| Rubric Item | Points | Key Files |
|---|---|---|
| Thiết kế lớp, cây kế thừa | 0.5 | `model/Entity.java`, `User`, `Bidder`, `Seller`, `Admin`, `Item`, `Electronics`, `Art`, `Vehicle` |
| OOP principles | 1.0 | Encapsulation (private fields), Inheritance (Entity→User→Bidder), Polymorphism (`getRole()`, `getCategory()`), Abstraction (abstract `User`, `Item`) |
| Design patterns | 1.0 | `pattern/observer/` · `pattern/factory/` · `pattern/strategy/` · `pattern/state/` · `dao/` |
| Quản lý user, sản phẩm | 1.0 | `AuthController`, `ItemController`, `AuctionController` + all 12 JavaFX screens |
| Chức năng đấu giá | 1.0 | `BidService.placeBid()`, `BidController`, `ManualBidStrategy`, `RunningState` |
| Xử lý lỗi & ngoại lệ | 1.0 | `exception/AuctionException` (abstract) + 5 custom exceptions + handlers in `App.java` |
| Concurrent bidding | 1.0 | `BidService` (`synchronized`) + `AuctionDao` (`SELECT FOR UPDATE`) |
| Realtime update | 0.5 | `AuctionEventManager`, `WebSocketObserver`, `AuctionWebSocketHandler` |
| Client-Server | 0.5 | `App.java` (Javalin) ↔ `ClientApp.java` (JavaFX) via HTTP + WebSocket |
| MVC | 0.5 | `ui/fxml/` (View) + `ui/controller/` (Controller) + `model/` + `dto/` (Model) |
| Build tool + convention | 0.5 | `build.gradle.kts` + `checkstyle.xml` + `.editorconfig` + Spotless |
| Unit Test | 0.5 | `test/` — 17 files, JUnit 5 + Mockito, integration against real PostgreSQL |
| CI/CD | 0.5 | `.github/workflows/ci.yml` — 5-stage pipeline |
| Auto-bidding | 0.5 | `AutoBidStrategy`, `AutoBidConfig`, `AutoBidConfigDao` |
| Anti-sniping | 0.5 | `BidService.placeBid()` (~10 lines + `TIME_EXTENDED` broadcast) |
| Bid History Chart | 0.5 | `AuctionDetailController` + `auction-detail.fxml` (JavaFX `LineChart`) |
| **Tổng** | **11.0** | |

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
