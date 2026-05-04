package com.auction.dao;

import static org.junit.jupiter.api.Assertions.*;

import com.auction.config.DatabaseConfig;
import com.auction.model.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Assumptions;

class BidTransactionDaoTest {

  private static Jdbi jdbi;
  private static UserDao userDao;
  private static ItemDao itemDao;
  private static AuctionDao auctionDao;
  private static BidTransactionDao bidDao;

  private User testSeller;
  private User testBidder;
  private Item testItem;
  private Auction testAuction;

  @BeforeAll
  static void setup() {
    try {
      jdbi = DatabaseConfig.create();
    } catch (Exception e) {
      Assumptions.abort("No DB available, skipping: " + e.getMessage());
    }
    userDao = new UserDao(jdbi);
    itemDao = new ItemDao(jdbi);
    auctionDao = new AuctionDao(jdbi);
    bidDao = new BidTransactionDao(jdbi);
  }

  @BeforeEach
  void init() {
    // 1. Dọn dẹp DB và Reset Identity
    jdbi.useHandle(
        handle -> {
          handle.execute("TRUNCATE TABLE auto_bid_configs CASCADE");
          handle.execute("TRUNCATE TABLE bid_transactions CASCADE");
          handle.execute("TRUNCATE TABLE auctions CASCADE");
          handle.execute("TRUNCATE TABLE items CASCADE");
          handle.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
        });

    // 2. Khởi tạo dữ liệu mẫu (ID dự kiến: Seller=1, Bidder=2, Item=1, Auction=1)
    testSeller = userDao.insert(new Seller("bid_seller", "hash", "seller@test.com"));
    testBidder = userDao.insert(new Bidder("bid_bidder", "hash", "bidder@test.com"));

    testItem = itemDao.insert(new Electronics("Bid Item", "Test", testSeller.getId(), "Brand"));

    testAuction =
        auctionDao.insert(
            new Auction(
                testItem.getId(),
                new BigDecimal("100000"),
                LocalDateTime.now(),
                LocalDateTime.now().plusHours(24)));

    System.out.println(
        "Cleaned DB & Initialized IDs -> Auction: "
            + testAuction.getId()
            + ", Bidder: "
            + testBidder.getId());
  }

  @Test
  @DisplayName("Insert should create bid transaction")
  void testInsert() {
    BidTransaction tx =
        new BidTransaction(
            testAuction.getId(), testBidder.getId(), new BigDecimal("150000"), false);

    bidDao.insert(tx);

    assertNotNull(tx.getId());
    assertEquals(testAuction.getId(), tx.getAuctionId());
    assertEquals(testBidder.getId(), tx.getBidderId());
    assertEquals(0, new BigDecimal("150000").compareTo(tx.getAmount()));
    assertFalse(tx.isAutoBid());
    assertNotNull(tx.getCreatedAt());
  }

  @Test
  @DisplayName("FindByAuctionId should return all bids for an auction")
  void testFindByAuctionId() {
    bidDao.insert(
        new BidTransaction(
            testAuction.getId(), testBidder.getId(), new BigDecimal("150000"), false));
    bidDao.insert(
        new BidTransaction(
            testAuction.getId(), testBidder.getId(), new BigDecimal("200000"), true));

    List<BidTransaction> bids = bidDao.findByAuctionId(testAuction.getId());

    assertEquals(2, bids.size());
    // Kiểm tra sắp xếp (thường là theo ID hoặc thời gian)
    assertTrue(bids.get(0).getAmount().compareTo(bids.get(1).getAmount()) < 0);
  }

  @Test
  @DisplayName("FindByBidderId should return all bids by a bidder")
  void testFindByBidderId() {
    bidDao.insert(
        new BidTransaction(
            testAuction.getId(), testBidder.getId(), new BigDecimal("150000"), false));
    bidDao.insert(
        new BidTransaction(
            testAuction.getId(), testBidder.getId(), new BigDecimal("200000"), false));

    List<BidTransaction> bids = bidDao.findByBidderId(testBidder.getId());

    assertEquals(2, bids.size());
    assertTrue(bids.stream().allMatch(b -> b.getBidderId().equals(testBidder.getId())));
  }

  @Test
  @DisplayName("FindLastBid should return the most recent bid")
  void testFindLastBid() {
    bidDao.insert(
        new BidTransaction(
            testAuction.getId(), testBidder.getId(), new BigDecimal("150000"), false));

    // Tạo bid mới với giá cao hơn/thời gian sau
    BidTransaction last =
        new BidTransaction(testAuction.getId(), testBidder.getId(), new BigDecimal("250000"), false);
    bidDao.insert(last);

    Optional<BidTransaction> found = bidDao.findLastBid(testAuction.getId());

    assertTrue(found.isPresent());
    assertEquals(last.getId(), found.get().getId());
    assertEquals(0, new BigDecimal("250000").compareTo(found.get().getAmount()));
  }

  @Test
  @DisplayName("CountByAuctionId should return correct count")
  void testCountByAuctionId() {
    bidDao.insert(
        new BidTransaction(
            testAuction.getId(), testBidder.getId(), new BigDecimal("150000"), false));
    bidDao.insert(
        new BidTransaction(
            testAuction.getId(), testBidder.getId(), new BigDecimal("200000"), false));

    int count = bidDao.countByAuctionId(testAuction.getId());
    assertEquals(2, count);
  }

  @Test
  @DisplayName("GetHighestPrice should return max bid amount")
  void testGetHighestPrice() {
    bidDao.insert(
        new BidTransaction(
            testAuction.getId(), testBidder.getId(), new BigDecimal("150000"), false));
    bidDao.insert(
        new BidTransaction(
            testAuction.getId(), testBidder.getId(), new BigDecimal("350000"), false));
    bidDao.insert(
        new BidTransaction(
            testAuction.getId(), testBidder.getId(), new BigDecimal("250000"), false));

    Optional<BigDecimal> highest = bidDao.getHighestPrice(testAuction.getId());

    assertTrue(highest.isPresent());
    assertEquals(0, new BigDecimal("350000").compareTo(highest.get()));
  }

  @Test
  @DisplayName("Insert with autoBid flag should work")
  void testInsertAutoBid() {
    BidTransaction tx =
        new BidTransaction(testAuction.getId(), testBidder.getId(), new BigDecimal("180000"), true);

    bidDao.insert(tx);

    assertTrue(tx.isAutoBid());

    Optional<BidTransaction> last = bidDao.findLastBid(testAuction.getId());
    assertTrue(last.isPresent());
    assertTrue(last.get().isAutoBid());
  }
}
