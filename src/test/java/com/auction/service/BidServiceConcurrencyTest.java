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
    jdbi = DatabaseConfig.create();

    AuctionDao auctionDao = new AuctionDao(jdbi);
    UserDao userDao = new UserDao(jdbi);
    BidTransactionDao bidTransactionDao = new BidTransactionDao(jdbi);
    AutoBidConfigDao autoBidConfigDao = new AutoBidConfigDao(jdbi);
    ItemDao itemDao = new ItemDao(jdbi);

    AuctionEventManager eventManager = new AuctionEventManager();
    AuctionService auctionService =
        new AuctionService(auctionDao, itemDao, userDao, eventManager, jdbi);
    AutoBidStrategy autoBidStrategy = new AutoBidStrategy(autoBidConfigDao);
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
          handle.execute("TRUNCATE auto_bid_configs, bid_transactions, auctions, items CASCADE");
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
}
