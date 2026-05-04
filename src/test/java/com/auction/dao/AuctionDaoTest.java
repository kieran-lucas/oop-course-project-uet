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

class AuctionDaoTest {

  private static Jdbi jdbi;
  private static UserDao userDao;
  private static ItemDao itemDao;
  private static AuctionDao auctionDao;

  private User testSeller;
  private User testBidder;
  private Item testItem;

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
  }

  @BeforeEach
  void init() {
    // 1. Dọn dẹp toàn bộ DB và Reset ID về 1
    jdbi.useHandle(
        handle -> {
          handle.execute("TRUNCATE TABLE auto_bid_configs CASCADE");
          handle.execute("TRUNCATE TABLE bid_transactions CASCADE");
          handle.execute("TRUNCATE TABLE auctions CASCADE");
          handle.execute("TRUNCATE TABLE items CASCADE");
          handle.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
        });

    // 2. Khởi tạo dữ liệu mẫu cho mỗi test case (ID sẽ luôn cố định: Seller=1, Bidder=2, Item=1)
    testSeller = userDao.insert(new Seller("auction_seller", "hash", "seller@test.com"));
    testBidder = userDao.insert(new Bidder("auction_bidder", "hash", "bidder@test.com"));
    testItem =
        itemDao.insert(new Electronics("Auction Item", "For auction", testSeller.getId(), "Brand"));

    System.out.println(
        "DB Reset. Test IDs -> Seller: " + testSeller.getId() + ", Item: " + testItem.getId());
  }

  @Test
  @DisplayName("Insert should create new auction")
  void testInsert() {
    Auction auction =
        new Auction(
            testItem.getId(),
            new BigDecimal("100000"),
            LocalDateTime.now(),
            LocalDateTime.now().plusHours(24));

    Auction saved = auctionDao.insert(auction);

    assertNotNull(saved.getId());
    assertEquals("OPEN", saved.getStatus());
    assertEquals(0, new BigDecimal("100000").compareTo(saved.getCurrentPrice()));
    assertNull(saved.getLeadingBidderId());
  }

  @Test
  @DisplayName("FindById should return auction")
  void testFindById() {
    Auction auction =
        new Auction(
            testItem.getId(),
            new BigDecimal("200000"),
            LocalDateTime.now(),
            LocalDateTime.now().plusHours(24));
    Auction saved = auctionDao.insert(auction);

    Optional<Auction> found = auctionDao.findById(saved.getId());

    assertTrue(found.isPresent());
    assertEquals(saved.getId(), found.get().getId());
    assertEquals(0, new BigDecimal("200000").compareTo(found.get().getStartingPrice()));
  }

  @Test
  @DisplayName("FindAll should return all auctions")
  void testFindAll() {
    auctionDao.insert(
        new Auction(
            testItem.getId(),
            new BigDecimal("100"),
            LocalDateTime.now(),
            LocalDateTime.now().plusHours(1)));
    auctionDao.insert(
        new Auction(
            testItem.getId(),
            new BigDecimal("200"),
            LocalDateTime.now(),
            LocalDateTime.now().plusHours(2)));

    List<Auction> auctions = auctionDao.findAll();

    assertEquals(2, auctions.size());
  }

  @Test
  @DisplayName("FindByStatus should filter by status")
  void testFindByStatus() {
    Auction open =
        new Auction(
            testItem.getId(),
            new BigDecimal("100"),
            LocalDateTime.now(),
            LocalDateTime.now().plusHours(1));
    auctionDao.insert(open);

    List<Auction> openAuctions = auctionDao.findByStatus("OPEN");

    assertFalse(openAuctions.isEmpty());
    assertTrue(openAuctions.stream().allMatch(a -> "OPEN".equals(a.getStatus())));
  }

  @Test
  @DisplayName("FindByItemId should return auctions for specific item")
  void testFindByItemId() {
    auctionDao.insert(
        new Auction(
            testItem.getId(),
            new BigDecimal("100"),
            LocalDateTime.now(),
            LocalDateTime.now().plusHours(1)));

    List<Auction> auctions = auctionDao.findByItemId(testItem.getId());

    assertFalse(auctions.isEmpty());
    assertEquals(testItem.getId(), auctions.get(0).getItemId());
  }

  @Test
  @DisplayName("Update should modify auction")
  void testUpdate() {
    Auction auction =
        new Auction(
            testItem.getId(),
            new BigDecimal("100000"),
            LocalDateTime.now(),
            LocalDateTime.now().plusHours(24));
    Auction saved = auctionDao.insert(auction);

    saved.setCurrentPrice(new BigDecimal("150000"));
    saved.setLeadingBidderId(testBidder.getId());
    saved.setStatus("RUNNING");

    auctionDao.update(saved);

    Optional<Auction> found = auctionDao.findById(saved.getId());
    assertTrue(found.isPresent());
    assertEquals(0, new BigDecimal("150000").compareTo(found.get().getCurrentPrice()));
    assertEquals(testBidder.getId(), found.get().getLeadingBidderId());
    assertEquals("RUNNING", found.get().getStatus());
  }

  @Test
  @DisplayName("Delete should remove auction")
  void testDelete() {
    Auction auction =
        new Auction(
            testItem.getId(),
            new BigDecimal("100000"),
            LocalDateTime.now(),
            LocalDateTime.now().plusHours(24));
    Auction saved = auctionDao.insert(auction);

    auctionDao.delete(saved.getId());

    assertFalse(auctionDao.findById(saved.getId()).isPresent());
  }

  @Test
  @DisplayName("StartScheduledAuctions should update status from OPEN to RUNNING")
  void testStartScheduledAuctions() {
    Auction auction =
        new Auction(
            testItem.getId(),
            new BigDecimal("100000"),
            LocalDateTime.now().minusMinutes(5), // Bắt đầu từ 5 phút trước
            LocalDateTime.now().plusHours(1));
    auctionDao.insert(auction);

    int started = auctionDao.startScheduledAuctions();
    assertTrue(started >= 1);

    List<Auction> running = auctionDao.findByStatus("RUNNING");
    assertTrue(running.stream().anyMatch(a -> a.getItemId().equals(testItem.getId())));
  }

  @Test
  @DisplayName("CloseExpiredAuctions should update status from RUNNING to FINISHED")
  void testCloseExpiredAuctions() {
    Auction auction =
        new Auction(
            testItem.getId(),
            new BigDecimal("100000"),
            LocalDateTime.now().minusHours(2),
            LocalDateTime.now().minusMinutes(1) // Đã kết thúc 1 phút trước
            );
    Auction saved = auctionDao.insert(auction);
    saved.setStatus("RUNNING");
    auctionDao.update(saved); // Ép status sang RUNNING để test hàm close

    int closed = auctionDao.closeExpiredAuctions();
    assertTrue(closed >= 1);

    List<Auction> finished = auctionDao.findByStatus("FINISHED");
    assertTrue(finished.stream().anyMatch(a -> a.getItemId().equals(testItem.getId())));
  }

  @Test
  @DisplayName("ExistsById should return true for existing auction")
  void testExistsById() {
    Auction auction =
        new Auction(
            testItem.getId(),
            new BigDecimal("100000"),
            LocalDateTime.now(),
            LocalDateTime.now().plusHours(24));
    Auction saved = auctionDao.insert(auction);

    assertTrue(auctionDao.existsById(saved.getId()));
    assertFalse(auctionDao.existsById(999L));
  }

  @Test
  @DisplayName("GetCurrentPrice should return correct price")
  void testGetCurrentPrice() {
    Auction auction =
        new Auction(
            testItem.getId(),
            new BigDecimal("500000"),
            LocalDateTime.now(),
            LocalDateTime.now().plusHours(24));
    Auction saved = auctionDao.insert(auction);

    Optional<BigDecimal> price = auctionDao.getCurrentPrice(saved.getId());

    assertTrue(price.isPresent());
    assertEquals(0, new BigDecimal("500000").compareTo(price.get()));
  }
}
