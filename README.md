<div align="center">

<img src="assets/app-screenshot.png" alt="Online Auction System - live bid chart + countdown timer" width="900"/>

# Online Auction System

*A real-time desktop auction platform — JavaFX client · Javalin server · PostgreSQL · WebSocket*

[![CI](https://github.com/kieran-labs/oop-course-project-uet/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/kieran-labs/oop-course-project-uet/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Javalin](https://img.shields.io/badge/Javalin-6.4.0-black)](https://javalin.io)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Embedded%20%2F%20CI%2016-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Gradle](https://img.shields.io/badge/Gradle-Kotlin%20DSL-02303A?logo=gradle&logoColor=white)](https://gradle.org/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

**[Download v1.0.0 JARs](https://github.com/kieran-labs/oop-course-project-uet/releases/tag/v1.0.0)** · **[Setup](docs/SETUP.md)** · **[Schema](docs/SCHEMA.md)** · **[CI](https://github.com/kieran-labs/oop-course-project-uet/actions/workflows/ci.yml)**

</div>

---

## Submission Links

> Final checklist for LTNC submission. Replace the two `TODO` links below with the final PDF/video links before submitting to the course system.

| Item | Link |
|---|---|
| GitHub repository | https://github.com/kieran-labs/oop-course-project-uet |
| Main branch | `main` |
| Prebuilt JARs | https://github.com/kieran-labs/oop-course-project-uet/releases/tag/v1.0.0 |
| Report PDF | `TODO_ADD_REPORT_PDF_LINK` |
| Demo video | `TODO_ADD_DEMO_VIDEO_LINK` |

---

## Evaluator Quick Start

### Requirements

| Requirement | Version / Note |
|---|---|
| Java | JDK **21+** |
| OS | Windows 10+ / macOS / Linux with desktop display |
| Port | `8080` must be free |
| Database | No local PostgreSQL installation required; the server starts embedded PostgreSQL automatically |

### 1. Download the release JARs

Download both files from the release page and place them in the same writable folder, for example `D:\auction-demo\`:

- `auction-server-1.0.0.jar`
- `auction-client-1.0.0.jar`

### 2. Start the server

**Windows PowerShell:**

```powershell
cd D:\auction-demo
$env:JWT_SECRET = "replace-with-a-random-secret-of-at-least-32-bytes"
java -jar auction-server-1.0.0.jar
```

**cmd.exe:**

```cmd
cd /d D:\auction-demo
set JWT_SECRET=replace-with-a-random-secret-of-at-least-32-bytes
java -jar auction-server-1.0.0.jar
```

**macOS / Linux / Git Bash:**

```bash
export JWT_SECRET="replace-with-a-random-secret-of-at-least-32-bytes"
java -jar auction-server-1.0.0.jar
```

The server is ready when Javalin logs that it has started at `http://localhost:8080`. Check it with:

```bash
curl http://localhost:8080/api/health
```

### 3. Start one or more clients

Open another terminal in the same folder:

```bash
java -jar auction-client-1.0.0.jar
```

Run the same command in multiple terminals to open multiple independent JavaFX clients for concurrent bidding and real-time update testing. Only the server needs `JWT_SECRET`; clients do not.

### 4. Default account

| Role | Username | Password |
|---|---|---|
| Admin | `admin` | `123456` |

Additional `SELLER` and `BIDDER` accounts can be created from the Register screen.

> The default admin password is for classroom/demo use. For non-demo use, set `DEFAULT_ADMIN_PASSWORD` before first startup.

---

## Overview

This project implements an online auction system with a JavaFX desktop client and a Javalin REST/WebSocket server. The server owns all database access and persists data in PostgreSQL. The default local run uses embedded PostgreSQL, while CI can use an external PostgreSQL service through `DB_URL`, `DB_USER`, and `DB_PASSWORD`.

Core capabilities:

- Role-based authentication: `ADMIN`, `SELLER`, `BIDDER`
- Seller item management: create, edit, delete items by category
- Auction lifecycle: `OPEN → RUNNING → SETTLING → FINISHED / PAID / CANCELED`
- Manual bidding with integer VND validation
- Concurrent bidding safety through PostgreSQL row-level locking (`SELECT ... FOR UPDATE`)
- Real-time bid updates through WebSocket + Observer pattern
- Auto-bidding with `maxBid`, `increment`, FIFO `PriorityQueue` ordering
- Anti-sniping: bid in final 30 seconds extends the auction by 60 seconds
- Live bid history chart in the JavaFX auction detail screen
- Unit/integration tests, Gradle quality gates, and GitHub Actions CI

---

## Screenshots

| Login | Auction List |
|:---:|:---:|
| <img src="assets/screenshots/login.png" width="420"/> | <img src="assets/screenshots/auction-list.png" width="420"/> |

| Auction Detail | Admin Dashboard |
|:---:|:---:|
| <img src="assets/screenshots/auction-detail.png" width="420"/> | <img src="assets/screenshots/admin.png" width="420"/> |

---

## Architecture

The application is split into a JavaFX desktop client, a Javalin server, and PostgreSQL persistence. The client never accesses the database directly; all protected operations go through JWT-secured REST endpoints or WebSocket channels managed by the server.

```mermaid
flowchart LR
    subgraph Client["JavaFX Desktop Client"]
        FXML["FXML Views"]
        UI["UI Controllers<br/>MVC layer"]
        Scene["SceneManager<br/>Session state"]
        Rest["RestClient<br/>HTTP + JSON"]
        WsClient["WebSocketClient<br/>auction/user channels"]
        LiveUI["Auction Detail UI<br/>chart + countdown + notifications"]

        FXML --> UI
        UI --> Scene
        UI --> Rest
        UI --> WsClient
        WsClient --> LiveUI
    end

    subgraph Server["Javalin Server"]
        Jwt["JwtMiddleware<br/>auth + role context"]
        Controllers["REST Controllers<br/>Auth · Item · Auction · Bid · Notification"]
        WsHandler["AuctionWebSocketHandler"]
        Services["Service Layer<br/>business rules + transactions"]
        Patterns["Design Patterns<br/>State · Factory · Observer · Strategy"]
        Scheduler["AuctionScheduler<br/>lifecycle transitions"]
        DAO["DAO Layer<br/>JDBI SQL access"]
    end

    DB[("PostgreSQL<br/>embedded local / external CI<br/>Flyway migrations")]

    Rest -- "REST /api/* + JWT" --> Jwt
    Jwt --> Controllers
    Controllers --> Services
    WsClient -- "WebSocket /ws/auction/{id}<br/>/ws/user/{id}" --> WsHandler
    Services --> Patterns
    Services --> DAO
    Scheduler --> Services
    WsHandler --> Patterns
    Patterns --> WsHandler
    DAO --> DB
```

```text
JavaFX Client
  ├─ FXML views
  ├─ ui/controller        # MVC controllers
  ├─ RestClient           # REST / JSON calls
  ├─ WebSocketClient      # auction + user WebSocket channels
  └─ SceneManager         # navigation and session state

Javalin Server
  ├─ Controllers          # REST endpoints + WebSocket handler
  ├─ Middleware           # JWT verification and role context
  ├─ Services             # business logic and transactions
  ├─ Patterns             # State, Factory, Observer, Strategy
  ├─ DAOs                 # JDBI SQL access, SELECT FOR UPDATE locks
  └─ PostgreSQL           # embedded by default, Flyway migrations V1-V17
```

Important design choices:

- **Client–Server separation:** only the server accesses the database.
- **Client MVC:** JavaFX FXML views are separated from UI controllers.
- **Server layering:** Controller → Service → DAO → Database.
- **SQL-first persistence:** JDBI keeps locking and transaction behavior explicit.
- **Database-level concurrency:** bidding correctness does not depend on a single JVM lock.

---

## Class Diagram

The core domain model is centered around `Entity`, `User`, `Item`, `Auction`, bid history, and auto-bid configuration. Inheritance is used for users and item categories; associations show how auctions connect sellers, bidders, bids, and auto-bid rules.

```mermaid
classDiagram
    direction LR

    class Entity {
        <<abstract>>
        -Long id
        -LocalDateTime createdAt
        +Long getId()
        +LocalDateTime getCreatedAt()
        +boolean equals(Object o)
        +int hashCode()
    }

    class User {
        <<abstract>>
        -String username
        -String passwordHash
        -String email
        -BigDecimal balance
        -BigDecimal reservedBalance
        -int tokenVersion
        +String getRole()
        +BigDecimal getAvailableBalance()
    }

    class Admin {
        +String getRole()
    }

    class Seller {
        +String getRole()
    }

    class Bidder {
        +String getRole()
    }

    class Item {
        <<abstract>>
        -String name
        -String description
        -Long sellerId
        -String status
        +String getCategory()
    }

    class Electronics {
        -String brand
        +String getCategory()
    }

    class Art {
        -String artist
        +String getCategory()
    }

    class Vehicle {
        -Integer year
        +String getCategory()
    }

    class Auction {
        -Long itemId
        -Long sellerId
        -BigDecimal startingPrice
        -BigDecimal currentPrice
        -Long leadingBidderId
        -LocalDateTime startTime
        -LocalDateTime endTime
        -AuctionStatus status
        -LocalDateTime updatedAt
        +boolean isExpired()
        +boolean isActive()
        +long getRemainingTimeMs()
    }

    class BidTransaction {
        -Long auctionId
        -Long bidderId
        -BigDecimal amount
        -boolean autoBid
        -String bidderUsername
    }

    class AutoBidConfig {
        -Long auctionId
        -Long bidderId
        -BigDecimal maxBid
        -BigDecimal increment
        -AutoBidStatus status
        -AutoBidFailureReason failureReason
        -LocalDateTime registeredAt
        +boolean canBidAt(BigDecimal currentPrice)
        +BigDecimal getNextBidAmount(BigDecimal currentPrice)
    }

    class AuctionState {
        <<interface>>
        +void placeBid(Auction auction, BigDecimal amount, Long bidderId)
        +void close(Auction auction)
        +void edit(Auction auction)
        +void extend(Auction auction, long extraSeconds)
    }

    class AutoBidStrategy {
        +void executeAll(...)
        +void executeAllInTransaction(...)
    }

    class AuctionEventManager {
        +void subscribe(Long auctionId, AuctionEventListener listener)
        +void unsubscribe(Long auctionId, AuctionEventListener listener)
        +void notifyBidUpdate(Long auctionId, BidUpdateMessage msg)
        +void notifyTimeExtended(Long auctionId, BidUpdateMessage msg)
        +void notifyAuctionEnd(Long auctionId, BidUpdateMessage msg)
    }

    Entity <|-- User
    User <|-- Admin
    User <|-- Seller
    User <|-- Bidder

    Entity <|-- Item
    Item <|-- Electronics
    Item <|-- Art
    Item <|-- Vehicle

    Entity <|-- Auction
    Entity <|-- BidTransaction
    Entity <|-- AutoBidConfig

    Auction "1" --> "1" Item : itemId
    Seller "1" --> "0..*" Item : owns
    Auction "1" --> "0..*" BidTransaction : bid history
    Bidder "1" --> "0..*" BidTransaction : places
    Auction "1" --> "0..*" AutoBidConfig : auto-bid rules
    Bidder "1" --> "0..*" AutoBidConfig : configures
    AuctionState ..> Auction : validates lifecycle behavior
    AutoBidStrategy ..> AutoBidConfig : prioritizes by registeredAt
    AuctionEventManager ..> Auction : publishes realtime events
```

---

## Main Technical Flow: Manual Bid

```text
AuctionDetailController
  → POST /api/auctions/{id}/bid with JWT
  → JwtMiddleware verifies token and role context
  → BidController requires BIDDER
  → BidService.placeBid(...)
      → jdbi.inTransaction(...)
      → auctionDao.findByIdForUpdate(...)      # SELECT FOR UPDATE
      → RunningState.placeBid(...)             # state validation
      → userDao.findByIdForUpdate(...)         # balance/reservation lock
      → release previous leader reservation
      → freeze current leader reservation
      → insert bid_transactions + wallet_transactions
      → execute auto-bid chain in the same transaction
  → after commit: Observer/WebSocket broadcasts BID_UPDATE / TIME_EXTENDED
  → all connected clients update price, countdown, and chart
```

---

## Features by Role

### Admin

- Login with seeded admin account
- View users
- Delete users when safe
- Approve/reject deposit requests
- Approve/reject password reset requests
- Delete or moderate auctions

### Seller

- Register/login as seller
- Create/edit/delete own items
- Create auctions for own available items
- View bid activity and notifications

### Bidder

- Register/login as bidder
- Submit deposit requests
- Join running auctions
- Place manual bids
- Configure/cancel auto-bid
- Receive real-time price and notification updates

---

## Build From Source

```bash
git clone https://github.com/kieran-labs/oop-course-project-uet.git
cd oop-course-project-uet
```

Set `JWT_SECRET` before starting the server:

```bash
export JWT_SECRET="replace-with-a-random-secret-of-at-least-32-bytes"
```

Windows PowerShell:

```powershell
$env:JWT_SECRET = "replace-with-a-random-secret-of-at-least-32-bytes"
```

### Run from source

```bash
./gradlew run          # server
./gradlew runClient    # client, in another terminal
```

Windows:

```cmd
gradlew.bat run
gradlew.bat runClient
```

### Build fat JARs

```bash
./gradlew clean buildJars
```

Windows:

```cmd
gradlew.bat clean buildJars
```

Generated files:

- `build/libs/auction-server-1.0.0.jar`
- `build/libs/auction-client-1.0.0.jar`

---

## Quality Gates

| Command | Purpose |
|---|---|
| `./gradlew spotlessCheck` | Verify Google Java formatting |
| `./gradlew test` | Run JUnit 5 / Mockito tests |
| `./gradlew check` | Run tests, Checkstyle, SpotBugs, and JaCoCo verification |
| `./gradlew jacocoTestReport` | Generate HTML coverage report |
| `./gradlew buildJars` | Build server and client fat JARs |

GitHub Actions runs formatting, tests, static analysis, and coverage verification on `main` pushes and pull requests.

---

## Rubric Coverage

| Rubric item | Evidence |
|---|---|
| Class design and inheritance | `Entity`, `User → Bidder/Seller/Admin`, `Item → Electronics/Art/Vehicle`, `Auction`, `BidTransaction` |
| OOP principles | Encapsulation, inheritance, polymorphism through `getRole()` / `getCategory()`, abstraction through abstract base classes and interfaces |
| Design patterns | `pattern/state`, `pattern/factory`, `pattern/observer`, `pattern/strategy`, DAO layer |
| User and product management | Auth, item CRUD, auction CRUD, role-based access |
| Auction functionality | Manual bidding, status lifecycle, settlement, winner determination |
| Error handling | Custom exception hierarchy and HTTP error mapping |
| Concurrent bidding | `BidService` transaction + `AuctionDao.findByIdForUpdate()` |
| Realtime update | `AuctionEventManager`, `WebSocketObserver`, `AuctionWebSocketHandler` |
| Client–Server | JavaFX client communicates with Javalin server through REST and WebSocket |
| MVC / layering | FXML + UI controllers; server Controller → Service → DAO |
| Build and conventions | Gradle Kotlin DSL, Checkstyle, Spotless, SpotBugs |
| Unit tests | JUnit 5 + Mockito + PostgreSQL integration tests |
| CI/CD | GitHub Actions workflow |
| Advanced: Auto-bidding | `AutoBidStrategy`, `AutoBidConfig`, `PriorityQueue` |
| Advanced: Anti-sniping | Final-30-second extension by 60 seconds |
| Advanced: Bid chart | JavaFX `AreaChart` updated from WebSocket events |

---

## Demo Flow

1. Start the server and at least three clients.
2. Log in as `admin / 123456`.
3. Register one seller and two bidders.
4. Bidders submit deposit requests.
5. Admin approves deposits and bidders receive real-time user notifications.
6. Seller creates an item and an auction.
7. Bidders open the same auction detail screen.
8. Place alternating bids and observe real-time price/chart updates.
9. Enable auto-bid for one bidder and trigger the auto-bid chain with another manual bid.
10. Place a bid near the end time to demonstrate anti-sniping extension.
11. Let the scheduler close and settle the auction.

---

## Known Limitations

- Payment is simulated through wallet balance and ledger records; there is no external payment gateway.
- Embedded PostgreSQL is intended for local evaluation and demo. Production should use managed PostgreSQL.
- WebSocket subscriptions are in-memory per server process. Horizontal scaling would require a broker such as Redis Pub/Sub.
- Password reset is admin-reviewed for classroom simplicity; production should use email or another secure out-of-band channel.

---

## Troubleshooting

### `JWT_SECRET is required and must be at least 32 bytes long`

Set the variable in the same terminal that starts the server. `.env` is not auto-loaded by the app.

### Port 8080 already in use

Stop the old server process or change the environment. On Windows, the helper scripts may help:

```cmd
server-status.bat
server-stop.bat
```

### Embedded PostgreSQL data directory is stuck

Stop the server and delete generated local state:

```cmd
rmdir /s /q data logs
```

macOS / Linux:

```bash
rm -rf data logs
```

---

## Team

| Member | GitHub | Role | Main Contributions |
|---|---|---|---|
| Bui Ngoc Phu Hung | [@HumaNormal](https://github.com/HumaNormal) | Backend Lead | Javalin server, REST controllers, WebSocket handler, DAOs, Flyway, database config |
| Tran Anh Duc | [@kieran-lucas](https://github.com/kieran-lucas) | Frontend Lead | JavaFX controllers, FXML screens, SceneManager, notifications UI, CSS theme, Lexend integration |
| Nguyen Dinh Viet Duc | [@Black1206-coder](https://github.com/Black1206-coder) | Business Logic | Services, design patterns, exception hierarchy, JWT, BCrypt authentication |
| Bui Quang Huy | [@stillqhuy](https://github.com/stillqhuy) | DevOps & QA | GitHub Actions, JUnit tests, Gradle configuration, Checkstyle, Spotless, SpotBugs, documentation |

---

## License

Released under the [MIT License](LICENSE).

<div align="center">
<sub>Built for Advanced Programming (LTNC) — University of Engineering and Technology, VNU Hanoi</sub>
</div>
