package com.auction.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.auction.config.DatabaseConfig;
import com.auction.dao.AuctionDao;
import com.auction.dao.AutoBidConfigDao;
import com.auction.dao.BidTransactionDao;
import com.auction.dao.ItemDao;
import com.auction.dao.UserDao;
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

class AuctionSchedulerSettlementTest {

  private static Jdbi jdbi;
  private static UserDao userDao;
  private static ItemDao itemDao;
  private static AuctionDao auctionDao;
  private static AuctionScheduler scheduler;
  private static BidService bidService;

  private User seller;
  private User bidder;

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
    AutoBidConfigDao autoBidConfigDao = new AutoBidConfigDao(jdbi);
    BidTransactionDao bidTransactionDao = new BidTransactionDao(jdbi);
    AuctionEventManager eventManager = new AuctionEventManager();
    AuctionService auctionService =
        new AuctionService(auctionDao, itemDao, userDao, eventManager, jdbi, bidTransactionDao);
    AutoBidStrategy autoBidStrategy = new AutoBidStrategy(autoBidConfigDao, userDao);
    var wsHandler = new com.auction.controller.AuctionWebSocketHandler(eventManager, jdbi);
    scheduler = new AuctionScheduler(auctionDao, userDao, itemDao, eventManager, jdbi, wsHandler);
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
  void init() {
    jdbi.useHandle(
        handle -> {
          handle.execute("TRUNCATE TABLE auto_bid_configs CASCADE");
          handle.execute("TRUNCATE TABLE bid_transactions CASCADE");
          handle.execute("TRUNCATE TABLE auctions CASCADE");
          handle.execute("TRUNCATE TABLE items CASCADE");
          handle.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
        });

    seller = userDao.insert(new Seller("scheduler_seller", "hash", "seller@test.com"));

    Bidder testBidder = new Bidder("scheduler_bidder", "hash", "bidder@test.com");
    testBidder.setBalance(new BigDecimal("500000"));
    bidder = userDao.insert(testBidder);
  }

  @Test
  @DisplayName("Settlement fallback chỉ release đúng reservation của phiên hiện tại")
  void insufficientBalanceFallbackOnlyReleasesCurrentAuctionReservation() throws Exception {
    reserveBidderBalance(new BigDecimal("1000000"));
    Auction auctionA = createRunningAuction("Item A", new BigDecimal("600000"));
    Auction auctionB = createRunningAuction("Item B", new BigDecimal("400000"));

    invokeSettleAndClose(auctionA.getId());

    User foundBidder = userDao.findById(bidder.getId()).orElseThrow();
    Auction foundAuctionA = auctionDao.findById(auctionA.getId()).orElseThrow();
    Auction foundAuctionB = auctionDao.findById(auctionB.getId()).orElseThrow();

    assertEquals(0, new BigDecimal("400000").compareTo(foundBidder.getReservedBalance()));
    assertEquals(AuctionStatus.FINISHED, foundAuctionA.getStatus());
    assertEquals(AuctionStatus.RUNNING, foundAuctionB.getStatus());
  }

  @Test
  @DisplayName("Settlement dùng bidder và giá mới nhất sau khi refetch trong transaction")
  void settlementUsesLatestBidAfterStaleSchedulerSnapshot() throws Exception {
    User oldLeader = createBidder("old_leader", new BigDecimal("1000000"));
    User newLeader = createBidder("new_leader", new BigDecimal("1000000"));
    reserveBalance(oldLeader.getId(), new BigDecimal("600000"));
    Auction auction =
        createRunningAuction("Race Item", oldLeader.getId(), new BigDecimal("600000"));

    Auction staleSnapshot = auctionDao.findById(auction.getId()).orElseThrow();
    commitNewWinningBid(
        staleSnapshot.getId(), oldLeader.getId(), newLeader.getId(), new BigDecimal("800000"));

    invokeSettleAndClose(staleSnapshot.getId());

    User foundOldLeader = userDao.findById(oldLeader.getId()).orElseThrow();
    User foundNewLeader = userDao.findById(newLeader.getId()).orElseThrow();
    User foundSeller = userDao.findById(seller.getId()).orElseThrow();
    Auction foundAuction = auctionDao.findById(staleSnapshot.getId()).orElseThrow();

    assertEquals(0, new BigDecimal("1000000").compareTo(foundOldLeader.getBalance()));
    assertEquals(0, BigDecimal.ZERO.compareTo(foundOldLeader.getReservedBalance()));
    assertEquals(0, new BigDecimal("200000").compareTo(foundNewLeader.getBalance()));
    assertEquals(0, BigDecimal.ZERO.compareTo(foundNewLeader.getReservedBalance()));
    assertEquals(0, new BigDecimal("800000").compareTo(foundSeller.getBalance()));
    assertEquals(newLeader.getId(), foundAuction.getLeadingBidderId());
    assertEquals(0, new BigDecimal("800000").compareTo(foundAuction.getCurrentPrice()));
    assertEquals(AuctionStatus.PAID, foundAuction.getStatus());
  }

  @Test
  @DisplayName("Scheduler không settle sớm khi bid cuối giờ đã gia hạn anti-snipe")
  void settlingClaimRespectsAntiSnipeExtension() throws Exception {
    LocalDateTime scanNow = LocalDateTime.now();
    User antiSnipeBidder = createBidder("anti_snipe_bidder", new BigDecimal("1000000"));
    Auction auction =
        createRunningAuction(
            "Anti Snipe Item", null, new BigDecimal("100000"), scanNow.minusSeconds(1));

    bidService.placeBid(auction.getId(), antiSnipeBidder.getId(), new BigDecimal("200000"), false);
    invokeSettleAndClose(auction.getId(), scanNow);

    Auction foundAuction = auctionDao.findById(auction.getId()).orElseThrow();
    User foundBidder = userDao.findById(antiSnipeBidder.getId()).orElseThrow();

    assertEquals(AuctionStatus.RUNNING, foundAuction.getStatus());
    assertEquals(antiSnipeBidder.getId(), foundAuction.getLeadingBidderId());
    assertEquals(0, new BigDecimal("200000").compareTo(foundAuction.getCurrentPrice()));
    assertEquals(0, new BigDecimal("200000").compareTo(foundBidder.getReservedBalance()));
  }

  private void reserveBidderBalance(BigDecimal amount) {
    reserveBalance(bidder.getId(), amount);
  }

  private void reserveBalance(Long bidderId, BigDecimal amount) {
    jdbi.useTransaction(
        handle -> userDao.updateReservedBalanceInTransaction(handle, bidderId, amount));
  }

  private Auction createRunningAuction(String itemName, BigDecimal currentPrice) {
    return createRunningAuction(itemName, bidder.getId(), currentPrice);
  }

  private Auction createRunningAuction(String itemName, Long leaderId, BigDecimal currentPrice) {
    return createRunningAuction(
        itemName, leaderId, currentPrice, LocalDateTime.now().minusMinutes(1));
  }

  private Auction createRunningAuction(
      String itemName, Long leaderId, BigDecimal currentPrice, LocalDateTime endTime) {
    Item item = itemDao.insert(new Item(itemName, "Settlement test item", seller.getId(), "ART"));
    Auction auction =
        new Auction(item.getId(), currentPrice, LocalDateTime.now().minusHours(2), endTime);
    auction.setSellerId(seller.getId());
    auction.setCurrentPrice(currentPrice);
    auction.setLeadingBidderId(leaderId);
    auction.setStatus(AuctionStatus.RUNNING);
    return auctionDao.insert(auction);
  }

  private User createBidder(String username, BigDecimal balance) {
    Bidder newBidder = new Bidder(username, "hash", username + "@test.com");
    newBidder.setBalance(balance);
    return userDao.insert(newBidder);
  }

  private void commitNewWinningBid(
      Long auctionId, Long oldLeaderId, Long newLeaderId, BigDecimal newPrice) {
    jdbi.useTransaction(
        handle -> {
          userDao.releaseReservedBalanceInTransaction(
              handle, oldLeaderId, new BigDecimal("600000"));
          userDao.updateReservedBalanceInTransaction(handle, newLeaderId, newPrice);
          handle
              .createUpdate(
                  """
                  UPDATE auctions
                  SET current_price = :price,
                      leading_bidder_id = :bidderId,
                      updated_at = NOW()
                  WHERE id = :auctionId
                  """)
              .bind("price", newPrice)
              .bind("bidderId", newLeaderId)
              .bind("auctionId", auctionId)
              .execute();
        });
  }

  private void invokeSettleAndClose(Long auctionId) throws Exception {
    invokeSettleAndClose(auctionId, LocalDateTime.now());
  }

  private void invokeSettleAndClose(Long auctionId, LocalDateTime now) throws Exception {
    Method settleAndClose =
        AuctionScheduler.class.getDeclaredMethod("settleAndClose", Long.class, LocalDateTime.class);
    settleAndClose.setAccessible(true);
    settleAndClose.invoke(scheduler, auctionId, now);
  }
}
