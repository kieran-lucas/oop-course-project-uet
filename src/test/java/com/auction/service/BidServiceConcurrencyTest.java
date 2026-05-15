package com.auction.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.auction.config.DatabaseConfig;
import com.auction.dao.AuctionDao;
import com.auction.dao.AutoBidConfigDao;
import com.auction.dao.BidTransactionDao;
import com.auction.dao.ItemDao;
import com.auction.dao.UserDao;
import com.auction.exception.AuctionClosedException;
import com.auction.exception.InvalidBidException;
import com.auction.model.Auction;
import com.auction.model.User;
import com.auction.pattern.observer.AuctionEventManager;
import com.auction.pattern.strategy.AutoBidStrategy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
class BidServiceConcurrencyTest {

  private Jdbi jdbi;
  private BidService bidService;

  @BeforeAll
  void setup() {
    try {
      jdbi = DatabaseConfig.create();
    } catch (Exception e) {
      Assumptions.abort("No DB available, skipping: " + e.getMessage());
    }

    AuctionDao auctionDao = new AuctionDao(jdbi);
    UserDao userDao = new UserDao(jdbi);
    BidTransactionDao bidTransactionDao = new BidTransactionDao(jdbi);
    AutoBidConfigDao autoBidConfigDao = new AutoBidConfigDao(jdbi);
    ItemDao itemDao = new ItemDao(jdbi);

    AuctionEventManager eventManager = new AuctionEventManager();
    AuctionService auctionService =
        new AuctionService(auctionDao, itemDao, userDao, eventManager, jdbi, bidTransactionDao);
    AutoBidStrategy autoBidStrategy = new AutoBidStrategy(autoBidConfigDao, userDao);
    bidService =
        new BidService(
            auctionDao,
            bidTransactionDao,
            autoBidConfigDao,
            eventManager,
            jdbi,
            auctionService,
            userDao,
            autoBidStrategy);
  }

  @BeforeEach
  void seedData() {
    jdbi.useHandle(
        handle -> {
          handle.execute(
              "TRUNCATE wallet_transactions, notifications, auto_bid_configs, bid_transactions,"
                  + " auctions, items CASCADE");
          handle.execute("TRUNCATE users RESTART IDENTITY CASCADE");

          handle.execute(
              """
INSERT INTO users (id, username, password_hash, email, role, created_at, balance)
VALUES (1, 'seller', 'hash', 'seller@test.com', 'SELLER', NOW(), 0)
""");

          for (long id = 2; id <= 11; id++) {
            handle
                .createUpdate(
                    """
INSERT INTO users (id, username, password_hash, email, role, created_at, balance)
VALUES (:id, :username, 'hash', :email, 'BIDDER', NOW(), 100000000)
""")
                .bind("id", id)
                .bind("username", "bidder" + id)
                .bind("email", "bidder" + id + "@test.com")
                .execute();
          }

          handle.execute(
              """
INSERT INTO items (id, seller_id, name, description, category, artist, created_at, updated_at)
VALUES (1, 1, 'Test Artwork', 'Concurrency test item', 'ART', 'Test Artist', NOW(), NOW())
""");

          handle.execute(
              """
INSERT INTO auctions (
    id, item_id, starting_price, current_price, leading_bidder_id, seller_id,
    start_time, end_time, status, created_at, updated_at
)
VALUES (
    1, 1, 1000000, 1000000, NULL, 1,
    NOW() - INTERVAL '1 hour', NOW() + INTERVAL '1 hour', 'RUNNING', NOW(), NOW()
)
""");
        });
  }

  @Test
  @SuppressWarnings("checkstyle:MethodName")
  void concurrentBids_onlyOneWins() throws Exception {
    int threadCount = 10;
    ExecutorService pool = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startGate = new CountDownLatch(1);
    List<Future<Boolean>> futures = new ArrayList<>();

    for (int i = 0; i < threadCount; i++) {
      final long bidderId = 2L + i;
      futures.add(
          pool.submit(
              () -> {
                startGate.await();
                try {
                  bidService.placeBid(1L, bidderId, new BigDecimal("2000000"), false);
                  return true;
                } catch (InvalidBidException | AuctionClosedException e) {
                  return false;
                }
              }));
    }

    startGate.countDown();
    pool.shutdown();
    assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS));

    long successCount =
        futures.stream()
            .mapToLong(
                future -> {
                  try {
                    return future.get() ? 1L : 0L;
                  } catch (Exception e) {
                    return 0L;
                  }
                })
            .sum();

    assertEquals(1L, successCount);

    long transactionCount =
        jdbi.withHandle(
            handle ->
                handle
                    .createQuery("SELECT COUNT(*) FROM bid_transactions WHERE auction_id = 1")
                    .mapTo(Long.class)
                    .one());
    assertEquals(1L, transactionCount);
  }

  @Test
  @SuppressWarnings("checkstyle:MethodName")
  void concurrentEscalatingBids_finalHighestBidWinsAndLosersAreReleased() throws Exception {
    List<BidAttempt> attempts =
        List.of(
            new BidAttempt(1L, 2L, new BigDecimal("1200000")),
            new BidAttempt(1L, 3L, new BigDecimal("1400000")),
            new BidAttempt(1L, 4L, new BigDecimal("1600000")),
            new BidAttempt(1L, 5L, new BigDecimal("1800000")),
            new BidAttempt(1L, 6L, new BigDecimal("2000000")));

    long successCount = runConcurrentBids(attempts);

    Auction auction = findAuction(1L);
    assertEquals(6L, auction.getLeadingBidderId());
    assertMoney("2000000", auction.getCurrentPrice());
    assertEquals(successCount, bidCount(1L));
    assertTrue(successCount >= 1);

    assertMoney("2000000", findUser(6L).getReservedBalance());
    for (long loserId = 2L; loserId <= 5L; loserId++) {
      assertMoney("0", findUser(loserId).getReservedBalance());
      assertNonNegativeBalances(loserId);
    }
    assertNonNegativeBalances(6L);
  }

  @Test
  @SuppressWarnings("checkstyle:MethodName")
  void concurrentBidsBySameBidderOnDifferentAuctions_doNotOvercommitBalance() throws Exception {
    setBalance(2L, new BigDecimal("500000"));
    long secondAuctionId = insertRunningAuction("Second Artwork", new BigDecimal("100000"));

    long successCount =
        runConcurrentBids(
            List.of(
                new BidAttempt(1L, 2L, new BigDecimal("400000")),
                new BidAttempt(secondAuctionId, 2L, new BigDecimal("400000"))));

    User bidder = findUser(2L);
    assertEquals(1L, successCount);
    assertEquals(1L, bidCountForBidder(2L));
    assertMoney("500000", bidder.getBalance());
    assertMoney("400000", bidder.getReservedBalance());
    assertMoney("100000", bidder.getAvailableBalance());
    assertNonNegativeBalances(2L);
  }

  @Test
  @SuppressWarnings("checkstyle:MethodName")
  void concurrentOutbids_releasePreviousReservationsAndFreezeFinalWinner() throws Exception {
    bidService.placeBid(1L, 2L, new BigDecimal("1200000"), false);

    long successCount =
        runConcurrentBids(
            List.of(
                new BidAttempt(1L, 3L, new BigDecimal("1400000")),
                new BidAttempt(1L, 4L, new BigDecimal("1600000"))));

    Auction auction = findAuction(1L);
    assertEquals(4L, auction.getLeadingBidderId());
    assertMoney("1600000", auction.getCurrentPrice());
    assertEquals(1L + successCount, bidCount(1L));

    assertMoney("0", findUser(2L).getReservedBalance());
    assertMoney("0", findUser(3L).getReservedBalance());
    assertMoney("1600000", findUser(4L).getReservedBalance());
    assertNonNegativeBalances(2L);
    assertNonNegativeBalances(3L);
    assertNonNegativeBalances(4L);
  }

  @Test
  @SuppressWarnings("checkstyle:MethodName")
  void manualBidTriggersAutoBidChain_freezeReleaseBalancesRemainConsistent() {
    insertAutoBidConfig(1L, 3L, new BigDecimal("700000"), new BigDecimal("100000"), 1);
    insertAutoBidConfig(1L, 4L, new BigDecimal("800000"), new BigDecimal("100000"), 2);
    setAuctionPrice(1L, new BigDecimal("100000"));

    bidService.placeBid(1L, 2L, new BigDecimal("200000"), false);

    Auction auction = findAuction(1L);
    assertEquals(4L, auction.getLeadingBidderId());
    assertMoney("800000", auction.getCurrentPrice());
    assertEquals(7L, bidCount(1L));

    assertMoney("0", findUser(2L).getReservedBalance());
    assertMoney("0", findUser(3L).getReservedBalance());
    assertMoney("800000", findUser(4L).getReservedBalance());
    assertNonNegativeBalances(2L);
    assertNonNegativeBalances(3L);
    assertNonNegativeBalances(4L);
  }

  private long runConcurrentBids(List<BidAttempt> attempts) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(attempts.size());
    CountDownLatch startGate = new CountDownLatch(1);
    List<Future<Boolean>> futures = new ArrayList<>();

    for (BidAttempt attempt : attempts) {
      futures.add(
          pool.submit(
              () -> {
                startGate.await();
                try {
                  bidService.placeBid(
                      attempt.auctionId(), attempt.bidderId(), attempt.amount(), false);
                  return true;
                } catch (InvalidBidException | AuctionClosedException | IllegalStateException e) {
                  return false;
                }
              }));
    }

    startGate.countDown();
    pool.shutdown();
    assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS));

    long successCount = 0;
    for (Future<Boolean> future : futures) {
      if (future.get()) {
        successCount++;
      }
    }
    return successCount;
  }

  private Auction findAuction(Long auctionId) {
    return new AuctionDao(jdbi).findById(auctionId).orElseThrow();
  }

  private User findUser(Long userId) {
    return new UserDao(jdbi).findById(userId).orElseThrow();
  }

  private long bidCount(Long auctionId) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery("SELECT COUNT(*) FROM bid_transactions WHERE auction_id = :auctionId")
                .bind("auctionId", auctionId)
                .mapTo(Long.class)
                .one());
  }

  private long bidCountForBidder(Long bidderId) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery("SELECT COUNT(*) FROM bid_transactions WHERE bidder_id = :bidderId")
                .bind("bidderId", bidderId)
                .mapTo(Long.class)
                .one());
  }

  private long insertRunningAuction(String itemName, BigDecimal startingPrice) {
    return jdbi.withHandle(
        handle -> {
          long itemId =
              handle
                  .createQuery(
                      """
                      INSERT INTO items (
                          id, seller_id, name, description, category, artist, created_at, updated_at
                      )
                      VALUES (
                          (SELECT COALESCE(MAX(id), 0) + 1 FROM items),
                          1, :name, 'Concurrency test item', 'ART', 'Test Artist', NOW(), NOW()
                      )
                      RETURNING id
                      """)
                  .bind("name", itemName)
                  .mapTo(Long.class)
                  .one();

          return handle
              .createQuery(
                  """
                  INSERT INTO auctions (
                      id, item_id, starting_price, current_price, leading_bidder_id, seller_id,
                      start_time, end_time, status, created_at, updated_at
                  )
                  VALUES (
                      (SELECT COALESCE(MAX(id), 0) + 1 FROM auctions),
                      :itemId, :price, :price, NULL, 1,
                      NOW() - INTERVAL '1 hour', NOW() + INTERVAL '1 hour',
                      'RUNNING', NOW(), NOW()
                  )
                  RETURNING id
                  """)
              .bind("itemId", itemId)
              .bind("price", startingPrice)
              .mapTo(Long.class)
              .one();
        });
  }

  private void setBalance(Long userId, BigDecimal balance) {
    jdbi.useHandle(
        handle ->
            handle
                .createUpdate(
                    "UPDATE users SET balance = :balance, reserved_balance = 0 WHERE id = :id")
                .bind("balance", balance)
                .bind("id", userId)
                .execute());
  }

  private void setAuctionPrice(Long auctionId, BigDecimal price) {
    jdbi.useHandle(
        handle ->
            handle
                .createUpdate(
                    """
                    UPDATE auctions
                    SET starting_price = :price, current_price = :price, leading_bidder_id = NULL
                    WHERE id = :id
                    """)
                .bind("price", price)
                .bind("id", auctionId)
                .execute());
  }

  private void insertAutoBidConfig(
      Long auctionId, Long bidderId, BigDecimal maxBid, BigDecimal increment, int orderSeconds) {
    jdbi.useHandle(
        handle ->
            handle
                .createUpdate(
                    """
                    INSERT INTO auto_bid_configs (
                        auction_id, bidder_id, max_bid, increment_amount,
                        active, status, registered_at
                    )
                    VALUES (
                        :auctionId, :bidderId, :maxBid, :increment,
                        true, 'ACTIVE', NOW() + (:orderSeconds * INTERVAL '1 second')
                    )
                    """)
                .bind("auctionId", auctionId)
                .bind("bidderId", bidderId)
                .bind("maxBid", maxBid)
                .bind("increment", increment)
                .bind("orderSeconds", orderSeconds)
                .execute());
  }

  private void assertMoney(String expected, BigDecimal actual) {
    assertEquals(0, new BigDecimal(expected).compareTo(actual));
  }

  private void assertNonNegativeBalances(Long userId) {
    User user = findUser(userId);
    assertTrue(user.getBalance().signum() >= 0, "balance must not be negative for user " + userId);
    assertTrue(
        user.getReservedBalance().signum() >= 0,
        "reserved balance must not be negative for user " + userId);
    assertTrue(
        user.getAvailableBalance().signum() >= 0,
        "available balance must not be negative for user " + userId);
  }

  private record BidAttempt(Long auctionId, Long bidderId, BigDecimal amount) {}
}
