package com.auction.dao;

import static org.junit.jupiter.api.Assertions.*;

import com.auction.config.DatabaseConfig;
import com.auction.model.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Assumptions;

/**
 * Test suite kiểm tra toàn bộ các thao tác CRUD và nghiệp vụ lên bảng {@code auctions} thông qua
 * {@link AuctionDao}.
 *
 * <p><b>Phạm vi kiểm tra:</b>
 *
 * <ul>
 *   <li>CRUD cơ bản: insert, findById, findAll, update, delete.
 *   <li>Truy vấn lọc: findByStatus, findByItemId, existsById, getCurrentPrice.
 * </ul>
 *
 * <p><b>Chiến lược dữ liệu:</b> Mỗi test chạy trong trạng thái DB hoàn toàn sạch. Phương thức
 * {@code init()} TRUNCATE tất cả bảng và tạo lại bộ dữ liệu mẫu cố định (Seller, Bidder, Item)
 * trước khi từng test bắt đầu, đảm bảo test isolation và ID luôn nhất quán.
 *
 * <p><b>Điều kiện tiên quyết:</b> PostgreSQL phải đang chạy với thông tin kết nối được cấu hình
 * trong {@link DatabaseConfig}. Nếu không kết nối được, toàn bộ class bị bỏ qua (ABORTED) thay vì
 * báo FAILED.
 */
class AuctionDaoTest {

  private static Jdbi jdbi;
  private static UserDao userDao;
  private static ItemDao itemDao;
  private static AuctionDao auctionDao;

  /** Người bán — owner của {@code testItem}; được tạo lại trước mỗi test. */
  private User testSeller;

  /**
   * Người đặt giá — dùng để kiểm tra cập nhật {@code leadingBidderId} trên auction; được tạo lại
   * trước mỗi test.
   */
  private User testBidder;

  /** Vật phẩm mặc định gắn với các auction trong test; thuộc sở hữu của {@code testSeller}. */
  private Item testItem;

  /**
   * Khởi tạo kết nối JDBI và các DAO một lần duy nhất cho cả class.
   *
   * <p>Nếu DB không khả dụng, class bị bỏ qua hoàn toàn qua {@link Assumptions#abort} để tránh báo
   * lỗi giả trong môi trường không có DB (ví dụ: CI chạy unit-test thuần).
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
  }

  /**
   * Chuẩn bị trạng thái DB sạch và dữ liệu mẫu trước mỗi test.
   *
   * <p><b>Bước 1 — Dọn dẹp:</b> TRUNCATE các bảng theo thứ tự con → cha để tuân thủ ràng buộc khóa
   * ngoại. {@code CASCADE} xử lý các phụ thuộc còn lại tự động. {@code RESTART IDENTITY} trên
   * {@code users} đưa sequence BIGSERIAL về 1, đảm bảo ID cố định qua mọi lần chạy.
   *
   * <p><b>Bước 2 — Seed dữ liệu:</b> Tạo Seller (id=1), Bidder (id=2) và một Item Electronics
   * (id=1) làm nền cho mọi test trong class. ID cố định giúp các assertion không bị ảnh hưởng bởi
   * thứ tự chạy test hay dữ liệu còn sót từ lần trước.
   */
  @BeforeEach
  void init() {
    // Bước 1: Dọn dẹp toàn bộ DB và đặt lại bộ đếm ID về 1.
    jdbi.useHandle(
        handle -> {
          handle.execute("TRUNCATE TABLE auto_bid_configs CASCADE");
          handle.execute("TRUNCATE TABLE bid_transactions CASCADE");
          handle.execute("TRUNCATE TABLE auctions CASCADE");
          handle.execute("TRUNCATE TABLE items CASCADE");
          // RESTART IDENTITY đảm bảo ID bắt đầu từ 1 sau mỗi lần dọn dẹp.
          handle.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
        });

    // Bước 2: Khởi tạo dữ liệu mẫu cho mỗi test case (ID sẽ luôn cố định: Seller=1, Bidder=2,
    // Item=1).
    testSeller = userDao.insert(new Seller("auction_seller", "hash", "seller@test.com"));
    testBidder = userDao.insert(new Bidder("auction_bidder", "hash", "bidder@test.com"));
    testItem = new Electronics("Auction Item", "For auction", testSeller.getId(), "Brand");
    itemDao.insert(testItem);

    System.out.println(
        "DB Reset. Test IDs -> Seller: " + testSeller.getId() + ", Item: " + testItem.getId());
  }

  /**
   * Kiểm tra insert auction mới: xác nhận ID được sinh tự động, status mặc định là {@code OPEN},
   * giá hiện tại bằng giá khởi điểm, và chưa có người dẫn đầu ({@code leadingBidderId} là null).
   */
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
    assertEquals(AuctionStatus.OPEN, saved.getStatus());
    assertEquals(0, new BigDecimal("100000").compareTo(saved.getCurrentPrice()));
    assertNull(saved.getLeadingBidderId());
  }

  /** Kiểm tra findById: xác nhận auction vừa insert có thể tìm lại đúng ID và giá khởi điểm. */
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

  /**
   * Kiểm tra findAll: sau khi insert 2 auction, danh sách trả về phải có đúng 2 phần tử. DB được
   * reset trước mỗi test nên không có nguy cơ đếm dư dữ liệu cũ.
   */
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

  /**
   * Kiểm tra findByStatus: sau khi insert một auction {@code OPEN}, kết quả lọc phải không rỗng và
   * toàn bộ phần tử phải có status đúng bằng {@code OPEN}.
   */
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
    assertTrue(openAuctions.stream().allMatch(a -> AuctionStatus.OPEN == a.getStatus()));
  }

  /** Kiểm tra findByItemId: auction được lấy về phải thuộc đúng {@code testItem}. */
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

  /**
   * Kiểm tra update: sau khi thay đổi {@code currentPrice}, {@code leadingBidderId} và {@code
   * status}, các giá trị mới phải được persist và đọc lại chính xác từ DB.
   */
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
    saved.setStatus(AuctionStatus.RUNNING);

    auctionDao.update(saved);

    Optional<Auction> found = auctionDao.findById(saved.getId());
    assertTrue(found.isPresent());
    assertEquals(0, new BigDecimal("150000").compareTo(found.get().getCurrentPrice()));
    assertEquals(testBidder.getId(), found.get().getLeadingBidderId());
    assertEquals(AuctionStatus.RUNNING, found.get().getStatus());
  }

  /** Kiểm tra delete: auction bị xóa không còn tìm thấy qua findById. */
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

  /**
   * Kiểm tra existsById: ID hợp lệ trả về {@code true}, ID không tồn tại (999L) trả về {@code
   * false}.
   */
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

  /**
   * Kiểm tra getCurrentPrice: giá trả về phải khớp chính xác với giá khởi điểm khi chưa có lượt đặt
   * giá nào. Dùng {@code compareTo} thay vì {@code equals} để tránh sai lệch do scale của {@link
   * BigDecimal}.
   */
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

  @Test
  @SuppressWarnings("checkstyle:MethodName")
  void atomicTransition_correctStatus_returnsTrue() {
    Auction auction =
        new Auction(
            testItem.getId(),
            new BigDecimal("100000"),
            LocalDateTime.now().minusHours(1),
            LocalDateTime.now().plusHours(1));
    auction.setStatus(AuctionStatus.RUNNING);
    Auction saved = auctionDao.insert(auction);

    boolean transitionResult = auctionDao.atomicTransition(saved.getId(), "RUNNING", "SETTLING");

    assertTrue(transitionResult);
    String status =
        jdbi.withHandle(
            handle ->
                handle
                    .createQuery("SELECT status FROM auctions WHERE id = :id")
                    .bind("id", saved.getId())
                    .mapTo(String.class)
                    .one());
    assertEquals("SETTLING", status);
  }

  @Test
  @SuppressWarnings("checkstyle:MethodName")
  void atomicTransition_wrongStatus_returnsFalse() {
    Auction auction =
        new Auction(
            testItem.getId(),
            new BigDecimal("100000"),
            LocalDateTime.now().minusHours(1),
            LocalDateTime.now().plusHours(1));
    auction.setStatus(AuctionStatus.RUNNING);
    Auction saved = auctionDao.insert(auction);

    boolean transitionResult = auctionDao.atomicTransition(saved.getId(), "OPEN", "RUNNING");

    assertFalse(transitionResult);
    String status =
        jdbi.withHandle(
            handle ->
                handle
                    .createQuery("SELECT status FROM auctions WHERE id = :id")
                    .bind("id", saved.getId())
                    .mapTo(String.class)
                    .one());
    assertEquals("RUNNING", status);
  }

  @Test
  @SuppressWarnings("checkstyle:MethodName")
  void atomicTransition_concurrent_onlyOneWins() throws Exception {
    Auction auction =
        new Auction(
            testItem.getId(),
            new BigDecimal("100000"),
            LocalDateTime.now().minusHours(1),
            LocalDateTime.now().plusHours(1));
    auction.setStatus(AuctionStatus.RUNNING);
    Auction saved = auctionDao.insert(auction);

    int threadCount = 5;
    ExecutorService pool = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startGate = new CountDownLatch(1);
    List<Future<Boolean>> futures = new ArrayList<>();

    for (int i = 0; i < threadCount; i++) {
      futures.add(
          pool.submit(
              () -> {
                startGate.await();
                return auctionDao.atomicTransition(saved.getId(), "RUNNING", "SETTLING");
              }));
    }

    startGate.countDown();
    pool.shutdown();
    assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

    long wins =
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

    assertEquals(1L, wins);
  }
}
