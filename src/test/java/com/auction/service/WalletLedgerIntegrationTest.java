package com.auction.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.auction.config.DatabaseConfig;
import com.auction.dao.AuctionDao;
import com.auction.dao.AutoBidConfigDao;
import com.auction.dao.BidTransactionDao;
import com.auction.dao.DepositRequestDao;
import com.auction.dao.ItemDao;
import com.auction.dao.UserDao;
import com.auction.model.Art;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.Bidder;
import com.auction.model.Item;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.pattern.observer.AuctionEventManager;
import com.auction.pattern.strategy.AutoBidStrategy;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration test kiểm tra tính toàn vẹn của sổ cái giao dịch ví ({@code wallet_transactions}).
 *
 * <p>Xác nhận rằng mỗi sự kiện tài chính — nạp tiền, đặt giá (freeze), bị vượt giá (release), hoàn
 * tất đấu giá (win_consume + seller_payout) — tạo ra đúng số lượng bản ghi ledger với đúng loại
 * ({@code kind}) và số tiền ({@code amount}). Nếu transaction rollback, không có bản ghi orphan nào
 * được tạo ra.
 *
 * <p><b>Điều kiện tiên quyết:</b> PostgreSQL phải đang chạy; bị bỏ qua (ABORTED) nếu không có DB.
 */
@DisplayName("Sổ cái giao dịch ví — FREEZE / RELEASE / WIN_CONSUME / SELLER_PAYOUT")
class WalletLedgerIntegrationTest {

  private static Jdbi jdbi;

  private UserDao userDao;
  private ItemDao itemDao;
  private AuctionDao auctionDao;
  private UserService userService;
  private BidService bidService;
  private AuctionScheduler scheduler;

  private User seller;
  private User bidderA;
  private User bidderB;

  @BeforeAll
  static void connectDb() {
    try {
      jdbi = DatabaseConfig.create();
    } catch (Exception e) {
      Assumptions.abort("No DB available, skipping: " + e.getMessage());
    }
  }

  @BeforeEach
  void setUp() {
    jdbi.useHandle(
        handle ->
            handle.execute(
                """
                TRUNCATE wallet_transactions, notifications, deposit_requests,
                         bid_transactions, auto_bid_configs, auctions, items, users
                RESTART IDENTITY CASCADE
                """));

    userDao = new UserDao(jdbi);
    itemDao = new ItemDao(jdbi);
    auctionDao = new AuctionDao(jdbi);
    DepositRequestDao depositRequestDao = new DepositRequestDao(jdbi);
    BidTransactionDao bidTransactionDao = new BidTransactionDao(jdbi);
    AutoBidConfigDao autoBidConfigDao = new AutoBidConfigDao(jdbi);
    AuctionEventManager eventManager = new AuctionEventManager();
    AuctionService auctionService =
        new AuctionService(auctionDao, itemDao, userDao, eventManager, jdbi, bidTransactionDao);
    AutoBidStrategy autoBidStrategy = new AutoBidStrategy(autoBidConfigDao, userDao);

    userService = new UserService(userDao, depositRequestDao, jdbi);
    var wsHandler = new com.auction.controller.AuctionWebSocketHandler(eventManager, jdbi);
    bidService =
        new BidService(
            auctionDao,
            bidTransactionDao,
            autoBidConfigDao,
            eventManager,
            jdbi,
            auctionService,
            userDao,
            autoBidStrategy,
            wsHandler);
    scheduler = new AuctionScheduler(auctionDao, userDao, itemDao, eventManager, jdbi, wsHandler);

    seller = userDao.insert(new Seller("ledger_seller", "hash", "seller-ledger@test.com"));
    bidderA = createBidder("ledger_bidder_a", new BigDecimal("1000000"));
    bidderB = createBidder("ledger_bidder_b", new BigDecimal("1000000"));
  }

  @Test
  @DisplayName("Duyệt nạp tiền → tạo bản ghi DEPOSIT trong sổ cái")
  void depositApprovalCreatesLedger() {
    var request = userService.requestDeposit(bidderA.getId(), new BigDecimal("250000"));

    userService.approveDeposit(request.getId());

    assertEquals(1L, ledgerCount(bidderA.getId(), null, "DEPOSIT"));
    assertEquals(0, new BigDecimal("250000").compareTo(ledgerAmount(bidderA.getId(), "DEPOSIT")));
  }

  @Test
  @DisplayName("Đặt giá → FREEZE; bị vượt giá → RELEASE — cả hai tạo bản ghi sổ cái")
  void bidFreezeAndOutbidReleaseCreateLedger() {
    Auction auction = createRunningAuction("Bid ledger item");

    bidService.placeBid(auction.getId(), bidderA.getId(), new BigDecimal("200000"), false);
    bidService.placeBid(auction.getId(), bidderB.getId(), new BigDecimal("300000"), false);

    assertEquals(1L, ledgerCount(bidderA.getId(), auction.getId(), "FREEZE"));
    assertEquals(1L, ledgerCount(bidderA.getId(), auction.getId(), "RELEASE"));
    assertEquals(1L, ledgerCount(bidderB.getId(), auction.getId(), "FREEZE"));
  }

  @Test
  @DisplayName("Transaction bid thất bại (rollback) → không để lại bản ghi orphan trong sổ cái")
  void failedBidRollbackLeavesNoLedgerRows() {
    Auction auction = createRunningAuction("Rollback ledger item");

    bidService.placeBid(auction.getId(), bidderA.getId(), new BigDecimal("200000"), false);

    jdbi.useHandle(
        handle ->
            handle
                .createUpdate("UPDATE users SET reserved_balance = 0 WHERE id = :id")
                .bind("id", bidderA.getId())
                .execute());

    assertThrows(
        IllegalStateException.class,
        () ->
            bidService.placeBid(auction.getId(), bidderB.getId(), new BigDecimal("300000"), false));

    assertEquals(1L, ledgerCount(bidderA.getId(), auction.getId(), "FREEZE"));
    assertEquals(0L, ledgerCount(bidderA.getId(), auction.getId(), "RELEASE"));
    assertEquals(0L, ledgerCount(bidderB.getId(), auction.getId(), "FREEZE"));
  }

  @Test
  @DisplayName(
      "Hoàn tất đấu giá → tạo bản ghi WIN_CONSUME (người thắng) và SELLER_PAYOUT (người bán)")
  void settlementCreatesWinnerAndSellerLedgerRows() throws Exception {
    Auction auction = createRunningAuction("Settlement ledger item");
    bidService.placeBid(auction.getId(), bidderA.getId(), new BigDecimal("200000"), false);

    forceAuctionExpired(auction.getId());
    invokeSettleAndClose(auction.getId());

    assertEquals(1L, ledgerCount(bidderA.getId(), auction.getId(), "WIN_CONSUME"));
    assertEquals(1L, ledgerCount(seller.getId(), auction.getId(), "SELLER_PAYOUT"));
  }

  private User createBidder(String username, BigDecimal balance) {
    Bidder bidder = new Bidder(username, "hash", username + "@test.com");
    bidder.setBalance(balance);
    return userDao.insert(bidder);
  }

  private Auction createRunningAuction(String itemName) {
    Item item = itemDao.insert(new Art(itemName, "ledger test item", seller.getId(), "Artist"));
    Auction auction =
        new Auction(
            item.getId(),
            new BigDecimal("100000"),
            LocalDateTime.now().minusHours(1),
            LocalDateTime.now().plusHours(1));
    auction.setSellerId(seller.getId());
    auction.setStatus(AuctionStatus.RUNNING);
    return auctionDao.insert(auction);
  }

  private void forceAuctionExpired(Long auctionId) {
    jdbi.useHandle(
        handle ->
            handle
                .createUpdate(
                    "UPDATE auctions SET end_time = NOW() - INTERVAL '1 second' WHERE id = :id")
                .bind("id", auctionId)
                .execute());
  }

  private void invokeSettleAndClose(Long auctionId) throws Exception {
    Method settleAndClose =
        AuctionScheduler.class.getDeclaredMethod("settleAndClose", Long.class, LocalDateTime.class);
    settleAndClose.setAccessible(true);
    settleAndClose.invoke(scheduler, auctionId, LocalDateTime.now());
  }

  private long ledgerCount(Long userId, Long auctionId, String kind) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(
                    """
                    SELECT COUNT(*)
                    FROM wallet_transactions
                    WHERE user_id = :userId
                      AND kind = :kind
                      AND (:auctionId IS NULL OR auction_id = :auctionId)
                    """)
                .bind("userId", userId)
                .bind("kind", kind)
                .bind("auctionId", auctionId)
                .mapTo(Long.class)
                .one());
  }

  private BigDecimal ledgerAmount(Long userId, String kind) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(
                    """
                    SELECT amount
                    FROM wallet_transactions
                    WHERE user_id = :userId AND kind = :kind
                    ORDER BY id DESC
                    LIMIT 1
                    """)
                .bind("userId", userId)
                .bind("kind", kind)
                .mapTo(BigDecimal.class)
                .one());
  }
}
