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
 * Test suite kiểm tra toàn bộ các thao tác của {@link AutoBidConfigDao} — lớp DAO quản lý cấu hình
 * đặt giá tự động (auto-bid) trên bảng {@code auto_bid_configs}.
 *
 * <p><b>Phạm vi kiểm tra:</b>
 *
 * <ul>
 *   <li>CRUD cơ bản: insert, findById, update.
 *   <li>Truy vấn đặc thù: findByAuctionAndBidder, findActiveByAuctionId, findByBidderId.
 *   <li>Nghiệp vụ trạng thái: deactivate (một config), deactivateAllByAuctionId (hàng loạt),
 *       hasActiveConfig, countActiveByAuctionId.
 *   <li>Update theo khóa tự nhiên: updateByAuctionAndBidder.
 * </ul>
 *
 * <p><b>Chiến lược dữ liệu:</b> Mỗi test chạy trong trạng thái DB sạch. {@code init()} TRUNCATE
 * toàn bộ bảng và seed lại hai bidder ({@code testBidder1}, {@code testBidder2}) cùng một auction,
 * giúp test các kịch bản multi-bidder mà không cần tạo dữ liệu lặp lại.
 *
 * <p><b>Điều kiện tiên quyết:</b> PostgreSQL phải đang chạy với thông tin kết nối được cấu hình
 * trong {@link DatabaseConfig}. Nếu không kết nối được, toàn bộ class bị bỏ qua (ABORTED).
 */
class AutoBidConfigDaoTest {

  private static Jdbi jdbi;
  private static UserDao userDao;
  private static ItemDao itemDao;
  private static AuctionDao auctionDao;
  private static AutoBidConfigDao autoBidDao;

  /** Người bán — owner của {@code testItem}. */
  private User testSeller;

  /** Bidder thứ nhất — thường đóng vai config được giữ nguyên (active). */
  private User testBidder1;

  /** Bidder thứ hai — thường đóng vai config bị vô hiệu hóa hoặc so sánh với Bidder1. */
  private User testBidder2;

  /** Vật phẩm mặc định gắn với {@code testAuction}. */
  private Item testItem;

  /** Auction dùng chung cho tất cả auto-bid config trong class. */
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
    autoBidDao = new AutoBidConfigDao(jdbi);
  }

  /**
   * Chuẩn bị trạng thái DB sạch và dữ liệu mẫu trước mỗi test.
   *
   * <p><b>Bước 1 — Dọn dẹp:</b> TRUNCATE theo thứ tự con → cha để tuân thủ FK constraint. {@code
   * RESTART IDENTITY} trên {@code users} đưa sequence về 1 để ID luôn cố định.
   *
   * <p><b>Bước 2 — Seed dữ liệu:</b> Tạo Seller (id=1), Bidder1 (id=2), Bidder2 (id=3), một Item và
   * một Auction với giá khởi điểm 100.000. Hai bidder cho phép test các kịch bản so sánh (active vs
   * inactive, count, deactivate all).
   */
  @BeforeEach
  void init() {
    // Bước 1: Dọn dẹp DB và đặt lại bộ đếm ID về 1 trước mỗi test case.
    jdbi.useHandle(
        handle -> {
          handle.execute("TRUNCATE TABLE auto_bid_configs CASCADE");
          handle.execute("TRUNCATE TABLE bid_transactions CASCADE");
          handle.execute("TRUNCATE TABLE auctions CASCADE");
          handle.execute("TRUNCATE TABLE items CASCADE");
          // RESTART IDENTITY đảm bảo ID bắt đầu từ 1 sau mỗi lần dọn dẹp.
          handle.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
        });

    // Bước 2: Khởi tạo dữ liệu mẫu cố định dùng chung cho các test trong class.
    testSeller = userDao.insert(new Seller("auto_seller", "hash", "seller@test.com"));
    testBidder1 = userDao.insert(new Bidder("auto_bidder1", "hash", "bidder1@test.com"));
    testBidder2 = userDao.insert(new Bidder("auto_bidder2", "hash", "bidder2@test.com"));

    testItem = new Item("Auto Item", "Test", testSeller.getId(), "ELECTRONICS");
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
        "Cleaned DB & Initialized IDs -> Seller: "
            + testSeller.getId()
            + ", Auction: "
            + testAuction.getId());
  }

  /**
   * Kiểm tra insert config mới: xác nhận ID được sinh tự động, các trường được lưu đúng ({@code
   * auctionId}, {@code bidderId}, {@code maxBid}, {@code increment}), config mặc định ở trạng thái
   * active và {@code registeredAt} không null.
   */
  @Test
  @DisplayName("Insert should create auto-bid config")
  void testInsert() {
    AutoBidConfig config =
        new AutoBidConfig(
            testAuction.getId(),
            testBidder1.getId(),
            new BigDecimal("1000000"),
            new BigDecimal("50000"));

    AutoBidConfig saved = autoBidDao.insert(config);

    assertNotNull(saved.getId());
    assertEquals(testAuction.getId(), saved.getAuctionId());
    assertEquals(testBidder1.getId(), saved.getBidderId());
    assertEquals(0, new BigDecimal("1000000").compareTo(saved.getMaxBid()));
    assertEquals(0, new BigDecimal("50000").compareTo(saved.getIncrement()));
    assertTrue(saved.isActive());
    assertEquals(AutoBidStatus.ACTIVE, saved.getStatus());
    assertNull(saved.getFailureReason());
    assertNotNull(saved.getRegisteredAt());
  }

  /** Kiểm tra findById: config vừa insert phải tìm lại được đúng ID và giá trị {@code maxBid}. */
  @Test
  @DisplayName("FindById should return config")
  void testFindById() {
    AutoBidConfig config =
        new AutoBidConfig(
            testAuction.getId(),
            testBidder1.getId(),
            new BigDecimal("2000000"),
            new BigDecimal("100000"));
    AutoBidConfig saved = autoBidDao.insert(config);

    Optional<AutoBidConfig> found = autoBidDao.findById(saved.getId());

    assertTrue(found.isPresent());
    assertEquals(saved.getId(), found.get().getId());
    assertEquals(0, new BigDecimal("2000000").compareTo(found.get().getMaxBid()));
  }

  /**
   * Kiểm tra findByAuctionAndBidder: truy vấn theo khóa tự nhiên (auctionId + bidderId) phải trả về
   * đúng config của {@code testBidder1} và không lẫn config của bidder khác.
   */
  @Test
  @DisplayName("FindByAuctionAndBidder should return config for specific user")
  void testFindByAuctionAndBidder() {
    AutoBidConfig config =
        new AutoBidConfig(
            testAuction.getId(),
            testBidder1.getId(),
            new BigDecimal("3000000"),
            new BigDecimal("150000"));
    autoBidDao.insert(config);

    Optional<AutoBidConfig> found =
        autoBidDao.findByAuctionAndBidder(testAuction.getId(), testBidder1.getId());

    assertTrue(found.isPresent());
    assertEquals(testBidder1.getId(), found.get().getBidderId());
    assertEquals(0, new BigDecimal("3000000").compareTo(found.get().getMaxBid()));
  }

  /**
   * Kiểm tra findActiveByAuctionId: chỉ config đang active mới xuất hiện trong kết quả.
   *
   * <p>Kịch bản: Insert 2 config (Bidder1 active, Bidder2 active), sau đó vô hiệu hóa Bidder2. Kết
   * quả trả về phải đúng 1 phần tử thuộc Bidder1.
   */
  @Test
  @DisplayName("FindActiveByAuctionId should return only active configs")
  void testFindActiveByAuctionId() {
    AutoBidConfig config1 =
        new AutoBidConfig(
            testAuction.getId(), testBidder1.getId(),
            BigDecimal.valueOf(1_000_000L), BigDecimal.valueOf(50_000L));
    autoBidDao.insert(config1);

    AutoBidConfig config2 =
        new AutoBidConfig(
            testAuction.getId(), testBidder2.getId(),
            BigDecimal.valueOf(2_000_000L), BigDecimal.valueOf(100_000L));
    AutoBidConfig saved2 = autoBidDao.insert(config2);

    // Vô hiệu hóa config2 để chỉ Bidder1 còn active.
    saved2.setActive(false);
    autoBidDao.update(saved2);

    List<AutoBidConfig> active = autoBidDao.findActiveByAuctionId(testAuction.getId());

    assertEquals(1, active.size());
    assertEquals(testBidder1.getId(), active.get(0).getBidderId());
  }

  /**
   * Kiểm tra findByBidderId: tất cả config của một bidder phải được trả về bất kể trạng thái active
   * hay không. Kết quả phải thuộc đúng {@code testBidder1}.
   */
  @Test
  @DisplayName("FindByBidderId should return all configs of a bidder")
  void testFindByBidderId() {
    autoBidDao.insert(
        new AutoBidConfig(
            testAuction.getId(), testBidder1.getId(),
            BigDecimal.valueOf(1_000_000L), BigDecimal.valueOf(50_000L)));

    List<AutoBidConfig> configs = autoBidDao.findByBidderId(testBidder1.getId());

    assertEquals(1, configs.size());
    assertEquals(testBidder1.getId(), configs.get(0).getBidderId());
  }

  /**
   * Kiểm tra update theo ID: sau khi thay đổi {@code maxBid}, {@code increment} và {@code active},
   * các giá trị mới phải được persist và đọc lại chính xác từ DB.
   */
  @Test
  @DisplayName("Update should modify config")
  void testUpdate() {
    AutoBidConfig config =
        new AutoBidConfig(
            testAuction.getId(), testBidder1.getId(),
            BigDecimal.valueOf(1_000_000L), BigDecimal.valueOf(50_000L));
    AutoBidConfig saved = autoBidDao.insert(config);

    saved.setMaxBid(BigDecimal.valueOf(1_500_000L));
    saved.setIncrement(BigDecimal.valueOf(75_000L));
    saved.setActive(false);

    boolean updated = autoBidDao.update(saved);
    assertTrue(updated);

    Optional<AutoBidConfig> found = autoBidDao.findById(saved.getId());
    assertTrue(found.isPresent());
    assertEquals(0, BigDecimal.valueOf(1_500_000L).compareTo(found.get().getMaxBid()));
    assertFalse(found.get().isActive());
    assertEquals(AutoBidStatus.STOPPED, found.get().getStatus());
  }

  /**
   * Kiểm tra updateByAuctionAndBidder: update theo khóa tự nhiên (không cần ID) phải cập nhật đúng
   * {@code maxBid}, {@code increment} và {@code active} của config tương ứng.
   *
   * <p>Phương thức này hữu ích khi caller chỉ biết auctionId + bidderId mà không cần truy vấn ID
   * trước.
   */
  @Test
  @DisplayName("UpdateByAuctionAndBidder should work")
  void testUpdateByAuctionAndBidder() {
    autoBidDao.insert(
        new AutoBidConfig(
            testAuction.getId(), testBidder1.getId(),
            BigDecimal.valueOf(1_000_000L), BigDecimal.valueOf(50_000L)));

    boolean updated =
        autoBidDao.updateByAuctionAndBidder(
            testAuction.getId(),
            testBidder1.getId(),
            BigDecimal.valueOf(2_000_000L),
            BigDecimal.valueOf(100_000L),
            false);

    assertTrue(updated);

    Optional<AutoBidConfig> found =
        autoBidDao.findByAuctionAndBidder(testAuction.getId(), testBidder1.getId());
    assertTrue(found.isPresent());
    assertEquals(0, BigDecimal.valueOf(2_000_000L).compareTo(found.get().getMaxBid()));
    assertFalse(found.get().isActive());
    assertEquals(AutoBidStatus.STOPPED, found.get().getStatus());
  }

  /**
   * Kiểm tra deactivate: sau khi vô hiệu hóa config của Bidder1, trường {@code active} phải là
   * {@code false} khi đọc lại từ DB.
   */
  @Test
  @DisplayName("Deactivate should set active to false")
  void testDeactivate() {
    autoBidDao.insert(
        new AutoBidConfig(
            testAuction.getId(), testBidder1.getId(),
            BigDecimal.valueOf(1_000_000L), BigDecimal.valueOf(50_000L)));

    boolean deactivated = autoBidDao.deactivate(testAuction.getId(), testBidder1.getId());
    assertTrue(deactivated);

    Optional<AutoBidConfig> found =
        autoBidDao.findByAuctionAndBidder(testAuction.getId(), testBidder1.getId());
    assertTrue(found.isPresent());
    assertFalse(found.get().isActive());
    assertEquals(AutoBidStatus.STOPPED, found.get().getStatus());
    assertNull(found.get().getFailureReason());
  }

  @Test
  @DisplayName("Status and failure_reason should round-trip through DAO")
  void testStatusAndFailureReasonRoundTrip() {
    AutoBidConfig config =
        new AutoBidConfig(
            testAuction.getId(),
            testBidder1.getId(),
            BigDecimal.valueOf(1_000_000L),
            BigDecimal.valueOf(50_000L));
    AutoBidConfig saved = autoBidDao.insert(config);
    saved.setStatus(AutoBidStatus.FAILED);
    saved.setFailureReason(AutoBidFailureReason.INSUFFICIENT_BALANCE);

    assertTrue(autoBidDao.update(saved));

    AutoBidConfig found = autoBidDao.findById(saved.getId()).orElseThrow();
    assertFalse(found.isActive());
    assertEquals(AutoBidStatus.FAILED, found.getStatus());
    assertEquals(AutoBidFailureReason.INSUFFICIENT_BALANCE, found.getFailureReason());
  }

  /**
   * Kiểm tra deactivateAllByAuctionId: tất cả config của một auction (cả 2 bidder) phải bị vô hiệu
   * hóa trong một lần gọi. Sau đó findActiveByAuctionId phải trả về danh sách rỗng.
   */
  @Test
  @DisplayName("DeactivateAllByAuctionId should deactivate all configs for an auction")
  void testDeactivateAllByAuctionId() {
    autoBidDao.insert(
        new AutoBidConfig(
            testAuction.getId(), testBidder1.getId(),
            BigDecimal.valueOf(1_000_000L), BigDecimal.valueOf(50_000L)));
    autoBidDao.insert(
        new AutoBidConfig(
            testAuction.getId(), testBidder2.getId(),
            BigDecimal.valueOf(2_000_000L), BigDecimal.valueOf(100_000L)));

    int deactivated = autoBidDao.deactivateAllByAuctionId(testAuction.getId());
    assertEquals(2, deactivated);

    List<AutoBidConfig> active = autoBidDao.findActiveByAuctionId(testAuction.getId());
    assertEquals(0, active.size());
  }

  /**
   * Kiểm tra hasActiveConfig: trả về {@code true} khi Bidder1 có config active, {@code false} khi
   * Bidder2 chưa có config nào trong auction này.
   */
  @Test
  @DisplayName("HasActiveConfig should return true if active config exists")
  void testHasActiveConfig() {
    autoBidDao.insert(
        new AutoBidConfig(
            testAuction.getId(), testBidder1.getId(),
            BigDecimal.valueOf(1_000_000L), BigDecimal.valueOf(50_000L)));

    assertTrue(autoBidDao.hasActiveConfig(testAuction.getId(), testBidder1.getId()));
    assertFalse(autoBidDao.hasActiveConfig(testAuction.getId(), testBidder2.getId()));
  }

  /**
   * Kiểm tra countActiveByAuctionId: sau khi insert 2 config active (Bidder1 và Bidder2), số lượng
   * trả về phải chính xác là 2.
   */
  @Test
  @DisplayName("CountActiveByAuctionId should return correct count")
  void testCountActiveByAuctionId() {
    autoBidDao.insert(
        new AutoBidConfig(
            testAuction.getId(), testBidder1.getId(),
            BigDecimal.valueOf(1_000_000L), BigDecimal.valueOf(50_000L)));
    autoBidDao.insert(
        new AutoBidConfig(
            testAuction.getId(), testBidder2.getId(),
            BigDecimal.valueOf(2_000_000L), BigDecimal.valueOf(100_000L)));

    int count = autoBidDao.countActiveByAuctionId(testAuction.getId());
    assertEquals(2, count);
  }
}
