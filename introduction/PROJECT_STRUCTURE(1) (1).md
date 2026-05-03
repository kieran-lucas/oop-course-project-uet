# Cấu trúc dự án đầy đủ — Online Auction System

## Bản đồ toàn bộ repo

```
auction-system/
│
│  ══════════════════════════════════════════════════
│  TẦNG 1: CẤU HÌNH GITHUB
│  ══════════════════════════════════════════════════
│
├── .github/
│   ├── workflows/
│   │   └── ci.yml                          ← CI/CD pipeline
│   └── pull_request_template.md            ← Form khi tạo PR
│
│  ══════════════════════════════════════════════════
│  TẦNG 2: CẤU HÌNH PROJECT
│  ══════════════════════════════════════════════════
│
├── .gitignore                              ← File Git bỏ qua
├── .editorconfig                           ← Thống nhất IDE settings
├── README.md                               ← Tài liệu dự án
├── build.gradle.kts                        ← ★ Cấu hình Gradle (dependencies, plugins)
├── settings.gradle.kts                     ← Tên project
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.properties       ← Version Gradle (8.12)
│       └── gradle-wrapper.jar              ← Auto-generated
├── gradlew                                 ← Launcher Linux/Mac (auto-generated)
├── gradlew.bat                             ← Launcher Windows (auto-generated)
├── config/
│   └── checkstyle/
│       └── checkstyle.xml                  ← Quy tắc Google Java Style
│
│  ══════════════════════════════════════════════════
│  TẦNG 3: SOURCE CODE
│  ══════════════════════════════════════════════════
│
├── src/
│   ├── main/
│   │   ├── java/com/auction/
│   │   │   │
│   │   │   │  ── ENTRY POINTS ──
│   │   │   ├── App.java                    ← Server: Javalin start, đăng ký routes
│   │   │   ├── ClientApp.java              ← Client: JavaFX Application launch
│   │   │   │
│   │   │   │  ── MODEL (M trong MVC) ──
│   │   │   ├── model/
│   │   │   │   ├── Entity.java             ← Abstract base: id, createdAt
│   │   │   │   ├── User.java               ← Abstract: username, email, getRole()
│   │   │   │   ├── Bidder.java             ← getRole() → "BIDDER"
│   │   │   │   ├── Seller.java             ← getRole() → "SELLER"
│   │   │   │   ├── Admin.java              ← getRole() → "ADMIN"
│   │   │   │   ├── Item.java               ← Abstract: name, description, getCategory()
│   │   │   │   ├── Electronics.java        ← getCategory() → "ELECTRONICS", + brand
│   │   │   │   ├── Art.java                ← getCategory() → "ART", + artist
│   │   │   │   ├── Vehicle.java            ← getCategory() → "VEHICLE", + year
│   │   │   │   ├── Auction.java            ← ★ Trung tâm: giá, thời gian, trạng thái
│   │   │   │   ├── BidTransaction.java     ← Lịch sử mỗi lần bid
│   │   │   │   └── AutoBidConfig.java      ← Cấu hình auto-bid: maxBid, increment
│   │   │   │
│   │   │   │  ── DTO (JSON ↔ Java) ──
│   │   │   ├── dto/
│   │   │   │   ├── LoginRequest.java       ← {username, password}
│   │   │   │   ├── RegisterRequest.java    ← {username, password, email, role}
│   │   │   │   ├── BidRequest.java         ← {auctionId, amount}
│   │   │   │   ├── AutoBidRequest.java     ← {auctionId, maxBid, increment}
│   │   │   │   ├── CreateItemRequest.java  ← {name, description, category, ...}
│   │   │   │   ├── CreateAuctionRequest.java ← {itemId, startingPrice, startTime, endTime}
│   │   │   │   ├── AuctionResponse.java    ← Trả về cho client (không có passwordHash)
│   │   │   │   ├── BidUpdateMessage.java   ← WebSocket push: {type, auctionId, price, ...}
│   │   │   │   └── ErrorResponse.java      ← {error, message}
│   │   │   │
│   │   │   │  ── CONTROLLER (C trong MVC) ──
│   │   │   ├── controller/
│   │   │   │   ├── AuthController.java     ← POST /api/auth/login, /register
│   │   │   │   ├── AuctionController.java  ← GET/POST/PUT/DELETE /api/auctions
│   │   │   │   ├── BidController.java      ← POST /api/auctions/{id}/bid
│   │   │   │   ├── ItemController.java     ← GET/POST /api/items
│   │   │   │   └── AuctionWebSocketHandler.java ← WS /ws/auction/{id}
│   │   │   │
│   │   │   │  ── SERVICE (Business Logic) ──
│   │   │   ├── service/
│   │   │   │   ├── UserService.java        ← Đăng ký, đăng nhập, BCrypt hash
│   │   │   │   ├── AuctionService.java     ← Tạo/sửa/xóa phiên, chuyển trạng thái
│   │   │   │   ├── BidService.java         ← ★ placeBid (synchronized), anti-sniping
│   │   │   │   ├── ItemService.java        ← Tạo item qua Factory
│   │   │   │   └── AuctionScheduler.java   ← Timer tự đóng phiên khi hết giờ
│   │   │   │
│   │   │   │  ── DAO (Database Access) ──
│   │   │   ├── dao/
│   │   │   │   ├── UserDao.java            ← SQL: users table
│   │   │   │   ├── AuctionDao.java         ← SQL: auctions table + FOR UPDATE
│   │   │   │   ├── BidTransactionDao.java  ← SQL: bid_transactions table
│   │   │   │   ├── ItemDao.java            ← SQL: items table
│   │   │   │   └── AutoBidConfigDao.java   ← SQL: auto_bid_configs table
│   │   │   │
│   │   │   │  ── DESIGN PATTERNS ──
│   │   │   ├── pattern/
│   │   │   │   ├── observer/
│   │   │   │   │   ├── AuctionEventListener.java   ← Interface: onBidUpdate, onAuctionEnd
│   │   │   │   │   ├── WebSocketObserver.java       ← Gửi JSON qua WS connection
│   │   │   │   │   └── AuctionEventManager.java     ← Quản lý list observers/auction
│   │   │   │   │
│   │   │   │   ├── factory/
│   │   │   │   │   └── ItemFactory.java             ← create(type) → đúng subclass
│   │   │   │   │
│   │   │   │   ├── strategy/
│   │   │   │   │   ├── BidStrategy.java             ← Interface: execute(auction, user)
│   │   │   │   │   ├── ManualBidStrategy.java       ← Validate + update price
│   │   │   │   │   └── AutoBidStrategy.java         ← PriorityQueue auto-bid
│   │   │   │   │
│   │   │   │   └── state/
│   │   │   │       ├── AuctionState.java            ← Interface: placeBid, close
│   │   │   │       ├── OpenState.java               ← Cho sửa, chưa cho bid
│   │   │   │       ├── RunningState.java            ← Cho bid, không cho sửa
│   │   │   │       ├── FinishedState.java           ← Throw nếu cố bid
│   │   │   │       ├── PaidState.java               ← Trạng thái cuối
│   │   │   │       └── CanceledState.java           ← Trạng thái cuối
│   │   │   │
│   │   │   │  ── EXCEPTIONS ──
│   │   │   ├── exception/
│   │   │   │   ├── InvalidBidException.java         ← Giá thấp hơn hiện tại
│   │   │   │   ├── AuctionClosedException.java      ← Phiên đã đóng
│   │   │   │   ├── UnauthorizedException.java       ← Chưa đăng nhập / sai role
│   │   │   │   ├── NotFoundException.java           ← Auction/User/Item không tồn tại
│   │   │   │   └── DuplicateException.java          ← Username/email đã tồn tại
│   │   │   │
│   │   │   │  ── CONFIGURATION ──
│   │   │   └── config/
│   │   │       ├── DatabaseConfig.java              ← HikariCP + JDBI setup
│   │   │       └── JwtUtil.java                     ← Tạo/verify JWT token (Auth0 java-jwt)
│   │   │
│   │   │  ══════════════════════════════════════════
│   │   │  TẦNG 4: RESOURCES (non-Java files)
│   │   │  ══════════════════════════════════════════
│   │   │
│   │   └── resources/
│   │       ├── logback.xml                          ← Logging config cho Javalin
│   │       ├── fxml/                                ← JavaFX giao diện (V trong MVC client)
│   │       │   ├── login.fxml
│   │       │   ├── register.fxml
│   │       │   ├── auction-list.fxml
│   │       │   ├── auction-detail.fxml              ← ★ Màn hình đấu giá realtime
│   │       │   ├── create-item.fxml                 ← Seller tạo sản phẩm
│   │       │   ├── create-auction.fxml              ← Seller tạo phiên
│   │       │   └── admin-panel.fxml                 ← Admin quản lý
│   │       └── db/
│   │           └── migration/
│   │               └── V1__initial_schema.sql       ← Tạo 5 bảng PostgreSQL
│   │
│   │  ══════════════════════════════════════════════
│   │  TẦNG 5: TESTS
│   │  ══════════════════════════════════════════════
│   │
│   └── test/java/com/auction/
│       ├── SetupTest.java                           ← Verify JUnit 5 + Java 21 OK
│       ├── model/
│       │   └── ModelTest.java                       ← Test OOP: inheritance, polymorphism
│       ├── service/
│       │   ├── BidServiceTest.java                  ← ★ Test bid logic + anti-sniping
│       │   ├── AuctionServiceTest.java              ← Test state transitions
│       │   └── ConcurrencyTest.java                 ← ★ Test race conditions (nhiều thread)
│       └── dao/
│           ├── AuctionDaoTest.java                  ← Integration test với PostgreSQL
│           └── UserDaoTest.java                     ← Integration test với PostgreSQL
│
│  ══════════════════════════════════════════════════
│  KHÔNG COMMIT (nằm trong .gitignore)
│  ══════════════════════════════════════════════════
│
├── build/                                  ← Output biên dịch (Gradle tạo)
├── .gradle/                                ← Gradle cache
├── .idea/                                  ← IntelliJ config
└── .env                                    ← DB_URL, DB_USER, DB_PASSWORD (secrets)
```

---

## Tổng kết số lượng

| Loại | Số file | Ghi chú |
|------|---------|---------|
| Config (gốc) | 8 | .gitignore, .editorconfig, build.gradle.kts, settings.gradle.kts, README.md, checkstyle.xml, gradle-wrapper.properties, logback.xml |
| GitHub | 2 | ci.yml, pull_request_template.md |
| Model | 12 | Entity, User×3, Item×3, Auction, BidTransaction, AutoBidConfig |
| DTO | 9 | Request/Response objects cho REST + WebSocket |
| Controller | 5 | Auth, Auction, Bid, Item, WebSocket |
| Service | 5 | User, Auction, Bid, Item, Scheduler |
| DAO | 5 | User, Auction, BidTransaction, Item, AutoBidConfig |
| Pattern | 13 | Observer(3), Factory(1), Strategy(3), State(6) |
| Exception | 5 | InvalidBid, AuctionClosed, Unauthorized, NotFound, Duplicate |
| Config class | 2 | DatabaseConfig, JwtUtil |
| FXML | 7 | login, register, auction-list, auction-detail, create-item, create-auction, admin-panel |
| SQL | 1 | V1__initial_schema.sql |
| Test | 6 | Setup, Model, BidService, AuctionService, Concurrency, DAO×2 |
| **Tổng** | **~80 files** | |

---

## Data flow — khi user bid 500k, data đi qua đâu?

```
1. Client: user nhấn "Đặt giá 500,000đ"
   → auction-detail.fxml (View) gọi controller
   → Controller tạo BidRequest{auctionId=5, amount=500000}
   → Gửi POST /api/auctions/5/bid (JSON) qua REST
   → Header: Authorization: Bearer <JWT token>

2. Server nhận:
   → Javalin middleware: JwtUtil.verifyToken(token) → biết userId, role
   → BidController.placeBid(ctx)
   → ctx.bodyAsClass(BidRequest.class)      ← Jackson parse JSON → DTO
   → bidService.placeBid(request, userId)   ← Gọi service

3. Service xử lý:
   → synchronized(auction) {                ← Tầng 1 concurrency
   →   state.placeBid(auction, bid)         ← State pattern: RunningState cho phép
   →   auction.setCurrentPrice(500000)
   →   auction.setLeadingBidderId(userId)
   →   if (remaining < 30s) endTime += 60s  ← Anti-sniping
   →   auctionDao.update(auction)           ← Gọi DAO
   →   bidTransactionDao.insert(txn)        ← Ghi lịch sử
   →   eventManager.notify(BID_UPDATE)      ← Observer push
   → }

4. DAO ghi database:
   → BEGIN                                  ← Tầng 2 concurrency
   → SELECT ... FOR UPDATE
   → UPDATE auctions SET current_price=500000
   → INSERT INTO bid_transactions (...)
   → COMMIT

5. Observer push:
   → Duyệt list WebSocketObserver
   → Mỗi observer gửi BidUpdateMessage qua WebSocket
   → Tất cả client đang xem phiên 5 nhận message

6. Client nhận WebSocket message:
   → Parse BidUpdateMessage
   → Cập nhật giá hiển thị trên auction-detail.fxml
   → Thêm data point vào LineChart (bid history)
   → Nếu có TIME_EXTENDED → cập nhật countdown
```

---

## Mapping rubric → file/folder

| Mục rubric | Điểm | File/folder liên quan |
|------------|-------|----------------------|
| Cây kế thừa | 0.5 | model/ (Entity→User→Bidder/Seller/Admin, Entity→Item→...) |
| OOP principles | 1.0 | model/ (encapsulation, inheritance, polymorphism, abstraction) |
| Design patterns | 1.0 | pattern/ (observer, factory, strategy, state) + dao/ |
| Quản lý user, sản phẩm | 1.0 | controller/ + service/ + dao/ + fxml/ |
| Chức năng đấu giá | 1.0 | BidService + BidController + strategy/ + state/ |
| Xử lý lỗi | 1.0 | exception/ + Javalin exception handler trong App.java |
| Concurrent bidding | 1.0 | BidService (synchronized) + AuctionDao (FOR UPDATE) |
| Realtime update | 0.5 | pattern/observer/ + AuctionWebSocketHandler |
| Client-Server | 0.5 | App.java (Javalin) ↔ ClientApp.java (JavaFX) |
| MVC | 0.5 | fxml/(View) + controller/(C) + model/(M) + dao/(DAO) |
| Build + convention | 0.5 | build.gradle.kts + checkstyle.xml + .editorconfig |
| Unit test | 0.5 | test/ (JUnit 5 + Mockito + @Nested) |
| CI/CD | 0.5 | .github/workflows/ci.yml |
| Auto-bidding | 0.5 | pattern/strategy/AutoBidStrategy + AutoBidConfig |
| Anti-sniping | 0.5 | BidService.placeBid() (10 dòng) |
| Bid history chart | 0.5 | auction-detail.fxml (JavaFX LineChart) |
| **Tổng** | **11.0** | |
