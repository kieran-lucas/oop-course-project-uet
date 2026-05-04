# Phân công file/folder theo Role — Online Auction System

> Dựa trên `PROJECT_STRUCTURE.md` (cấu trúc chính thức) và `TIEN_DO_8_TUAN_V2.md`
> **Nhóm:** 4 thành viên · **~80 files**

---

## 👤 A — Backend Lead

**Đặc trưng:** Toàn bộ routing HTTP, SQL thực tế, kết nối DB. Mọi request đều đi qua code của A trước.

```
App.java                                    ← entry point server, đăng ký routes + exception handlers

controller/
├── AuthController.java                     ← POST /api/auth/login, /register
├── AuctionController.java                  ← GET/POST/PUT/DELETE /api/auctions
├── BidController.java                      ← POST /api/auctions/{id}/bid
├── ItemController.java                     ← GET/POST /api/items
└── AuctionWebSocketHandler.java            ← WS /ws/auction/{id}

dao/
├── UserDao.java                            ← SQL: users table
├── AuctionDao.java                         ← SQL: auctions + SELECT FOR UPDATE
├── BidTransactionDao.java                  ← SQL: bid_transactions table
├── ItemDao.java                            ← SQL: items table
└── AutoBidConfigDao.java                   ← SQL: auto_bid_configs table

config/
└── DatabaseConfig.java                     ← HikariCP + JDBI setup

resources/db/migration/
└── V1__initial_schema.sql                  ← tạo 5 bảng PostgreSQL
```

**Tổng: ~12 files**

---

## 👤 B — Frontend Lead

**Đặc trưng:** Người duy nhất dùng SceneBuilder. Toàn bộ thứ người dùng nhìn thấy là do B tạo ra.

```
ClientApp.java                              ← entry point JavaFX, load scene đầu tiên

resources/fxml/
├── login.fxml                              ← màn hình đăng nhập
├── register.fxml                           ← màn hình đăng ký
├── auction-list.fxml                       ← danh sách phiên đấu giá (TableView + filter)
├── auction-detail.fxml                     ← ★ realtime bid + chart + countdown
├── create-item.fxml                        ← seller tạo sản phẩm (dropdown category)
├── create-auction.fxml                     ← seller tạo phiên (giá, thời gian)
└── admin-panel.fxml                        ← admin quản lý users + auctions
```

> ⚠️ **Lưu ý — Cần bổ sung vào PROJECT_STRUCTURE:**
> Theo `TIEN_DO_8_TUAN_V2`, B còn tạo thêm các file sau nhưng **chưa có trong structure chính thức**.
> Cả nhóm cần thống nhất đặt vào đâu trước khi B bắt đầu code:
>
> ```
> util/
> ├── RestClient.java          ← HTTP client wrapper (java.net.http + auto attach JWT header)
> ├── WebSocketClient.java     ← WS client + Platform.runLater() khi nhận message
> └── SceneManager.java        ← điều hướng màn hình + fade transition
>
> controller/client/           ← hoặc gộp vào controller/ nếu muốn giữ flat structure
> ├── LoginController.java
> ├── RegisterController.java
> ├── AuctionListController.java
> ├── AuctionDetailController.java   ← ★ phức tạp nhất: WS + chart + countdown + auto-bid UI
> └── AdminPanelController.java
> ```

**Tổng hiện tại: ~8 files** | Sau khi bổ sung: **~16 files**

---

## 👤 C — Business Logic

**Đặc trưng:** "Bộ não" của hệ thống — không có SQL, không có UI, chỉ có logic thuần. Mọi quy tắc nghiệp vụ đều nằm trong code của C.

```
service/
├── UserService.java                        ← BCrypt hash, validate, register, login
├── AuctionService.java                     ← tạo/sửa/xóa phiên, chuyển trạng thái qua State
├── BidService.java                         ← ★ placeBid + synchronized + anti-sniping + trigger auto-bid
├── ItemService.java                        ← tạo item qua Factory, check ownership
└── AuctionScheduler.java                   ← timer tự OPEN→RUNNING→FINISHED

pattern/
├── observer/
│   ├── AuctionEventListener.java           ← interface: onBidUpdate, onTimeExtended, onAuctionEnd
│   ├── WebSocketObserver.java              ← gửi BidUpdateMessage JSON qua WS connection
│   └── AuctionEventManager.java            ← Map<auctionId, List<listener>>, notify()
│
├── factory/
│   └── ItemFactory.java                    ← create("electronics", data) → new Electronics(...)
│
├── strategy/
│   ├── BidStrategy.java                    ← interface: execute(auction, bidderId, amount)
│   ├── ManualBidStrategy.java              ← validate amount > currentPrice, update auction
│   └── AutoBidStrategy.java                ← PriorityQueue sort by registeredAt, chain auto-bids
│
└── state/
    ├── AuctionState.java                   ← interface: placeBid(), close(), edit(), extend()
    ├── OpenState.java                      ← cho sửa, chưa cho bid (throw AuctionClosedException)
    ├── RunningState.java                   ← cho bid + extend, không cho sửa
    ├── FinishedState.java                  ← throw nếu cố bid
    ├── PaidState.java                      ← trạng thái cuối, throw tất cả
    └── CanceledState.java                  ← trạng thái cuối, throw tất cả

exception/
├── InvalidBidException.java                ← giá thấp hơn hiện tại → HTTP 400
├── AuctionClosedException.java             ← phiên đã đóng / chưa mở → HTTP 400
├── UnauthorizedException.java              ← chưa đăng nhập / sai role → HTTP 401
├── NotFoundException.java                  ← auction/user/item không tồn tại → HTTP 404
└── DuplicateException.java                 ← username/email đã tồn tại → HTTP 409

config/
└── JwtUtil.java                            ← createToken(userId, role) / verifyToken(token)
```

**Tổng: ~24 files**

---

## 👤 D — DevOps & QA

**Đặc trưng:** Không viết feature, nhưng kiểm soát toàn bộ chất lượng code của 3 người còn lại. Nếu D làm tốt, CI luôn xanh và bugs được phát hiện sớm.

```
.github/
├── workflows/
│   └── ci.yml                              ← pipeline: spotlessCheck → checkstyle → test → jacocoReport
└── pull_request_template.md                ← form checklist khi tạo PR

[root project]
├── build.gradle.kts                        ← ★ toàn bộ dependencies + plugins (Javalin, JDBI, JavaFX, JWT...)
├── settings.gradle.kts                     ← tên project: "auction-system"
├── .gitignore                              ← bỏ qua build/, .gradle/, .idea/, .env
├── .editorconfig                           ← thống nhất indent, charset, line ending
├── gradlew / gradlew.bat                   ← Gradle wrapper launcher
└── gradle/wrapper/
    └── gradle-wrapper.properties           ← Gradle version 8.12

config/checkstyle/
└── checkstyle.xml                          ← Google Java Style rules

resources/
└── logback.xml                             ← logging config cho Javalin

test/java/com/auction/
├── SetupTest.java                          ← verify JUnit 5 chạy OK + Java 21 features
├── model/
│   └── ModelTest.java                      ← test OOP: inheritance, polymorphism, getRole(), getCategory()
├── service/
│   ├── UserServiceTest.java                ← register + login + duplicate + wrong password
│   ├── BidServiceTest.java                 ← ★ bid thành công + bid thấp + bid khi đóng + anti-sniping
│   ├── AuctionServiceTest.java             ← state transitions: OPEN→RUNNING→FINISHED
│   └── ConcurrencyTest.java               ← ★ 10 threads đồng thời bid, verify no lost update
└── dao/
    ├── AuctionDaoTest.java                 ← integration test CRUD auctions với PostgreSQL
    └── UserDaoTest.java                    ← integration test insert + findByUsername với PostgreSQL
```

**Tổng: ~16 files**

---

## 📦 Shared — Cả nhóm cùng sở hữu

Các file/folder này **không thuộc riêng ai**, mọi thành viên đều đọc và có thể sửa khi cần:

```
model/                                      ← đã code xong từ trước (C code tuần 0)
├── Entity.java                             ← abstract base: id, createdAt
├── User.java                               ← abstract: username, email, getRole()
├── Bidder.java / Seller.java / Admin.java  ← getRole() → "BIDDER" / "SELLER" / "ADMIN"
├── Item.java                               ← abstract: name, description, getCategory()
├── Electronics.java / Art.java / Vehicle.java ← getCategory() + thuộc tính riêng
├── Auction.java                            ← ★ trung tâm: currentPrice, endTime, status
├── BidTransaction.java                     ← lịch sử mỗi lần bid
└── AutoBidConfig.java                      ← maxBid, increment, registeredAt

dto/                                        ← đã code xong từ trước (C code tuần 0)
├── LoginRequest.java                       ← {username, password}
├── RegisterRequest.java                    ← {username, password, email, role}
├── BidRequest.java                         ← {auctionId, amount}
├── AutoBidRequest.java                     ← {auctionId, maxBid, increment}
├── CreateItemRequest.java                  ← {name, description, category, categoryDetail}
├── CreateAuctionRequest.java               ← {itemId, startingPrice, startTime, endTime}
├── AuctionResponse.java                    ← trả về client (enriched: itemName, leadingBidderUsername)
├── BidUpdateMessage.java                   ← WebSocket push: {type, auctionId, price, newEndTime...}
└── ErrorResponse.java                      ← {error, message}

README.md                                   ← mỗi người viết 1 phần (D tổng hợp tuần 7-8)
```

**Tổng: ~21 files**

---

## 📊 Bảng tóm tắt

| Role | Files trong structure | Files cần bổ sung | Tổng |
|------|-----------------------|-------------------|------|
| A — Backend Lead | ~12 | 0 | **~12** |
| B — Frontend Lead | ~8 | ~8 (`util/` + `controller/client/`) | **~16** |
| C — Business Logic | ~24 | 0 | **~24** |
| D — DevOps & QA | ~16 | 0 | **~16** |
| Shared (model + dto) | ~21 | 0 | **~21** |
| **Tổng** | | | **~89 files** |

---

## 🔗 Sơ đồ phụ thuộc giữa các Role

```
     B (Frontend — FXML + Client Controllers + Utils)
         │
         │  gọi qua HTTP REST / WebSocket
         ▼
     A (Backend — Controller + WebSocketHandler)
         │
         │  gọi service
         ▼
     C (Business Logic — Service + Pattern + Exception)
         │
         │  gọi DAO
         ▼
     A (DAO — SQL + DatabaseConfig)
         │
         │  query
         ▼
     PostgreSQL

     D (QA) ──────────────────────► test TẤT CẢ layers trên
     D (DevOps) ───────────────────► build.gradle.kts + ci.yml kiểm soát toàn bộ pipeline
```

### Điểm giao thoa cần thống nhất sớm

| Giao thoa | Ai liên quan | Cần thống nhất gì |
|-----------|-------------|-------------------|
| `BidService` (C) gọi `AuctionDao` (A) | A ↔ C | Method signature của DAO (tên, tham số, kiểu trả về) |
| `BidUpdateMessage` (DTO/Shared) | B ↔ C | Format JSON WebSocket: các field, kiểu type enum |
| Exception (C) → HTTP status (A) | A ↔ C | Mapping: exception class → status code trong `App.java` |
| `AuctionWebSocketHandler` (A) ↔ `WebSocketObserver` (C) | A ↔ C | Cách inject handler vào observer (constructor / singleton) |
| Test (D) import class của A, B, C | D ↔ tất cả | Interfaces ổn định trước khi D viết test |

---

## ⚠️ Việc cần làm ngay — Trước khi code tuần 1

- [ ] **Thống nhất** đặt `util/` và `controller/client/` vào đâu → cập nhật `PROJECT_STRUCTURE.md`
- [ ] **A + C** ngồi lại thống nhất interface DAO (method names, return types) trước khi ai code
- [ ] **B + C** thống nhất format `BidUpdateMessage` JSON (các field, type enum values)
- [ ] **A** expose interface `AuctionWebSocketHandler.broadcast()` để C dùng trong `WebSocketObserver`
- [ ] **D** setup Gradle + CI trước → mọi người mới bắt đầu code trên nền đó
