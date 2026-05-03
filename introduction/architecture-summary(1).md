# Kiến trúc hệ thống đấu giá trực tuyến — Bản chốt

## Tổng quan stack

| Mảng | Lựa chọn |
|------|----------|
| GUI | JavaFX + FXML |
| Networking | REST API + WebSocket |
| HTTP Framework | Javalin |
| Database | PostgreSQL |
| Database Access | JDBI + HikariCP |
| JSON Serialization | Jackson |
| Build Tool | Gradle (Kotlin DSL) |
| Testing | JUnit 5 |
| CI/CD | GitHub Actions (mức cao: test + quality + PostgreSQL service) |
| Coding Convention | Checkstyle + Spotless + EditorConfig (Google Java Style) |
| Git Workflow | GitHub Flow + Conventional Commits + branch protection |
| Design Patterns | Observer, Factory Method, Strategy, State, DAO |
| Chức năng nâng cao | Anti-sniping, Auto-bidding, Bid History Chart |
| Authentication | JWT (com.auth0:java-jwt) |
| Password Storage | BCrypt |
| Transport | HTTP (dev) / HTTPS-ready |

---

## Kiến trúc tổng thể

```
┌─────────────────────────────────────────────────┐
│                CLIENT (JavaFX)                  │
│                                                 │
│  View (.fxml)  ←→  Controller  ←→  Model (DTO)  │
│                                                 │
│  ┌──────────────┐    ┌───────────────────┐      │
│  │ REST Client  │    │ WebSocket Client  │      │
│  │ (HTTP calls) │    │ (Event listener)  │      │
│  │ + JWT header │    │ + JWT auth        │      │
│  └──────┬───────┘    └────────┬──────────┘      │
└─────────┼─────────────────────┼─────────────────┘
          │ HTTP                │ WS
          ▼                    ▼
┌─────────────────────────────────────────────────┐
│                SERVER (Javalin)                 │
│                                                 │
│  ┌──────────────────────────────────────┐       │
│  │  JWT Middleware (verify mỗi request) │       │
│  └──────────────┬───────────────────────┘       │
│  ┌──────────────┐    ┌───────────────────┐      │
│  │ REST Routes  │    │ WebSocket Handler │      │
│  │ (Controller) │    │ (Observer Mgr)    │      │
│  └──────┬───────┘    └────────┬──────────┘      │
│         │                     │                 │
│         ▼                     ▼                 │
│  ┌────────────────────────────────────┐         │
│  │         Service Layer              │         │
│  │  (Business logic + synchronized)   │         │
│  └──────────────┬─────────────────────┘         │
│                 │                               │
│  ┌──────────────▼─────────────────────┐         │
│  │          DAO Layer (JDBI)          │         │
│  └──────────────┬─────────────────────┘         │
└─────────────────┼───────────────────────────────┘
                  │ SQL
                  ▼
          ┌───────────────┐
          │  PostgreSQL   │
          │  (HikariCP)   │
          └───────────────┘
```

---

## Phân chia REST vs WebSocket

### REST (hỏi-đáp, xong ngắt)
- POST /api/auth/login → trả JWT token
- POST /api/auth/register
- GET /api/auctions (cần JWT)
- GET /api/auctions/{id} (cần JWT)
- POST /api/auctions (seller tạo phiên, cần JWT + role SELLER)
- PUT /api/auctions/{id} (seller sửa, cần JWT + role SELLER)
- DELETE /api/auctions/{id} (cần JWT + role SELLER/ADMIN)
- POST /api/auctions/{id}/bid (cần JWT + role BIDDER)
- POST /api/auctions/{id}/auto-bid (cần JWT + role BIDDER)
- GET /api/items (cần JWT)
- POST /api/items (cần JWT + role SELLER)

### WebSocket (kết nối mở, push liên tục)
- /ws/auction/{id}?token=<JWT> ← verify khi connect
  - Server → Client: BID_UPDATE (giá mới, người dẫn đầu)
  - Server → Client: TIME_EXTENDED (anti-sniping gia hạn)
  - Server → Client: AUCTION_ENDED (phiên kết thúc, người thắng)
  - Server → Client: AUTO_BID_TRIGGERED (auto-bid kích hoạt)

---

## Cây kế thừa class

```
Entity (abstract)
├── id: Long
├── createdAt: LocalDateTime
│
├── User (abstract)
│   ├── username: String
│   ├── passwordHash: String
│   ├── email: String
│   │
│   ├── Bidder
│   │   └── getRole() → "BIDDER"
│   ├── Seller
│   │   └── getRole() → "SELLER"
│   └── Admin
│       └── getRole() → "ADMIN"
│
├── Item (abstract)
│   ├── name: String
│   ├── description: String
│   ├── sellerId: Long
│   │
│   ├── Electronics
│   │   ├── brand: String
│   │   └── getCategory() → "ELECTRONICS"
│   ├── Art
│   │   ├── artist: String
│   │   └── getCategory() → "ART"
│   └── Vehicle
│       ├── year: int
│       └── getCategory() → "VEHICLE"
│
├── Auction
│   ├── itemId: Long
│   ├── startingPrice: BigDecimal      ← dùng BigDecimal, không dùng double
│   ├── currentPrice: BigDecimal       ← tránh lỗi floating point cho tiền tệ
│   ├── startTime: LocalDateTime
│   ├── endTime: LocalDateTime
│   ├── leadingBidderId: Long
│   └── status: String                ← OPEN/RUNNING/FINISHED/PAID/CANCELED
│
├── BidTransaction
│   ├── auctionId: Long
│   ├── bidderId: Long
│   ├── amount: BigDecimal
│   ├── autoBid: boolean
│   └── createdAt: LocalDateTime       ← kế thừa từ Entity
│
└── AutoBidConfig
    ├── auctionId: Long
    ├── bidderId: Long
    ├── maxBid: BigDecimal
    ├── increment: BigDecimal
    ├── active: boolean
    └── registeredAt: LocalDateTime    ← dùng cho PriorityQueue sort
```

---

## Design Patterns — áp dụng cụ thể

### 1. Observer → Realtime update
- Subject: AuctionEventManager (server)
- Observer: mỗi WebSocket connection (WebSocketObserver)
- Khi placeBid() thành công → eventManager.notify(BID_UPDATE)

### 2. Factory Method → Tạo Item
- ItemFactory.create("electronics", data) → new Electronics(...)
- Dùng khi server nhận JSON từ client, quyết định tạo subclass nào

### 3. Strategy → Loại bid
- BidStrategy interface: execute(auction, user)
- ManualBidStrategy: validate + update price
- AutoBidStrategy: duyệt PriorityQueue, bid tự động

### 4. State → Trạng thái phiên
- AuctionState interface: placeBid(), close(), extend()
- OpenState: cho sửa thông tin, chưa cho bid
- RunningState: cho bid, không cho sửa
- FinishedState: throw exception nếu cố bid
- PaidState / CanceledState: trạng thái cuối

### 5. DAO → Tách database access
- AuctionDao: SQL queries cho bảng auctions
- UserDao: SQL queries cho bảng users
- BidTransactionDao: SQL queries cho bảng bid_transactions
- ItemDao: SQL queries cho bảng items
- AutoBidConfigDao: SQL queries cho bảng auto_bid_configs

---

## Security — 3 lớp bảo mật

### Lớp 1: Password Storage (BCrypt)
- Password hash một chiều trước khi lưu database
- Thư viện: at.favre.lib:bcrypt
- Liên kết: UserService.register() và login()

### Lớp 2: Authentication (JWT)
- Login → server tạo token chứa {userId, role, expiration}
- Mỗi request gửi kèm header: Authorization: Bearer <token>
- Server verify chữ ký → biết user → không tra database
- Thư viện: com.auth0:java-jwt
- Liên kết: config/JwtUtil.java, AuthController, Javalin middleware

### Lớp 3: Transport (HTTP / HTTPS-ready)
- Dev: HTTP (localhost, đủ an toàn)
- Production-ready: Javalin hỗ trợ HTTPS bằng 3 dòng config

---

## Concurrency — hai tầng bảo vệ

### Tầng 1: Application (Java)
```java
synchronized (auction) {
    // validate bid
    // update current price
    // save to database
    // notify observers
}
```

### Tầng 2: Database (PostgreSQL)
```sql
BEGIN;
SELECT * FROM auctions WHERE id = ? FOR UPDATE;
UPDATE auctions SET current_price = ?, leading_bidder_id = ? WHERE id = ?;
INSERT INTO bid_transactions (...) VALUES (...);
COMMIT;
```

---

## Chức năng nâng cao

### Anti-sniping (~10 dòng)
Trong placeBid(): nếu remaining < 30s → endTime += 60s → broadcast TIME_EXTENDED

### Auto-bidding (~50-80 dòng)
AutoBidConfig(userId, maxBid, increment, registeredAt)
PriorityQueue sắp theo registeredAt
Khi có bid mới → duyệt queue → ai đủ budget → tự động bid

### Bid History Chart (~30-40 dòng client)
JavaFX LineChart
Mỗi BID_UPDATE từ WebSocket → thêm data point (timestamp, price)
Platform.runLater() để update UI thread

---

## CI/CD Pipeline (GitHub Actions)

```yaml
Trigger: push + pull_request
│
├── Setup Java 21 (Temurin)
├── Start PostgreSQL 16 service container
├── Run: gradle spotlessCheck (format check)
├── Run: gradle checkstyleMain (convention check)
├── Run: gradle test (unit + integration tests)
├── Run: gradle jacocoTestReport (coverage)
└── Upload coverage report as artifact
```

---

## Rubric mapping

| Mục | Điểm | Đạt bằng cách |
|-----|-------|---------------|
| Thiết kế lớp, cây kế thừa | 0.5 | Entity→User→Bidder/Seller/Admin, Entity→Item→Electronics/Art/Vehicle |
| OOP principles | 1.0 | Encapsulation (private+getter/setter), Inheritance (cây trên), Polymorphism (getCategory(), getRole()), Abstraction (Entity, User, Item abstract) |
| Design patterns | 1.0 | Observer + Factory + Strategy + State + DAO |
| Quản lý user, sản phẩm | 1.0 | REST CRUD + JavaFX GUI |
| Chức năng đấu giá | 1.0 | placeBid + validation + State pattern |
| Xử lý lỗi & ngoại lệ | 1.0 | Custom exceptions + Javalin exception handler |
| Concurrent bidding | 1.0 | synchronized (tầng app) + PostgreSQL FOR UPDATE (tầng DB) |
| Realtime update | 0.5 | WebSocket + Observer pattern |
| Client-Server | 0.5 | Javalin server + JavaFX client |
| MVC | 0.5 | Client: FXML(View) + Controller + DTO(Model). Server: Controller + Service + DAO |
| Build tool + convention | 0.5 | Gradle + Checkstyle + Spotless + EditorConfig |
| Unit Test | 0.5 | JUnit 5 + Nested + DisplayName + assertThrows |
| CI/CD | 0.5 | GitHub Actions + PostgreSQL service + Checkstyle + JaCoCo |
| Auto-bidding | 0.5 | Strategy pattern + PriorityQueue |
| Anti-sniping | 0.5 | 10 dòng trong placeBid() |
| Bid History Chart | 0.5 | JavaFX LineChart + WebSocket data |
| **Tổng** | **11.0** | |
