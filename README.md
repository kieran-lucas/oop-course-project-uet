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
flowchart TD
    subgraph Client["JavaFX Desktop Client"]
        ClientApp["ClientApp / Launcher"]
        SceneManager["SceneManager<br/>single Scene + cached FXML"]
        Controllers["FXML Controllers<br/>ui.controller"]
        RestClient["RestClient<br/>HTTP JSON"]
        WebSocketClient["WebSocketClient<br/>auction/user channels"]
        ClientStore["NotificationStore<br/>BackgroundBidWatcher<br/>UserBalanceWatcher"]

        ClientApp --> SceneManager
        SceneManager --> Controllers
        Controllers --> RestClient
        Controllers --> WebSocketClient
        WebSocketClient --> ClientStore
    end

    subgraph Server["Javalin Server"]
        App["App.java<br/>composition root + inline routes"]
        JwtMiddleware["JwtMiddleware<br/>auth + role context"]
        RestControllers["REST Controllers<br/>Auth · Item · Auction · Bid · Notification"]
        WsHandler["AuctionWebSocketHandler"]
        Services["Service Layer<br/>business rules + transactions"]
        Patterns["Design Patterns<br/>Factory · State · Observer · Strategy"]
        Scheduler["AuctionScheduler"]
        DAO["DAO Layer<br/>JDBI + SQL"]

        App --> JwtMiddleware
        JwtMiddleware --> RestControllers
        RestControllers --> Services
        App --> Services
        App --> WsHandler
        Services --> Patterns
        Services --> DAO
        Scheduler --> Services
        WsHandler --> Patterns
    end

    DB[("PostgreSQL<br/>Embedded or external<br/>Flyway migrations")]

    RestClient -- "REST /api/* + JWT" --> JwtMiddleware
    WebSocketClient -- "/ws/auction/{id}<br/>/ws/user/{id}" --> WsHandler
    DAO --> DB
```

```text
src/main/java/com/auction
  ├─ App.java, AdminSeeder.java, ClientApp.java, Launcher.java
  ├─ config/             # DatabaseConfig, JwtUtil
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
```

---

## Source-Code Coverage Audit for UML

The class diagrams below were reconciled against source files imported by `App.java`, the JavaFX entry points, core services, model classes, pattern implementations, and client utilities. The important correction from the previous README is that several endpoint groups are **not separate controller classes**: user profile/deposit, admin deposit/password-reset/user management, auto-bid endpoints, and `/internal/shutdown` are registered inline in `App.java`.

| Package | Files represented in UML |
|---|---|
| `com.auction` | `App`, `AdminSeeder`, `ClientApp`, `Launcher` |
| `config` / `middleware` | `DatabaseConfig`, `JwtUtil`, `JwtMiddleware` |
| `controller` | `AuthController`, `ItemController`, `AuctionController`, `BidController`, `NotificationController`, `AuctionWebSocketHandler` |
| `service` | `UserService`, `PasswordResetService`, `ItemService`, `AuctionService`, `BidService`, `NotificationService`, `AuctionScheduler` |
| `dao` | `UserDao`, `ItemDao`, `AuctionDao`, `BidTransactionDao`, `AutoBidConfigDao`, `DepositRequestDao`, `PasswordResetRequestDao`, `NotificationDao`, `WalletTransactionDao`, row mappers |
| `model` | `Entity`, `User`, `Admin`, `Seller`, `Bidder`, `Item`, `Electronics`, `Art`, `Vehicle`, `Auction`, `AuctionStatus`, `BidTransaction`, `AutoBidConfig`, `AutoBidStatus`, `AutoBidFailureReason`, `DepositRecord`, `PasswordResetRecord` |
| `dto` | `LoginRequest`, `RegisterRequest`, `ForgotPasswordRequest`, `ChangePasswordRequest`, `DepositRequest`, `CreateItemRequest`, `CreateAuctionRequest`, `BidRequest`, `AutoBidRequest`, `PageRequest`, `UserResponse`, `AuctionResponse`, `BidUpdateMessage`, `ErrorResponse` |
| `exception` | `InvalidBidException`, `AuctionClosedException`, `UnauthorizedException`, `NotFoundException`, `DuplicateException` |
| `pattern` | `UserFactory`, `ItemFactory`, `AuctionStateFactory`, `AuctionState`, `AuctionStates`, `OpenState`, `RunningState`, `SettlingState`, `FinishedState`, `PaidState`, `CanceledState`, `AuctionEventListener`, `AuctionEventManager`, `WebSocketObserver`, `AutoBidStrategy`, `AutoBidExecutor`, `InTransactionBidExecutor` |
| `util` | `MoneyValidator`, `NotificationFormat`, `RestClient`, `WebSocketClient`, `BackgroundBidWatcher`, `UserBalanceWatcher`, `NotificationStore`, `NotificationItem` |
| `ui.controller` / `ui.util` | `WelcomeController`, `LoginController`, `RegisterController`, `ForgotPasswordController`, `AuctionListController`, `AuctionDetailController`, `CreateItemController`, `CreateAuctionController`, `ProfileController`, `DepositController`, `ChangePasswordController`, `AdminPanelController`, `SceneManager`, `Navigable` |

All Mermaid diagrams use top-down layout (`TD`/`TB`) to reduce horizontal expansion in GitHub's renderer. Getter/setter boilerplate is summarized where it is structurally repetitive. Business methods, lifecycle methods, factory methods, validation methods, transaction methods, and WebSocket/event methods are listed explicitly.

---

## Class Diagrams

### 1. Runtime Composition, Security, and Inline Routes

```mermaid
classDiagram
    direction TB

    class App {
        -int SERVER_PORT
        -Path DATA_DIR
        -Path SERVER_PID_FILE
        -Path SERVER_TOKEN_FILE
        -SecureRandom SECURE_RANDOM
        -AtomicBoolean SHUTTING_DOWN
        +main(String[] args)
        -buildJavalin(ObjectMapper mapper) Javalin
        -registerShutdownHook(Javalin app, AuctionScheduler scheduler)
        -stopServer(Javalin app, AuctionScheduler scheduler)
        -isServerAlreadyRunning() boolean
        -loadOrCreateShutdownToken() String
        -writeServerPid()
        -deleteServerPid()
        -isLocalRequest(String ip) boolean
        -requireAdmin(Context ctx)
        -requireRole(Context ctx, String requiredRole)
        -seedAdminIfNeeded(UserDao userDao)
        -registerExceptionHandlers(Javalin app)
    }

    class InlineAppRoutes {
        <<conceptual group inside App.java>>
        +GET /api/health
        +POST /internal/shutdown
        +GET /api/users/me
        +PUT /api/users/me/password
        +GET /api/users/me/deposit-requests
        +POST /api/users/me/deposit
        +GET /api/admin/deposit-requests
        +POST /api/admin/deposit-requests/{id}/approve
        +POST /api/admin/deposit-requests/{id}/reject
        +GET /api/admin/password-reset-requests
        +POST /api/admin/password-reset-requests/{id}/approve
        +POST /api/admin/password-reset-requests/{id}/reject
        +DELETE /api/admin/auctions/{id}
        +GET /api/admin/users
        +DELETE /api/admin/users/{id}
        +GET /api/auctions/{id}/auto-bid
        +POST /api/auctions/{id}/auto-bid
        +DELETE /api/auctions/{id}/auto-bid
        +WS /ws/auction/{id}
        +WS /ws/user/{id}
    }

    class AdminSeeder {
        -UserDao userDao
        -String DEFAULT_ADMIN_USERNAME
        -String DEFAULT_ADMIN_EMAIL
        -String ENV_PASSWORD_VAR
        -String DEMO_FALLBACK_PASSWORD
        +seed()
        ~resolveAdminPassword() String
    }

    class DatabaseConfig {
        -Jdbi jdbi
        -HikariDataSource dataSource
        -EmbeddedPostgres embeddedPostgres
        -File EMBEDDED_DATA_DIR
        +create() Jdbi
        +shutDown()
        -initExternalPostgres(String dbUrl)
        -initEmbeddedPostgres()
        -runMigrations(HikariDataSource dataSource)
        -buildHikariConfig(String url, String user, String pass) HikariConfig
        -stopPreviousPostgresIfNeeded()
        -registerShutdownHook()
    }

    class JwtUtil {
        -int MIN_SECRET_BYTES
        -String SECRET_KEY
        -Algorithm ALGORITHM
        -JWTVerifier VERIFIER
        +validateConfiguration()
        ~requireJwtSecret(String secret) String
        +createToken(Long userId, String username, String role) String
        +createToken(Long userId, String username, String role, int tokenVersion) String
        +verifyToken(String token) DecodedJWT
    }

    class JwtMiddleware {
        -UserDao userDao
        +configure(UserDao configuredUserDao)
        +handle(Context ctx)
        -attachUserClaims(Context ctx, DecodedJWT jwt)
        -validateTokenVersion(DecodedJWT jwt)
        -extractTokenVersion(DecodedJWT jwt) int
    }

    class AuthController {
        +register(Javalin app, UserService userService)
        +registerPasswordReset(Javalin app, PasswordResetService resetService)
        -handleRegister(Context ctx, UserService userService)
        -handleLogin(Context ctx, UserService userService)
        -handleForgotPassword(Context ctx, PasswordResetService service)
    }

    class ItemController {
        +register(Javalin app, ItemService itemService)
        -handleGetAll(Context ctx, ItemService itemService)
        -handleGetById(Context ctx, ItemService itemService)
        -handleCreate(Context ctx, ItemService itemService)
        -handleUpdate(Context ctx, ItemService itemService)
        -handleDelete(Context ctx, ItemService itemService)
    }

    class AuctionController {
        +register(Javalin app, AuctionService auctionService)
        -handleGetAll(Context ctx, AuctionService auctionService)
        -handleGetById(Context ctx, AuctionService auctionService)
        -handleCreate(Context ctx, AuctionService auctionService)
        -handleUpdate(Context ctx, AuctionService auctionService)
        -handleDelete(Context ctx, AuctionService auctionService)
    }

    class BidController {
        -BidService bidService
        +register(Javalin app)
        ~handleManualBid(Context ctx)
    }

    class NotificationController {
        +register(Javalin app, NotificationService notificationService)
        -handleGetNotifications(Context ctx, NotificationService notificationService)
        -handleMarkRead(Context ctx, NotificationService notificationService)
        -handleMarkAllRead(Context ctx, NotificationService notificationService)
    }

    class AuctionWebSocketHandler {
        -Jdbi jdbi
        -UserDao userDao
        -Map connections
        -Map userConnections
        -Map observers
        -Map sessionExpiresAt
        -Map expirationTasks
        -ScheduledExecutorService expirationScheduler
        -AuctionEventManager eventManager
        -ObjectMapper objectMapper
        +onConnect(WsConnectContext ctx)
        +onUserConnect(WsConnectContext ctx)
        +onClose(WsCloseContext ctx)
        +onUserClose(WsCloseContext ctx)
        +onError(WsErrorContext ctx)
        +onUserError(WsErrorContext ctx)
        +broadcast(Long auctionId, BidUpdateMessage message)
        +pushUserNotification(Long userId, String message)
        +notifyBalanceUpdate(Long userId, BigDecimal newBalance, BigDecimal delta, boolean approved)
        +saveNotificationToDatabase(Long userId, String message, String type)
    }

    class AuctionScheduler {
        -AuctionDao auctionDao
        -UserDao userDao
        -ItemDao itemDao
        -AuctionEventManager eventManager
        -Jdbi jdbi
        -AuctionWebSocketHandler wsHandler
        -ScheduledExecutorService scheduler
        -ScheduledFuture scheduledTask
        -AtomicBoolean running
        +start()
        +stop()
        ~scanAndTransition()
        -openToRunning(LocalDateTime now)
        -runningToFinished(LocalDateTime now)
        -settleAndClose(Long id, LocalDateTime now)
        -notifyAuctionEnded(Auction auction)
    }

    App --> DatabaseConfig
    App --> JwtUtil
    App --> JwtMiddleware
    App --> AdminSeeder
    App --> InlineAppRoutes
    App --> AuthController
    App --> ItemController
    App --> AuctionController
    App --> BidController
    App --> NotificationController
    App --> AuctionWebSocketHandler
    App --> AuctionScheduler
    JwtMiddleware --> JwtUtil
    JwtMiddleware --> UserDao
    AuctionWebSocketHandler --> JwtUtil
    AuctionWebSocketHandler --> AuctionEventManager
    AuctionWebSocketHandler --> WebSocketObserver
    AuctionScheduler --> AuctionDao
    AuctionScheduler --> UserDao
    AuctionScheduler --> ItemDao
    AuctionScheduler --> AuctionEventManager
    AuctionScheduler --> AuctionWebSocketHandler
```

### 2. Service Layer and Data Access Layer

```mermaid
classDiagram
    direction TB

    class UserService {
        -UserDao userDao
        -DepositRequestDao depositRequestDao
        -Jdbi jdbi
        +register(RegisterRequest req) User
        +login(LoginRequest req) String
        +getRoleByUsername(String username) String
        +findById(Long userId) UserResponse
        +changePassword(Long userId, ChangePasswordRequest req)
        +requestDeposit(Long userId, BigDecimal amount) DepositRecord
        +getPendingDeposits() List
        +approveDeposit(Long requestId) UserResponse
        +rejectDeposit(Long requestId) Long
        +getAll() List
        +delete(Long userId)
    }

    class PasswordResetService {
        -UserDao userDao
        -PasswordResetRequestDao resetDao
        -Jdbi jdbi
        +requestReset(String email)
        +getPendingRequests() List
        +approveReset(Long requestId) String
        +rejectReset(Long requestId)
        -generateTempPassword() String
    }

    class ItemService {
        -ItemDao itemDao
        +create(CreateItemRequest req, Long sellerId) Item
        +getAll() List
        +getBySellerId(Long sellerId) List
        +getById(Long id) Item
        +update(Long id, CreateItemRequest request, Long requesterId) Item
        +delete(Long id, Long requesterId, String requesterRole)
        -checkOwnership(Item item, Long requesterId, String action)
    }

    class AuctionService {
        -AuctionDao auctionDao
        -ItemDao itemDao
        -UserDao userDao
        -AuctionEventManager eventManager
        -Jdbi jdbi
        -BidTransactionDao bidTransactionDao
        -AuctionWebSocketHandler wsHandler
        +getAll(String status, PageRequest pageRequest) List
        +getById(Long id) AuctionResponse
        +getAuctionById(Long id) AuctionResponse
        +create(CreateAuctionRequest req, Long sellerId) Auction
        +delete(Long id, Long requesterId, String requesterRole)
        +hardDelete(Long id)
        +getState(Auction auction) AuctionState
        -createInTransaction(CreateAuctionRequest req, Long sellerId) Auction
        -validateItemCanBeAuctioned(...)
        -enrichAuctionResponse(Auction auction) AuctionResponse
    }

    class BidService {
        -AuctionDao auctionDao
        -BidTransactionDao bidTransactionDao
        -AutoBidConfigDao autoBidConfigDao
        -AuctionEventManager eventManager
        -Jdbi jdbi
        -AuctionService auctionService
        -UserDao userDao
        -AutoBidStrategy autoBidStrategy
        -AuctionWebSocketHandler wsHandler
        +placeBid(Long auctionId, Long bidderId, BigDecimal amount, boolean isAutoBid) BidTransaction
        +getBidHistory(Long auctionId) List
        +createAutoBid(Long auctionId, Long bidderId, BigDecimal maxBid, BigDecimal increment) AutoBidConfig
        -executeChainBidInHandle(...)
        -notifyBidUpdate(...)
        -requirePositiveIntegerVnd(BigDecimal amount, String label)
    }

    class NotificationService {
        -NotificationDao notificationDao
        +getRecentNotifications(Long userId) List
        +markRead(Long notificationId, Long userId)
        +markAllRead(Long userId) int
    }

    class UserDao {
        -Jdbi jdbi
        -String SELECT_COLUMNS
        +insert(User user) User
        +findById(Long id) Optional
        +findByIdForUpdate(Handle handle, Long id) User
        +findByUsername(String username) Optional
        +findByEmail(String email) Optional
        +existsByUsername(String username) boolean
        +existsByEmail(String email) boolean
        +findAll() List
        +update(User user)
        +delete(Long id)
        +updateReservedBalanceInTransaction(Handle handle, Long userId, BigDecimal amount)
        +releaseReservedBalanceInTransaction(Handle handle, Long userId, BigDecimal amount)
    }

    class ItemDao {
        -Jdbi jdbi
        +insert(Item item) Item
        +findAll() List
        +findById(Long id) Optional
        +findByIdForUpdate(Handle handle, Long id) Optional
        +findBySellerId(Long sellerId) List
        +update(Item item)
        +delete(Long id)
        +updateStatusInTransaction(Handle handle, Long itemId, String status)
    }

    class AuctionDao {
        -Jdbi jdbi
        -String SELECT_COLUMNS
        +insert(Auction auction) Auction
        +insertInTransaction(Handle handle, Auction auction) Auction
        +findById(Long id) Optional
        +findByIdForUpdate(Handle handle, Long id) Auction
        +findAll(PageRequest pageRequest) List
        +findByStatus(String status, PageRequest pageRequest) List
        +existsById(Long id) boolean
        +existsActiveAuctionForItem(Long itemId) boolean
        +existsPaidAuctionForItem(Long itemId) boolean
        +update(Auction auction)
        +updateInTransaction(Handle handle, Auction auction)
        +atomicTransition(Long id, String from, String to) boolean
        +findDueAuctionIds(String status, LocalDateTime now) List
        +findExpiredAuctionIds(String status, LocalDateTime now) List
    }

    class BidTransactionDao {
        -Jdbi jdbi
        +insert(Handle handle, BidTransaction tx) BidTransaction
        +findByAuctionId(Long auctionId) List
    }

    class AutoBidConfigDao {
        -Jdbi jdbi
        +insert(AutoBidConfig config) AutoBidConfig
        +findById(Long id) Optional
        +findByAuctionAndBidder(Long auctionId, Long bidderId) Optional
        +findActiveByAuctionId(Long auctionId) List
        +hasActiveConfig(Handle handle, Long auctionId, Long bidderId) boolean
        +update(AutoBidConfig config)
    }

    class DepositRequestDao {
        -Jdbi jdbi
        +insert(DepositRecord record) DepositRecord
        +findById(Long id) Optional
        +findByIdForUpdate(Handle handle, Long id) Optional
        +findByUserId(Long userId) List
        +findByStatus(String status) List
        +transitionStatusInTransaction(Handle handle, Long id, String from, String to)
    }

    class PasswordResetRequestDao {
        -Jdbi jdbi
        +insert(PasswordResetRecord record)
        +findByIdForUpdate(Handle handle, Long id) Optional
        +findByStatus(String status) List
        +hasPendingRequest(Long userId) boolean
        +transitionStatusInTransaction(Handle handle, Long id, String from, String to)
    }

    class NotificationDao {
        -Jdbi jdbi
        +findRecentByUserId(Long userId) List
        +markRead(Long notificationId, Long userId)
        +markAllRead(Long userId) int
    }

    class WalletTransactionDao {
        +insert(Handle handle, Long userId, Long auctionId, Long bidId, String type, BigDecimal amount, String note)
    }

    UserService --> UserDao
    UserService --> DepositRequestDao
    UserService ..> WalletTransactionDao
    PasswordResetService --> UserDao
    PasswordResetService --> PasswordResetRequestDao
    ItemService --> ItemDao
    AuctionService --> AuctionDao
    AuctionService --> ItemDao
    AuctionService --> UserDao
    AuctionService --> BidTransactionDao
    BidService --> AuctionDao
    BidService --> BidTransactionDao
    BidService --> AutoBidConfigDao
    BidService --> UserDao
    BidService --> AuctionService
    BidService ..> WalletTransactionDao
    NotificationService --> NotificationDao
```

### 3. Domain Model, Records, and Enums

```mermaid
classDiagram
    direction TB

    class Entity {
        <<abstract>>
        -Long id
        -LocalDateTime createdAt
        +Long getId()
        +void setId(Long id)
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
        +String getUsername()
        +BigDecimal getBalance()
        +void setBalance(BigDecimal balance)
        +int getTokenVersion()
        +void setTokenVersion(int tokenVersion)
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
        +String getName()
        +String getDescription()
        +Long getSellerId()
        +String getStatus()
        +void setStatus(String status)
    }

    class Electronics {
        -String brand
        +String getCategory()
        +String getBrand()
    }

    class Art {
        -String artist
        +String getCategory()
        +String getArtist()
    }

    class Vehicle {
        -Integer year
        +String getCategory()
        +Integer getYear()
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
        +BigDecimal getCurrentPrice()
        +void setCurrentPrice(BigDecimal price)
        +void setStatus(AuctionStatus status)
        +void setUpdatedAt(LocalDateTime updatedAt)
    }

    class BidTransaction {
        -Long auctionId
        -Long bidderId
        -BigDecimal amount
        -boolean autoBid
        -String bidderUsername
        +Long getAuctionId()
        +Long getBidderId()
        +BigDecimal getAmount()
        +boolean isAutoBid()
        +String getBidderUsername()
    }

    class AutoBidConfig {
        -Long auctionId
        -Long bidderId
        -BigDecimal maxBid
        -BigDecimal increment
        -AutoBidStatus status
        -AutoBidFailureReason failureReason
        -LocalDateTime registeredAt
        +boolean isActive()
        +boolean canBidAt(BigDecimal currentPrice)
        +BigDecimal getNextBidAmount(BigDecimal currentPrice)
        +void setStatus(AutoBidStatus status)
        +void setFailureReason(AutoBidFailureReason reason)
    }

    class DepositRecord {
        -Long id
        -Long userId
        -String username
        -BigDecimal amount
        -String status
        -LocalDateTime createdAt
        -LocalDateTime reviewedAt
        +Long getUserId()
        +BigDecimal getAmount()
        +String getStatus()
        +void setStatus(String status)
    }

    class PasswordResetRecord {
        -Long id
        -Long userId
        -String username
        -String email
        -String status
        -LocalDateTime createdAt
        -LocalDateTime reviewedAt
        +Long getUserId()
        +String getEmail()
        +String getStatus()
        +void setStatus(String status)
    }

    class AuctionStatus {
        <<enum>>
        OPEN
        RUNNING
        SETTLING
        FINISHED
        PAID
        CANCELED
        +from(String s)
    }

    class AutoBidStatus {
        <<enum>>
        ACTIVE
        STOPPED
        EXHAUSTED
        FAILED
        +from(String value)
    }

    class AutoBidFailureReason {
        <<enum>>
        MAX_PRICE_TOO_LOW
        INSUFFICIENT_BALANCE
        AUCTION_NOT_RUNNING
        BIDDER_ALREADY_HIGHEST
        ACTIVE_AUTOBID_EXISTS
        +from(String value)
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

    Seller "1" --> "0..*" Item : owns
    Item "1" --> "0..1" Auction : auctioned by
    Auction "1" --> "0..*" BidTransaction : bid history
    Bidder "1" --> "0..*" BidTransaction : places
    Auction "1" --> "0..*" AutoBidConfig : auto-bid rules
    Bidder "1" --> "0..*" AutoBidConfig : configures
    Bidder "1" --> "0..*" DepositRecord : deposit requests
    User "1" --> "0..*" PasswordResetRecord : reset requests
    Auction --> AuctionStatus
    AutoBidConfig --> AutoBidStatus
    AutoBidConfig --> AutoBidFailureReason
```

### 4. DTOs, WebSocket Contracts, and Exceptions

```mermaid
classDiagram
    direction TB

    class LoginRequest {
        -String username
        -String password
        +getUsername()
        +getPassword()
    }

    class RegisterRequest {
        -String username
        -String email
        -String password
        -String role
        +getUsername()
        +getEmail()
        +getPassword()
        +getRole()
    }

    class ForgotPasswordRequest {
        -String email
        +getEmail()
    }

    class ChangePasswordRequest {
        -String currentPassword
        -String newPassword
        +getCurrentPassword()
        +getNewPassword()
    }

    class DepositRequest {
        -BigDecimal amount
        +getAmount()
    }

    class CreateItemRequest {
        -String name
        -String description
        -String category
        -String categoryDetail
        +getName()
        +getDescription()
        +getCategory()
        +getCategoryDetail()
    }

    class CreateAuctionRequest {
        -Long itemId
        -BigDecimal startingPrice
        -LocalDateTime startTime
        -LocalDateTime endTime
        +getItemId()
        +getStartingPrice()
        +getStartTime()
        +getEndTime()
    }

    class BidRequest {
        -BigDecimal amount
        +getAmount()
    }

    class AutoBidRequest {
        -BigDecimal maxBid
        -BigDecimal increment
        +getMaxBid()
        +getIncrement()
    }

    class PageRequest {
        -int page
        -int size
        -int offset
        +of(int page,int size) PageRequest
        +getLimit() int
        +getOffset() int
    }

    class UserResponse {
        -Long id
        -String username
        -String email
        -String role
        -BigDecimal balance
        -BigDecimal reservedBalance
        +from(User user) UserResponse
    }

    class AuctionResponse {
        -Long id
        -String itemName
        -String itemCategory
        -BigDecimal startingPrice
        -BigDecimal currentPrice
        -String status
        -String leadingBidderUsername
        -long remainingTimeMs
        +from(Auction auction) AuctionResponse
    }

    class ErrorResponse {
        -String code
        -String message
        +of(String code,String message) ErrorResponse
    }

    class BidUpdateMessage {
        +TYPE_BID_UPDATE
        +TYPE_TIME_EXTENDED
        +TYPE_AUCTION_ENDED
        +TYPE_AUTO_BID_TRIGGERED
        +TYPE_BALANCE_UPDATED
        +TYPE_USER_NOTIFICATION
        -String type
        -Long auctionId
        -BigDecimal currentPrice
        -Long leadingBidderId
        -String leadingBidderUsername
        -LocalDateTime endTime
        -LocalDateTime timestamp
        -boolean autoBid
        -BigDecimal newBalance
        -BigDecimal balanceDelta
        -boolean approved
        -String message
        +bidUpdate(...)
        +timeExtended(...)
        +auctionEnded(...)
        +balanceUpdated(...)
        +balanceChanged(...)
        +userNotification(...)
    }

    class InvalidBidException
    class AuctionClosedException
    class UnauthorizedException
    class NotFoundException
    class DuplicateException

    UserResponse ..> User
    AuctionResponse ..> Auction
    BidUpdateMessage ..> Auction
    ErrorResponse ..> InvalidBidException
    ErrorResponse ..> AuctionClosedException
    ErrorResponse ..> UnauthorizedException
    ErrorResponse ..> NotFoundException
    ErrorResponse ..> DuplicateException
```

### 5. Design Patterns and Realtime Collaboration

```mermaid
classDiagram
    direction TB

    class UserFactory {
        +create(String role) User
    }

    class ItemFactory {
        +create(CreateItemRequest req, Long sellerId) Item
        -parseYear(String yearText) int
    }

    class AuctionStateFactory {
        +create(String status) AuctionState
    }

    class AuctionState {
        <<interface>>
        +placeBid(Auction auction, BigDecimal amount, Long bidderId)
        +close(Auction auction)
        +edit(Auction auction)
        +extend(Auction auction, long extraSeconds)
    }

    class AuctionStates {
        +OPEN
        +RUNNING
        +SETTLING
        +FINISHED
        +PAID
        +CANCELED
    }

    class OpenState { +placeBid(...) +close(...) +edit(...) +extend(...) }
    class RunningState { +placeBid(...) +close(...) +edit(...) +extend(...) }
    class SettlingState { +placeBid(...) +close(...) +edit(...) +extend(...) }
    class FinishedState { +placeBid(...) +close(...) +edit(...) +extend(...) }
    class PaidState { +placeBid(...) +close(...) +edit(...) +extend(...) }
    class CanceledState { +placeBid(...) +close(...) +edit(...) +extend(...) }

    class AuctionEventListener {
        <<interface>>
        +onBidUpdate(BidUpdateMessage msg)
        +onTimeExtended(BidUpdateMessage msg)
        +onAuctionEnd(BidUpdateMessage msg)
    }

    class AuctionEventManager {
        -Map listeners
        +subscribe(Long auctionId,AuctionEventListener listener)
        +unsubscribe(Long auctionId,AuctionEventListener listener)
        +notifyBidUpdate(Long auctionId,BidUpdateMessage msg)
        +notifyTimeExtended(Long auctionId,BidUpdateMessage msg)
        +notifyAuctionEnd(Long auctionId,BidUpdateMessage msg)
        -notifyAll(...)
    }

    class WebSocketObserver {
        -AuctionWebSocketHandler handler
        -Long auctionId
        +onBidUpdate(BidUpdateMessage msg)
        +onTimeExtended(BidUpdateMessage msg)
        +onAuctionEnd(BidUpdateMessage msg)
    }

    class AutoBidStrategy {
        -AutoBidConfigDao autoBidConfigDao
        -UserDao userDao
        +executeAll(Long auctionId,BigDecimal currentPriceAfterBid,Long initialLeaderId,AutoBidExecutor executor)
        +executeAllInTransaction(Handle handle,Long auctionId,BigDecimal currentPriceAfterBid,Long initialLeaderId,InTransactionBidExecutor executor)
    }

    class AutoBidExecutor {
        <<interface>>
        +execute(Long auctionId,Long bidderId,BigDecimal amount) BidTransaction
    }

    class InTransactionBidExecutor {
        <<interface>>
        +execute(Handle handle,Long auctionId,Long bidderId,BigDecimal amount) BidTransaction
    }

    UserFactory ..> User
    ItemFactory ..> Item
    AuctionStateFactory --> AuctionStates
    AuctionStateFactory ..> AuctionState
    AuctionState <|.. OpenState
    AuctionState <|.. RunningState
    AuctionState <|.. SettlingState
    AuctionState <|.. FinishedState
    AuctionState <|.. PaidState
    AuctionState <|.. CanceledState
    AuctionStates --> OpenState
    AuctionStates --> RunningState
    AuctionStates --> SettlingState
    AuctionStates --> FinishedState
    AuctionStates --> PaidState
    AuctionStates --> CanceledState
    AuctionEventListener <|.. WebSocketObserver
    AuctionEventManager --> AuctionEventListener
    WebSocketObserver --> AuctionWebSocketHandler
    AutoBidStrategy --> AutoBidConfig
    AutoBidStrategy --> AutoBidExecutor
    AutoBidStrategy --> InTransactionBidExecutor
```

### 6. JavaFX Client, Navigation, Utilities, and Notifications

```mermaid
classDiagram
    direction TB

    class Launcher { +main(String[] args) }
    class ClientApp { -double MIN_WIDTH -double MIN_HEIGHT +start(Stage primaryStage) +main(String[] args) -loadFonts() }
    class Navigable { <<interface>> +onNavigatedTo() +onDataReceived(Object data) +onNavigatedFrom() }

    class SceneManager {
        -Stage primaryStage
        -Scene scene
        -StackPane rootContainer
        -Map viewCache
        -Map controllerCache
        -Deque backStack
        -String jwtToken
        -String currentUsername
        -String currentRole
        -Long currentUserId
        +init(Stage primaryStage,double width,double height) SceneManager
        +getInstance() SceneManager
        +navigateTo(String fxml)
        +navigateTo(String fxml,Object data)
        +navigateBack(String fallbackFxml)
        +logout()
        +invalidateCache(String fxml)
    }

    class WelcomeController { +goToLoginAsAdmin() +goToLoginAsBidder() +goToLoginAsSeller() }
    class LoginController { -TextField usernameField -PasswordField passwordField -String expectedRole +onDataReceived(Object data) +onNavigatedTo() +onNavigatedFrom() +handleLogin() +goToRegister() +goToForgotPassword() }
    class RegisterController { -TextField usernameField -TextField emailField -PasswordField passwordField -ComboBox roleCombo +onNavigatedTo() +handleRegister() +goToLogin() }
    class ForgotPasswordController { -TextField emailField -Button submitButton +onNavigatedTo() +handleSubmit() }
    class AuctionListController { -TableView auctionTable -ObservableList auctions -Timeline refreshTimeline +onNavigatedTo() +onNavigatedFrom() +handleSearch() +loadAuctions() +handleBellClick() }
    class AuctionDetailController { -Long auctionId -AuctionResponse currentAuction -WebSocketClient webSocketClient -Timeline countdownTimeline +onDataReceived(Object data) +onNavigatedTo() +onNavigatedFrom() +handleBid() +handleAutoBid() +handleCancelAutoBid() +loadAuctionDetail() }
    class CreateItemController { -TextField nameField -TextArea descriptionField -ComboBox categoryCombo +onNavigatedTo() +handleCategoryChange() +handleCreate() +goBack() }
    class CreateAuctionController { -ComboBox itemCombo -TextField startingPriceField -DatePicker startDatePicker -DatePicker endDatePicker +onNavigatedTo() +handleCreate() +goToCreateItem() +goBack() }
    class ProfileController { -Label usernameLabel -Label roleLabel -Label profileBalanceLabel +onNavigatedTo() +onNavigatedFrom() +goToChangePassword() +goToDeposit() +handleLogout() }
    class DepositController { -TextField amountField -ListView historyList -Timeline depositPollTimeline +onNavigatedTo() +onNavigatedFrom() +handleDeposit() +loadBalance() +loadHistory() }
    class ChangePasswordController { -PasswordField currentPasswordField -PasswordField newPasswordField -PasswordField confirmPasswordField +onNavigatedTo() +handleChangePassword() }
    class AdminPanelController { -TableView auctionTable -TableView userTable -TableView depositTable -TableView passwordResetTable +onNavigatedTo() +onNavigatedFrom() +handleRefresh() +handleSearch() +handleRefreshDeposits() +handleRefreshPasswordResets() +handleLogout() }

    class RestClient { -String BASE_URL -HttpClient HTTP_CLIENT -ObjectMapper MAPPER +get(String path) +post(String path,Object body) +put(String path,Object body) +patch(String path,Object body) +delete(String path) +parse(String json,Class clazz) +parseList(String json,Class clazz) }
    class WebSocketClient { -WebSocket auctionSocket -WebSocket userSocket -String currentToken -ScheduledExecutorService scheduler +connect(Long auctionId,String jwtToken,Consumer onMessage) +connectUser(Long userId,String jwtToken,Consumer onMessage) +disconnectAuction() +disconnectUser() +disconnectAll() }
    class BackgroundBidWatcher { -Map watchers +getInstance() +watch(Long auctionId,String token,String itemName,Long userId) +stopWatching(Long auctionId) +stopAll() }
    class UserBalanceWatcher { -WebSocketClient wsClient -BiConsumer onBalanceUpdate +getInstance() +connect(Long userId,String token) +disconnect() +setOnBalanceUpdate(BiConsumer listener) }
    class NotificationStore { -ObservableList notifications -SimpleIntegerProperty unreadCount +getInstance() +add(String notification) +add(NotificationItem item) +markAllRead() +clear() +unreadCountProperty() }
    class NotificationItem { -Long id -String message -String type -boolean read -LocalDateTime createdAt +clientOnly(String message) NotificationItem +getMessage() +isRead() +setRead(boolean read) }
    class MoneyValidator { +requirePositiveIntegerVnd(BigDecimal amount,String fieldName) +isIntegerVnd(BigDecimal amount) boolean +toIntegerVndExact(BigDecimal amount,String fieldName) long }
    class NotificationFormat { +USER_OPEN +USER_CLOSE +user(String username) String +auctionName(Long auctionId,String itemName) String }

    Launcher --> ClientApp
    ClientApp --> SceneManager
    SceneManager --> Navigable
    SceneManager --> NotificationStore
    SceneManager --> BackgroundBidWatcher
    SceneManager --> UserBalanceWatcher
    Navigable <|.. LoginController
    Navigable <|.. RegisterController
    Navigable <|.. ForgotPasswordController
    Navigable <|.. AuctionListController
    Navigable <|.. AuctionDetailController
    Navigable <|.. CreateItemController
    Navigable <|.. CreateAuctionController
    Navigable <|.. ProfileController
    Navigable <|.. DepositController
    Navigable <|.. ChangePasswordController
    Navigable <|.. AdminPanelController
    WelcomeController --> SceneManager
    LoginController --> RestClient
    LoginController --> SceneManager
    LoginController --> UserBalanceWatcher
    RegisterController --> RestClient
    RegisterController --> SceneManager
    RegisterController --> UserBalanceWatcher
    ForgotPasswordController --> RestClient
    AuctionListController --> RestClient
    AuctionListController --> NotificationStore
    AuctionListController --> NotificationFormat
    AuctionDetailController --> RestClient
    AuctionDetailController --> WebSocketClient
    AuctionDetailController --> BackgroundBidWatcher
    CreateItemController --> RestClient
    CreateAuctionController --> RestClient
    ProfileController --> RestClient
    ProfileController --> UserBalanceWatcher
    ProfileController --> BackgroundBidWatcher
    DepositController --> RestClient
    ChangePasswordController --> RestClient
    AdminPanelController --> RestClient
    BackgroundBidWatcher --> WebSocketClient
    BackgroundBidWatcher --> NotificationStore
    UserBalanceWatcher --> WebSocketClient
    UserBalanceWatcher --> NotificationStore
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
