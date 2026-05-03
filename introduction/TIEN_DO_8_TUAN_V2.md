# Tiến độ 8 tuần — Online Auction System (Bản chi tiết v2)

> **Nhóm:** 4 thành viên · **Tổng điểm:** 10 + 1 bonus · **~80 files**
> **Tech stack:** Javalin · JDBI + HikariCP · PostgreSQL · JavaFX + FXML · JWT · BCrypt · WebSocket · Gradle
> **Tools:** IntelliJ IDEA · SceneBuilder (UI) · pgAdmin 4 (DB) · GitHub Desktop · Postman
> **Login:** bằng username (không phải email)

---

## Tổng quan tiến trình đã hoàn thành

| Layer | Files | Trạng thái |
|-------|-------|-----------|
| Model (12 files) | Entity, User, Bidder, Seller, Admin, Item, Electronics, Art, Vehicle, Auction, BidTransaction, AutoBidConfig | ✅ XONG |
| DTO (9 files) | LoginRequest, RegisterRequest, BidRequest, AutoBidRequest, CreateItemRequest, CreateAuctionRequest, AuctionResponse, BidUpdateMessage, ErrorResponse | ✅ XONG |
| DAO (5 files) | UserDao, AuctionDao, BidTransactionDao, ItemDao, AutoBidConfigDao | ✅ XONG |
| DAO Tests (5 files) | UserDaoTest, AuctionDaoTest, BidTransactionDaoTest, ItemDaoTest, AutoBidConfigDaoTest | ✅ XONG |
| Config (2 files) | DatabaseConfig, SetupTest | ✅ XONG |
| Gradle + CI | build.gradle.kts, ci.yml, checkstyle.xml, .editorconfig, spotless | ✅ XONG |
| SQL Schema | V1__initial_schema.sql | ✅ XONG |
| CSS Theme | auction-theme.css (dark premium, ~450 dòng) | ✅ XONG |
| UI Guide | JAVAFX_UI_GUIDE.md (SceneBuilder workflow, animation snippets) | ✅ XONG |
| ModelTest | Test OOP: inheritance, polymorphism, getRole(), getCategory() | ✅ XONG |

**Tổng đã xong: ~35/80 files** — toàn bộ nền tảng data layer + config + testing cơ bản.

---

## Phân công vai trò (giữ nguyên)

| Vai trò | Tên | Phụ trách chính | Phụ trách phụ |
|---------|-----|----------------|---------------|
| **A** | Backend Lead | Javalin server, REST routes, WebSocket handler, SQL optimization | Concurrency DB-level |
| **B** | Frontend Lead | JavaFX GUI (SceneBuilder), FXML, REST Client, WebSocket Client | Bid History Chart |
| **C** | Business Logic | Service layer, 5 Design Patterns, JWT/BCrypt, Exception system | Auto-bidding, Anti-sniping |
| **D** | DevOps & QA | CI/CD maintain, Unit/Integration test, Git workflow, Documentation | Code review, JaCoCo |

---

## TUẦN 1 — Authentication + Exception System + Client Scaffold

> **Trạng thái đầu tuần:** Model ✅, DTO ✅, DAO ✅, DAO Tests ✅, Config ✅, CI ✅
>
> **Mục tiêu cuối tuần:** Login/Register chạy end-to-end (client → server → DB → JWT → client). Exception system hoàn chỉnh.

### A — Backend Lead

**File tạo mới:**
- `src/main/java/com/auction/App.java` — entry point server Javalin
  - `Javalin.create(config -> { config.jsonMapper(...) })` — cấu hình Jackson
  - `app.before("/api/*", JwtMiddleware::handle)` — middleware xác thực
  - `app.exception(InvalidBidException.class, (e, ctx) -> { ctx.status(400).json(ErrorResponse.of("INVALID_BID", e.getMessage())) })` — đăng ký 5 exception handlers
  - `AuthController.register(app)` — đăng ký routes auth
  - Endpoint health check: `GET /api/health` → `{"status":"ok"}`
- `src/main/java/com/auction/controller/AuthController.java`
  - `POST /api/auth/login` — nhận `LoginRequest`, gọi `UserService.login()`, trả `{"token":"eyJ..."}`
  - `POST /api/auth/register` — nhận `RegisterRequest`, gọi `UserService.register()`, trả `{"token":"eyJ...", "role":"BIDDER"}`
  - Cả hai endpoint đều public (không qua JWT middleware)

**Cách test:** Chạy `./gradlew run`, mở Postman:
- POST `http://localhost:8080/api/auth/register` body: `{"username":"alice", "password":"123456", "email":"alice@mail.com", "role":"BIDDER"}` → nhận JWT token
- POST `http://localhost:8080/api/auth/login` body: `{"username":"alice", "password":"123456"}` → nhận JWT token

### B — Frontend Lead

**File tạo mới:**
- `src/main/java/com/auction/ClientApp.java` — entry point JavaFX
  - `extends Application`, override `start(Stage primaryStage)`
  - Load CSS theme: `scene.getStylesheets().add(getResource("/css/auction-theme.css"))`
  - Load FXML đầu tiên: `login.fxml`
  - Set minimum window size: 1000×700
- `src/main/resources/fxml/login.fxml` — thiết kế bằng SceneBuilder
  - Root: `StackPane` (styleClass="login-root"), child: `VBox` (styleClass="login-card", maxWidth=400)
  - Các element: Label app-title, TextField usernameField, PasswordField passwordField, Button loginBtn, Hyperlink "Đăng ký", Label errorLabel (visible=false)
  - fx:controller = `com.auction.controller.client.LoginController`
- `src/main/resources/fxml/register.fxml` — tương tự login, thêm:
  - TextField emailField, ComboBox roleCombo (items: BIDDER, SELLER)
  - fx:controller = `com.auction.controller.client.RegisterController`
- `src/main/java/com/auction/controller/client/LoginController.java`
  - `@FXML handleLogin()` — gọi REST POST /api/auth/login, lưu JWT token, chuyển sang auction-list
  - `@FXML goToRegister()` — chuyển sang register.fxml
  - Hiệu ứng: fade transition khi chuyển màn, shake khi login fail
- `src/main/java/com/auction/controller/client/RegisterController.java`
- `src/main/java/com/auction/util/RestClient.java` — utility dùng `java.net.http.HttpClient`
  - `post(String path, Object body)` → HttpResponse (auto serialize JSON bằng Jackson)
  - `get(String path)` → HttpResponse
  - Tự động gắn header `Authorization: Bearer <token>` nếu đã login
  - Base URL: `http://localhost:8080`
- `src/main/java/com/auction/util/SceneManager.java`
  - `switchTo(String fxmlPath)` — load FXML + fade transition
  - `getCurrentStage()` — singleton Stage

**Workflow SceneBuilder:**
1. Mở SceneBuilder → kéo StackPane → kéo VBox (alignment=CENTER, spacing=24, maxWidth=400)
2. Add styleClass "login-card" (panel Properties → Style Class)
3. Menu Preview → Scene Style Sheets → chọn `auction-theme.css` → thấy dark theme live
4. Save → IntelliJ tự cập nhật

### C — Business Logic

**File tạo mới:**
- `src/main/java/com/auction/config/JwtUtil.java`
  - `createToken(Long userId, String username, String role)` → String JWT (expire 24h)
  - `verifyToken(String token)` → `DecodedJWT` (chứa userId, username, role)
  - Secret key đọc từ `System.getenv("JWT_SECRET")`, default "auction-secret-key-dev"
  - Dùng thư viện `com.auth0:java-jwt`
- `src/main/java/com/auction/service/UserService.java`
  - `register(RegisterRequest req)` → User
    - Validate: username không trống, email format, role hợp lệ
    - Check trùng: `userDao.findByUsername()` → throw `DuplicateException` nếu đã tồn tại
    - Hash password: `BCrypt.withDefaults().hashToString(12, req.getPassword().toCharArray())`
    - Tạo đúng subclass: switch(role) → new Bidder/Seller/Admin
    - `userDao.insert(user)`
  - `login(LoginRequest req)` → String JWT
    - `userDao.findByUsername(req.getUsername())` → throw `NotFoundException` nếu không tìm thấy
    - `BCrypt.verifyer().verify(password, passwordHash)` → throw `UnauthorizedException` nếu sai
    - `JwtUtil.createToken(user.getId(), user.getUsername(), user.getRole())`
- `src/main/java/com/auction/middleware/JwtMiddleware.java`
  - Đọc header `Authorization: Bearer <token>`
  - `JwtUtil.verifyToken(token)` → set attribute `userId`, `username`, `role` vào context
  - Skip nếu path bắt đầu bằng `/api/auth/`
  - Throw `UnauthorizedException` nếu token thiếu hoặc invalid
- `src/main/java/com/auction/exception/` — 5 file:
  - `InvalidBidException.java` — extends RuntimeException
  - `AuctionClosedException.java`
  - `UnauthorizedException.java`
  - `NotFoundException.java`
  - `DuplicateException.java`

### D — DevOps & QA

**File tạo mới / cập nhật:**
- `src/test/java/com/auction/service/UserServiceTest.java`
  - `testRegisterSuccess()` — register Bidder, verify id not null, role = "BIDDER"
  - `testRegisterDuplicateUsername()` — register 2 lần cùng username → DuplicateException
  - `testLoginSuccess()` — register → login → verify JWT token parse được
  - `testLoginWrongPassword()` — register → login sai pass → UnauthorizedException
  - `testLoginUserNotFound()` — login username không tồn tại → NotFoundException
- `src/test/java/com/auction/config/JwtUtilTest.java` (unit test, không cần DB)
  - `testCreateAndVerify()` — tạo token → verify → đúng userId, role
  - `testExpiredToken()` — tạo token expire 0ms → verify → throw
  - `testInvalidToken()` — verify "garbage" → throw
- Cập nhật `ci.yml`: thêm job chạy `spotlessCheck` trước `test`
- Setup pre-commit hook cho cả team: `./gradlew spotlessApply` trước mỗi commit

### Deliverables tuần 1
- [ ] `POST /api/auth/register` → trả JWT (test bằng Postman)
- [ ] `POST /api/auth/login` → trả JWT (test bằng Postman)
- [ ] JWT Middleware chặn request không có token (GET /api/health → 401)
- [ ] Exception handler trả JSON `ErrorResponse` (không trả stacktrace)
- [ ] Login screen JavaFX hiển thị với dark theme
- [ ] UserServiceTest + JwtUtilTest pass
- [ ] CI xanh

**Điểm tích lũy: ~2.5/11** (Cây kế thừa 0.5 + OOP 1.0 + Build/convention 0.5 + CI/CD 0.5)

---

## TUẦN 2 — REST CRUD + Factory + State Pattern + FXML chính

> **Mục tiêu:** CRUD đầy đủ cho Item + Auction qua REST. Factory tạo đúng subclass. State pattern quản lý trạng thái phiên. Client hiển thị danh sách auction.

### A — Backend Lead

**File tạo mới:**
- `src/main/java/com/auction/controller/ItemController.java`
  - `GET /api/items` — list all items (public, có filter theo sellerId nếu query param)
  - `GET /api/items/:id` — chi tiết 1 item
  - `POST /api/items` — seller tạo item (check role = SELLER từ JWT)
  - `PUT /api/items/:id` — seller sửa item (check ownership: sellerId == userId từ JWT)
  - `DELETE /api/items/:id` — seller/admin xóa item
- `src/main/java/com/auction/controller/AuctionController.java`
  - `GET /api/auctions` — list all (hỗ trợ query param `?status=RUNNING`)
  - `GET /api/auctions/:id` — chi tiết, trả `AuctionResponse` (bao gồm itemName, leadingBidderUsername)
  - `POST /api/auctions` — seller tạo phiên (check role, check item thuộc seller)
  - `PUT /api/auctions/:id` — seller sửa (chỉ khi status = OPEN)
  - `DELETE /api/auctions/:id` — seller/admin xóa
- Đăng ký tất cả routes trong `App.java`: `ItemController.register(app, itemService)`, `AuctionController.register(app, auctionService)`

### B — Frontend Lead

**File tạo mới (SceneBuilder):**
- `src/main/resources/fxml/auction-list.fxml`
  - Root: `BorderPane` (styleClass="main-layout")
  - TOP: `HBox` top-bar (Logo "Auction House" gold, Region spacer, usernameDisplay, Button logout)
  - CENTER: `VBox` chứa search + filter bar + `TableView auctionTable`
  - TableView columns: Sản phẩm (itemCol), Giá hiện tại (priceCol), Thời gian còn (timeCol), Trạng thái (statusCol badge), Hành động (actionCol button "Vào")
  - Nút "+ Tạo phiên" visible chỉ khi role = SELLER
- `src/main/java/com/auction/controller/client/AuctionListController.java`
  - `initialize()` → gọi `RestClient.get("/api/auctions")` → parse JSON → `ObservableList<AuctionResponse>` → bind vào TableView
  - Custom `CellFactory` cho statusCol: hiển thị badge (Label + styleClass "status-running/finished/...")
  - Custom `CellFactory` cho priceCol: format `NumberFormat.getCurrencyInstance(new Locale("vi","VN"))`
  - `handleCreateAuction()` → chuyển sang create-auction.fxml
  - `handleViewAuction(Long auctionId)` → chuyển sang auction-detail.fxml
  - Nút refresh mỗi 10 giây (Timeline + KeyFrame)
- `src/main/resources/fxml/create-item.fxml`
  - Form: TextField name, TextArea description, ComboBox category (ELECTRONICS/ART/VEHICLE), TextField categoryDetail (label thay đổi theo category: "Thương hiệu"/"Nghệ sĩ"/"Năm SX")
- `src/main/resources/fxml/create-auction.fxml`
  - Form: ComboBox chọn item (load từ GET /api/items?sellerId=me), TextField startingPrice, DatePicker + TimePicker cho startTime/endTime

### C — Business Logic

**File tạo mới:**
- `src/main/java/com/auction/pattern/factory/ItemFactory.java` — **Factory Method pattern**
  - `static Item create(CreateItemRequest req, Long sellerId)` → switch(category):
    - "ELECTRONICS" → `new Electronics(name, desc, sellerId, categoryDetail)` (categoryDetail = brand)
    - "ART" → `new Art(name, desc, sellerId, categoryDetail)` (categoryDetail = artist)
    - "VEHICLE" → `new Vehicle(name, desc, sellerId, Integer.parseInt(categoryDetail))` (categoryDetail = year)
    - default → throw `IllegalArgumentException`
  - Javadoc giải thích Factory Method pattern bằng tiếng Việt
- `src/main/java/com/auction/pattern/state/` — **State pattern** (6 files):
  - `AuctionState.java` — interface: `placeBid(Auction, BigDecimal, Long)`, `close(Auction)`, `edit(Auction)`, `extend(Auction, long seconds)`
  - `OpenState.java` — cho phép edit, chưa cho bid (throw `AuctionClosedException("Phiên chưa bắt đầu")`)
  - `RunningState.java` — cho phép bid + extend, không cho edit (throw `AuctionClosedException("Không thể sửa khi đang diễn ra")`)
  - `FinishedState.java` — throw mọi hành động trừ close
  - `PaidState.java` — throw tất cả
  - `CanceledState.java` — throw tất cả
- `src/main/java/com/auction/service/ItemService.java`
  - `create(CreateItemRequest req, Long sellerId)` → dùng `ItemFactory.create()` → `itemDao.insert()`
  - `getAll()`, `getBySellerId(Long)`, `getById(Long)` — delegate sang DAO
  - `update()`, `delete()` — check ownership (sellerId == userId)
- `src/main/java/com/auction/service/AuctionService.java`
  - `create(CreateAuctionRequest req, Long sellerId)` → validate item thuộc seller → `new Auction(...)` → `auctionDao.insert()`
  - `getAll(String statusFilter)`, `getById(Long id)` — trả AuctionResponse (enriched với itemName, leadingBidderUsername)
  - `update(Long id, ...)` — lấy auction → resolve state → `state.edit(auction)` → `auctionDao.update()`
  - `delete(Long id, Long userId, String role)` — check ownership hoặc ADMIN
  - `getState(Auction auction)` → switch(status): "OPEN" → new OpenState(), "RUNNING" → new RunningState(), etc.

### D — DevOps & QA

**File tạo mới:**
- `src/test/java/com/auction/service/AuctionServiceTest.java`
  - `testCreateAuction()` — tạo phiên, verify status = "OPEN"
  - `testEditOpenAuction()` — sửa khi OPEN → OK
  - `testEditRunningAuction()` — set status RUNNING → sửa → throw AuctionClosedException
  - `testBidOpenAuction()` — bid khi OPEN → throw AuctionClosedException("Phiên chưa bắt đầu")
  - `testBidFinishedAuction()` — bid khi FINISHED → throw
  - `testStateTransitions()` — verify OPEN → RUNNING → FINISHED flow
- `src/test/java/com/auction/pattern/factory/ItemFactoryTest.java` (unit test, không cần DB)
  - `testCreateElectronics()` — verify instanceof Electronics, brand đúng
  - `testCreateArt()` — verify instanceof Art, artist đúng
  - `testCreateVehicle()` — verify instanceof Vehicle, year đúng
  - `testInvalidCategory()` → IllegalArgumentException
- Chạy `./gradlew spotlessApply` + verify CI

### Deliverables tuần 2 — MILESTONE 1
- [ ] CRUD Item qua REST hoạt động (test bằng Postman)
- [ ] CRUD Auction qua REST hoạt động
- [ ] Factory tạo đúng subclass (Electronics/Art/Vehicle) → ItemFactoryTest pass
- [ ] State pattern chặn đúng hành động theo trạng thái → AuctionServiceTest pass
- [ ] Client hiển thị danh sách auctions (TableView + dark theme + status badge)
- [ ] Form tạo item + auction hoạt động

**Điểm tích lũy: ~4.5/11** (thêm: Design patterns 1.0 + CRUD user/product 1.0)

---

## TUẦN 3 — Bidding Core + Observer + WebSocket

> **Mục tiêu:** Đặt giá hoạt động end-to-end. Observer pattern push BID_UPDATE qua WebSocket. Countdown timer.

### A — Backend Lead

**File tạo mới:**
- `src/main/java/com/auction/controller/BidController.java`
  - `POST /api/auctions/:id/bid` — nhận `BidRequest`, check role = BIDDER, gọi `bidService.placeBid()`
  - `GET /api/auctions/:id/bids` — trả list `BidTransaction` sorted by createdAt DESC (cho bid history chart)
- `src/main/java/com/auction/controller/AuctionWebSocketHandler.java`
  - Path: `/ws/auction/{id}?token=<JWT>`
  - `onConnect(WsConnectContext ctx)` — verify JWT → lưu connection vào `Map<Long, Set<WsContext>>` (key = auctionId)
  - `onClose(WsCloseContext ctx)` — remove connection khỏi map
  - `onError(WsErrorContext ctx)` — log error, remove connection
  - `broadcast(Long auctionId, BidUpdateMessage msg)` — serialize JSON → gửi cho tất cả connections của phiên đó
- Đăng ký WebSocket trong `App.java`: `app.ws("/ws/auction/{id}", wsHandler)`

### B — Frontend Lead

**File tạo mới (SceneBuilder):**
- `src/main/resources/fxml/auction-detail.fxml` — màn hình quan trọng nhất
  - Layout chính xác theo mockup: BorderPane > TOP (header + back button + status badge) > CENTER (HBox: cột trái info+bid+chart, cột phải bid history)
  - Vùng giá: Label currentPrice (styleClass="current-price", font 48px gold), Label leadingBidder, Label countdown (styleClass="countdown", font monospace 28px)
  - Form bid: TextField bidAmount + Button "Đặt giá" (styleClass="btn-bid")
  - TitledPane "Auto-bid" (expanded=false, sẽ kết nối tuần 4)
  - LineChart bidChart (trục X = thời gian, trục Y = giá)
  - ListView bidHistoryList (cột phải, width=280)
- `src/main/java/com/auction/controller/client/AuctionDetailController.java`
  - `initialize()` → load auction detail (GET /api/auctions/:id) → populate UI
  - `handleBid()` → POST /api/auctions/:id/bid → nếu lỗi: shake bidAmountField + hiện errorLabel
  - Countdown timer: `Timeline` chạy mỗi giây, format HH:MM:SS, thêm class "countdown-urgent" khi < 30s, pulse animation khi < 30s
  - Load bid history: GET /api/auctions/:id/bids → populate ListView + LineChart
- `src/main/java/com/auction/util/WebSocketClient.java`
  - Dùng `java.net.http.HttpClient.newWebSocketBuilder()`
  - `connect(Long auctionId, String token)` — mở WS connection
  - `onMessage(String json)` → parse `BidUpdateMessage` → `Platform.runLater()`:
    - `BID_UPDATE` → cập nhật currentPrice, leadingBidder, thêm row vào ListView, thêm point vào LineChart
    - `TIME_EXTENDED` → cập nhật countdown endTime
    - `AUCTION_ENDED` → disable nút bid, hiển thị "Người thắng: ..."
  - `disconnect()` — gọi khi rời màn hình

### C — Business Logic

**File tạo mới:**
- `src/main/java/com/auction/pattern/observer/` — **Observer pattern** (3 files):
  - `AuctionEventListener.java` — interface:
    - `void onBidUpdate(BidUpdateMessage msg)`
    - `void onTimeExtended(BidUpdateMessage msg)`
    - `void onAuctionEnd(BidUpdateMessage msg)`
  - `WebSocketObserver.java` — implements AuctionEventListener
    - Constructor nhận `AuctionWebSocketHandler handler, Long auctionId`
    - Mỗi method → gọi `handler.broadcast(auctionId, msg)`
  - `AuctionEventManager.java` — quản lý observers
    - `Map<Long, List<AuctionEventListener>> listeners` (key = auctionId)
    - `subscribe(Long auctionId, AuctionEventListener listener)`
    - `unsubscribe(Long auctionId, AuctionEventListener listener)`
    - `notifyBidUpdate(Long auctionId, BidUpdateMessage msg)` → loop listeners → `onBidUpdate(msg)`
- `src/main/java/com/auction/pattern/strategy/BidStrategy.java` — **Strategy pattern** (interface)
  - `BidResult execute(Auction auction, Long bidderId, BigDecimal amount, boolean isAutoBid)`
- `src/main/java/com/auction/pattern/strategy/ManualBidStrategy.java`
  - Validate: amount > currentPrice (throw InvalidBidException nếu không)
  - Validate: bidderId != auction.sellerId (seller không tự bid)
  - Update: auction.setCurrentPrice(amount), auction.setLeadingBidderId(bidderId)
- `src/main/java/com/auction/service/BidService.java`
  - `placeBid(Long auctionId, Long bidderId, BigDecimal amount, boolean isAutoBid)`:
    - Lấy auction → resolve State → `state.placeBid()` (check RUNNING)
    - `synchronized(auction)` — tầng 1 concurrency
    - Gọi `manualBidStrategy.execute(auction, bidderId, amount)`
    - `auctionDao.update(auction)` — update giá mới
    - `bidTransactionDao.insert(new BidTransaction(...))` — ghi lịch sử
    - Tạo `BidUpdateMessage.bidUpdate(...)` → `eventManager.notifyBidUpdate(...)`

### D — DevOps & QA

**File tạo mới:**
- `src/main/java/com/auction/service/AuctionScheduler.java`
  - Dùng `ScheduledExecutorService` chạy mỗi 5 giây
  - Query tất cả auction status=RUNNING mà endTime < now → set status="FINISHED"
  - Notify: `eventManager.notifyAuctionEnd(...)` với `BidUpdateMessage.auctionEnded(...)`
  - Chuyển OPEN → RUNNING khi đến startTime
- `src/test/java/com/auction/service/BidServiceTest.java`
  - `testBidSuccess()` — bid 500k (> starting 100k) → currentPrice = 500k, leadingBidderId đúng
  - `testBidTooLow()` — bid 50k (< current 100k) → throw InvalidBidException
  - `testBidWhenFinished()` — set status FINISHED → bid → throw AuctionClosedException
  - `testBidOwnAuction()` — seller tự bid → throw InvalidBidException("Không thể bid sản phẩm của mình")
- Test end-to-end thủ công: mở 2 cửa sổ client → cả hai vào cùng phiên → client A bid → client B thấy giá cập nhật realtime qua WebSocket

### Deliverables tuần 3
- [ ] Đặt giá chạy end-to-end: client → REST → BidService → DB → WebSocket → all clients
- [ ] Observer pattern notify BID_UPDATE cho tất cả client đang xem phiên
- [ ] Countdown timer đếm ngược chính xác, đổi đỏ khi < 30s
- [ ] Bid history hiển thị trong ListView
- [ ] LineChart hiển thị initial data từ REST
- [ ] BidServiceTest pass
- [ ] AuctionScheduler tự chuyển OPEN → RUNNING → FINISHED

**Điểm tích lũy: ~7.0/11** (thêm: Bidding 1.0 + Realtime 0.5 + Client-Server 0.5 + MVC 0.5)

---

## TUẦN 4 — Concurrency + Anti-sniping + Auto-bidding

> **Mục tiêu:** Concurrent bidding an toàn (2 tầng lock). 2 tính năng nâng cao hoàn tất.

### A — Backend Lead

- Nâng cấp `BidService.placeBid()` — **tầng 2 concurrency** (DB-level):
  ```java
  jdbi.inTransaction(handle -> {
      Auction auction = auctionDao.findByIdForUpdate(handle, auctionId); // SELECT ... FOR UPDATE
      // validate + update
      auctionDao.updateInTransaction(handle, auction);
      bidTransactionDao.insertInTransaction(handle, bid);
      return auction;
  });
  ```
- Thêm route: `POST /api/auctions/:id/auto-bid` trong BidController — nhận `AutoBidRequest`, gọi `bidService.setupAutoBid()`
- Thêm route: `DELETE /api/auctions/:id/auto-bid` — tắt auto-bid

### B — Frontend Lead

- Kết nối TitledPane "Auto-bid" trong `auction-detail.fxml`:
  - TextField maxBidField, TextField incrementField, Button "Bật Auto-bid" / "Tắt Auto-bid"
  - Hiển thị trạng thái: "Auto-bid đang hoạt động (max: 10,000,000đ)" hoặc "Chưa bật"
- Nhận `AUTO_BID_TRIGGERED` từ WebSocket → Toast notification: "Hệ thống đã tự động đặt giá 5,100,000đ cho bạn"
- Nhận `TIME_EXTENDED` → cập nhật countdown endTime + flash hiệu ứng

### C — Business Logic

- Code **Anti-sniping** trong `BidService.placeBid()` (thêm ~10 dòng sau khi update):
  ```java
  if (auction.getRemainingTimeMs() < 30_000) { // còn < 30 giây
      auction.setEndTime(auction.getEndTime().plusSeconds(60));
      auctionDao.update(auction);
      eventManager.notifyTimeExtended(auctionId, BidUpdateMessage.timeExtended(auctionId, auction.getEndTime()));
  }
  ```
- Code `src/main/java/com/auction/pattern/strategy/AutoBidStrategy.java`:
  - `executeAll(Long auctionId, BigDecimal currentPrice)` — duyệt active AutoBidConfigs:
    - Query: `autoBidConfigDao.findActiveByAuctionId(auctionId)` → sort by registeredAt (PriorityQueue)
    - Loop: ai có `canBidAt(currentPrice)` → `placeBid()` với isAutoBid=true
    - Nếu maxBid bị vượt → set `autoBidConfig.active = false` → update DB
    - Ưu tiên: ai đăng ký trước (registeredAt nhỏ hơn) được xử lý trước
- Tích hợp auto-bid trigger vào flow `placeBid()`: sau khi manual bid thành công → `autoBidStrategy.executeAll()` → có thể trigger chain auto-bids
- `bidService.setupAutoBid(Long auctionId, Long bidderId, AutoBidRequest req)` — validate maxBid > currentPrice, increment > 0 → `autoBidConfigDao.insert()`

### D — DevOps & QA

**File tạo mới:**
- `src/test/java/com/auction/service/ConcurrencyTest.java`
  - `testConcurrentBids()` — tạo 10 threads cùng bid 1 phiên, mỗi thread bid giá khác nhau
    - Verify sau khi tất cả threads xong: chỉ 1 người dẫn đầu, currentPrice = giá cao nhất, đúng số BidTransaction records
    - Không có lost update, không có rollback
  - `testConcurrentBidsSamePrice()` — 5 threads cùng bid cùng giá → chỉ 1 thành công, 4 bị reject
- Test anti-sniping: bid khi remainingTime = 25s → verify endTime tăng thêm 60s → BidUpdateMessage type = TIME_EXTENDED
- Test auto-bid: User A setup auto-bid (max=10M, increment=100k), User B bid 5M → verify hệ thống tự bid 5.1M cho A → BidTransaction với autoBid=true

### Deliverables tuần 4 — MILESTONE 2
- [ ] ConcurrencyTest pass với 10+ threads
- [ ] Anti-sniping: bid trong 30s cuối → gia hạn → broadcast TIME_EXTENDED
- [ ] Auto-bidding: setup → tự bid → chain bids → dừng khi vượt maxBid
- [ ] Auto-bid UI: form + toggle + trạng thái hiển thị

**Điểm tích lũy: ~9.0/11** (thêm: Concurrency 1.0 + Anti-sniping 0.5 + Auto-bid 0.5)

---

## TUẦN 5 — Bid History Chart + Exception Handling + Admin Panel

> **Mục tiêu:** Tính năng nâng cao cuối cùng. Xử lý lỗi toàn diện. Admin panel.

### A — Backend Lead

- API admin: `GET /api/admin/users` (list all users, check role=ADMIN), `DELETE /api/admin/users/:id`
- API admin: `GET /api/admin/auctions` (list all kể cả CANCELED)
- Review + optimize SQL: thêm INDEX trên `auctions(status)`, `bid_transactions(auction_id)`, `auto_bid_configs(auction_id, active)`
- Xử lý edge cases trong BidController: bid khi phiên OPEN (chưa RUNNING), bid trùng leadingBidder (không tự bid chính mình)

### B — Frontend Lead

- **Bid History Chart realtime** trong `auction-detail.fxml`:
  - Load initial: GET /api/auctions/:id/bids → populate LineChart (trục X = epoch seconds, trục Y = price double)
  - Mỗi BID_UPDATE từ WebSocket → `Platform.runLater(() → bidSeries.getData().add(new XYChart.Data<>(timestamp, price)))`
  - Giới hạn 50 điểm gần nhất (remove data[0] khi > 50)
  - Chart style: line gold #d4a574, dot gold, grid #252540 (match dark theme)
- `src/main/resources/fxml/admin-panel.fxml` (SceneBuilder):
  - BorderPane: LEFT = sidebar navigation (VBox buttons: "Users", "Auctions"), CENTER = content area
  - Tab Users: TableView (username, email, role, createdAt, button "Xóa")
  - Tab Auctions: TableView (itemName, status, currentPrice, button "Hủy phiên")
- `src/main/java/com/auction/controller/client/AdminPanelController.java`
- Polish toàn bộ UI:
  - Error messages hiển thị đúng chỗ (Label đỏ dưới form)
  - Loading indicator khi gọi API (ProgressIndicator)
  - Role-based navigation: Bidder → auction-list (không thấy "Tạo phiên"), Seller → thêm nút tạo, Admin → thêm link "Admin Panel"
  - Fade transition giữa tất cả màn hình

### C — Business Logic

- Review exception handling toàn diện — đảm bảo mọi service method:
  - Validate input → throw custom exception với message tiếng Việt rõ ràng
  - Try-catch cho database errors → log + throw readable error
  - Mapping chính xác: InvalidBid → 400, AuctionClosed → 400, Unauthorized → 401, NotFound → 404, Duplicate → 409
- Review 5 design patterns — thêm Javadoc ở đầu mỗi file giải thích:
  - Pattern nào đang dùng, tại sao chọn pattern này
  - Liên kết với các file khác (ai gọi ai)
  - Ví dụ minh họa bằng tiếng Việt
- Code WebSocket reconnect: khi connection bị mất → auto reconnect sau 3 giây, retry tối đa 5 lần

### D — DevOps & QA

- Viết test edge cases:
  - Test bid khi phiên OPEN (chưa RUNNING) → AuctionClosedException("Phiên chưa bắt đầu")
  - Test auto-bid khi maxBid = currentPrice (đúng bằng, không đủ increment) → auto-bid dừng, active=false
  - Test delete item có auction đang RUNNING → cascade hay block? (dùng pgAdmin 4 verify)
- Chạy full test suite: `./gradlew test` → fix flaky tests
- Setup `JaCoCo` coverage report trong build.gradle.kts → verify > 60% coverage cho service layer

### Deliverables tuần 5
- [ ] Bid History Chart cập nhật realtime từ WebSocket
- [ ] Admin panel hoạt động (list users, list auctions, xóa/hủy)
- [ ] Exception handling toàn diện: mọi lỗi trả JSON chuẩn, message tiếng Việt
- [ ] 5 design patterns có Javadoc giải thích

**Điểm tích lũy: ~10.5/11** (thêm: Xử lý lỗi 1.0 + Chart 0.5)

---

## TUẦN 6 — Testing + Code Quality + WebSocket Stability

> **Mục tiêu:** Test coverage cao. Code sạch. WebSocket ổn định.

### A — Backend Lead

- Viết integration test full flow:
  - Scenario 1: Register seller → login → create item → create auction → Register bidder → login → bid → auto-bid → anti-sniping → auction end → verify winner
  - Scenario 2: Concurrent 5 bidders + 2 auto-bidders → verify consistency
- Fix performance issues (nếu có): N+1 query khi load auction list, connection pool tuning

### B — Frontend Lead

- Manual testing toàn bộ flows theo role:
  - **Bidder flow:** Login → browse auctions → filter by status → vào phiên → bid → thấy chart update → auto-bid → countdown → phiên kết thúc → thấy kết quả
  - **Seller flow:** Login → tạo item → tạo phiên → xem phiên đang chạy → thấy bids real-time → phiên kết thúc
  - **Admin flow:** Login → admin panel → xem users → xem auctions → hủy phiên vi phạm
- Fix UI bugs phát hiện trong testing
- Đảm bảo: resize cửa sổ không vỡ layout (min size 1000×700)

### C — Business Logic

- **Code review toàn bộ project** (~80 files):
  - Naming conventions: camelCase methods, UPPER_CASE constants, PascalCase classes
  - OOP compliance: private fields + public getters/setters, abstract methods override đúng
  - Pattern compliance: không có code trùng lặp, đúng single responsibility
  - Javadoc tiếng Việt đầy đủ ở tất cả public methods
- Chuẩn bị list 20 câu hỏi giải thích code (mỗi thành viên phải trả lời được 5 câu random)

### D — DevOps & QA

- Đảm bảo test coverage:
  - Service layer: > 70%
  - DAO layer: > 60%
  - Pattern layer: > 80%
- CI pipeline final:
  - Step 1: `spotlessCheck`
  - Step 2: `checkstyleMain`
  - Step 3: `test` (with PostgreSQL 16 service container)
  - Step 4: `jacocoTestReport` → upload artifact
- Fix bất kỳ Checkstyle/Spotless warnings còn lại
- Verify Conventional Commits trên tất cả commits: `feat:`, `fix:`, `test:`, `docs:`, `chore:`

### Hoạt động nhóm tuần 6

**Session Code Review (2h):**
- Mỗi người trình bày 15 phút: phần code mình viết, giải thích logic, tại sao chọn cách này
- Người khác hỏi → phải trả lời được

### Deliverables tuần 6 — MILESTONE 3
- [ ] Tất cả tests pass (unit + integration + concurrency)
- [ ] CI pipeline xanh trên main
- [ ] JaCoCo > 60% overall
- [ ] Code review hoàn tất, không còn warnings
- [ ] Mọi thành viên trả lời được 5 câu hỏi random

---

## TUẦN 7 — Documentation + Demo Prep

> **Mục tiêu:** README.md hoàn chỉnh. Demo sẵn sàng. Mọi người tự tin giải thích code.

### A — Backend Lead

- Viết README phần: Database Schema (5 bảng + ERD text), API Documentation (liệt kê tất cả endpoints: method, path, request body, response body, auth required, example)
- Final test: tạo fresh database (pgAdmin 4 → drop + create) → chạy migration → test full flow

### B — Frontend Lead

- Chụp screenshots tất cả 7 màn hình (dark theme) cho README
- Quay video demo ngắn 3-5 phút: login → browse → bid → auto-bid trigger → anti-sniping trigger → chart update → admin panel → kết quả
- Fix bất kỳ UI issues phát hiện khi quay demo

### C — Business Logic

- Viết README phần: Architecture Overview (sơ đồ client-server), Design Patterns (giải thích 5 patterns + lý do chọn + file nào), OOP Principles (cây kế thừa + ví dụ polymorphism)
- Vẽ class diagram (text-based hoặc Mermaid) cho model layer
- Vẽ sequence diagram cho flow "User bid 500k"

### D — DevOps & QA

- Viết README phần: Setup Instructions (clone → setup DB → config .env → gradlew build → gradlew run + runClient), CI/CD Pipeline (giải thích mỗi step), Coding Conventions
- Viết `CONTRIBUTING.md` — Git workflow, branch naming, commit format, PR template
- Tag release `v1.0.0` trên GitHub
- Final CI/CD: đảm bảo main branch build xanh

### Hoạt động nhóm tuần 7

**Session 1 — Mock Demo (2h):**
- Trình bày demo như thật: 1 người demo, 3 người hỏi
- Timer 5 phút demo + 10 phút Q&A
- Rotate: mỗi người đều phải demo 1 lần

**Session 2 — Code Walkthrough (2h):**
- Random chọn file → người KHÔNG viết file đó phải giải thích logic
- Ví dụ: chọn `BidService.java` → B (Frontend Lead) phải giải thích synchronized + FOR UPDATE + observer notify

---

## TUẦN 8 — Final Polish + Submit

> **Mục tiêu:** Hoàn hảo mọi thứ. Sẵn sàng nộp. Zero doubt.

### Toàn team

- **Clone fresh repo** trên máy khác → `./gradlew build` → `./gradlew test` → `./gradlew run` → mở client → test full flow → PASS
- Review README.md lần cuối: kiểm tra link, kiểm tra screenshot đúng, kiểm tra instructions chạy được
- Kiểm tra commit history trên GitHub: đủ commits cho mỗi người (3-4/tuần × 8 tuần ≈ 25+ commits/người), spread đều qua 8 tuần
- Kiểm tra branch history: nhiều PRs, có reviewer, có comments

### Session cuối cùng (2h):

Mỗi người ngồi trước code, được chỉ random 3 file bất kỳ trong 80 files → phải giải thích:
1. File này làm gì?
2. Nó liên kết với file nào khác?
3. Tại sao thiết kế như vậy?

Nếu ai không trả lời được → cả nhóm pair programming giải thích cho nhau cho đến khi hiểu.

### Deliverables tuần 8 — SUBMIT
- [ ] README.md đầy đủ: setup, architecture, API docs, patterns, screenshots, demo video
- [ ] GitHub repo sạch: 25+ commits/người, PRs, conventional commits
- [ ] CI/CD xanh trên main
- [ ] Tag v1.0.0
- [ ] Mọi thành viên giải thích được bất kỳ file nào

**Điểm mục tiêu: 11.0/11** (thêm: Unit test 0.5 + CI/CD 0.5)

---

## Bảng tổng hợp điểm theo tuần

| Tuần | Điểm mới | Tích lũy | Mục rubric đạt được |
|------|----------|----------|---------------------|
| 1 | 2.5 | 2.5 | Cây kế thừa (0.5) + OOP (1.0) + Build/convention (0.5) + CI/CD (0.5) |
| 2 | 2.0 | 4.5 | Design patterns (1.0) + CRUD user/product (1.0) |
| 3 | 2.5 | 7.0 | Bidding (1.0) + Realtime Observer/Socket (0.5) + Client-Server (0.5) + MVC (0.5) |
| 4 | 2.0 | 9.0 | Concurrency (1.0) + Anti-sniping (0.5) + Auto-bid (0.5) |
| 5 | 1.5 | 10.5 | Xử lý lỗi (1.0) + Chart (0.5) |
| 6 | 0.5 | 11.0 | Unit test (0.5) |
| 7–8 | — | 11.0 | Documentation + Demo (không có điểm riêng nhưng cần cho submission) |

---

## Bảng file theo tuần — ai code gì, khi nào

| Tuần | A (Backend) | B (Frontend) | C (Logic) | D (QA) |
|------|------------|-------------|-----------|--------|
| 1 | App.java, AuthController | ClientApp, login.fxml, register.fxml, LoginController, RegisterController, RestClient, SceneManager | JwtUtil, UserService, JwtMiddleware, 5 Exceptions | UserServiceTest, JwtUtilTest |
| 2 | ItemController, AuctionController | auction-list.fxml, AuctionListController, create-item.fxml, create-auction.fxml | ItemFactory, 6 State classes, ItemService, AuctionService | AuctionServiceTest, ItemFactoryTest |
| 3 | BidController, AuctionWebSocketHandler | auction-detail.fxml, AuctionDetailController, WebSocketClient | 3 Observer classes, BidStrategy, ManualBidStrategy, BidService | AuctionScheduler, BidServiceTest |
| 4 | BidService upgrade (FOR UPDATE) | Auto-bid UI, TIME_EXTENDED UI | Anti-sniping logic, AutoBidStrategy | ConcurrencyTest |
| 5 | Admin API, SQL indexes | Bid History Chart, admin-panel.fxml, AdminPanelController, UI polish | Exception review, Pattern Javadoc, WS reconnect | Edge case tests, JaCoCo |
| 6 | Integration test full flow | Manual testing, bug fixes | Code review 80 files, 20 câu hỏi | Coverage > 60%, CI final |
| 7 | README: DB + API docs | Screenshots, demo video | README: Architecture + Patterns | README: Setup + CI/CD, CONTRIBUTING.md |
| 8 | Fresh DB test | Final UI fix | Sequence + class diagrams | Tag v1.0.0, final CI |

---

## Quy tắc làm việc nhóm

### Git Workflow
- Branch naming: `feat/login-api`, `fix/bid-validation`, `test/concurrency`, `docs/readme-api`
- Commit format: `feat: add login endpoint with JWT`, `fix: handle concurrent bid race condition`
- PR rule: ít nhất 1 reviewer approve, squash merge vào main
- Tối thiểu: 3-4 commits/người/tuần → ~25 commits/người tổng cộng

### Họp nhóm
- Standup 15 phút (Zalo/Discord): Thứ 2 + Thứ 5 — mỗi người: đang làm gì, stuck gì, cần gì
- Review session 1-2h: Cuối mỗi tuần — demo progress, code review, plan tuần sau
- Code walkthrough session 2h: Tuần 6 + 7 — đảm bảo mọi người hiểu code

### Nguyên tắc sống còn
1. **Commit hàng ngày** — không để code local > 2 ngày
2. **Chạy `./gradlew spotlessApply` trước MỌI commit**
3. **Khi stuck > 1 giờ → hỏi nhóm** ngay lập tức
4. **Mỗi PR: đọc code diff trước khi approve** — không approve mù
5. **Tuần 6-7: MỌI người đọc MỌI file** — đây là yêu cầu cứng, vi phạm = 0 điểm cả nhóm

---

## Risk & Backup Plan

| Rủi ro | Xác suất | Backup |
|--------|---------|--------|
| WebSocket phức tạp hơn dự kiến | Cao | Tuần 3 backup: polling GET /api/auctions/:id mỗi 2s thay cho WS, sửa WS sau |
| PostgreSQL setup khó trên máy member | Trung bình | Docker: `docker run -d -p 5432:5432 -e POSTGRES_PASSWORD=pass postgres:16` |
| JavaFX build issues (module path) | Trung bình | Thêm VM options trong build.gradle.kts: `--add-modules javafx.controls,javafx.fxml` |
| 1 thành viên chậm tiến độ | Cao | Redistribute trong weekly review, pair programming với người xong sớm |
| Auto-bidding chain loop vô hạn | Thấp | Set limit max 10 auto-bids per trigger, log warning khi đạt limit |
| CI fail trên GitHub Actions | Thấp | D debug CI trên branch riêng, không block team trên main |
| SceneBuilder không hiển thị custom CSS | Thấp | Preview → Scene Style Sheets → re-add file CSS, restart SceneBuilder |
| Spotless format phá Javadoc | Đã xảy ra | Luôn chạy `./gradlew spotlessApply` trước commit, đã setup pre-commit hook |
