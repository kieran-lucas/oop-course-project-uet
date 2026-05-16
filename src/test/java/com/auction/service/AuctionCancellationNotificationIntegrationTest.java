package com.auction.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.auction.config.DatabaseConfig;
import com.auction.dao.AuctionDao;
import com.auction.dao.BidTransactionDao;
import com.auction.dao.ItemDao;
import com.auction.dao.UserDao;
import com.auction.dto.BidUpdateMessage;
import com.auction.model.AuctionStatus;
import com.auction.pattern.observer.AuctionEventManager;
import java.math.BigDecimal;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Auction cancellation notification transaction")
class AuctionCancellationNotificationIntegrationTest {

  private static Jdbi jdbi;

  private AuctionEventManager eventManager;
  private AuctionService auctionService;

  @BeforeAll
  static void connectDb() {
    try {
      jdbi = DatabaseConfig.create();
    } catch (Exception e) {
      Assumptions.abort("No DB available, skipping: " + e.getMessage());
    }
  }

  @BeforeEach
  void setUp() {
    jdbi.useHandle(
        handle ->
            handle.execute(
                """
                TRUNCATE notifications, bid_transactions, auto_bid_configs, auctions, items, users
                RESTART IDENTITY CASCADE
                """));

    eventManager = mock(AuctionEventManager.class);
    auctionService =
        new AuctionService(
            new AuctionDao(jdbi),
            new ItemDao(jdbi),
            new UserDao(jdbi),
            eventManager,
            jdbi,
            new BidTransactionDao(jdbi));
  }

  @Test
  @DisplayName("Cancel RUNNING auction persists status, release, and notification together")
  void cancelRunningAuctionCommitsNotificationWithState() {
    long sellerId = insertUser("cancel_seller", "SELLER", "seller@test.com", "0", "0");
    long bidderId = insertUser("cancel_bidder", "BIDDER", "bidder@test.com", "1000", "250");
    long itemId = insertItem(sellerId);
    long auctionId = insertRunningAuction(itemId, sellerId, bidderId, "250");

    auctionService.delete(auctionId, 999L, "ADMIN");

    assertEquals(AuctionStatus.CANCELED.name(), auctionStatus(auctionId));
    assertMoney("0.00", reservedBalance(bidderId));
    assertEquals(1L, cancellationNotificationCount(bidderId, auctionId));
    verify(eventManager).notifyAuctionEnd(eq(auctionId), any(BidUpdateMessage.class));
  }

  @Test
  @DisplayName("Rollback leaves no cancellation notification")
  void rollbackDoesNotLeaveOrphanCancellationNotification() {
    long sellerId = insertUser("rollback_seller", "SELLER", "rollback-seller@test.com", "0", "0");
    long bidderId =
        insertUser("rollback_bidder", "BIDDER", "rollback-bidder@test.com", "1000", "0");
    long itemId = insertItem(sellerId);
    long auctionId = insertRunningAuction(itemId, sellerId, bidderId, "250");

    assertThrows(
        IllegalStateException.class, () -> auctionService.delete(auctionId, 999L, "ADMIN"));

    assertEquals(AuctionStatus.RUNNING.name(), auctionStatus(auctionId));
    assertMoney("0.00", reservedBalance(bidderId));
    assertEquals(0L, cancellationNotificationCount(bidderId, auctionId));
    verify(eventManager, never()).notifyAuctionEnd(eq(auctionId), any(BidUpdateMessage.class));
  }

  @Test
  @DisplayName("Canceling an already canceled auction is rejected — no duplicate notification")
  void alreadyCanceledAuctionDoesNotDuplicateNotification() {
    long sellerId = insertUser("duplicate_seller", "SELLER", "duplicate-seller@test.com", "0", "0");
    long bidderId =
        insertUser("duplicate_bidder", "BIDDER", "duplicate-bidder@test.com", "1000", "0");
    long itemId = insertItem(sellerId);
    long auctionId =
        insertAuction(itemId, sellerId, bidderId, "250", AuctionStatus.CANCELED.name());

    // delete() now refuses to re-cancel an already-CANCELED auction (the ADMIN branch
    // only accepts OPEN / RUNNING and directs the admin to hardDelete for any other
    // status). The important invariant the test enforces is that no duplicate
    // AUCTION_CANCELED notification rows are inserted — that's true precisely because
    // the call throws before reaching the notification insert.
    assertThrows(
        IllegalStateException.class, () -> auctionService.delete(auctionId, 999L, "ADMIN"));

    assertEquals(AuctionStatus.CANCELED.name(), auctionStatus(auctionId));
    assertEquals(0L, cancellationNotificationCount(bidderId, auctionId));
    verify(eventManager, never()).notifyAuctionEnd(eq(auctionId), any(BidUpdateMessage.class));
  }

  private long insertUser(
      String username, String role, String email, String balance, String reservedBalance) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(
                    """
                    INSERT INTO users (
                        username, password_hash, email, role, balance, reserved_balance
                    )
                    VALUES (:username, 'hash', :email, :role, :balance, :reservedBalance)
                    RETURNING id
                    """)
                .bind("username", username)
                .bind("email", email)
                .bind("role", role)
                .bind("balance", new BigDecimal(balance))
                .bind("reservedBalance", new BigDecimal(reservedBalance))
                .mapTo(Long.class)
                .one());
  }

  private long insertItem(long sellerId) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(
                    """
                    INSERT INTO items (seller_id, name, description, category, status)
                    VALUES (:sellerId, 'Cancel test item', 'desc', 'ART', 'IN_AUCTION')
                    RETURNING id
                    """)
                .bind("sellerId", sellerId)
                .mapTo(Long.class)
                .one());
  }

  private long insertRunningAuction(
      long itemId, long sellerId, long bidderId, String currentPrice) {
    return insertAuction(itemId, sellerId, bidderId, currentPrice, AuctionStatus.RUNNING.name());
  }

  private long insertAuction(
      long itemId, long sellerId, long bidderId, String currentPrice, String status) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(
                    """
                    INSERT INTO auctions (
                        item_id, seller_id, starting_price, current_price, leading_bidder_id,
                        start_time, end_time, status
                    )
                    VALUES (
                        :itemId, :sellerId, 100, :currentPrice, :bidderId,
                        NOW() - INTERVAL '10 minutes', NOW() + INTERVAL '1 hour', :status
                    )
                    RETURNING id
                    """)
                .bind("itemId", itemId)
                .bind("sellerId", sellerId)
                .bind("currentPrice", new BigDecimal(currentPrice))
                .bind("bidderId", bidderId)
                .bind("status", status)
                .mapTo(Long.class)
                .one());
  }

  private String auctionStatus(long auctionId) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery("SELECT status FROM auctions WHERE id = :id")
                .bind("id", auctionId)
                .mapTo(String.class)
                .one());
  }

  private BigDecimal reservedBalance(long userId) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery("SELECT reserved_balance FROM users WHERE id = :id")
                .bind("id", userId)
                .mapTo(BigDecimal.class)
                .one());
  }

  private long cancellationNotificationCount(long userId, long auctionId) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(
                    """
                    SELECT COUNT(*)
                    FROM notifications
                    WHERE user_id = :userId
                      AND notification_type = 'AUCTION_CANCELED'
                      AND message LIKE :message
                    """)
                .bind("userId", userId)
                .bind("message", "%#" + auctionId + "%")
                .mapTo(Long.class)
                .one());
  }

  private void assertMoney(String expected, BigDecimal actual) {
    assertTrue(
        new BigDecimal(expected).compareTo(actual) == 0,
        "Expected " + expected + " but was " + actual);
  }
}
