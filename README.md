<div align="center">

<img src="assets/app-screenshot.png" alt="Online Auction System - live bid chart + countdown timer" width="900"/>

# Online Auction System

*A real-time desktop auction platform - JavaFX client • Javalin server • PostgreSQL • WebSocket*

[![CI](https://img.shields.io/badge/CI-passing-brightgreen?style=for-the-badge&logo=githubactions&logoColor=white)](https://github.com/kieran-labs/oop-course-project-uet/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/JAVA-21-orange?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Coverage](https://img.shields.io/badge/COVERAGE-JaCoCo-brightgreen?style=for-the-badge)](https://github.com/kieran-labs/oop-course-project-uet/actions)
[![SpotBugs](https://img.shields.io/badge/SPOTBUGS-passing-yellow?style=for-the-badge)](https://github.com/kieran-labs/oop-course-project-uet/actions)
[![Javalin](https://img.shields.io/badge/JAVALIN-6.4.0-black?style=for-the-badge)](https://javalin.io)
[![PostgreSQL](https://img.shields.io/badge/POSTGRESQL-16-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/LICENSE-MIT-blue?style=for-the-badge)](LICENSE)

**[📹 Demo Video](#)** • **[📄 PDF Report](#)** • **[⬇️ Download JARs](https://github.com/kieran-labs/oop-course-project-uet/releases/tag/v1.0.0)**

</div>

---

## 🧩 Overview

A full-stack **desktop auction platform** built with Java 21. A **JavaFX client** communicates with a **Javalin HTTP/WebSocket server** backed by a PostgreSQL database (embedded, zero-install). Multiple clients can bid simultaneously with real-time price updates pushed over WebSocket - no polling, no stale data.

**What makes this project non-trivial:**

- **Concurrent bid safety** via database-level `SELECT FOR UPDATE` inside a JDBI transaction - prevents lost updates and double-winners under simultaneous bids
- **Anti-sniping protection**: bids in the final 30 seconds automatically extend the deadline by 60 seconds
- **Auto-bidding engine** using a `PriorityQueue` with FIFO tie-breaking, capable of chaining multiple auto-bids in a single transaction
- A complete **5-state auction lifecycle** enforced by the State pattern - illegal operations throw typed exceptions, not silent failures
- **12 JavaFX screens** with a clean blue theme (`#1565C0` primary, `#EFF6FF` background), fade transitions, and a live `LineChart` fed directly from WebSocket events

The project covers **3 user roles** (Admin, Seller, Bidder), **3 item categories** (Electronics, Art, Vehicle), and a complete lifecycle from item creation through payment and password management - **~99 Java files**, 20+ test classes, 17 database migrations.

**Environment:** Java 21+ • Windows / macOS / Linux • No external services required

---

## 🖼️ Screenshots

| Login | Auction List |
|:---:|:---:|
| <img src="assets/screenshots/login.png" width="420"/> | <img src="assets/screenshots/auction-list.png" width="420"/> |

| Live Bid Detail *(with real-time chart + countdown)* | Admin Dashboard |
|:---:|:---:|
| <img src="assets/screenshots/auction-detail.png" width="420"/> | <img src="assets/screenshots/admin.png" width="420"/> |

---

## ✅ Completed Features

### Required

- [x] Registration / login with role-based access control (Bidder · Seller · Admin)
- [x] Create / edit / delete items - 3 categories (Electronics, Art, Vehicle)
- [x] Create and manage auction sessions; lifecycle `OPEN → RUNNING → FINISHED → PAID / CANCELED`
- [x] Manual bidding - BIDDER-only, validates integer VND `amount > currentPrice`, stored atomically
- [x] Automatic session expiry (`AuctionScheduler`)
- [x] Winner determination, transitions auction to `PAID`
- [x] Error handling & exceptions - 5 custom exception types, HTTP status mapping
- [x] JavaFX GUI - 12 screens, Lexend font, blue theme
- [x] Concurrent bidding safety - `SELECT FOR UPDATE` inside JDBI transaction
- [x] Real-time updates - WebSocket push, Observer pattern, no polling
- [x] Clean Client–Server architecture (Javalin ↔ JavaFX)
- [x] MVC on client side (FXML + ui/controller) and server side (Controller → Service → DAO)
- [x] Gradle build tool, Google Java Style, Conventional Commits
- [x] Unit Tests - JUnit 5 + Mockito, integration tests against real PostgreSQL
- [x] CI/CD - GitHub Actions: `spotlessCheck` → `clean test check jacocoTestReport`

### Advanced

- [x] **Auto-Bidding** - configurable `maxBid` + `increment`, `PriorityQueue` ordered by `registeredAt` (FIFO)
- [x] **Anti-sniping** - bid in final 30s → extend by 60s → broadcast `TIME_EXTENDED`
- [x] **Live Bid History Chart** - JavaFX `LineChart` updated in real time from WebSocket events, no manual refresh needed


# Class Diagram — Final

## 1. Domain Model

```mermaid
classDiagram
    direction LR

    class Entity {
        <<abstract>>
        -Long id
        -LocalDateTime createdAt
    }

    class User {
        <<abstract>>
        -String username
        -String email
        -BigDecimal balance
        +getRole() String
    }
    class Bidder {
        +getRole() String
    }
    class Seller {
        +getRole() String
    }
    class Admin {
        +getRole() String
    }

    class Item {
        <<abstract>>
        -String name
        -String description
        +getCategory() String
    }
    class Electronics {
        -String brand
        +getCategory() String
    }
    class Art {
        -String artist
        +getCategory() String
    }
    class Vehicle {
        -int year
        +getCategory() String
    }

    class AuctionStatus {
        <<enumeration>>
        OPEN
        RUNNING
        FINISHED
        PAID
        CANCELED
    }
    class DepositStatus {
        <<enumeration>>
        PENDING
        APPROVED
        REJECTED
    }
    class ResetStatus {
        <<enumeration>>
        PENDING
        USED
        EXPIRED
    }

    class Auction {
        -BigDecimal startingPrice
        -BigDecimal currentPrice
        -LocalDateTime startTime
        -LocalDateTime endTime
        -AuctionStatus status
    }
    class BidTransaction {
        -BigDecimal amount
        -boolean autoBid
        -LocalDateTime placedAt
    }
    class AutoBidConfig {
        -BigDecimal maxBid
        -BigDecimal increment
        -LocalDateTime registeredAt
    }
    class DepositRecord {
        -BigDecimal amount
        -DepositStatus status
    }
    class PasswordResetRecord {
        -String token
        -ResetStatus status
    }

    Entity <|-- User
    Entity <|-- Item
    Entity <|-- Auction
    Entity <|-- BidTransaction
    Entity <|-- AutoBidConfig
    Entity <|-- DepositRecord
    Entity <|-- PasswordResetRecord

    User <|-- Bidder
    User <|-- Seller
    User <|-- Admin

    Item <|-- Electronics
    Item <|-- Art
    Item <|-- Vehicle

    Auction ..> AuctionStatus : uses
    DepositRecord ..> DepositStatus : uses
    PasswordResetRecord ..> ResetStatus : uses

    Seller "1" --> "0..*" Item : owns
    Auction "1" --> "1" Item : features
    Auction "1" --> "0..*" BidTransaction : contains
    Bidder "1" --> "0..*" BidTransaction : places
    Bidder "1" --> "0..*" AutoBidConfig : configures
    Auction "1" --> "0..*" AutoBidConfig : has
    Bidder "1" --> "0..*" DepositRecord : requests
    User "1" --> "0..*" PasswordResetRecord : requests
```

---

## 2. Exception Hierarchy

```mermaid
classDiagram
    direction LR

    class RuntimeException

    class AuctionException {
        <<abstract>>
        +AuctionException(String message)
        +AuctionException(String message, Throwable cause)
    }

    class NotFoundException {
        <<abstract>>
        -String resourceType
        -Long resourceId
        +NotFoundException(String resourceType, Long resourceId)
    }
    class AuctionNotFoundException {
        +AuctionNotFoundException(Long auctionId)
    }
    class UserNotFoundException {
        +UserNotFoundException(Long userId)
    }
    class ItemNotFoundException {
        +ItemNotFoundException(Long itemId)
    }

    class AuctionClosedException {
        -Long auctionId
        -AuctionStatus currentStatus
        +AuctionClosedException(Long auctionId, AuctionStatus currentStatus)
    }
    class InvalidBidException {
        -BigDecimal attempted
        -BigDecimal minimum
        +InvalidBidException(BigDecimal attempted, BigDecimal minimum)
    }
    class InsufficientFundsException {
        -BigDecimal required
        -BigDecimal available
        +InsufficientFundsException(BigDecimal required, BigDecimal available)
    }
    class DuplicateException {
        -String resourceType
        -String conflictingValue
        +DuplicateException(String resourceType, String conflictingValue)
    }
    class UnauthorizedException {
        -String action
        +UnauthorizedException(String action)
    }

    RuntimeException <|-- AuctionException
    AuctionException <|-- NotFoundException
    AuctionException <|-- AuctionClosedException
    AuctionException <|-- InvalidBidException
    AuctionException <|-- InsufficientFundsException
    AuctionException <|-- DuplicateException
    AuctionException <|-- UnauthorizedException
    NotFoundException <|-- AuctionNotFoundException
    NotFoundException <|-- UserNotFoundException
    NotFoundException <|-- ItemNotFoundException
```

---

## 3. Design Patterns

```mermaid
classDiagram
    direction LR

    class AuctionState {
        <<interface>>
        +placeBid(Auction, Bidder, BigDecimal) void
        +close(Auction) void
        +edit(Auction) void
        +extend(Auction, LocalDateTime) void
    }
    class OpenState {
        +placeBid(Auction, Bidder, BigDecimal) void
        +close(Auction) void
        +edit(Auction) void
        +extend(Auction, LocalDateTime) void
    }
    class RunningState {
        +placeBid(Auction, Bidder, BigDecimal) void
        +close(Auction) void
        +edit(Auction) void
        +extend(Auction, LocalDateTime) void
    }
    class FinishedState {
        +placeBid(Auction, Bidder, BigDecimal) void
        +close(Auction) void
        +edit(Auction) void
        +extend(Auction, LocalDateTime) void
    }
    class PaidState {
        +placeBid(Auction, Bidder, BigDecimal) void
        +close(Auction) void
        +edit(Auction) void
        +extend(Auction, LocalDateTime) void
    }
    class CanceledState {
        +placeBid(Auction, Bidder, BigDecimal) void
        +close(Auction) void
        +edit(Auction) void
        +extend(Auction, LocalDateTime) void
    }
    class Auction {
        -AuctionState state
        +setState(AuctionState) void
        +placeBid(Bidder, BigDecimal) void
    }

    AuctionState <|.. OpenState
    AuctionState <|.. RunningState
    AuctionState <|.. FinishedState
    AuctionState <|.. PaidState
    AuctionState <|.. CanceledState
    Auction --> AuctionState : delegates to

    class BidStrategy {
        <<interface>>
        +execute(Auction, Bidder, BigDecimal, boolean) void
    }
    class ManualBidStrategy {
        +execute(Auction, Bidder, BigDecimal, boolean) void
    }
    class AutoBidStrategy {
        -PriorityQueue~AutoBidConfig~ queue
        +execute(Auction, Bidder, BigDecimal, boolean) void
    }
    class BidService {
        -BidStrategy strategy
        -AuctionEventManager eventManager
        +setStrategy(BidStrategy) void
        +bid(Auction, Bidder, BigDecimal, boolean) void
    }

    BidStrategy <|.. ManualBidStrategy
    BidStrategy <|.. AutoBidStrategy
    BidService --> BidStrategy : uses
    BidService --> AuctionEventManager : notifies

    class AuctionEventListener {
        <<interface>>
        +onBidUpdate(BidTransaction) void
        +onTimeExtended(LocalDateTime) void
        +onAuctionEnd(Auction) void
    }
    class AuctionEventManager {
        <<singleton>>
        -Map~Long, List~AuctionEventListener~~ listeners
        +getInstance() AuctionEventManager
        +subscribe(Long auctionId, AuctionEventListener) void
        +unsubscribe(Long auctionId, AuctionEventListener) void
        +notifyBidUpdate(Long auctionId, BidTransaction) void
        +notifyTimeExtended(Long auctionId, LocalDateTime) void
        +notifyAuctionEnd(Long auctionId, Auction) void
    }
    class WebSocketObserver {
        -WsContext session
        -AuctionEventManager eventManager
        +subscribe(Long auctionId) void
        +unsubscribe(Long auctionId) void
        +onBidUpdate(BidTransaction) void
        +onTimeExtended(LocalDateTime) void
        +onAuctionEnd(Auction) void
    }

    class CreateItemRequest {
        <<DTO>>
        +String name
        +String description
        +String category
        +Map~String, Object~ attributes
    }
    class ItemFactory {
        +create(CreateItemRequest, Seller) Item
    }

    AuctionEventListener <|.. WebSocketObserver
    AuctionEventManager o-- AuctionEventListener : notifies
    WebSocketObserver --> AuctionEventManager : subscribes to
    ItemFactory ..> CreateItemRequest : uses
    ItemFactory ..> Item : creates
```

---

## 4. UI Layer

```mermaid
classDiagram
    direction LR

    class Application
    class ClientApp {
        +start(Stage) void
        +main(String[]) void
    }
    Application <|-- ClientApp

    class Navigable {
        <<interface>>
        +onNavigatedTo(Object data) void
    }
    class SceneManager {
        <<singleton>>
        -Stage primaryStage
        +getInstance() SceneManager
        +navigateTo(String fxmlPath, Object data) void
    }

    class ApiClient {
        <<singleton>>
        -String baseUrl
        -String authToken
        +getInstance() ApiClient
        +get(String path) Response
        +post(String path, Object body) Response
        +put(String path, Object body) Response
        +delete(String path) Response
        +setAuthToken(String token) void
        +clearAuthToken() void
    }

    class WebSocketClient {
        -WsSession session
        -List~AuctionEventListener~ listeners
        +connect(Long auctionId) void
        +disconnect() void
        +addListener(AuctionEventListener) void
        +removeListener(AuctionEventListener) void
        +onMessage(String msg) void
    }

    class LoginController {
        -ApiClient apiClient
        +onNavigatedTo(Object data) void
        +handleLogin() void
    }
    class RegisterController {
        -ApiClient apiClient
        +onNavigatedTo(Object data) void
        +handleRegister() void
    }
    class AuctionListController {
        -ApiClient apiClient
        +onNavigatedTo(Object data) void
        +loadAuctions() void
        +handleSearch(String keyword) void
        +handleFilter(String category) void
    }
    class AuctionDetailController {
        -ApiClient apiClient
        -WebSocketClient wsClient
        +onNavigatedTo(Object data) void
        +handleBid() void
        +handleAutoBid() void
        +handleExtend() void
        +onBidUpdate(BidTransaction) void
        +onTimeExtended(LocalDateTime) void
        +onAuctionEnd(Auction) void
    }
    class CreateAuctionController {
        -ApiClient apiClient
        +onNavigatedTo(Object data) void
        +handleCreate() void
        +handleCancel() void
    }
    class CreateItemController {
        -ApiClient apiClient
        +onNavigatedTo(Object data) void
        +handleCreate() void
    }
    class AdminPanelController {
        -ApiClient apiClient
        +onNavigatedTo(Object data) void
        +loadUsers() void
        +approveDeposit() void
        +banUser() void
    }
    class ProfileController {
        -ApiClient apiClient
        +onNavigatedTo(Object data) void
        +loadProfile() void
        +handleEdit() void
    }
    class DepositController {
        -ApiClient apiClient
        +onNavigatedTo(Object data) void
        +handleDeposit() void
    }
    class ChangePasswordController {
        -ApiClient apiClient
        +onNavigatedTo(Object data) void
        +handleChange() void
    }
    class ForgotPasswordController {
        -ApiClient apiClient
        +onNavigatedTo(Object data) void
        +handleRequest() void
        +handleReset(String token) void
    }

    Navigable <|.. LoginController
    Navigable <|.. RegisterController
    Navigable <|.. AuctionListController
    Navigable <|.. AuctionDetailController
    Navigable <|.. CreateAuctionController
    Navigable <|.. CreateItemController
    Navigable <|.. AdminPanelController
    Navigable <|.. ProfileController
    Navigable <|.. DepositController
    Navigable <|.. ChangePasswordController
    Navigable <|.. ForgotPasswordController

    AuctionEventListener <|.. AuctionDetailController
    SceneManager --> Navigable : navigates to
    AuctionDetailController --> WebSocketClient : uses
    WebSocketClient o-- AuctionEventListener : notifies

    LoginController --> ApiClient : uses
    RegisterController --> ApiClient : uses
    AuctionListController --> ApiClient : uses
    AuctionDetailController --> ApiClient : uses
    CreateAuctionController --> ApiClient : uses
    CreateItemController --> ApiClient : uses
    AdminPanelController --> ApiClient : uses
    ProfileController --> ApiClient : uses
    DepositController --> ApiClient : uses
    ChangePasswordController --> ApiClient : uses
    ForgotPasswordController --> ApiClient : uses
```

## 🏗️ Architecture

```mermaid
graph TB
    subgraph CLIENT["🖥️  CLIENT - JavaFX"]
        FXML["View - FXML (12 screens)"]
        CTRL["ui/controller/"]
        RC["RestClient<br/>+ JWT header"]
        WSC["WebSocketClient<br/>+ Platform.runLater()"]
        UTIL["SceneManager · NotificationStore<br/>BackgroundBidWatcher · Navigable"]
        FXML <--> CTRL
        CTRL --- RC
        CTRL --- WSC
        CTRL --- UTIL
    end
    RC -->|"HTTP REST / JSON + Bearer token"| JWT
    WSC <-->|"WebSocket /ws/auction/{id}?token=..."| WSH
    subgraph SERVER["⚙️  SERVER - Javalin"]
        JWT["JWT Middleware<br/>verify every /api/* request"]
        REST["REST Controllers<br/>Auth · Auction · Bid · Item"]
        WSH["AuctionWebSocketHandler<br/>Observer Manager"]
        SVC["Service Layer<br/>BidService · AuctionService<br/>UserService · ItemService<br/>AuctionScheduler · PasswordResetService"]
        DAO["DAO Layer - JDBI 3<br/>7 DAOs · SELECT FOR UPDATE<br/>in AuctionDao"]
        JWT --> REST
        REST --> SVC
        WSH --> SVC
        SVC --> DAO
    end
    DAO -->|"SQL via HikariCP pool"| DB[("PostgreSQL<br/>Embedded<br/>Flyway migrations")]
```
---

## 🔄 Data Flow - End-to-End

*Scenario: Bidder places a bid of 500,000 VND from the JavaFX client*

```
1. AuctionDetailController (JavaFX)
   └─► POST /api/auctions/{id}/bid  { amount: 500000 }  + Authorization: Bearer <JWT>

2. JwtMiddleware
   └─► verifyToken() → extract { userId, username, role }
   └─► reject if role is not BIDDER for manual bids
   └─► BidController.placeBid(ctx)

3. BidService.placeBid()
   └─► validate: integer VND amount > currentPrice, sufficient available balance
   └─► jdbi.inTransaction(handle -> {
         auctionDao.findByIdForUpdate(handle, id)  ← SELECT FOR UPDATE (row lock)
         RunningState.placeBid()                   ← State: validate amount > currentPrice
         if (remaining < 30s) extend by 60s        ← Anti-sniping
         auctionDao.updateInTransaction(handle)    ← UPDATE price + endTime (atomic)
         bidTransactionDao.insert(handle, tx)      ← INSERT bid record
       })

4. AuctionEventManager
   └─► notifyTimeExtended()  (if anti-snipe triggered)
   └─► notifyBidUpdate()
   └─► WebSocketObserver → broadcast BidUpdateMessage (JSON) → all connected clients

5. All AuctionDetailControllers
   └─► Platform.runLater():
         update currentPrice label
         append point to LineChart
         reset countdown timer

6. BidService (after transaction)
   └─► autoBidStrategy.execute() → trigger auto-bid chain if any configs exist
```

---

## 🧠 Design Patterns

### 1. Observer - Real-time Push

```
AuctionEventManager (Subject)
  └─► Map<auctionId, List<AuctionEventListener>>

AuctionEventListener (Observer interface)
  ├── onBidUpdate(auctionId, price, bidder)
  ├── onTimeExtended(auctionId, newEndTime)
  └── onAuctionEnd(auctionId, winner, finalPrice)

WebSocketObserver (Concrete Observer)
  └─► BidUpdateMessage JSON → broadcast over WebSocket
```

**Trigger:** `BidService.placeBid()` succeeds → `eventManager.notify()` → all open `AuctionDetailController` instances update immediately.

### 2. Factory Method - Item Creation

```
ItemFactory.create(CreateItemRequest, sellerId)
  ├── "ELECTRONICS" → new Electronics(name, desc, brand)
  ├── "ART"         → new Art(name, desc, artist)
  └── "VEHICLE"     → new Vehicle(name, desc, year)
```

`ItemService` calls `ItemFactory` without needing to know which subclass is being instantiated.

### 3. Strategy - Bid Execution

```
BidStrategy (interface)
  └── execute(auction, bidderId, amount, isAutoBid)

ManualBidStrategy   → validate amount > currentPrice → update auction
AutoBidStrategy     → PriorityQueue<AutoBidConfig> sorted by registeredAt
                    → chain auto-bids until maxBid is exceeded
```

### 4. State - Auction Lifecycle

```
AuctionState (interface): placeBid(), close(), edit(), extend()

OpenState     → can edit, cannot bid
RunningState  → can bid + extend, cannot edit
FinishedState → throws on all operations
PaidState     → throws on all operations (terminal)
CanceledState → throws on all operations (terminal)
```

Transitions are driven by `AuctionScheduler`. Calling `placeBid()` on `FinishedState` throws `AuctionClosedException` → HTTP 409.

### 5. DAO - Database Isolation

Each table has exactly one dedicated DAO class using JDBI 3. `AuctionDao` is the only class that exposes `findByIdForUpdate()` - SQL uses `SELECT ... FOR UPDATE` to guarantee row-level locking for concurrent bids.

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
│   ├── Electronics         ← getCategory() = "ELECTRONICS" · + brand: String
│   ├── Art                 ← getCategory() = "ART"         · + artist: String
│   └── Vehicle             ← getCategory() = "VEHICLE"     · + year: int
│
├── Auction                 ← startingPrice / currentPrice: BigDecimal (not double)
│                              status: OPEN / RUNNING / FINISHED / PAID / CANCELED
│
├── BidTransaction          ← auctionId, bidderId, amount, autoBid: boolean
└── AutoBidConfig           ← maxBid, increment, registeredAt (PriorityQueue sort key)
```

`BigDecimal` is used consistently for all monetary values - no `double` or `float` anywhere.

---

## 📡 API Reference

### REST Endpoints

| Method | Path | Auth | Role | Description |
|---|---|---|---|---|
| `POST` | `/api/auth/register` | Public | - | Register (BIDDER / SELLER) |
| `POST` | `/api/auth/login` | Public | - | Login → JWT token |
| `PUT` | `/api/users/me/password` | Required | Any | Change password |
| `POST` | `/api/auth/forgot-password` | Public | - | Request password reset |
| `GET` | `/api/items` | Optional | Any | List all items |
| `POST` | `/api/items` | Required | SELLER | Create item |
| `GET` | `/api/auctions` | Optional | Any | List auctions (`?status=`) |
| `GET` | `/api/auctions/{id}` | Optional | Any | Auction detail (enriched) |
| `POST` | `/api/auctions` | Required | SELLER | Create auction |
| `PUT` | `/api/auctions/{id}` | Required | SELLER | Edit auction (OPEN state only) |
| `DELETE` | `/api/auctions/{id}` | Required | SELLER/ADMIN | Cancel auction |
| `POST` | `/api/auctions/{id}/bid` | Required | BIDDER only | Place manual bid |
| `POST` | `/api/auctions/{id}/auto-bid` | Required | BIDDER | Register auto-bid |
| `POST` | `/api/users/me/deposit` | Required | BIDDER | Submit deposit request |
| Admin endpoints | `/api/admin/*` | Required | ADMIN | Manage users, deposits, password resets |

All errors return `ErrorResponse { error: String, message: String }` with the corresponding HTTP status (400 / 401 / 404 / 409).

<details>
<summary><b>Key Request / Response Examples</b></summary>

<br>

<details>
<summary><code>POST /api/auth/login</code></summary>

**Request**
```json
{
  "username": "admin",
  "password": "123456"
}
```

**Response `200 OK`**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "admin",
  "role": "ADMIN"
}
```
</details>

<details>
<summary><code>POST /api/auctions/{id}/bid</code></summary>

**Request** *(Authorization: Bearer &lt;token&gt;)*
```json
{
  "amount": 500000
}
```

**Response `201 Created`**
```json
{
  "auctionId": 3,
  "bidderId": 7,
  "amount": 500000,
  "autoBid": false
}
```

**Error `400 Bad Request`** *(bid too low or invalid amount)*
```json
{
  "error": "BID_TOO_LOW",
  "message": "Bid amount must be higher than current price 450000"
}
```
</details>

<details>
<summary><code>POST /api/auctions/{id}/auto-bid</code></summary>

**Request** *(Authorization: Bearer &lt;token&gt;)*
```json
{
  "maxBid": 2000000,
  "increment": 50000
}
```

**Response `201 Created`**
```json
{
  "auctionId": 3,
  "bidderId": 7,
  "maxBid": 2000000,
  "increment": 50000,
  "status": "ACTIVE"
}
```
</details>

</details>

### WebSocket Protocol

```
Endpoint: /ws/auction/{auctionId}?token=<JWT>
```

| Direction | `type` | Payload |
|---|---|---|
| Server → Client | `BID_UPDATE` | `{ currentPrice, leadingBidderUsername, timestamp }` |
| Server → Client | `TIME_EXTENDED` | `{ newEndTime }` |
| Server → Client | `AUCTION_ENDED` | `{ winner, finalPrice }` |
| Server → Client | `AUTO_BID_TRIGGERED` | `{ bidderId, amount }` |

---

## Business Rules Snapshot

- Manual bid endpoint `POST /api/auctions/{id}/bid` is **BIDDER-only**. Sellers and admins are rejected by the HTTP authorization layer.
- Sellers create items and auctions, and can only edit/cancel their own auctions according to auction state rules. Admins use admin-only routes for moderation.
- Money inputs use integer VND at service/API boundaries. Negative, zero where positive is required, and decimal amounts such as `1000.50` are rejected.
- Auto-bid is BIDDER-only, scoped to the authenticated bidder, and cannot be stopped for another bidder. Auto-bid uses `maxBid` and `increment` with integer VND validation.
- Wallet balance changes are recorded in `wallet_transactions` for deposit approval, bid freeze/release, cancellation release, winner consume, and seller payout.
- PostgreSQL schema is owned by Flyway migrations under `src/main/resources/db/migration`; CI and local test bootstrap run the full migration set.

---

## 🤔 Technical Decisions

**Javalin over Spring Boot** - Spring Boot adds startup time, annotation-based DI, and layers of abstraction that make the execution flow harder to trace. Javalin lets you write `app.post("/path", handler)` explicitly; the resulting JAR is also ~50 MB lighter.

**Embedded PostgreSQL over H2** - H2 does not support `SELECT FOR UPDATE` the same way PostgreSQL does. Since concurrent bidding is a core requirement, integration tests need to run against the real engine to be meaningful.

**JDBI 3 over Hibernate/JPA** - ORM hides the SQL, making concurrency bugs harder to debug. With JDBI every query is explicit - you can see exactly the order of locks, updates, and inserts within a transaction.

**`SELECT FOR UPDATE` instead of `synchronized`** - `synchronized` only protects within a single JVM instance. `SELECT FOR UPDATE` operates at the database level - the entire validate → update → insert sequence runs inside a single `jdbi.inTransaction()` call, guaranteeing true atomicity.

**Admin-reviewed password reset** - SMTP setup requires external credentials and environment configuration that complicates the evaluator experience. Admin-reviewed reset achieves the same security goal (a trusted party authorises the reset) without any external dependency.

**`BigDecimal` for monetary values** - `double` introduces floating-point errors (`0.1 + 0.2 ≠ 0.3`). Every bid amount, account balance, and starting price uses `BigDecimal`, stored as `NUMERIC` in PostgreSQL.

**`PriorityQueue` ordered by `registeredAt` for auto-bid** - When multiple bidders register auto-bid configs, the one who registered first gets priority. Sorting by `registeredAt: LocalDateTime` gives a deterministic and fair ordering.

---

## ⚠️ Known Limitations

- **Single-server deployment** - `SELECT FOR UPDATE` at the DB layer protects correctness even with multiple server nodes. However, some in-memory state (such as the `AuctionEventManager` listener map) is per-instance - horizontal scaling would require a message broker (e.g. Redis Pub/Sub).

- **No payment gateway** - The `PAID` status exists in the state machine, but actual payment is mocked (balance is debited directly). A production system would need a payment provider integration.

- **Embedded PostgreSQL data directory** - An unclean shutdown may leave `data/postgres/` in a state that requires manual deletion (see Troubleshooting). Production deployments should use a managed PostgreSQL instance.

- **Basic WebSocket reconnection** - `WebSocketClient` retries on disconnection but does not implement exponential backoff. On an unstable network, a missed `TIME_EXTENDED` event could cause the client countdown to drift.

- **Password reset resets to `"123456"`** - Acceptable for an academic context; a production system would require a one-time token sent out-of-band.

---

## 🚀 Getting Started

### Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| Java (JDK) | 21+ | [Download Adoptium](https://adoptium.net/) |
| OS | Windows 10+ / macOS 12+ / Ubuntu 20.04+ | JavaFX requires a display environment |
| RAM | 512 MB minimum | Embedded PostgreSQL + JavaFX |
| Display | 1280×720 minimum | Required for JavaFX rendering |

PostgreSQL is **embedded** for local runs - no installation needed. The server starts an embedded PostgreSQL instance in `data/postgres` and applies all Flyway migrations automatically. CI uses an empty PostgreSQL service database and lets the same Flyway migration path prepare the schema.

```bash
java -version
# Expected: openjdk version "21.x.x"
```

### ⬇️ Download Prebuilt JARs *(recommended for evaluators)*

| File | Size | Download |
|---|---|---|
| Server | ~101 MB | [**auction-server-1.0.0.jar**](https://github.com/kieran-labs/oop-course-project-uet/releases/download/v1.0.0/auction-server-1.0.0.jar) |
| Client | ~101 MB | [**auction-client-1.0.0.jar**](https://github.com/kieran-labs/oop-course-project-uet/releases/download/v1.0.0/auction-client-1.0.0.jar) |

### ▶️ Running the Application

#### Configuration

`JWT_SECRET` is required before starting the server. It must be unique per environment
and at least 32 bytes when encoded as UTF-8. The server fails fast if it is missing,
blank, or too short.

```bash
export JWT_SECRET="replace-with-a-random-secret-of-at-least-32-bytes"
```

```powershell
$env:JWT_SECRET = "replace-with-a-random-secret-of-at-least-32-bytes"
```

**Step 1 - Start the server** (wait for `Javalin started in X ms` before proceeding)

```bash
java -jar auction-server-1.0.0.jar
```

From source, the equivalent Gradle command is:

```bash
./gradlew run          # macOS / Linux
gradlew.bat run        # Windows
```

On Windows, `server-start.bat`, `server-stop.bat`, and `server-status.bat` wrap the same server workflow.

**Step 2 - Start one or more clients** (each terminal = independent client)

```bash
java -jar auction-client-1.0.0.jar
```

From source:

```bash
./gradlew runClient          # macOS / Linux
gradlew.bat runClient        # Windows
```

To simulate concurrent bidding, open multiple terminals and run the client command in each.

### 🔑 Default Accounts

The following accounts are seeded automatically on first run via `V2__seed_admin.sql`:

| Role | Username | Password |
|---|---|---|
| Admin | `admin` | `123456` |

Additional Seller and Bidder accounts can be registered from the login screen.

### Build from Source

```bash
git clone https://github.com/kieran-labs/oop-course-project-uet.git
cd oop-course-project-uet

./gradlew buildJars          # macOS / Linux
gradlew.bat buildJars        # Windows
```

Output JARs will be placed in `build/libs/`.

### Gradle Commands

| Command | Description |
|---|---|
| `./gradlew buildJars` | Build server + client fat JARs |
| `./gradlew run` | Start the Javalin server from source |
| `./gradlew runClient` | Start the JavaFX client from source |
| `./gradlew spotlessCheck` | Check Google Java Style formatting, as CI does before tests |
| `./gradlew test` | Run the JUnit test suite |
| `./gradlew check` | Run quality gates, including JaCoCo instruction coverage verification at 20% minimum |
| `./gradlew jacocoTestReport` | Coverage report → `build/reports/jacoco/` |
| `./gradlew spotlessApply` | Auto-format all Java code |
| `./gradlew spotbugsMain` | Static analysis |
| `./gradlew clean` | Clean build artifacts |

CI runs `./gradlew spotlessCheck` first, then `./gradlew clean test check jacocoTestReport --info --stacktrace`.

### Troubleshooting

**`initdb: directory "data/postgres" exists but is not empty`**

```bash
rm -rf data/postgres          # Linux / macOS
rmdir /s /q data\postgres     # Windows
```

This occurs when the server is killed uncleanly. Delete the directory and restart.

**JavaFX window does not appear on Linux**

Ensure a display server is running. On headless servers, use `export DISPLAY=:0` or run via a desktop session.

---

## 👥 Team

| Member | GitHub | Role | Technical Contributions |
|---|---|---|---|
| **Bui Ngoc Phu Hung** | [@HumaNormal](https://github.com/HumaNormal) | Backend Lead | Javalin server setup · REST controllers · WebSocket handler (`AuctionWebSocketHandler`) · JDBI DAOs · Flyway migrations · HikariCP connection pool config |
| **Tran Anh Duc** | [@kieran-lucas](https://github.com/kieran-lucas) | Frontend Lead | 12 JavaFX screen controllers · 12 FXML layout files · `SceneManager` singleton · fade transition system · blue CSS theme (`#1565C0`) · Lexend font integration |
| **Nguyen Dinh Viet Duc** | [@Black1206-coder](https://github.com/Black1206-coder) | Business Logic | Service-layer classes · 4 design pattern packages (13 files) · `AuctionException` hierarchy (5 custom types) · JWT authentication · BCrypt password hashing |
| **Bui Quang Huy** | [@stillqhuy](https://github.com/stillqhuy) | DevOps & QA | GitHub Actions CI pipeline · JUnit 5 regression suite · Gradle Kotlin DSL config · Checkstyle + Spotless + SpotBugs integration · Git workflow & documentation |

All members jointly own `model/` (14 domain classes), `dto/` (13 transfer objects), and project documentation.

---

<details>
<summary><b>🛠️ Tech Stack & Why We Chose It</b></summary>

<br>

| Layer | Technology | Version | Why |
|---|---|---|---|
| Language | Java | 21 (LTS) | Records, sealed classes, pattern matching |
| GUI | JavaFX + FXML | 21 | Native desktop UI; FXML separates View from Controller |
| HTTP + WebSocket | Javalin | 6.4.0 | Lightweight (~50 MB JAR); explicit routing, no DI overhead |
| Database | PostgreSQL (Embedded) | 16 | True `SELECT FOR UPDATE` support; H2 does not handle it correctly |
| Connection Pool | HikariCP | 6.2.1 | Lowest-latency JDBC pool |
| SQL Mapper | JDBI 3 | 3.45.4 | SQL-first - every query is explicit and easy to debug under concurrency |
| JSON | Jackson + JSR310 | 2.18.2 | De-facto standard; JSR310 handles `LocalDateTime` natively |
| Auth | JWT (Auth0) | 4.4.0 | HMAC JWT with per-user tokenVersion check after password changes |
| Password | BCrypt | 0.10.2 | One-way hash with salt, cost factor 12 |
| Testing | JUnit 5 + Mockito | 5.11.4 | Parameterized tests + mock injection |
| Coverage | JaCoCo | - | GitHub Actions artifact + 20% minimum instruction coverage gate in `check` |
| Build | Gradle (Kotlin DSL) | 8.12.1 | Type-safe build scripts |
| Code Style | Checkstyle + Spotless | - | Google Java Style, enforced in CI + pre-commit hook |
| Static Analysis | SpotBugs | 6.0.9 | MAX effort - null dereferences, race conditions |
| CI/CD | GitHub Actions | - | `spotlessCheck` + Gradle verification pipeline |

</details>

<details>
<summary><b>📁 Project Structure</b></summary>

<br>

```
auction-system/
│
├── .githooks/
│   └── pre-commit                              ← Runs spotlessApply automatically before every commit
│                                                  Enforces Google Java Style without manual formatting
│
├── .github/
│   └── workflows/
│       └── ci.yml                              ← GitHub Actions pipeline (trigger: push + PR → main)
│                                                  Steps: spotlessCheck → clean test check jacocoTestReport
│                                                  check runs Checkstyle, SpotBugs, and JaCoCo coverage verification
│                                                  Spins up an empty PostgreSQL 16 test database
│                                                  Flyway applies all migrations during test bootstrap
│                                                  Uploads coverage artifact on completion
│
├── assets/
│   ├── app-screenshot.png                      ← Full app overview screenshot (README only)
│   ├── grading-rubric.png                      ← Grading rubric image (README only)
│   └── screenshots/
│       ├── admin.png                           ← AdminPanel: user management, deposit approval, password reset review
│       ├── auction-detail.png                  ← Auction detail: WebSocket realtime feed + LineChart + countdown timer
│       ├── auction-list.png                    ← Auction list with status filter (?status=)
│       └── login.png                           ← Login screen (JWT-based auth entry point)
│
├── config/
│   └── checkstyle/
│       └── checkstyle.xml                      ← Google Java Style ruleset enforced via Checkstyle
│                                                  Runs as gradle checkstyleMain in CI pipeline
│
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar                  ← Committed intentionally for reproducible builds
│       └── gradle-wrapper.properties           ← Pins Gradle version across all environments
│
├── src/
│   ├── main/
│   │   ├── java/com/auction/
│   │   │   ├── App.java                        ← Server entry point: Javalin setup, route registration,
│   │   │   │                                      global exception handlers (AuctionException hierarchy)
│   │   │   ├── ClientApp.java                  ← Client entry point: JavaFX Application, loads welcome.fxml
│   │   │   ├── Launcher.java                   ← Fat JAR wrapper - bypasses JavaFX module path restriction
│   │   │   │                                      Required when packaging with shadow/fat JAR
│   │   │   │
│   │   │   ├── config/
│   │   │   │   ├── DatabaseConfig.java         ← HikariCP connection pool + JDBI instance setup
│   │   │   │   │                                  Reads DB credentials from environment/config
│   │   │   │   └── JwtUtil.java                ← JWT generation & verification (com.auth0:java-jwt)
│   │   │   │                                      Token payload: { userId, username, role, expiry }
│   │   │   │
│   │   │   ├── controller/
│   │   │   │   ├── AuctionController.java      ← REST: GET /api/auctions, GET /api/auctions/{id},
│   │   │   │   │                                  POST /api/auctions, PUT /api/auctions/{id},
│   │   │   │   │                                  DELETE /api/auctions/{id}
│   │   │   │   ├── AuctionWebSocketHandler.java← WebSocket endpoint: /ws/auction/{id}?token=<JWT>
│   │   │   │   │                                  Registers WebSocketObserver into AuctionEventManager
│   │   │   │   │                                  Pushes: BID_UPDATE · TIME_EXTENDED · AUCTION_ENDED · AUTO_BID_TRIGGERED
│   │   │   │   ├── AuthController.java         ← REST: POST /api/auth/register · /login (public, no JWT)
│   │   │   │   │                                  POST /api/auth/forgot-password (public password reset request)
│   │   │   │   ├── BidController.java          ← REST: POST /api/auctions/{id}/bid (manual, role: BIDDER)
│   │   │   │   │                                  POST /api/auctions/{id}/auto-bid (register auto-bid config)
│   │   │   │   └── ItemController.java         ← REST: GET /api/items · POST /api/items (role: SELLER)
│   │   │   │
│   │   │   ├── dao/                            ← DAO pattern: one class per table, all use JDBI
│   │   │   │   ├── AuctionDao.java             ← ★ Includes SELECT ... FOR UPDATE for DB-level concurrency lock
│   │   │   │   │                                  Maps to: auctions table (status: OPEN/RUNNING/FINISHED/PAID/CANCELED)
│   │   │   │   ├── AutoBidConfigDao.java       ← Maps to: auto_bid_configs (maxBid, increment, registeredAt)
│   │   │   │   │                                  registeredAt used as PriorityQueue sort key
│   │   │   │   ├── BidTransactionDao.java      ← Maps to: bid_transactions (amount, autoBid flag, timestamp)
│   │   │   │   ├── DepositRequestDao.java      ← Maps to: deposit_requests (status: PENDING/APPROVED/REJECTED)
│   │   │   │   ├── ItemDao.java                ← Maps to: items (category-aware: ELECTRONICS/ART/VEHICLE)
│   │   │   │   ├── PasswordResetRequestDao.java← Maps to: password_reset_requests (Admin-reviewed workflow)
│   │   │   │   └── UserDao.java                ← Maps to: users (passwordHash via BCrypt, balance as BigDecimal)
│   │   │   │
│   │   │   ├── dto/                            ← 13 request/response objects - decouples API contract from domain model
│   │   │   │   ├── AuctionResponse.java        ← Enriched auction view (includes item info + leading bidder username)
│   │   │   │   ├── AutoBidRequest.java         ← { maxBid, incrementAmount } for auto-bid registration
│   │   │   │   ├── BidRequest.java             ← { amount } for manual bid placement
│   │   │   │   ├── BidUpdateMessage.java       ← WebSocket push payload: { currentPrice, leadingBidderUsername, timestamp }
│   │   │   │   ├── ChangePasswordRequest.java  ← { oldPassword, newPassword }
│   │   │   │   ├── CreateAuctionRequest.java   ← { itemId, startingPrice, startTime, endTime }
│   │   │   │   ├── CreateItemRequest.java      ← { name, description, category, brand/artist/year }
│   │   │   │   ├── DepositRequest.java         ← { amount } - creates a PENDING DepositRecord
│   │   │   │   ├── ErrorResponse.java          ← Standardized error envelope: { status, message }
│   │   │   │   ├── ForgotPasswordRequest.java  ← Triggers Admin-reviewed PasswordResetRecord (PENDING)
│   │   │   │   ├── LoginRequest.java           ← { username, password } → returns JWT token on success
│   │   │   │   ├── RegisterRequest.java        ← { username, email, password, role: BIDDER|SELLER }
│   │   │   │   └── UserResponse.java           ← Safe user view (passwordHash never exposed)
│   │   │   │
│   │   │   ├── exception/                      ← Custom exception hierarchy rooted at AuctionException (abstract)
│   │   │   │   │                                  App.java maps each subclass → HTTP status code
│   │   │   │   ├── AuctionClosedException.java ← Thrown by FinishedState/CanceledState when bid attempted
│   │   │   │   ├── AuctionException.java       ← Abstract base - all domain exceptions extend this
│   │   │   │   ├── DuplicateException.java     ← 409 Conflict (e.g. duplicate username/email on register)
│   │   │   │   ├── InvalidBidException.java    ← 400 Bad Request (bid amount ≤ current price)
│   │   │   │   ├── NotFoundException.java      ← 404 Not Found (auction/item/user missing from DB)
│   │   │   │   ├── package-info.java           ← Package-level Javadoc descriptor
│   │   │   │   └── UnauthorizedException.java  ← 401/403 (JWT invalid or insufficient role)
│   │   │   │
│   │   │   ├── middleware/
│   │   │   │   └── JwtMiddleware.java          ← Javalin before-handler applied to all /api/* routes
│   │   │   │                                      Extracts + verifies JWT from Authorization: Bearer <token>
│   │   │   │                                      Injects { userId, username, role } into request context
│   │   │   │
│   │   │   ├── model/                          ← 15 domain classes - pure data, no framework coupling
│   │   │   │   ├── Admin.java                  ← User subclass · getRole() = "ADMIN"
│   │   │   │   ├── Art.java                    ← Item subclass · getCategory() = "ART" · + artist: String
│   │   │   │   ├── Auction.java                ← Core aggregate: price (BigDecimal), status, startTime/endTime
│   │   │   │   ├── AutoBidConfig.java          ← { maxBid, incrementAmount, registeredAt } - PriorityQueue sort key
│   │   │   │   ├── Bidder.java                 ← User subclass · getRole() = "BIDDER" · holds balance
│   │   │   │   ├── BidTransaction.java         ← Immutable record: { auctionId, bidderId, amount, autoBid }
│   │   │   │   ├── DepositRecord.java          ← { userId, amount, status, reviewedAt }
│   │   │   │   ├── Electronics.java            ← Item subclass · getCategory() = "ELECTRONICS" · + brand: String
│   │   │   │   ├── Entity.java                 ← Abstract root: { id: Long, createdAt: LocalDateTime }
│   │   │   │   ├── Item.java                   ← Abstract: { name, description, sellerId } - 3 concrete subclasses
│   │   │   │   ├── PasswordResetRecord.java    ← { userId, status, reviewedAt } - Admin-reviewed reset flow
│   │   │   │   ├── Seller.java                 ← User subclass · getRole() = "SELLER"
│   │   │   │   ├── User.java                   ← Abstract: { username, passwordHash, email, balance: BigDecimal }
│   │   │   │   └── Vehicle.java                ← Item subclass · getCategory() = "VEHICLE" · + year: int
│   │   │   │
│   │   │   ├── pattern/
│   │   │   │   ├── factory/
│   │   │   │   │   └── ItemFactory.java        ← Factory Method pattern
│   │   │   │   │                                  create(CreateItemRequest, sellerId) switches on category string:
│   │   │   │   │                                  "ELECTRONICS" → new Electronics()
│   │   │   │   │                                  "ART"         → new Art()
│   │   │   │   │                                  "VEHICLE"     → new Vehicle()
│   │   │   │   │                                  ItemService calls this - never references subclasses directly
│   │   │   │   │
│   │   │   │   ├── observer/
│   │   │   │   │   ├── AuctionEventListener.java  ← Observer interface: onBidUpdate · onTimeExtended · onAuctionEnd
│   │   │   │   │   ├── AuctionEventManager.java   ← Subject (server-side): maintains listener registry per auction
│   │   │   │   │   │                                 BidService calls notify(BID_UPDATE) after successful placeBid()
│   │   │   │   │   └── WebSocketObserver.java     ← Concrete Observer: serializes BidUpdateMessage → JSON,
│   │   │   │   │                                     pushes to all connected WebSocket clients for that auction
│   │   │   │   │
│   │   │   │   ├── state/
│   │   │   │   │   ├── AuctionState.java       ← State interface: placeBid() · close() · edit() · extend()
│   │   │   │   │   ├── CanceledState.java      ← Terminal state - throws on all operations
│   │   │   │   │   ├── FinishedState.java      ← Terminal state - throws on all operations
│   │   │   │   │   ├── OpenState.java          ← Allows edit; rejects bids (auction not yet started)
│   │   │   │   │   ├── PaidState.java          ← Terminal state - throws on all operations
│   │   │   │   │   └── RunningState.java       ← Allows bid + time extension (anti-sniping); rejects edit
│   │   │   │   │
│   │   │   │   └── strategy/
│   │   │   │       ├── AutoBidStrategy.java    ← Iterates PriorityQueue of AutoBidConfigs (sorted by registeredAt)
│   │   │   │       │                              Chains auto-bids until all participants' maxBids are exceeded
│   │   │   │       ├── BidStrategy.java        ← Strategy interface: execute(auction, bidderId, amount, isAutoBid)
│   │   │   │       └── ManualBidStrategy.java  ← Validates amount > currentPrice, updates auction, persists transaction
│   │   │   │
│   │   │   ├── service/
│   │   │   │   ├── AuctionScheduler.java       ← ScheduledExecutorService: polls DB to auto-transition states
│   │   │   │   │                                  OPEN → RUNNING at startTime · RUNNING → FINISHED at endTime
│   │   │   │   ├── AuctionService.java         ← CRUD orchestration for auctions (create, edit, delete, list, get)
│   │   │   │   ├── BidService.java             ← ★ Core bidding engine - two-layer concurrency protection:
│   │   │   │   │                                  Layer 1 (app):  synchronized(auction) { validate → update → save → notify }
│   │   │   │   │                                  Layer 2 (DB):   jdbi.inTransaction() + SELECT ... FOR UPDATE
│   │   │   │   │                                  Anti-sniping:   if remaining < 30s → extend endTime +60s + notifyTimeExtended
│   │   │   │   │                                  Post-bid:       triggers AutoBidStrategy chain via AuctionEventManager
│   │   │   │   ├── ItemService.java            ← Delegates item creation to ItemFactory; persists via ItemDao
│   │   │   │   ├── PasswordResetService.java   ← Creates PasswordResetRecord(PENDING)
│   │   │   │   │                                  Admin approves → password reset to "123456" (user must change after)
│   │   │   │   └── UserService.java            ← Registration (BCrypt hash), login (BCrypt verify), balance management
│   │   │   │
│   │   │   ├── ui/
│   │   │   │   ├── controller/                 ← 12 JavaFX controllers (MVC: C layer - each paired with an FXML view)
│   │   │   │   │   ├── AdminPanelController.java      ← Manages users, approves/rejects deposits & password resets
│   │   │   │   │   ├── AuctionDetailController.java   ← ★ Connects to WebSocket, renders JavaFX LineChart (bid history),
│   │   │   │   │   │                                     runs countdown timer, handles manual & auto-bid forms
│   │   │   │   │   ├── AuctionListController.java     ← Fetches auction list via REST; supports status filter
│   │   │   │   │   ├── ChangePasswordController.java  ← PUT /api/users/me/password flow
│   │   │   │   │   ├── CreateAuctionController.java   ← POST /api/auctions (role: SELLER)
│   │   │   │   │   ├── CreateItemController.java      ← POST /api/items (role: SELLER); form adapts to category
│   │   │   │   │   ├── DepositController.java         ← POST /api/users/me/deposit - submits PENDING deposit request
│   │   │   │   │   ├── ForgotPasswordController.java  ← Submits forgot-password request into Admin review queue
│   │   │   │   │   ├── LoginController.java           ← POST /api/auth/login → stores JWT in memory for session
│   │   │   │   │   ├── ProfileController.java         ← Displays user info + current balance
│   │   │   │   │   ├── RegisterController.java        ← POST /api/auth/register (role: BIDDER | SELLER)
│   │   │   │   │   └── WelcomeController.java         ← App landing screen; routes to login or register
│   │   │   │   │
│   │   │   │   └── util/
│   │   │   │       ├── Navigable.java          ← Interface: allows controllers to receive data on navigation
│   │   │   │       └── SceneManager.java       ← Singleton: manages all JavaFX scene/stage transitions
│   │   │   │                                      Central router - all screen switches go through here
│   │   │   │
│   │   │   └── util/                           ← 5 client-side utility classes
│   │   │       ├── BackgroundBidWatcher.java   ← Background thread: polls for bid updates when WS is inactive
│   │   │       ├── NotificationStore.java      ← In-memory store for push notifications received via WebSocket
│   │   │       ├── RestClient.java             ← HTTP client wrapper: auto-injects Authorization: Bearer <JWT>
│   │   │       ├── UserBalanceWatcher.java     ← Background thread: polls /api/users/me to keep balance fresh
│   │   │       └── WebSocketClient.java        ← Manages WS lifecycle; wraps UI updates in Platform.runLater()
│   │   │
│   │   └── resources/
│   │       ├── logback.xml                     ← SLF4J/Logback configuration (log levels + appenders)
│   │       │
│   │       ├── css/
│   │       │   └── style.css                   ← Global JavaFX stylesheet - blue theme: #1565C0 · #EFF6FF
│   │       │
│   │       ├── db/
│   │       │   └── migration/                  ← Versioned SQL migrations (Flyway-style, applied in order)
│   │       │       ├── V1__initial_schema.sql  ← Creates core tables: users, items, auctions, bids, auto-bid configs
│   │       │       ├── V2__seed_admin.sql      ← Seeds default admin account
│   │       │       ├── V3..V16                 ← Balance, deposits, notifications, item/auction status, token version
│   │       │       └── V17__wallet_transactions.sql ← Wallet ledger for balance/reserved-balance movements
│   │       │
│   │       ├── fonts/                          ← Lexend typeface bundled at 9 weights (Black → Thin)
│   │       │   ├── Lexend-Black.ttf
│   │       │   ├── Lexend-Bold.ttf
│   │       │   ├── Lexend-ExtraBold.ttf
│   │       │   ├── Lexend-ExtraLight.ttf
│   │       │   ├── Lexend-Light.ttf
│   │       │   ├── Lexend-Medium.ttf
│   │       │   ├── Lexend-Regular.ttf
│   │       │   ├── Lexend-SemiBold.ttf
│   │       │   └── Lexend-Thin.ttf
│   │       │
│   │       ├── icons/                          ← 6 PNG icons used across JavaFX screens
│   │       │   ├── auction.png
│   │       │   ├── auctionpic.png
│   │       │   ├── businessman.png
│   │       │   ├── computer.png
│   │       │   ├── seller.png
│   │       │   └── settings.png
│   │       │
│   │       └── ui/
│   │           └── fxml/                       ← 12 FXML screens (MVC: V layer - designed in SceneBuilder)
│   │               ├── admin-panel.fxml        ← Bound to AdminPanelController
│   │               ├── auction-detail.fxml     ← Bound to AuctionDetailController (LineChart + WebSocket display)
│   │               ├── auction-list.fxml       ← Bound to AuctionListController
│   │               ├── change-password.fxml    ← Bound to ChangePasswordController
│   │               ├── create-auction.fxml     ← Bound to CreateAuctionController
│   │               ├── create-item.fxml        ← Bound to CreateItemController (dynamic category fields)
│   │               ├── deposit.fxml            ← Bound to DepositController
│   │               ├── forgot-password.fxml    ← Bound to ForgotPasswordController
│   │               ├── login.fxml              ← Bound to LoginController
│   │               ├── profile.fxml            ← Bound to ProfileController
│   │               ├── register.fxml           ← Bound to RegisterController
│   │               └── welcome.fxml            ← Bound to WelcomeController (app entry screen)
│
└── test/
    └── java/com/auction/
        ├── SetupTest.java                      ← Bootstraps embedded PostgreSQL + JDBI for all integration tests
        │
        ├── config/
        │   ├── DatabaseConfigTest.java         ← Verifies HikariCP pool connects and JDBI handle is functional
        │   └── JwtUtilTest.java                ← Tests token generation, verification, expiry, and invalid token rejection
        │
        ├── dao/
        │   ├── AuctionDaoTest.java             ← Tests CRUD + SELECT FOR UPDATE locking behavior
        │   ├── AutoBidConfigDaoTest.java       ← Tests config persistence + PriorityQueue ordering by registeredAt
        │   ├── BidTransactionDaoTest.java      ← Tests bid record insertion and retrieval by auction
        │   ├── ItemDaoTest.java                ← Tests category-specific field persistence (brand/artist/year)
        │   └── UserDaoTest.java                ← Tests registration, BCrypt hash storage, balance updates
        │
        ├── exception/
        │   ├── AuctionClosedExceptionTest.java ← Verifies message content + HTTP status mapping
        │   ├── DuplicateExceptionTest.java     ← Verifies 409 mapping
        │   ├── InvalidBidExceptionTest.java    ← Verifies 400 mapping
        │   ├── NotFoundExceptionTest.java      ← Verifies 404 mapping
        │   └── UnauthorizedExceptionTest.java  ← Verifies 401/403 mapping
        │
        ├── model/
        │   └── ModelTest.java                  ← Tests inheritance chain: Entity → User/Item + subclasses
        │                                          Verifies getRole() / getCategory() polymorphic dispatch
        │
        ├── service/
        │   ├── AuctionServiceTest.java         ← Tests create/edit/delete + State pattern transition guards
        │   ├── BidServiceTest.java             ← ★ Tests concurrent bidding, anti-sniping trigger (30s threshold),
        │   │                                      auto-bid chain execution, synchronized + FOR UPDATE interaction
        │   ├── ItemFactoryTest.java            ← Tests Factory Method: correct subclass returned per category string
        │   └── UserServiceTest.java            ← Tests registration, BCrypt verification, balance mutation
        │
        └── util/                               ← (empty - reserved for future client-side utility tests)
```

</details>

<details>
<summary><b>📊 Rubric Coverage</b> - for evaluators</summary>

<br>

*Direct mapping from rubric criteria to source files.*

| Rubric item | Points | File / Folder |
|---|---|---|
| Class design, inheritance hierarchy | 0.5 | `model/Entity` → `User` → `Bidder/Seller/Admin` · `Item` → `Electronics/Art/Vehicle` |
| OOP principles | 1.0 | Encapsulation (private + getter/setter) · Inheritance · Polymorphism (`getRole()`, `getCategory()`) · Abstraction (abstract `User`, `Item`) |
| Design patterns | 1.0 | `pattern/observer/` · `pattern/factory/` · `pattern/strategy/` · `pattern/state/` · `dao/` |
| User & item management | 1.0 | `AuthController` · `ItemController` · `AuctionController` + 12 JavaFX screens |
| Auction functionality | 1.0 | `BidService.placeBid()` · `BidController` · `ManualBidStrategy` · `RunningState` |
| Error handling & exceptions | 1.0 | `exception/AuctionException` (abstract) + 5 custom + handlers in `App.java` |
| Concurrent bidding | 1.0 | `BidService` → `jdbi.inTransaction()` → `AuctionDao.findByIdForUpdate()` (`SELECT FOR UPDATE`) |
| Real-time updates | 0.5 | `AuctionEventManager` · `WebSocketObserver` · `AuctionWebSocketHandler` |
| Client-Server | 0.5 | `App.java` (Javalin) ↔ `ClientApp.java` (JavaFX) via HTTP + WebSocket |
| MVC | 0.5 | `ui/fxml/` (View) + `ui/controller/` (Controller) + `model/` + `dto/` (Model) |
| Build + conventions | 0.5 | `build.gradle.kts` · `checkstyle.xml` · `.editorconfig` · Spotless |
| Unit Tests | 0.5 | `test/` - JUnit 5 + Mockito, integration tests against real PostgreSQL |
| CI/CD | 0.5 | `.github/workflows/ci.yml` - `spotlessCheck` + Gradle verification pipeline |
| Auto-bidding | 0.5 | `AutoBidStrategy` · `AutoBidConfig` · `AutoBidConfigDao` |
| Anti-sniping | 0.5 | `BidService.placeBid()` - `ANTI_SNIPE_THRESHOLD_MS = 30_000` · `EXTENSION = 60s` |
| Bid History Chart | 0.5 | `AuctionDetailController` + `auction-detail.fxml` (JavaFX `LineChart`) |
| **Total** | **11.0** | |

</details>

---

## 📄 Reports

| Resource | Link |
|---|---|
| 📹 Demo Video | [Watch on YouTube / Drive](#) |
| 📄 PDF Report | [View Report](#) |
| 📦 GitHub Releases | [v1.0.0 - Prebuilt JARs](https://github.com/kieran-labs/oop-course-project-uet/releases/tag/v1.0.0) |
| 📊 CI Pipeline | [GitHub Actions](https://github.com/kieran-labs/oop-course-project-uet/actions) |
| 📈 Coverage Report | Available as artifact in each CI run |

---

## 📜 License

Distributed under the MIT License. See [`LICENSE`](LICENSE) for details.

---

<div align="center">
<sub>Built for Advanced Programming (LTNC) - University of Engineering and Technology, VNU Hanoi</sub>
</div>
