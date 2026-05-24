# UML Source-Level Coverage Audit

This audit maps the `src/main/java` source tree to the README class diagrams and separates real source classes from compiler-generated artifacts.

## Scope

Included:

- Top-level Java source files under `src/main/java/com/auction`.
- Source-level nested classes, records, enums, and interfaces that appear as named compiled classes.

Excluded from UML class diagrams unless explicitly needed:

- `src/test/java` test classes.
- `build/classes` compiler output.
- Anonymous compiler-generated classes such as `AuctionListController$1`, `AdminPanelController$10`, or `WebSocketClient$2`.
- `package-info.java`, because it documents a package and is not a runtime/domain class.
- Gradle/cache/database/generated runtime files such as `.gradle`, `build`, `data/postgres`, and `logs`.

## Top-Level Source Files

The following top-level source files are represented by the README UML set:

| Package | Source files |
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

## Source-Level Nested Types Still Worth Representing

These are not separate `.java` files, but they are real source-level named types and appear as `.class` files after compilation. They should be included in a strict "all classes" UML version.

| Owner source file | Nested type | Kind | Recommended UML placement |
|---|---|---|---|
| `BidTransactionDao.java` | `BidTransactionMapper` | private static class | DAO diagram, optional if row mappers are shown |
| `BidTransactionDao.java` | `BidHistoryEntry` | public record | DAO/DTO boundary diagram |
| `AuctionDao.java` | `AuctionMapper` | mapper class | DAO diagram, optional |
| `AutoBidConfigDao.java` | `AutoBidConfigMapper` | mapper class | DAO diagram, optional |
| `DepositRequestDao.java` | `DepositRecordMapper` | mapper class | DAO diagram, optional |
| `ItemDao.java` | `ItemMapper` | mapper class | DAO diagram, optional |
| `PasswordResetRequestDao.java` | `Mapper` | mapper class | DAO diagram, optional |
| `UserDao.java` | `UserMapper` | mapper class | DAO diagram, optional |
| `AuctionScheduler.java` | `BalanceChange` | private record | Runtime/scheduler diagram |
| `AuctionScheduler.java` | `UserNotification` | private record | Runtime/scheduler diagram |
| `AuctionScheduler.java` | `SettlementResult` | private record | Runtime/scheduler diagram |
| `AutoBidStrategy.java` | `AutoBidExecutor` | nested interface | Strategy diagram; already represented |
| `AutoBidStrategy.java` | `InTransactionBidExecutor` | nested interface | Strategy diagram; already represented |
| `SceneManager.java` | `ResizeDirection` | private enum | JavaFX/navigation diagram |
| `AuctionListController.java` | `BalanceDisplay` | private record | JavaFX/notification diagram |
| `CreateAuctionController.java` | `GlassDateCell` | private final class | JavaFX/date-picker UI detail diagram |
| `CreateAuctionController.java` | `GlassCalendarState` | private static final class | JavaFX/date-picker UI detail diagram |

## Confirmed README Corrections Already Applied

- `UserResponse` uses `availableBalance`, not `reservedBalance`.
- `AuctionResponse` uses `fromAuction()`, not `from()`.
- `ErrorResponse` uses `error`, `message`, and `timestamp`, not `code`.
- `PageRequest` is a `record` with `page`, `size`, `offset()`, and `of()`.
- Exception inheritance is `AuctionException <|-- ...`; `ErrorResponse` is not directly dependent on exception classes.
- Foreign-key-like model links now point to `User`, `Item`, or `Auction` when the source stores IDs only.

## Remaining README Improvement Needed

For the README class diagrams to be maximally strict, the next patch should add the source-level nested types listed above, especially:

- `BidHistoryEntry`
- `AuctionScheduler.BalanceChange`
- `AuctionScheduler.UserNotification`
- `AuctionScheduler.SettlementResult`
- `SceneManager.ResizeDirection`
- `AuctionListController.BalanceDisplay`
- `CreateAuctionController.GlassDateCell`
- `CreateAuctionController.GlassCalendarState`

The anonymous `$1`, `$2`, `$3` classes produced by Java lambdas/anonymous UI callbacks should not be drawn as design-level UML classes.
