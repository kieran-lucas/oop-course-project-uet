package com.auction.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.auction.config.DatabaseConfig;
import com.auction.dao.AuctionDao;
import com.auction.dao.ItemDao;
import com.auction.dao.UserDao;
import com.auction.dto.CreateAuctionRequest;
import com.auction.exception.DuplicateException;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
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

class AuctionServiceCreateIntegrationTest {

  private static Jdbi jdbi;
  private static UserDao userDao;
  private static ItemDao itemDao;
  private static AuctionDao auctionDao;
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
    auctionService =
        new AuctionService(auctionDao, itemDao, userDao, new AuctionEventManager(), jdbi);
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
    testItem = new Item("Single item", "Only one unit", testSeller.getId(), "ELECTRONICS");
    testItem = itemDao.insert(testItem);
  }

  @Test
  @DisplayName("Same item already RUNNING -> reject")
  void sameItemRunningAuctionRejectsCreate() {
    createExistingAuction(AuctionStatus.RUNNING);

    assertThrows(
        DuplicateException.class,
        () -> auctionService.create(newCreateRequest(testItem.getId()), testSeller.getId()));
  }

  @Test
  @DisplayName("Same item already OPEN -> reject")
  void sameItemOpenAuctionRejectsCreate() {
    createExistingAuction(AuctionStatus.OPEN);

    assertThrows(
        DuplicateException.class,
        () -> auctionService.create(newCreateRequest(testItem.getId()), testSeller.getId()));
  }

  @Test
  @DisplayName("Same item already SETTLING -> reject")
  void sameItemSettlingAuctionRejectsCreate() {
    createExistingAuction(AuctionStatus.SETTLING);

    assertThrows(
        DuplicateException.class,
        () -> auctionService.create(newCreateRequest(testItem.getId()), testSeller.getId()));
  }

  @Test
  @DisplayName("Same item CANCELED and AVAILABLE -> allowed")
  void sameItemCanceledAuctionAllowsCreate() {
    createExistingAuction(AuctionStatus.CANCELED);

    Auction created = auctionService.create(newCreateRequest(testItem.getId()), testSeller.getId());

    assertEquals(AuctionStatus.OPEN, created.getStatus());
    assertEquals(2, auctionDao.findByItemId(testItem.getId()).size());
    assertEquals("IN_AUCTION", itemDao.findById(testItem.getId()).orElseThrow().getStatus());
  }

  @Test
  @DisplayName("Same item PAID -> reject")
  void sameItemPaidAuctionRejectsCreate() {
    createExistingAuction(AuctionStatus.PAID);

    assertThrows(
        DuplicateException.class,
        () -> auctionService.create(newCreateRequest(testItem.getId()), testSeller.getId()));
  }

  @Test
  @DisplayName("Item SOLD -> reject")
  void soldItemRejectsCreate() {
    testItem.setStatus("SOLD");
    itemDao.update(testItem);

    assertThrows(
        DuplicateException.class,
        () -> auctionService.create(newCreateRequest(testItem.getId()), testSeller.getId()));
  }

  @Test
  @DisplayName("Concurrent create same item creates only one auction")
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
}
