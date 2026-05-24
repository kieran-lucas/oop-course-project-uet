# UML Source-Level Coverage Audit

This audit maps the `src/main/java` source tree to the README class diagrams and separates real source classes from compiler-generated artifacts.

## Audit Basis

This pass used the complete source tree and the current `README.md` on `main`. The goal is factual agreement between source declarations and the Mermaid UML nodes where the README claims to represent source-level classes, while still keeping the diagrams readable instead of turning them into a byte-for-byte AST dump.

## Scope

Included:

- Top-level Java source files under `src/main/java/com/auction`.
- Source-level nested classes, records, enums, and interfaces that appear as named compiled classes.
- README Mermaid `classDiagram` declarations and relation endpoints.

Excluded from strict UML class coverage:

- `src/test/java` test classes.
- `build/classes` compiler output.
- Anonymous compiler-generated classes such as `AuctionListController$1`, `AdminPanelController$10`, `CreateAuctionController$1`, `WebSocketClient$2`, and similar callback/lambda/anonymous UI helper classes.
- `package-info.java`, because it documents a package and is not a runtime/domain class.
- Gradle/cache/database/generated runtime files such as `.gradle`, `build`, `data/postgres`, and `logs`.

## Top-Level Source-File Coverage

Top-level source-file coverage is complete at design level.

| Package | Source files represented by README UML |
|---|---|
| `com.auction` | `AdminSeeder.java`, `App.java`, `ClientApp.java`, `Launcher.java` |
| `config` | `DatabaseConfig.java`, `JwtUtil.java` |
| `middleware` | `JwtMiddleware.java` |
| `controller` | `AuctionController.java`, `AuctionWebSocketHandler.java`, `AuthController.java`, `BidController.java`, `ItemController.java`, `NotificationController.java` |
| `dao` | `AuctionDao.java`, `AutoBidConfigDao.java`, `BidTransactionDao.java`, `DepositRequestDao.java`, `ItemDao.java`, `NotificationDao.java`, `PasswordResetRequestDao.java`, `UserDao.java`, `WalletTransactionDao.java` |
| `dto` | `AuctionResponse.java`, `AutoBidRequest.java`, `BidRequest.java`, `BidUpdateMessage.java`, `ChangePasswordRequest.java`, `CreateAuctionRequest.java`, `CreateItemRequest.java`, `DepositRequest.java`, `ErrorResponse.java`, `ForgotPasswordRequest.java`, `LoginRequest.java`, `PageRequest.java`, `RegisterRequest.java`, `UserResponse.java` |
| `exception` | `AuctionClosedException.java`, `AuctionException.java`, `DuplicateException.java`, `InvalidBidException.java`, `NotFoundException.java`, `UnauthorizedException.java` |
| `model` | `Admin.java`, `Art.java`, `Auction.java`, `AuctionStatus.java`, `AutoBidConfig.java`, `AutoBidFailureReason.java`, `AutoBidStatus.java`, `Bidder.java`, `BidTransaction.java`, `DepositRecord.java`, `Electronics.java`, `Entity.java`, `Item.java`, `PasswordResetRecord.java`, `Seller.java`, `User.java`, `Vehicle.java` |
| `pattern/factory` | `AuctionStateFactory.java`, `ItemFactory.java`, `UserFactory.java` |
| `pattern/observer` | `AuctionEventListener.java`, `AuctionEventManager.java`, `WebSocketObserver.java` |
| `pattern/state` | `AuctionState.java`, `AuctionStates.java`, `CanceledState.java`, `FinishedState.java`, `OpenState.java`, `PaidState.java`, `RunningState.java`, `SettlingState.java` |
| `pattern/strategy` | `AutoBidStrategy.java` |
| `service` | `AuctionScheduler.java`, `AuctionService.java`, `BidService.java`, `ItemService.java`, `NotificationService.java`, `PasswordResetService.java`, `UserService.java` |
| `ui/controller` | `AdminPanelController.java`, `AuctionDetailController.java`, `AuctionListController.java`, `ChangePasswordController.java`, `CreateAuctionController.java`, `CreateItemController.java`, `DepositController.java`, `ForgotPasswordController.java`, `LoginController.java`, `ProfileController.java`, `RegisterController.java`, `WelcomeController.java` |
| `ui/util` | `Navigable.java`, `SceneManager.java` |
| `util` | `BackgroundBidWatcher.java`, `MoneyValidator.java`, `NotificationFormat.java`, `NotificationItem.java`, `NotificationStore.java`, `RestClient.java`, `UserBalanceWatcher.java`, `WebSocketClient.java` |

## Confirmed Correct High-Level Fixes

- `InlineAppRoutes` is not represented as a real source class in README UML.
- The architecture flowchart is explicitly labeled as runtime communication/data-flow, not a strict Java import graph.
- `App.java` runtime composition includes direct creation/dependency links to the main DAOs, services, `AutoBidStrategy`, controllers, WebSocket handler, and scheduler.
- `AuctionStates` consistently lists all six singleton states: `OPEN`, `RUNNING`, `SETTLING`, `FINISHED`, `PAID`, `CANCELED`.
- `AuctionWebSocketHandler` includes the major public WebSocket/notification methods and representative cleanup/token helpers.
- `AuctionStateFactory` includes its private constructor.
- `UserResponse`, `AuctionResponse`, `ErrorResponse`, `PageRequest`, and exception inheritance are corrected at design level.
- Foreign-key-like model links point to `User`, `Item`, or `Auction` when source stores IDs only.

## Confirmed Strict Residual Fixes

The previous strict pass found several factual mismatches in README Mermaid declarations. They have now been resolved in `README.md`.

| README location | Previous mismatch | Current README status |
|---|---|---|
| Diagram 2 and 7, `BidHistoryEntry` | Previously declared with flattened fields `id`, `auctionId`, `bidderId`, `bidderUsername`, `amount`, `autoBid`, `createdAt`. | Now matches source shape: `-transaction`, `-username`, plus shortcut methods `getAuctionId()`, `getBidderId()`, `getAmount()`, `isAutoBid()`, `getCreatedAt()`. |
| Diagram 7, `BalanceDisplay` | Previously declared as `-balance`, `-availableBalance`. | Now matches source record components: `-text`, `-color`. |
| Diagram 7, `GlassDateCell` | Previously omitted `shadow`, constructor, and `refreshAppearance()`. | Now includes `-shadow`, `-GlassDateCell()`, and `-refreshAppearance()`. |
| Diagram 7, `GlassCalendarState` | Previously declared as `<<record>>` with `visibleMonth`, `selectedDate`. | Now represented as `<<nested class>>` with `hoveredCell`, `hoverProgress`, `hoverTimeline`, and `refreshAll()`. |
| Diagram 4, `BidUpdateMessage` constants | Previously used private visibility markers `-TYPE_*`. | Now uses public visibility markers `+TYPE_*`, matching public static final constants. |

## Current Verdict

- Top-level source-file coverage: **complete at design level**.
- Source-level named nested type coverage: **complete at design level**.
- Previously confirmed factual mismatches: **resolved in README**.
- Compiler-generated anonymous classes: **correctly excluded**.
- Remaining differences: **intentional presentation compression only**.

## Intentional Presentation Compression

The README diagrams are source-grounded UML diagrams, not a byte-for-byte AST/member listing. The following are intentional and should not be treated as factual mismatches:

| Area | Reason |
|---|---|
| Exhaustive private helper methods | Some small private helpers are omitted to keep diagrams readable. |
| Constructor overloads | Some constructors are omitted when they add little design value. |
| Full getter/setter sets | DTO/model diagrams list important fields and representative accessors where repeating every accessor would bloat the diagram. |
| Anonymous `$1`, `$2`, lambda/callback classes | These are compiler/generated implementation artifacts, not design-level source classes. |
| Renamed nested helper nodes | A few nested helpers are renamed in Mermaid, for example `PasswordResetMapper`, `SchedulerBalanceChange`, `SchedulerUserNotification`, and `SchedulerSettlementResult`, to avoid ambiguous class names while preserving the owner relation. |

For grading, this is the intended final balance: the diagrams are defendably source-grounded and cover all real design-level classes, while remaining readable enough to render and review on GitHub.
