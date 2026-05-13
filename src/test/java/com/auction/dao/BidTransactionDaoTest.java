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

/**
 * Test suite kiểm tra toàn bộ các thao tác của {@link BidTransactionDao} — lớp DAO quản lý lịch sử
 * lượt đặt giá trên bảng {@code bid_transactions}.
 *
 * <p><b>Phạm vi kiểm tra:</b>
 *
 * <ul>
 *   <li>Insert lượt đặt giá thường và lượt đặt giá tự động ({@code autoBid = true}).
 *   <li>Truy vấn lọc: findByAuctionId, findByBidderId.
 *   <li>Truy vấn nghiệp vụ: findLastBid (lượt đặt gần nhất), getHighestPrice (giá cao nhất),
 *       countByAuctionId (tổng số lượt).
 * </ul>
 *
 * <p><b>Chiến lược dữ liệu:</b> Mỗi test chạy trong trạng thái DB sạch. {@code init()} TRUNCATE
 * toàn bộ bảng và seed lại một Seller, một Bidder và một Auction trước khi từng test bắt đầu.
 *
 * <p><b>Điều kiện tiên quyết:</b> PostgreSQL phải đang chạy với thông tin kết nối được cấu hình
 * trong {@link DatabaseConfig}. Nếu không kết nối được, toàn bộ class bị bỏ qua (ABORTED).
 */
class BidTransactionDaoTest {

  private static Jdbi jdbi;
  private static UserDao userDao;
  private static ItemDao itemDao;
  private static AuctionDao auctionDao;
  private static BidTransactionDao bidDao;

  /** Người bán — owner của {@code testItem}; được tạo lại trước mỗi test. */
  private User testSeller;

  /** Người đặt giá — thực thể bidder duy nhất dùng trong class này. */
  private User testBidder;

  /** Vật phẩm mặc định gắn với {@code testAuction}. */
  private Item testItem;

  /** Auction dùng chung cho tất cả lượt đặt giá trong class. */
  private Auction testAuction;

  /**
   * Khởi tạo JDBI và tất cả DAO một lần duy nhất cho cả class.
   *
   * <p>Nếu DB không khả dụng, class bị bỏ qua hoàn toàn qua {@link Assumptions#abort} để tránh báo
   * lỗi giả trong môi trường không có DB.
   */
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

  /**
   * Chuẩn bị trạng thái DB sạch và dữ liệu mẫu trước mỗi test.
   *
   * <p><b>Bước 1 — Dọn dẹp:</b> TRUNCATE theo thứ tự con → cha để tuân thủ FK constraint. {@code
   * RESTART IDENTITY} trên {@code users} đưa sequence về 1 để ID luôn cố định.
   *
   * <p><b>Bước 2 — Seed dữ liệu:</b> Tạo Seller (id=1), Bidder (id=2), một Item và một Auction với
   * giá khởi điểm 100.000. Bộ dữ liệu này là nền cần thiết để insert {@link BidTransaction} vì bảng
   * {@code bid_transactions} có FK tới cả {@code auctions} lẫn {@code users}.
   */
  @BeforeEach
  void init() {
    // Bước 1: Dọn dẹp DB và đặt lại bộ đếm ID về 1.
    jdbi.useHandle(
        handle -> {
          handle.execute("TRUNCATE TABLE auto_bid_configs CASCADE");
          handle.execute("TRUNCATE TABLE bid_transactions CASCADE");
          handle.execute("TRUNCATE TABLE auctions CASCADE");
          handle.execute("TRUNCATE TABLE items CASCADE");
          // RESTART IDENTITY đảm bảo ID bắt đầu từ 1 sau mỗi lần dọn dẹp.
          handle.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
        });

    // Bước 2: Khởi tạo dữ liệu mẫu (ID dự kiến: Seller=1, Bidder=2, Item=1, Auction=1).
    testSeller = userDao.insert(new Seller("bid_seller", "hash", "seller@test.com"));
    testBidder = userDao.insert(new Bidder("bid_bidder", "hash", "bidder@test.com"));

    testItem = new Item("Bid Item", "Test", testSeller.getId(), "ELECTRONICS");
    testItem.setBrand("Brand");
    itemDao.insert(testItem);

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

  /**
   * Kiểm tra insert lượt đặt giá thường: xác nhận ID được sinh tự động, các trường ({@code
   * auctionId}, {@code bidderId}, {@code amount}) được lưu đúng, cờ {@code autoBid} là {@code
   * false} và {@code createdAt} không null.
   */
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

  /**
   * Kiểm tra findByAuctionId: sau khi insert 2 lượt đặt giá với số tiền khác nhau, danh sách trả về
   * phải có đúng 2 phần tử và được sắp xếp tăng dần theo amount (hoặc ID).
   */
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
    // Kiểm tra thứ tự sắp xếp: lượt đặt giá thấp hơn phải đứng trước lượt đặt giá cao hơn.
    assertTrue(bids.get(0).getAmount().compareTo(bids.get(1).getAmount()) < 0);
  }

  /**
   * Kiểm tra findByBidderId: tất cả lượt đặt giá của một bidder phải được trả về và toàn bộ phải
   * thuộc đúng {@code testBidder}.
   */
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

  /**
   * Kiểm tra findLastBid: sau khi insert 2 lượt đặt giá, lượt được insert sau cùng (giá 250.000)
   * phải được nhận dạng là lượt đặt gần nhất và có ID tương ứng.
   *
   * <p>Lượt đặt giá đầu (150.000) được insert trước để xác nhận DAO không đơn giản trả về lượt đầu
   * tiên mà phải tìm đúng lượt cuối cùng.
   */
  @Test
  @DisplayName("FindLastBid should return the most recent bid")
  void testFindLastBid() {
    bidDao.insert(
        new BidTransaction(
            testAuction.getId(), testBidder.getId(), new BigDecimal("150000"), false));

    // Tạo bid mới với giá cao hơn — đây là bid được insert sau cùng, phải là kết quả trả về.
    BidTransaction last =
        new BidTransaction(
            testAuction.getId(), testBidder.getId(), new BigDecimal("250000"), false);
    bidDao.insert(last);

    Optional<BidTransaction> found = bidDao.findLastBid(testAuction.getId());

    assertTrue(found.isPresent());
    assertEquals(last.getId(), found.get().getId());
    assertEquals(0, new BigDecimal("250000").compareTo(found.get().getAmount()));
  }

  /**
   * Kiểm tra countByAuctionId: sau khi insert 2 lượt đặt giá, tổng số lượt đếm được phải chính xác
   * là 2.
   */
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

  /**
   * Kiểm tra getHighestPrice: trong số 3 lượt đặt giá (150.000, 350.000, 250.000), giá cao nhất
   * phải là 350.000. Dùng {@code compareTo} thay vì {@code equals} để tránh sai lệch do scale của
   * {@link BigDecimal}.
   */
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

  /**
   * Kiểm tra insert lượt đặt giá tự động ({@code autoBid = true}): xác nhận cờ {@code autoBid} được
   * lưu và đọc lại đúng, giúp phân biệt lượt đặt thủ công và tự động trong lịch sử.
   */
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
