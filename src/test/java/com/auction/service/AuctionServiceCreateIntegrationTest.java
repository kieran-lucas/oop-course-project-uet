package com.auction.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.auction.config.DatabaseConfig;
import com.auction.dao.AuctionDao;
import com.auction.dao.BidTransactionDao;
import com.auction.dao.ItemDao;
import com.auction.dao.UserDao;
import com.auction.dto.CreateAuctionRequest;
import com.auction.exception.DuplicateException;
import com.auction.model.Art;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.BidTransaction;
import com.auction.model.Bidder;
import com.auction.model.Electronics;
import com.auction.model.Item;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.pattern.observer.AuctionEventManager;
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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration test kiểm tra logic tạo phiên đấu giá của {@link AuctionService#create}.
 *
 * <p>Xác nhận các quy tắc phòng trùng lặp: item đang trong phiên OPEN/RUNNING/SETTLING/PAID đều bị
 * từ chối; chỉ item từ phiên CANCELED hoặc chưa có phiên mới được tạo lại. Ngoài ra kiểm tra tính
 * đúng đắn khi có hai thread đồng thời tạo phiên cho cùng một item.
 *
 * <p><b>Điều kiện tiên quyết:</b> PostgreSQL phải đang chạy; bị bỏ qua (ABORTED) nếu không có DB.
 */
@DisplayName("AuctionService — tạo phiên đấu giá, kiểm tra trùng lặp và đồng thời")
class AuctionServiceCreateIntegrationTest {

  private static Jdbi jdbi;
  private static UserDao userDao;
  private static ItemDao itemDao;
  private static AuctionDao auctionDao;
  private static BidTransactionDao bidTransactionDao;
  private static AuctionService auctionService;

  private User testSeller;
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
    bidTransactionDao = new BidTransactionDao(jdbi);
    auctionService =
        new AuctionService(
            auctionDao, itemDao, userDao, new AuctionEventManager(), jdbi, bidTransactionDao);
  }

  @BeforeEach
  void init() {
    jdbi.useHandle(
        handle -> {
          handle.execute("TRUNCATE TABLE auto_bid_configs CASCADE");
          handle.execute("TRUNCATE TABLE bid_transactions CASCADE");
          handle.execute("TRUNCATE TABLE auctions CASCADE");
          handle.execute("TRUNCATE TABLE items CASCADE");
          handle.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
        });

    testSeller = userDao.insert(new Seller("seller", "hash", "seller@test.com"));
    testItem = new Electronics("Single item", "Only one unit", testSeller.getId(), "Brand");
    testItem = itemDao.insert(testItem);
  }

  @Test
  @DisplayName("Item đang ở phiên RUNNING → từ chối tạo mới")
  void sameItemRunningAuctionRejectsCreate() {
    createExistingAuction(AuctionStatus.RUNNING);

    assertThrows(
        DuplicateException.class,
        () -> auctionService.create(newCreateRequest(testItem.getId()), testSeller.getId()));
  }

  @Test
  @DisplayName("Item đang ở phiên OPEN → từ chối tạo mới")
  void sameItemOpenAuctionRejectsCreate() {
    createExistingAuction(AuctionStatus.OPEN);

    assertThrows(
        DuplicateException.class,
        () -> auctionService.create(newCreateRequest(testItem.getId()), testSeller.getId()));
  }

  @Test
  @DisplayName("Item đang ở phiên SETTLING → từ chối tạo mới")
  void sameItemSettlingAuctionRejectsCreate() {
    createExistingAuction(AuctionStatus.SETTLING);

    assertThrows(
        DuplicateException.class,
        () -> auctionService.create(newCreateRequest(testItem.getId()), testSeller.getId()));
  }

  @Test
  @DisplayName("Item từ phiên CANCELED và AVAILABLE → cho phép tạo mới")
  void sameItemCanceledAuctionAllowsCreate() {
    createExistingAuction(AuctionStatus.CANCELED);

    Auction created = auctionService.create(newCreateRequest(testItem.getId()), testSeller.getId());

    assertEquals(AuctionStatus.OPEN, created.getStatus());
    assertEquals(2, auctionDao.findByItemId(testItem.getId()).size());
    assertEquals("IN_AUCTION", itemDao.findById(testItem.getId()).orElseThrow().getStatus());
  }

  @Test
  @DisplayName("Item từ phiên PAID → từ chối tạo mới")
  void sameItemPaidAuctionRejectsCreate() {
    createExistingAuction(AuctionStatus.PAID);

    assertThrows(
        DuplicateException.class,
        () -> auctionService.create(newCreateRequest(testItem.getId()), testSeller.getId()));
  }

  @Test
  @DisplayName("Item đã SOLD → từ chối tạo phiên mới")
  void soldItemRejectsCreate() {
    testItem.setStatus("SOLD");
    itemDao.update(testItem);

    assertThrows(
        DuplicateException.class,
        () -> auctionService.create(newCreateRequest(testItem.getId()), testSeller.getId()));
  }

  @Test
  @DisplayName("Tạo phiên đồng thời cho cùng một item — chỉ một phiên được tạo thành công")
  void concurrentCreateSameItemOnlyOneSucceeds() throws Exception {
    int threadCount = 2;
    ExecutorService pool = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startGate = new CountDownLatch(1);
    List<Future<Optional<Auction>>> futures = new ArrayList<>();

    for (int i = 0; i < threadCount; i++) {
      futures.add(
          pool.submit(
              () -> {
                startGate.await();
                try {
                  return Optional.of(
                      auctionService.create(
                          newCreateRequest(testItem.getId()), testSeller.getId()));
                } catch (DuplicateException e) {
                  return Optional.empty();
                }
              }));
    }

    startGate.countDown();
    pool.shutdown();
    assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

    long successfulCreates = 0;
    for (Future<Optional<Auction>> future : futures) {
      if (future.get().isPresent()) {
        successfulCreates++;
      }
    }

    assertEquals(1L, successfulCreates);
    assertEquals(1, auctionDao.findByItemId(testItem.getId()).size());
    assertEquals("IN_AUCTION", itemDao.findById(testItem.getId()).orElseThrow().getStatus());
  }

  @Test
  @DisplayName("Item trong phiên chưa có bid → sửa category trong transaction")
  void listedItemWithoutBidsCanBeUpdated() {
    createExistingAuction(AuctionStatus.OPEN);
    setItemStatus("IN_AUCTION");
    Item replacement = new Art("Updated art", "Updated desc", testSeller.getId(), "Artist");
    replacement.setId(testItem.getId());

    auctionService.updateItemWithoutBids(replacement);

    Item found = itemDao.findById(testItem.getId()).orElseThrow();
    assertInstanceOf(Art.class, found);
    assertEquals("IN_AUCTION", found.getStatus());
  }

  @Test
  @DisplayName("Item trong phiên SETTLING → từ chối sửa")
  void settlingItemCannotBeUpdated() {
    createExistingAuction(AuctionStatus.SETTLING);
    setItemStatus("IN_AUCTION");
    Item replacement = new Art("Updated art", "Updated desc", testSeller.getId(), "Artist");
    replacement.setId(testItem.getId());

    assertThrows(
        IllegalStateException.class, () -> auctionService.updateItemWithoutBids(replacement));
  }

  @Test
  @DisplayName("Bid commit trước xóa item → xóa bị từ chối")
  void committedBidBlocksConcurrentItemRemoval() throws Exception {
    Auction auction = createExistingAuction(AuctionStatus.RUNNING);
    setItemStatus("IN_AUCTION");
    User bidder = userDao.insert(new Bidder("bidder", "hash", "bidder@test.com"));
    CountDownLatch auctionLocked = new CountDownLatch(1);
    CountDownLatch allowBidCommit = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);

    Future<?> bidFuture =
        pool.submit(
            () ->
                jdbi.useTransaction(
                    handle -> {
                      auctionDao.findByIdForUpdate(handle, auction.getId());
                      auctionLocked.countDown();
                      try {
                        assertTrue(allowBidCommit.await(5, TimeUnit.SECONDS));
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                      }
                      bidTransactionDao.insert(
                          handle,
                          new BidTransaction(
                              auction.getId(), bidder.getId(), new BigDecimal("200000"), false));
                    }));

    assertTrue(auctionLocked.await(5, TimeUnit.SECONDS));
    Future<?> removeFuture =
        pool.submit(
            () ->
                assertThrows(
                    IllegalStateException.class,
                    () ->
                        auctionService.removeItemWithoutBids(
                            testItem.getId(), testSeller.getId(), "SELLER")));
    allowBidCommit.countDown();

    bidFuture.get(5, TimeUnit.SECONDS);
    removeFuture.get(5, TimeUnit.SECONDS);
    pool.shutdown();
    assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
    assertEquals("IN_AUCTION", itemDao.findById(testItem.getId()).orElseThrow().getStatus());
  }

  @Test
  @DisplayName("Listed item without bids -> cancel auction and soft-delete item atomically")
  void listedItemWithoutBidsCanBeRemoved() {
    Auction auction = createExistingAuction(AuctionStatus.OPEN);
    setItemStatus("IN_AUCTION");

    auctionService.removeItemWithoutBids(testItem.getId(), testSeller.getId(), "SELLER");

    assertEquals(
        AuctionStatus.CANCELED, auctionDao.findById(auction.getId()).orElseThrow().getStatus());
    assertEquals("REMOVED", itemDao.findById(testItem.getId()).orElseThrow().getStatus());
  }

  @Test
  @DisplayName("Committed bid before seller cancellation -> cancellation is rejected")
  void committedBidBlocksConcurrentSellerCancellation() throws Exception {
    Auction auction = createExistingAuction(AuctionStatus.RUNNING);
    setItemStatus("IN_AUCTION");
    User bidder = userDao.insert(new Bidder("bidder", "hash", "bidder@test.com"));
    CountDownLatch auctionLocked = new CountDownLatch(1);
    CountDownLatch allowBidCommit = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);

    Future<?> bidFuture =
        pool.submit(
            () ->
                jdbi.useTransaction(
                    handle -> {
                      auctionDao.findByIdForUpdate(handle, auction.getId());
                      auctionLocked.countDown();
                      try {
                        assertTrue(allowBidCommit.await(5, TimeUnit.SECONDS));
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                      }
                      bidTransactionDao.insert(
                          handle,
                          new BidTransaction(
                              auction.getId(), bidder.getId(), new BigDecimal("200000"), false));
                    }));

    assertTrue(auctionLocked.await(5, TimeUnit.SECONDS));
    Future<?> cancelFuture =
        pool.submit(
            () ->
                assertThrows(
                    IllegalStateException.class,
                    () -> auctionService.delete(auction.getId(), testSeller.getId(), "SELLER")));
    allowBidCommit.countDown();

    bidFuture.get(5, TimeUnit.SECONDS);
    cancelFuture.get(5, TimeUnit.SECONDS);
    pool.shutdown();
    assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
    assertEquals(
        AuctionStatus.RUNNING, auctionDao.findById(auction.getId()).orElseThrow().getStatus());
  }

  private Auction createExistingAuction(AuctionStatus status) {
    Auction auction =
        new Auction(
            testItem.getId(),
            new BigDecimal("100000"),
            LocalDateTime.now().plusHours(1),
            LocalDateTime.now().plusHours(2));
    auction.setSellerId(testSeller.getId());
    auction.setStatus(status);
    return auctionDao.insert(auction);
  }

  private CreateAuctionRequest newCreateRequest(Long itemId) {
    CreateAuctionRequest request = new CreateAuctionRequest();
    request.setItemId(itemId);
    request.setStartingPrice(new BigDecimal("200000"));
    request.setStartTime(LocalDateTime.now().plusHours(3));
    request.setEndTime(LocalDateTime.now().plusHours(4));
    return request;
  }

  private void setItemStatus(String status) {
    testItem.setStatus(status);
    itemDao.update(testItem);
  }
}
