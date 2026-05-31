<div align='center'>

<img src='assets/app-screenshot.png' alt='Online Auction System - live bid chart and countdown timer' width='900'/>

# Online Auction System

*A real-time desktop auction platform — JavaFX client · Javalin server · Embedded PostgreSQL · HikariCP · Flyway · JDBI · WebSocket*

[![CI](https://img.shields.io/github/actions/workflow/status/kieran-labs/oop-course-project-uet/ci.yml?branch=main&style=for-the-badge&logo=githubactions&logoColor=white&label=CI)](https://github.com/kieran-labs/oop-course-project-uet/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-E76F00?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Javalin](https://img.shields.io/badge/Javalin-6.4.0-111827?style=for-the-badge)](https://javalin.io)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Embedded%20%2F%20CI%2016-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![HikariCP](https://img.shields.io/badge/HikariCP-Connection%20Pool-0EA5E9?style=for-the-badge)](https://github.com/brettwooldridge/HikariCP)
[![Flyway](https://img.shields.io/badge/Flyway-Migrations-CC0200?style=for-the-badge)](https://flywaydb.org/)
[![JDBI](https://img.shields.io/badge/JDBI-DAO%20Layer-7C3AED?style=for-the-badge)](https://jdbi.org/)
[![Gradle](https://img.shields.io/badge/Gradle-Kotlin%20DSL-02303A?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org/)
[![License](https://img.shields.io/badge/License-MIT-2563EB?style=for-the-badge)](LICENSE)

[![Download Server JAR](https://img.shields.io/badge/Download-Server%20JAR-0969DA?style=for-the-badge&logo=openjdk&logoColor=white)](https://github.com/kieran-labs/oop-course-project-uet/releases/download/v1.0.0/auction-server-1.0.0.jar)
[![Download Client JAR](https://img.shields.io/badge/Download-Client%20JAR-2EA44F?style=for-the-badge&logo=openjdk&logoColor=white)](https://github.com/kieran-labs/oop-course-project-uet/releases/download/v1.0.0/auction-client-1.0.0.jar)

![Run Order](https://img.shields.io/badge/Run%20Order-Server%20%E2%86%92%20Client-FFB000?style=for-the-badge)
![Default Port](https://img.shields.io/badge/Port-8080-6F42C1?style=for-the-badge)
![Recommended](https://img.shields.io/badge/Recommended-Use%20Prebuilt%20JARs-0EA5E9?style=for-the-badge)
![Copy And Run](https://img.shields.io/badge/No%20Manual%20Config-Copy%20%26%20Run-10B981?style=for-the-badge)

**[Release v1.0.0](https://github.com/kieran-labs/oop-course-project-uet/releases/tag/v1.0.0)** · **[Setup](docs/SETUP.md)** · **[Schema](docs/SCHEMA.md)** · **[Business Rules](docs/BUSINESS_RULES.md)** · **[UML Source Audit](docs/UML_SOURCE_AUDIT.md)** · **[CI](https://github.com/kieran-labs/oop-course-project-uet/actions/workflows/ci.yml)**

</div>

---

## 🚀 Evaluator First: Required Submission Information

> [!IMPORTANT]
> **For grading/evaluation, use the prebuilt JAR method below.** Do **not** build from source unless you are regenerating artifacts as a developer. The server commands below already include the required `JWT_SECRET`, so the evaluator only needs to copy and run them.

| Required item | Value |
|---|---|
| **GitHub repository** | [Project repository](https://github.com/kieran-labs/oop-course-project-uet) |
| **Main branch** | `main` |
| **Recommended run method** | Download the two prebuilt JARs, then run **Server first → Client second** |
| **Release page** | [Release v1.0.0](https://github.com/kieran-labs/oop-course-project-uet/releases/tag/v1.0.0) |
| **Server JAR** | [Download server executable](https://github.com/kieran-labs/oop-course-project-uet/releases/download/v1.0.0/auction-server-1.0.0.jar) |
| **Client JAR** | [Download client executable](https://github.com/kieran-labs/oop-course-project-uet/releases/download/v1.0.0/auction-client-1.0.0.jar) |
| **📄 Report PDF** | [![Report PDF](https://img.shields.io/badge/Report%20PDF-Final%20PDF-F59E0B?style=for-the-badge&labelColor=404040&logo=data:image/svg%2Bxml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCI%2BPHBhdGggZmlsbD0iI2ZmZiIgZD0iTTIwIDJIOGMtMS4xIDAtMiAuOS0yIDJ2MTJjMCAxLjEuOSAyIDIgMmgxMmMxLjEgMCAyLS45IDItMlY0YzAtMS4xLS45LTItMi0yek0xMS41IDkuNWMwIC44My0uNjcgMS41LTEuNSAxLjVIOXYySDcuNVY3SDEwYy44MyAwIDEuNS42NyAxLjUgMS41djF6bTUgMmMwIC44My0uNjcgMS41LTEuNSAxLjVoLTIuNVY3SDE1Yy44MyAwIDEuNS42NyAxLjUgMS41djN6bTQtM0gxOXYxaDEuNVYxMUgxOXYyaC0xLjVWN2gzdjEuNXpNOSA5LjVoMXYtMUg5djF6bTUgMmgxdi0zaC0xdjN6TTQgNkgydjE0YzAgMS4xLjkgMiAyIDJoMTR2LTJINFY2eiIvPjwvc3ZnPg%3D%3D)](https://drive.google.com/file/d/1tma9vZBT1YerlTCyiguBYfJj-S69wJ7R/view) |
| **🎥 Demo video** | [![Demo Video](https://img.shields.io/badge/Demo%20Video-Watch%20on%20YouTube-FF0000?style=for-the-badge&logo=youtube&logoColor=white)](https://www.youtube.com/watch?v=7G_DbTu-bU0) |



### ⚡ Quick Download

| Server | Client |
|:---:|:---:|
| [![Download Server JAR](https://img.shields.io/badge/Download-Server%20JAR-0969DA?style=for-the-badge&logo=openjdk&logoColor=white)](https://github.com/kieran-labs/oop-course-project-uet/releases/download/v1.0.0/auction-server-1.0.0.jar) | [![Download Client JAR](https://img.shields.io/badge/Download-Client%20JAR-2EA44F?style=for-the-badge&logo=openjdk&logoColor=white)](https://github.com/kieran-labs/oop-course-project-uet/releases/download/v1.0.0/auction-client-1.0.0.jar) |
| `auction-server-1.0.0.jar` | `auction-client-1.0.0.jar` |



---

## 1. Problem Description and System Scope

This project implements an **online auction system** where sellers can list items, create auctions, and bidders can join auctions, place bids, configure auto-bidding, and receive real-time updates. The system is built as a desktop client-server application: a JavaFX client communicates with a Javalin backend through REST APIs and WebSocket channels. The backend persists data through **Embedded PostgreSQL / external PostgreSQL**, **PostgreSQL JDBC**, **HikariCP**, **Flyway**, and **JDBI**.

**System scope:**

| Area | Included scope |
|---|---|
| User management | Register, login, JWT authentication, BCrypt password hashing, token-version invalidation, and role-based access for `ADMIN`, `SELLER`, and `BIDDER` |
| Item management | Sellers create, view, edit, and delete their own `AVAILABLE` items by category; item state prevents mutation while an item is in auction, sold, or removed |
| Auction management | Sellers create auctions; the scheduler manages lifecycle transitions and settlement through `OPEN`, `RUNNING`, `SETTLING`, `FINISHED`, `PAID`, and `CANCELED` |
| Bidding | Manual bidding, auto-bidding, bid history, validation, row-level locking, reserved balance, and wallet ledger |
| Realtime update | WebSocket notifications for bid updates, time extension, auction ending, user notifications, and balance changes |
| Admin workflow | Deposit approval/rejection, password-reset approval/rejection, user management, and auction moderation |
| Database infrastructure | Embedded PostgreSQL runtime, optional external PostgreSQL, PostgreSQL JDBC, HikariCP connection pooling, Flyway migrations, and JDBI DAO access |
| Persistence | Persistent user, item, auction, bid, auto-bid, deposit, password-reset request, notification, balance, reserved-balance, and wallet-ledger data |
| Quality | Unit/integration tests, CI, static analysis, coverage, formatting, and executable fat-JAR build pipeline |

---

## 2. Technology, Runtime Environment, and Installation Requirements

| Category | Technology / Requirement |
|---|---|
| Language | Java **21** |
| Client UI | JavaFX **21** modules: `javafx.controls`, `javafx.fxml`, `javafx.web`; FXML + CSS + bundled Lexend fonts |
| Backend | Javalin **6.4.0** REST API + WebSocket |
| JSON Serialization | Jackson Databind + Jackson JSR310 module for Java time objects |
| Database Runtime | Embedded PostgreSQL for local evaluation; optional external PostgreSQL through `DB_URL`, `DB_USER`, and `DB_PASSWORD` |
| JDBC Driver | PostgreSQL JDBC driver |
| Connection Pool | HikariCP connection pool through `HikariDataSource` |
| Database Migration | Flyway Core + Flyway PostgreSQL migrations from `src/main/resources/db/migration` |
| Persistence Access | JDBI Core / SQL Object / PostgreSQL support |
| Authentication | JWT using HMAC-256 + BCrypt password hashing |
| Logging / Environment | SLF4J + Logback; dotenv-java + environment variables such as `JWT_SECRET` |
| Build Tool | Gradle Kotlin DSL |
| Fat JAR Packaging | ShadowJar tasks: `shadowJar`, `shadowClient`, `buildJars` |
| Testing / Quality | JUnit 5, Mockito, JaCoCo, Checkstyle, SpotBugs, Spotless, GitHub Actions |
| Operating System | Windows 10+ / macOS / Linux with JDK 21+ |
| Required Port | `8080` must be free before starting the server |

**Required installation:**

1. Install **JDK 21+**.
2. Make sure `java` is available in terminal:

```bash
java -version
```

3. No separate PostgreSQL installation is required for normal evaluation because the server starts Embedded PostgreSQL automatically.
4. For grading/evaluation, prefer the **prebuilt release JARs**. They provide the cleanest path because dependencies are already packaged and the run commands below include the required `JWT_SECRET`.

---

## 3. Recommended Run Method — Use Prebuilt JARs

> [!IMPORTANT]
> **Use this method for grading.** Download the two JAR files below, put them in the same folder, then follow Section 4 exactly. Do **not** use the source-build section unless you are a developer regenerating the executable artifacts.

> [!NOTE]
> The server starts Embedded PostgreSQL automatically. No separate PostgreSQL installation is required for normal evaluation. On first run, the embedded PostgreSQL library may download/cache the OS-specific PostgreSQL binary before the server finishes startup.

### Step 0 — Download these two files

| File | Direct download |
|---|---|
| **Server JAR** | [Server executable JAR](https://github.com/kieran-labs/oop-course-project-uet/releases/download/v1.0.0/auction-server-1.0.0.jar) |
| **Client JAR** | [Client executable JAR](https://github.com/kieran-labs/oop-course-project-uet/releases/download/v1.0.0/auction-client-1.0.0.jar) |

Release page: [Release v1.0.0](https://github.com/kieran-labs/oop-course-project-uet/releases/tag/v1.0.0)

Put both files in the same folder:

```text
auction-server-1.0.0.jar
auction-client-1.0.0.jar
```

---

## 🔥 4. Run the Application — Server First, Client Second

> [!IMPORTANT]
> Follow this order exactly:
>
> 1. Open **Terminal 1** in the folder containing the two JAR files.
> 2. Run the **Server** command below.
> 3. Keep Terminal 1 open.
> 4. Open **Terminal 2** in the same folder.
> 5. Run the **Client** command below.
>
> The server requires `JWT_SECRET`. The commands below already include it, so do **not** set anything manually.

### Step 1 — Open Terminal 1 and start the Server

#### Windows PowerShell

```powershell
$env:JWT_SECRET='auction-demo-secret-1234567890-abcdef-32bytes'; java -jar .\auction-server-1.0.0.jar
```

#### Windows Command Prompt / CMD

```cmd
set "JWT_SECRET=auction-demo-secret-1234567890-abcdef-32bytes" && java -jar .\auction-server-1.0.0.jar
```

#### macOS / Linux

```bash
JWT_SECRET='auction-demo-secret-1234567890-abcdef-32bytes' java -jar ./auction-server-1.0.0.jar
```

Wait until the server finishes startup. The backend listens on:

```text
http://localhost:8080
```

> [!WARNING]
> Do **not** close Terminal 1 while using the application. Closing Terminal 1 stops the server.

### Step 2 — Open Terminal 2 and start the Client

#### Windows PowerShell

```powershell
java -jar .\auction-client-1.0.0.jar
```

#### Windows Command Prompt / CMD

```cmd
java -jar .\auction-client-1.0.0.jar
```

#### macOS / Linux

```bash
java -jar ./auction-client-1.0.0.jar
```

To demonstrate multiple clients, open more terminals in the same folder and run the same client command again.

### Step 3 — Login with the seeded admin account

| Role | Username | Password |
|---|---|---|
| Admin | `admin` | `123456` |

### Step 4 — Recommended demo flow

1. Start the server and at least two clients.
2. Login as admin.
3. Register one seller and two bidders.
4. Bidders submit deposit requests.
5. Admin approves deposits.
6. Seller creates an item and an auction.
7. Bidders join the same auction and place bids.
8. Configure auto-bid for one bidder.
9. Observe realtime bid updates, chained auto-bid playback, terminal auto-bid states, chart updates, notifications, balance updates, and anti-sniping extension.

---

## 5. Main Project Modules and Directory Structure

```text
Root / build / tooling
  ├─ build.gradle.kts          # Gradle Kotlin DSL, dependencies, quality gates, ShadowJar packaging
  ├─ settings.gradle.kts       # Gradle project name
  ├─ gradlew, gradlew.bat      # Gradle wrapper
  ├─ build/libs/               # Generated executable fat JARs
  ├─ .github/workflows/ci.yml  # GitHub Actions CI pipeline
  ├─ config/checkstyle/        # Checkstyle rules
  ├─ assets/                   # README screenshots and grading images
  ├─ server-start.bat          # Windows helper: start server
  ├─ server-stop.bat           # Windows helper: stop server
  ├─ server-status.bat         # Windows helper: check server status
  └─ db-reset.bat              # Windows helper: reset local generated database/log state

src/main/java/com/auction
  ├─ App.java, AdminSeeder.java, ClientApp.java, Launcher.java
  ├─ config/             # DatabaseConfig: Embedded PostgreSQL + HikariCP + Flyway + JDBI; JwtUtil
  ├─ middleware/         # JwtMiddleware
  ├─ controller/         # REST controllers + AuctionWebSocketHandler
  ├─ service/            # business services + AuctionScheduler
  ├─ dao/                # JDBI DAOs + row mappers
  ├─ model/              # domain models, records, enums
  ├─ dto/                # request/response/WebSocket/error contracts
  ├─ exception/          # custom domain/API exceptions
  ├─ pattern/            # factory, state, observer, strategy
  ├─ util/               # REST/WS client, validators, notifications, formatting
  └─ ui/                 # JavaFX controllers and navigation utilities

src/main/resources
  ├─ db/migration/       # Versioned Flyway database migrations
  ├─ ui/fxml/            # JavaFX screen layouts
  ├─ css/                # JavaFX styling
  ├─ fonts/              # bundled Lexend font files
  └─ icons/              # UI icons

docs/
  ├─ SETUP.md
  ├─ SCHEMA.md
  ├─ BUSINESS_RULES.md
  └─ UML_SOURCE_AUDIT.md
```

---

## 6. Completed Features Mapped to Rubric

> [!IMPORTANT]
> This table intentionally maps each completed feature group directly to the grading rubric. It is written as evidence, not only as a feature list, so the evaluator can verify the implemented scope against the expected assessment items without guessing.

| Rubric area | Completed functionality | Concrete implementation evidence |
|---|---|---|
| User management and authentication | Register, login, JWT authentication, BCrypt password hashing, token-version invalidation, role-based authorization for `ADMIN`, `SELLER`, and `BIDDER` | `AuthController`, `UserService`, `UserDao`, `JwtUtil`, `JwtMiddleware`, `UserFactory`, `Admin`, `Seller`, `Bidder`, `users.token_version` |
| Product / item management | Sellers can create, view, edit, and delete their own `AVAILABLE` items by category; item state prevents mutation while an item is in auction, sold, or removed | `ItemController`, `ItemService`, `ItemDao`, `ItemFactory`, `Item`, `Electronics`, `Art`, `Vehicle`, `items.status` |
| Auction management | Sellers create auctions for their own available items; the system validates ownership and item availability | `AuctionController`, `AuctionService`, `AuctionDao`, `CreateAuctionRequest`, `AuctionResponse`, `auctions.seller_id` |
| Auction lifecycle | Auctions move through `OPEN → RUNNING → SETTLING → FINISHED / PAID / CANCELED` with scheduler-driven transitions and settlement logic | `AuctionStatus`, `AuctionScheduler`, `AuctionStateFactory`, `AuctionStates`, `OpenState`, `RunningState`, `SettlingState`, `FinishedState`, `PaidState`, `CanceledState` |
| Manual bidding | Bidders can join auctions and place valid manual bids with bid history and highest-price tracking | `BidController`, `BidService`, `BidTransactionDao`, `BidTransaction`, `BidRequest`, `BidUpdateMessage` |
| Concurrent bidding | Bid placement is transaction-protected and uses row-level locking to avoid race conditions | `BidService.placeBid(...)`, `jdbi.inTransaction(...)`, `AuctionDao.findByIdForUpdate(...)`, `UserDao.findByIdForUpdate(...)` |
| Wallet and deposit workflow | Bidders submit deposits; admin approves/rejects; wallet balance and reserved balance are updated consistently | `DepositRequestDao`, `DepositRecord`, `UserService.approveDeposit(...)`, `WalletTransactionDao`, `User.balance`, `User.reservedBalance`, `wallet_transactions` |
| Admin functions | Admin can manage users, approve/reject deposits, approve/reject password reset requests, and moderate auctions | `AdminPanelController`, admin routes in `App.java`, `UserService`, `PasswordResetService`, `DepositRequestDao`, `PasswordResetRequestDao` |
| Error handling | Domain errors are represented by a custom exception hierarchy and mapped to HTTP/API errors | `AuctionException`, `InvalidBidException`, `AuctionClosedException`, `UnauthorizedException`, `NotFoundException`, `DuplicateException`, `ErrorResponse` |
| Realtime update | Bid updates, time extension updates, auction-ended events, user notifications, and balance updates are pushed through WebSocket | `AuctionWebSocketHandler`, `AuctionEventManager`, `AuctionEventListener`, `WebSocketObserver`, `WebSocketClient`, `BidUpdateMessage` |
| Advanced feature: auto-bidding | Users can configure auto-bidding with maximum bid, increment, status detection, persisted failure reasons, chained execution, 100-step chain guard, terminal-state recovery, and automatic cleanup when auctions are canceled or settled | `AutoBidStrategy`, `AutoBidConfig`, `AutoBidConfigDao`, `AutoBidRequest`, `AutoBidStatus`, `AutoBidFailureReason`, `CHAIN_LIMIT_REACHED`, `AuctionDetailController` |
| Advanced feature: anti-sniping | Late bids automatically extend auction end time to reduce last-second unfair wins | `BidService`, `ANTI_SNIPE_THRESHOLD_MS`, `ANTI_SNIPE_EXTENSION_SECONDS`, `BidUpdateMessage.timeExtended(...)` |
| Client-server architecture | JavaFX desktop client communicates with a Javalin backend through REST APIs and WebSocket channels | JavaFX controllers, `RestClient`, `WebSocketClient`, REST controllers, `AuctionWebSocketHandler` |
| MVC / layered architecture | UI, controller, service, DAO, model, DTO, and database migration responsibilities are separated | `ui/controller`, `controller`, `service`, `dao`, `model`, `dto`, `db/migration` |
| OOP principles | Encapsulation, inheritance, polymorphism, abstraction, interfaces, and role/category specialization are used in the domain model and patterns | `Entity`, `User → Admin/Seller/Bidder`, `Item → Electronics/Art/Vehicle`, `AuctionState`, `AuctionEventListener`, factories and strategies |
| Design patterns | Factory, State, Observer, Strategy, and DAO patterns are implemented explicitly | `pattern/factory`, `pattern/state`, `pattern/observer`, `pattern/strategy`, `dao` package |
| JavaFX client functionality | Client includes login/register/profile/admin screens, auction list/detail, bid chart, notifications, wallet/deposit screens, and custom styling | `ClientApp`, `Launcher`, `SceneManager`, JavaFX controllers, FXML files, CSS, screenshots |
| Database infrastructure | Server starts Embedded PostgreSQL, configures HikariCP connection pooling, runs Flyway migrations, then exposes JDBI DAOs to the service layer | `DatabaseConfig`, `HikariConfig`, `HikariDataSource`, `EmbeddedPostgres`, `Flyway`, `Jdbi`, `buildHikariConfig(...)`, `src/main/resources/db/migration` |
| Persistence and migrations | PostgreSQL schema is versioned and data is persisted across users, items, auctions, bids, deposits, password reset requests, notifications, and wallet ledger records | Flyway migrations in `src/main/resources/db/migration`, `DatabaseConfig`, `HikariDataSource`, `Jdbi`, DAOs, `WalletTransactionDao` |
| Build, testing, and quality | Project includes Gradle build, server/client executable fat JARs, unit/integration tests, formatting/static checks, coverage, and CI | `build.gradle.kts`, `shadowJar`, `shadowClient`, `buildJars`, `build/libs/*.jar`, JUnit tests including auto-bid terminal-state and chain-limit tests, Checkstyle, SpotBugs, Spotless, JaCoCo, GitHub Actions |

---

## Screenshots

| Login | Auction List |
|:---:|:---:|
| <img src='assets/screenshots/login.png' width='420'/> | <img src='assets/screenshots/auction-list.png' width='420'/> |

| Auction Detail | Admin Dashboard |
|:---:|:---:|
| <img src='assets/screenshots/auction-detail.png' width='420'/> | <img src='assets/screenshots/admin.png' width='420'/> |

---

## Architecture

The architecture flowchart below is a **runtime communication/data-flow view**, not a strict Java import graph. It shows how the JavaFX client talks to server routes and WebSocket endpoints, then how server requests move through controllers, services, DAOs, design patterns, HikariCP, PostgreSQL JDBC, Flyway migrations, and PostgreSQL.

```mermaid
flowchart LR
    ClientApp[ClientApp / Launcher] --> SceneManager[SceneManager]
    SceneManager --> UiControllers[JavaFX Controllers]
    UiControllers --> RestClient[RestClient]
    UiControllers --> WebSocketClient[WebSocketClient]

    RestClient --> HttpApi[Javalin HTTP API]
    WebSocketClient --> WsApi[Javalin WebSocket API]
    HttpApi --> JwtMiddleware[JwtMiddleware]
    WsApi --> WsHandler[AuctionWebSocketHandler]

    App[App.java] --> JwtMiddleware
    App --> Controllers[REST Controllers]
    App --> WsHandler
    App --> Services[Services]
    App --> Scheduler[AuctionScheduler]
    App --> DatabaseConfig[DatabaseConfig]

    JwtMiddleware --> Controllers
    Controllers --> Services
    Scheduler --> Services
    Services --> Patterns[Design Patterns]
    Services --> Daos[JDBI DAOs]
    Daos --> Pool[HikariCP / HikariDataSource]
    Pool --> Driver[PostgreSQL JDBC]
    Driver --> Database[(Embedded PostgreSQL or external PostgreSQL)]
    DatabaseConfig --> Pool
    DatabaseConfig --> Flyway[Flyway migrations]
    Flyway --> Database
```

---

## Source-Code Coverage Audit for UML

The diagrams below are intentionally split into smaller GitHub-safe Mermaid blocks. Endpoint paths are kept in Markdown tables instead of class bodies because GitHub Mermaid can fail on route strings such as `/api/auctions/{id}/bid`. Mermaid stereotypes such as `record`, `mapper`, or `interface` are described in text instead of angle-bracket syntax to prevent GitHub rendering errors.

| Package | Files represented in UML |
|---|---|
| `com.auction` | `App`, `AdminSeeder`, `ClientApp`, `Launcher` |
| `config` / `middleware` | `DatabaseConfig`, `JwtUtil`, `JwtMiddleware` |
| `controller` | `AuthController`, `ItemController`, `AuctionController`, `BidController`, `NotificationController`, `AuctionWebSocketHandler` |
| `service` | `UserService`, `PasswordResetService`, `ItemService`, `AuctionService`, `BidService`, `NotificationService`, `AuctionScheduler` |
| `dao` | `UserDao`, `ItemDao`, `AuctionDao`, `BidTransactionDao`, `AutoBidConfigDao`, `DepositRequestDao`, `PasswordResetRequestDao`, `NotificationDao`, `WalletTransactionDao` |
| `model` | `Entity`, `User`, `Admin`, `Seller`, `Bidder`, `Item`, `Electronics`, `Art`, `Vehicle`, `Auction`, `AuctionStatus`, `BidTransaction`, `AutoBidConfig`, `AutoBidStatus`, `AutoBidFailureReason`, `DepositRecord`, `PasswordResetRecord` |
| `dto` | request DTOs, response DTOs, `BidUpdateMessage`, `ErrorResponse`, `PageRequest` |
| `exception` | `AuctionException`, `InvalidBidException`, `AuctionClosedException`, `UnauthorizedException`, `NotFoundException`, `DuplicateException` |
| `pattern` | Factory, State, Observer, and Strategy implementations |
| `util` | `MoneyValidator`, `NotificationFormat`, `RestClient`, `WebSocketClient`, notification utilities |
| `ui.controller` / `ui.util` | JavaFX controllers, `SceneManager`, `Navigable` |
| nested source-level helpers | DAO row mappers, `BidHistoryEntry`, scheduler records, `AutoBidUiState`, `ResizeDirection`, `BalanceDisplay`, date-picker helper classes |

### Inline Routes in `App.java`

| Group | Endpoints |
|---|---|
| Health / shutdown | `GET /api/health`, `POST /internal/shutdown` |
| Current user | `GET /api/users/me`, `PUT /api/users/me/password` |
| Deposit | `GET /api/users/me/deposit-requests`, `POST /api/users/me/deposit` |
| Admin deposit | `GET /api/admin/deposit-requests`, approve/reject by id |
| Admin password reset | `GET /api/admin/password-reset-requests`, approve/reject by id |
| Admin management | `DELETE /api/admin/auctions/{id}`, `GET /api/admin/users`, `DELETE /api/admin/users/{id}` |
| Auto-bid | `GET/POST/DELETE /api/auctions/{id}/auto-bid` |
| WebSocket | `/ws/auction/{id}`, `/ws/user/{id}` |

---

## Class Diagrams

### 1. Runtime Composition, Security, and Route Registration

```mermaid
classDiagram
    direction LR

    class App {
        -SERVER_PORT
        -DATA_DIR
        -SERVER_PID_FILE
        -SERVER_TOKEN_FILE
        -SECURE_RANDOM
        -SHUTTING_DOWN
        +main()
        -buildJavalin()
        -registerShutdownHook()
        -stopServer()
        -isServerAlreadyRunning()
        -loadOrCreateShutdownToken()
        -writeServerPid()
        -deleteServerPid()
        -isLocalRequest()
        -requireAdmin()
        -requireRole()
        -seedAdminIfNeeded()
        -registerExceptionHandlers()
    }

    class AdminSeeder {
        -userDao
        -DEFAULT_ADMIN_USERNAME
        -DEFAULT_ADMIN_EMAIL
        -ENV_PASSWORD_VAR
        -DEMO_FALLBACK_PASSWORD
        +seed()
        +resolveAdminPassword()
    }

    class DatabaseConfig {
        -jdbi
        -dataSource
        -embeddedPostgres
        -EMBEDDED_DATA_DIR
        -POSTGRES_PID_FILE
        -POSTMASTER_PID_FILE
        +create()
        +shutDown()
        -initExternalPostgres()
        -initEmbeddedPostgres()
        -runMigrations()
        -buildHikariConfig()
        -registerShutdownHook()
    }

    class JwtUtil {
        -MIN_SECRET_BYTES
        -SECRET_KEY
        -ALGORITHM
        -VERIFIER
        +validateConfiguration()
        +requireJwtSecret()
        +createToken()
        +verifyToken()
    }

    class JwtMiddleware {
        -userDao
        +configure()
        +handle()
        -attachUserClaims()
        -validateTokenVersion()
        -extractTokenVersion()
    }

    class AuctionWebSocketHandler {
        -jdbi
        -userDao
        -connections
        -userConnections
        -observers
        -sessionExpiresAt
        -expirationTasks
        -expirationScheduler
        -eventManager
        -objectMapper
        +onConnect()
        +onUserConnect()
        +onClose()
        +onUserClose()
        +onError()
        +onUserError()
        +broadcast()
        +pushUserNotification()
        +notifyBalanceUpdate()
        +notifyBalanceChange()
        +notifyUser()
        +getConnectionCount()
        +saveNotificationToDatabase()
    }

    class AuctionEventManager {
        -listeners
        +subscribe()
        +unsubscribe()
        +notifyBidUpdate()
        +notifyTimeExtended()
        +notifyAuctionEnd()
    }

    class WebSocketObserver {
        -handler
        -auctionId
        +onBidUpdate()
        +onTimeExtended()
        +onAuctionEnd()
        +getAuctionId()
    }

    class AuthController {
        +register()
        +registerPasswordReset()
        -handleRegister()
        -handleLogin()
        -handleForgotPassword()
    }

    class ItemController {
        +register()
        -handleGetAll()
        -handleGetById()
        -handleCreate()
        -handleUpdate()
        -handleDelete()
    }

    class AuctionController {
        +register()
        -handleGetAll()
        -handleGetById()
        -handleCreate()
        -handleUpdate()
        -handleDelete()
    }

    class BidController {
        -bidService
        +register()
        +handleManualBid()
    }

    class NotificationController {
        +register()
        -handleGetNotifications()
        -handleMarkRead()
        -handleMarkAllRead()
    }

    App --> DatabaseConfig
    App --> JwtUtil
    App --> JwtMiddleware
    App --> AdminSeeder
    App --> AuthController
    App --> ItemController
    App --> AuctionController
    App --> BidController
    App --> NotificationController
    App --> AuctionWebSocketHandler
    JwtMiddleware --> JwtUtil
    AuctionWebSocketHandler --> JwtUtil
    AuctionWebSocketHandler --> AuctionEventManager
    AuctionWebSocketHandler --> WebSocketObserver
```

### 2. Service Layer and Data Access Layer

```mermaid
classDiagram
    direction LR

    class UserService {
        -userDao
        -depositRequestDao
        -jdbi
        +register()
        +login()
        +getRoleByUsername()
        +findById()
        +changePassword()
        +requestDeposit()
        +getPendingDeposits()
        +approveDeposit()
        +rejectDeposit()
        +getAll()
        +delete()
    }

    class PasswordResetService {
        -userDao
        -resetDao
        -jdbi
        +requestReset()
        +getPendingRequests()
        +approveReset()
        +rejectReset()
        -generateTempPassword()
    }

    class ItemService {
        -itemDao
        +create()
        +getAll()
        +getBySellerId()
        +getById()
        +update()
        +delete()
        -checkOwnership()
        -ensureAvailableForMutation()
    }

    class AuctionService {
        -auctionDao
        -itemDao
        -userDao
        -eventManager
        -jdbi
        -bidTransactionDao
        -wsHandler
        +getAllAuctions()
        +getAll()
        +getById()
        +getAuctionById()
        +create()
        +update()
        +delete()
        +hardDelete()
        +getState()
    }

    class BidService {
        -auctionDao
        -bidTransactionDao
        -autoBidConfigDao
        -eventManager
        -jdbi
        -auctionService
        -userDao
        -autoBidStrategy
        -wsHandler
        +placeBid()
        +getBidHistory()
        +createAutoBid()
    }

    class NotificationService {
        -notificationDao
        +getRecentNotifications()
        +markRead()
        +markAllRead()
    }

    class UserDao {
        -jdbi
        +insert()
        +findById()
        +findByIdForUpdate()
        +findByUsername()
        +findByEmail()
        +findAll()
        +update()
        +delete()
        +updateReservedBalanceInTransaction()
        +releaseReservedBalanceInTransaction()
    }

    class ItemDao {
        -jdbi
        +insert()
        +findAll()
        +findById()
        +findByIdForUpdate()
        +findBySellerId()
        +update()
        +delete()
        +updateStatusInTransaction()
    }

    class AuctionDao {
        -jdbi
        +insert()
        +insertInTransaction()
        +findById()
        +findByIdForUpdate()
        +findAll()
        +findByStatus()
        +update()
        +updateInTransaction()
        +atomicTransition()
        +findDueAuctionIds()
        +findExpiredAuctionIds()
        +hardDelete()
    }

    class BidTransactionDao {
        -jdbi
        +insert()
        +findByAuctionId()
        +findByBidderId()
        +findLastBid()
        +findWithUsernames()
        +countByAuctionId()
        +getHighestPrice()
        +deleteByAuctionId()
    }

    class AutoBidConfigDao {
        -jdbi
        +insert()
        +findById()
        +findByAuctionAndBidder()
        +findActiveByAuctionId()
        +findActiveByAuctionIdInTransaction()
        +findByIdInTransaction()
        +hasActiveConfig()
        +upsertInTransaction()
        +update()
        +updateStatusInTransaction()
        +deactivate()
        +deactivateAllByAuctionId()
        +countActiveByAuctionId()
    }

    class DepositRequestDao {
        -jdbi
        +insert()
        +findById()
        +findByIdForUpdate()
        +findByUserId()
        +findByStatus()
        +transitionStatusInTransaction()
    }

    class PasswordResetRequestDao {
        -jdbi
        +insert()
        +findByIdForUpdate()
        +findByStatus()
        +hasPendingRequest()
        +transitionStatusInTransaction()
    }

    class NotificationDao {
        -jdbi
        +findRecentByUserId()
        +markRead()
        +markAllRead()
    }

    class WalletTransactionDao {
        +insert()
    }

    class AutoBidStrategy {
        -autoBidConfigDao
        -userDao
        +executeAll()
        +executeAllInTransaction()
    }

    UserService --> UserDao
    UserService --> DepositRequestDao
    UserService --> WalletTransactionDao
    PasswordResetService --> UserDao
    PasswordResetService --> PasswordResetRequestDao
    ItemService --> ItemDao
    AuctionService --> AuctionDao
    AuctionService --> ItemDao
    AuctionService --> UserDao
    AuctionService --> BidTransactionDao
    AuctionService --> WalletTransactionDao
    BidService --> AuctionDao
    BidService --> BidTransactionDao
    BidService --> AutoBidConfigDao
    BidService --> UserDao
    BidService --> AuctionService
    BidService --> AutoBidStrategy
    NotificationService --> NotificationDao
```

### 3. Domain Model, Records, and Enums

```mermaid
classDiagram
    direction LR

    class Entity {
        -id
        -createdAt
        +getId()
        +setId()
        +getCreatedAt()
        +setCreatedAt()
        +equals()
        +hashCode()
    }

    class User {
        -username
        -passwordHash
        -email
        -balance
        -reservedBalance
        -tokenVersion
        +getRole()
        +getAvailableBalance()
        +getUsername()
        +setUsername()
        +getPasswordHash()
        +setPasswordHash()
        +getEmail()
        +setEmail()
        +getBalance()
        +setBalance()
        +getReservedBalance()
        +setReservedBalance()
        +getTokenVersion()
        +setTokenVersion()
    }

    class Admin {
        +getRole()
    }

    class Seller {
        +getRole()
    }

    class Bidder {
        +getRole()
    }

    class Item {
        -name
        -description
        -sellerId
        -status
        +getCategory()
        +getName()
        +setName()
        +getDescription()
        +setDescription()
        +getSellerId()
        +setSellerId()
        +getStatus()
        +setStatus()
    }

    class Electronics {
        -brand
        +getCategory()
        +getBrand()
        +setBrand()
    }

    class Art {
        -artist
        +getCategory()
        +getArtist()
        +setArtist()
    }

    class Vehicle {
        -year
        +getCategory()
        +getYear()
        +setYear()
    }

    class Auction {
        -itemId
        -sellerId
        -startingPrice
        -currentPrice
        -leadingBidderId
        -startTime
        -endTime
        -status
        -updatedAt
        +isExpired()
        +isActive()
        +getRemainingTimeMs()
        +getCurrentPrice()
        +setCurrentPrice()
        +getStartingPrice()
        +setStartingPrice()
        +getLeadingBidderId()
        +setLeadingBidderId()
        +setStatus()
        +setUpdatedAt()
    }

    class BidTransaction {
        -auctionId
        -bidderId
        -amount
        -autoBid
        -bidderUsername
        +getAuctionId()
        +setAuctionId()
        +getBidderId()
        +setBidderId()
        +getAmount()
        +setAmount()
        +isAutoBid()
        +setAutoBid()
        +getBidderUsername()
        +setBidderUsername()
    }

    class AutoBidConfig {
        -auctionId
        -bidderId
        -maxBid
        -increment
        -status
        -failureReason
        -registeredAt
        +isActive()
        +canBidAt()
        +getNextBidAmount()
        +setActive()
        +setStatus()
        +setFailureReason()
    }

    class AuctionStatus {
        OPEN
        RUNNING
        SETTLING
        FINISHED
        PAID
        CANCELED
        +from()
    }

    class AutoBidStatus {
        ACTIVE
        STOPPED
        EXHAUSTED
        FAILED
        +from()
    }

    class AutoBidFailureReason {
        MAX_PRICE_TOO_LOW
        INSUFFICIENT_BALANCE
        CHAIN_LIMIT_REACHED
        AUCTION_NOT_RUNNING
        BIDDER_ALREADY_HIGHEST
        ACTIVE_AUTOBID_EXISTS
        +from()
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
    Auction --> AuctionStatus
    AutoBidConfig --> AutoBidStatus
    AutoBidConfig --> AutoBidFailureReason
    Auction --> Item
    Auction --> User
    BidTransaction --> Auction
    BidTransaction --> User
    AutoBidConfig --> Auction
    AutoBidConfig --> User
```

### 4. DTOs, WebSocket Contracts, and Exceptions

```mermaid
classDiagram
    direction LR

    class LoginRequest {
        -username
        -password
        +getUsername()
        +setUsername()
        +getPassword()
        +setPassword()
    }

    class RegisterRequest {
        -username
        -email
        -password
        -role
        +getUsername()
        +setUsername()
        +getEmail()
        +setEmail()
        +getPassword()
        +setPassword()
        +getRole()
        +setRole()
    }

    class CreateItemRequest {
        -name
        -description
        -category
        -categoryDetail
        +getName()
        +setName()
        +getDescription()
        +setDescription()
        +getCategory()
        +setCategory()
        +getCategoryDetail()
        +setCategoryDetail()
    }

    class CreateAuctionRequest {
        -itemId
        -startingPrice
        -startTime
        -endTime
        +getItemId()
        +setItemId()
        +getStartingPrice()
        +setStartingPrice()
        +getStartTime()
        +setStartTime()
        +getEndTime()
        +setEndTime()
    }

    class BidRequest {
        -amount
        +getAmount()
        +setAmount()
    }

    class AutoBidRequest {
        -maxBid
        -increment
        +getMaxBid()
        +setMaxBid()
        +getIncrement()
        +setIncrement()
    }

    class AuctionResponse {
        -id
        -itemId
        -sellerId
        -itemName
        -itemCategory
        -startingPrice
        -currentPrice
        -leadingBidderId
        -leadingBidderUsername
        -startTime
        -endTime
        -status
        -remainingTimeMs
        +fromAuction()
    }

    class UserResponse {
        -id
        -username
        -email
        -role
        -balance
        -availableBalance
        -createdAt
        +from()
    }

    class ErrorResponse {
        -error
        -message
        -timestamp
        +of()
        +toString()
    }

    class BidUpdateMessage {
        -type
        -auctionId
        -currentPrice
        -leadingBidderId
        -leadingBidderUsername
        -endTime
        -timestamp
        -autoBid
        -newBalance
        -balanceDelta
        -approved
        -message
        +bidUpdate()
        +timeExtended()
        +auctionEnded()
        +balanceUpdated()
        +balanceChanged()
        +userNotification()
    }

    class User {
        -username
        -email
        -balance
        -reservedBalance
        +getRole()
        +getAvailableBalance()
    }

    class Auction {
        -itemId
        -sellerId
        -currentPrice
        -leadingBidderId
        -status
        +isActive()
        +getRemainingTimeMs()
    }

    class AuctionException {
        +toString()
    }

    class InvalidBidException {
        +InvalidBidException()
    }

    class AuctionClosedException {
        +AuctionClosedException()
    }

    class UnauthorizedException {
        +UnauthorizedException()
    }

    class NotFoundException {
        +NotFoundException()
    }

    class DuplicateException {
        +DuplicateException()
    }

    UserResponse --> User
    AuctionResponse --> Auction
    BidUpdateMessage --> Auction
    AuctionException <|-- InvalidBidException
    AuctionException <|-- AuctionClosedException
    AuctionException <|-- UnauthorizedException
    AuctionException <|-- NotFoundException
    AuctionException <|-- DuplicateException
```

### 5. Design Patterns and Realtime Collaboration

```mermaid
classDiagram
    direction LR

    class UserFactory {
        +create()
    }

    class Admin {
        +getRole()
    }

    class Seller {
        +getRole()
    }

    class Bidder {
        +getRole()
    }

    class ItemFactory {
        +create()
        -parseYear()
    }

    class Electronics {
        -brand
        +getCategory()
        +getBrand()
        +setBrand()
    }

    class Art {
        -artist
        +getCategory()
        +getArtist()
        +setArtist()
    }

    class Vehicle {
        -year
        +getCategory()
        +getYear()
        +setYear()
    }

    class AuctionStateFactory {
        +create()
    }

    class AuctionState {
        +placeBid()
        +close()
        +edit()
        +extend()
    }

    class AuctionStates {
        +OPEN
        +RUNNING
        +SETTLING
        +FINISHED
        +PAID
        +CANCELED
    }

    class OpenState {
        +placeBid()
        +close()
        +edit()
        +extend()
    }

    class RunningState {
        +placeBid()
        +close()
        +edit()
        +extend()
    }

    class SettlingState {
        +placeBid()
        +close()
        +edit()
        +extend()
    }

    class FinishedState {
        +placeBid()
        +close()
        +edit()
        +extend()
    }

    class PaidState {
        +placeBid()
        +close()
        +edit()
        +extend()
    }

    class CanceledState {
        +placeBid()
        +close()
        +edit()
        +extend()
    }

    class AuctionEventListener {
        +onBidUpdate()
        +onTimeExtended()
        +onAuctionEnd()
    }

    class AuctionEventManager {
        -listeners
        +subscribe()
        +unsubscribe()
        +notifyBidUpdate()
        +notifyTimeExtended()
        +notifyAuctionEnd()
    }

    class WebSocketObserver {
        -handler
        -auctionId
        +onBidUpdate()
        +onTimeExtended()
        +onAuctionEnd()
        +getAuctionId()
    }

    class AuctionWebSocketHandler {
        -connections
        -userConnections
        -observers
        +broadcast()
        +pushUserNotification()
        +notifyBalanceUpdate()
        +notifyBalanceChange()
    }

    class AutoBidStrategy {
        -autoBidConfigDao
        -userDao
        +executeAll()
        +executeAllInTransaction()
    }

    class AutoBidConfigDao {
        -jdbi
        +findActiveByAuctionId()
        +findActiveByAuctionIdInTransaction()
        +findByAuctionAndBidder()
        +findByIdInTransaction()
        +hasActiveConfig()
        +upsertInTransaction()
        +update()
        +updateStatusInTransaction()
    }

    class UserDao {
        -jdbi
        +findByIdForUpdate()
        +updateReservedBalanceInTransaction()
        +releaseReservedBalanceInTransaction()
    }

    class AutoBidConfig {
        -auctionId
        -bidderId
        -maxBid
        -increment
        -status
        -failureReason
        +isActive()
        +canBidAt()
        +getNextBidAmount()
        +setStatus()
        +setFailureReason()
    }

    class AutoBidExecutor {
        +execute()
    }

    class InTransactionBidExecutor {
        +execute()
    }

    UserFactory --> Admin
    UserFactory --> Seller
    UserFactory --> Bidder
    ItemFactory --> Electronics
    ItemFactory --> Art
    ItemFactory --> Vehicle
    AuctionStateFactory --> AuctionStates
    AuctionStateFactory --> AuctionState
    AuctionStates --> OpenState
    AuctionStates --> RunningState
    AuctionStates --> SettlingState
    AuctionStates --> FinishedState
    AuctionStates --> PaidState
    AuctionStates --> CanceledState
    AuctionState <|.. OpenState
    AuctionState <|.. RunningState
    AuctionState <|.. SettlingState
    AuctionState <|.. FinishedState
    AuctionState <|.. PaidState
    AuctionState <|.. CanceledState
    AuctionEventListener <|.. WebSocketObserver
    AuctionEventManager --> AuctionEventListener
    AuctionWebSocketHandler --> WebSocketObserver
    WebSocketObserver --> AuctionWebSocketHandler
    AutoBidStrategy --> AutoBidConfigDao
    AutoBidStrategy --> UserDao
    AutoBidStrategy --> AutoBidConfig
    AutoBidStrategy --> AutoBidExecutor
    AutoBidStrategy --> InTransactionBidExecutor
```

### 6. JavaFX Client, Navigation, Utilities, and Notifications

```mermaid
classDiagram
    direction LR

    class Launcher {
        +main()
    }

    class ClientApp {
        -MIN_WIDTH
        -MIN_HEIGHT
        +start()
        +main()
        -loadFonts()
    }

    class Navigable {
        +onNavigatedTo()
        +onDataReceived()
        +onNavigatedFrom()
    }

    class SceneManager {
        -primaryStage
        -scene
        -rootContainer
        -viewCache
        -controllerCache
        -backStack
        -jwtToken
        -currentUsername
        -currentRole
        -currentUserId
        +init()
        +getInstance()
        +navigateTo()
        +navigateBack()
        +logout()
        +invalidateCache()
    }

    class WelcomeController {
        +goToLoginAsAdmin()
        +goToLoginAsBidder()
        +goToLoginAsSeller()
    }

    class LoginController {
        -usernameField
        -passwordField
        -expectedRole
        +onDataReceived()
        +onNavigatedTo()
        +onNavigatedFrom()
        +handleLogin()
        +goToRegister()
        +goToForgotPassword()
    }

    class RegisterController {
        -usernameField
        -emailField
        -passwordField
        -roleCombo
        +onNavigatedTo()
        +handleRegister()
        +goToLogin()
    }

    class AuctionListController {
        -auctionTable
        -auctions
        -refreshTimeline
        +onNavigatedTo()
        +onNavigatedFrom()
        +handleSearch()
        +loadAuctions()
        +handleBellClick()
    }

    class AuctionDetailController {
        -auctionId
        -currentAuction
        -webSocketClient
        -countdownTimeline
        +onDataReceived()
        +onNavigatedTo()
        +onNavigatedFrom()
        +handleBid()
        +handleAutoBid()
        +handleCancelAutoBid()
        +loadAuctionDetail()
    }

    class AdminPanelController {
        -auctionTable
        -userTable
        -depositTable
        -passwordResetTable
        +onNavigatedTo()
        +onNavigatedFrom()
        +handleRefresh()
        +handleSearch()
        +handleRefreshDeposits()
        +handleRefreshPasswordResets()
        +handleLogout()
    }

    class RestClient {
        -BASE_URL
        -HTTP_CLIENT
        -MAPPER
        +get()
        +post()
        +put()
        +patch()
        +delete()
        +parse()
        +parseList()
    }

    class WebSocketClient {
        -auctionSocket
        -userSocket
        -currentToken
        -scheduler
        +connect()
        +connectUser()
        +disconnectAuction()
        +disconnectUser()
        +disconnectAll()
    }

    class NotificationStore {
        -notifications
        -unreadCount
        +getInstance()
        +add()
        +markAllRead()
        +clear()
        +unreadCountProperty()
    }

    Launcher --> ClientApp
    ClientApp --> SceneManager
    SceneManager --> Navigable
    WelcomeController --> SceneManager
    LoginController --> RestClient
    LoginController --> SceneManager
    RegisterController --> RestClient
    RegisterController --> SceneManager
    AuctionListController --> RestClient
    AuctionListController --> NotificationStore
    AuctionDetailController --> RestClient
    AuctionDetailController --> WebSocketClient
    AdminPanelController --> RestClient
    WebSocketClient --> NotificationStore
    Navigable <|.. LoginController
    Navigable <|.. RegisterController
    Navigable <|.. AuctionListController
    Navigable <|.. AuctionDetailController
    Navigable <|.. AdminPanelController
```

### 7. Source-Level Nested Types and Helpers

```mermaid
classDiagram
    direction LR

    class AuctionDao {
        +findById()
        +findByIdForUpdate()
    }

    class AuctionMapper {
        +map()
    }

    class AutoBidConfigDao {
        +findActiveByAuctionId()
        +upsertInTransaction()
        +updateStatusInTransaction()
    }

    class AutoBidConfigMapper {
        +map()
    }

    class BidTransactionDao {
        +findWithUsernames()
        +findByAuctionId()
    }

    class BidTransactionMapper {
        +map()
    }

    class BidHistoryEntry {
        -transaction
        -username
        +getAuctionId()
        +getBidderId()
        +getAmount()
        +isAutoBid()
        +getCreatedAt()
    }

    class DepositRequestDao {
        +findByIdForUpdate()
    }

    class DepositRecordMapper {
        +map()
    }

    class ItemDao {
        +findById()
    }

    class ItemMapper {
        +map()
    }

    class PasswordResetRequestDao {
        +findByIdForUpdate()
    }

    class PasswordResetMapper {
        +map()
    }

    class UserDao {
        +findById()
        +findByUsername()
    }

    class UserMapper {
        +map()
    }

    class AuctionScheduler {
        -settleAndClose()
    }

    class SchedulerBalanceChange {
        -userId
        -newBalance
        -delta
        -message
        -notificationType
    }

    class SchedulerUserNotification {
        -userId
        -message
        -notificationType
    }

    class SchedulerSettlementResult {
        -auction
        -userNotifications
        -balanceChanges
    }

    class AutoBidStrategy {
        +executeAll()
        +executeAllInTransaction()
    }

    class AutoBidExecutor {
        +execute()
    }

    class InTransactionBidExecutor {
        +execute()
    }

    class AuctionDetailController {
        -deferredAutoBidTerminalState
    }

    class AutoBidUiState {
        -status
        -reason
        -maxBid
        -increment
    }

    class SceneManager {
        +init()
        +navigateTo()
    }

    class ResizeDirection {
        NONE
        NORTH
        SOUTH
        EAST
        WEST
        NORTH_EAST
        NORTH_WEST
        SOUTH_EAST
        SOUTH_WEST
    }

    class AuctionListController {
        -loadCurrentBalance()
    }

    class BalanceDisplay {
        -text
        -color
    }

    class CreateAuctionController {
        -configureDatePickerGlassPopup()
    }

    class GlassDateCell {
        -picker
        -state
        -shadow
        +updateItem()
        -refreshAppearance()
    }

    class GlassCalendarState {
        -hoveredCell
        -hoverProgress
        -hoverTimeline
        -refreshAll()
    }

    AuctionDao *-- AuctionMapper
    AutoBidConfigDao *-- AutoBidConfigMapper
    BidTransactionDao *-- BidTransactionMapper
    BidTransactionDao *-- BidHistoryEntry
    DepositRequestDao *-- DepositRecordMapper
    ItemDao *-- ItemMapper
    PasswordResetRequestDao *-- PasswordResetMapper
    UserDao *-- UserMapper
    AuctionScheduler *-- SchedulerBalanceChange
    AuctionScheduler *-- SchedulerUserNotification
    AuctionScheduler *-- SchedulerSettlementResult
    SchedulerSettlementResult --> SchedulerUserNotification
    SchedulerSettlementResult --> SchedulerBalanceChange
    AutoBidStrategy *-- AutoBidExecutor
    AutoBidStrategy *-- InTransactionBidExecutor
    AuctionDetailController *-- AutoBidUiState
    SceneManager *-- ResizeDirection
    AuctionListController *-- BalanceDisplay
    CreateAuctionController *-- GlassDateCell
    CreateAuctionController *-- GlassCalendarState
    GlassDateCell --> GlassCalendarState
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

## Developer Only: Build JARs from Source

> [!WARNING]
> This section is for developers only. For grading/evaluation, use the prebuilt JARs in Section 3. Build from source only when you intentionally need to regenerate the executable artifacts.

macOS / Linux:

```bash
./gradlew clean buildJars
```

Windows:

```cmd
gradlew.bat clean buildJars
```

Generated JAR paths:

```text
build/libs/auction-server-1.0.0.jar
build/libs/auction-client-1.0.0.jar
```

---

## Developer Build and Quality Gates

```bash
git clone https://github.com/kieran-labs/oop-course-project-uet.git
cd oop-course-project-uet
```

| Command | Purpose |
|---|---|
| `./gradlew run` | Run the server from source |
| `./gradlew runClient` | Run the JavaFX client from source |
| `./gradlew spotlessCheck` | Verify Google Java formatting |
| `./gradlew test` | Run JUnit 5 / Mockito tests |
| `./gradlew check` | Run tests, Checkstyle, SpotBugs, and JaCoCo verification |
| `./gradlew jacocoTestReport` | Generate HTML coverage report |
| `./gradlew buildJars` | Build server and client fat JARs |

GitHub Actions runs formatting, tests, static analysis, and coverage verification on `main` pushes and pull requests.

---

## Rubric Coverage Cross-Check

The completed-feature table above is the primary rubric map. This cross-check restates the same evidence by assessment dimension so the project can be reviewed from either direction: feature-first or rubric-first.

| Rubric item | Evidence |
|---|---|
| Class design and inheritance | `Entity`, `User → Bidder/Seller/Admin`, `Item → Electronics/Art/Vehicle`, `Auction`, `BidTransaction`, `AutoBidConfig` |
| OOP principles | Encapsulation through private fields/getters/setters, inheritance for users/items, polymorphism through `getRole()` / `getCategory()`, abstraction through state/listener interfaces |
| Design patterns | Factory (`UserFactory`, `ItemFactory`, `AuctionStateFactory`), State (`AuctionState` and concrete states), Observer (`AuctionEventManager`, `WebSocketObserver`), Strategy (`AutoBidStrategy`), DAO layer |
| User and product management | Authentication, role-based access, item CRUD, auction CRUD, deposit/password-reset admin workflows |
| Auction functionality | Manual bidding, bid history, lifecycle transitions, settlement, cancellation, winner/leader tracking |
| Error handling | Custom exception hierarchy and API `ErrorResponse` mapping |
| Concurrent bidding | Transactional bid placement and row-level locking via `findByIdForUpdate` methods |
| Realtime update | WebSocket bid updates, auction end events, time extension events, notification pushes, balance updates |
| Client-server | JavaFX client communicates with Javalin server through REST and WebSocket |
| MVC / layering | FXML + JavaFX controllers; server Controller → Service → DAO → HikariCP → PostgreSQL |
| Database infrastructure | Embedded PostgreSQL, PostgreSQL JDBC, HikariCP connection pooling, Flyway migrations, and JDBI DAO access |
| Build and conventions | Gradle Kotlin DSL, ShadowJar fat-JAR tasks, Checkstyle, Spotless, SpotBugs |
| Unit/integration tests | JUnit 5 / Mockito / PostgreSQL integration tests across config, controller, DAO, service, model, pattern, util packages |
| CI/CD | GitHub Actions workflow for formatting, tests, static analysis, coverage, and build verification |
| Advanced features | Auto-bidding with persisted terminal failure reasons, 100-step chain guard, anti-sniping, realtime bid chart, wallet reservation, persistent notifications |

---

## Known Limitations

- Payment is simulated through wallet balance and ledger records; there is no external payment gateway.
- Embedded PostgreSQL is intended for local evaluation and demo. Production should use managed PostgreSQL.
- WebSocket subscriptions are in-memory per server process. Horizontal scaling would require a broker such as Redis Pub/Sub.
- Password reset is admin-reviewed for classroom simplicity; production should use email or another secure out-of-band channel.

---

## Troubleshooting

### `JWT_SECRET is required and must be at least 32 bytes long`

Use the exact one-line server command from Section 4.

Windows PowerShell:

```powershell
$env:JWT_SECRET='auction-demo-secret-1234567890-abcdef-32bytes'; java -jar .\auction-server-1.0.0.jar
```

macOS / Linux:

```bash
JWT_SECRET='auction-demo-secret-1234567890-abcdef-32bytes' java -jar ./auction-server-1.0.0.jar
```

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
| Bui Quang Huy | [@stillqhuy](https://github.com/stillqhuy) | DevOps & QA | GitHub Actions, JUnit tests, Gradle configuration, Checkstyle, SpotBugs, documentation |

---

## License

Released under the [MIT License](LICENSE).

<div align='center'>
<sub>Built for Advanced Programming (LTNC) — University of Engineering and Technology, VNU Hanoi</sub>
</div>
