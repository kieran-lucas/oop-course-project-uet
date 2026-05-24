package com.auction.service;

import com.auction.controller.AuctionWebSocketHandler;
import com.auction.dao.AuctionDao;
import com.auction.dao.ItemDao;
import com.auction.dao.UserDao;
import com.auction.dao.WalletTransactionDao;
import com.auction.dto.BidUpdateMessage;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.User;
import com.auction.pattern.observer.AuctionEventManager;
import com.auction.util.NotificationFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Bộ lập lịch nền — tự động chuyển trạng thái phiên đấu giá theo thời gian.
 *
 * <p><b>Vai trò trong hệ thống:</b> AuctionScheduler là "đồng hồ" của hệ thống. Nó chạy liên tục
 * trong nền (background thread), cứ mỗi {@value #SCAN_INTERVAL_SECONDS} giây lại quét toàn bộ các
 * phiên đấu giá đang hoạt động và thực hiện chuyển trạng thái nếu điều kiện thỏa mãn:
 *
 * <pre>
 *   OPEN ──(startTime đến)──► RUNNING ──(endTime qua)──► SETTLING ──► PAID/FINISHED
 * </pre>
 *
 * <p>Khi settlement hoàn tất, scheduler đồng bộ cả auction status và item status trong cùng
 * transaction: phiên bán thành công đưa item về {@code SOLD}; phiên không bán được đưa item về
 * {@code AVAILABLE} để seller có thể tạo phiên mới.
 */
public class AuctionScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(AuctionScheduler.class);
  private static final int SCAN_INTERVAL_SECONDS = 5;

  private final AuctionDao auctionDao;
  private final UserDao userDao;
  private final ItemDao itemDao;
  private final AuctionEventManager eventManager;
  private final org.jdbi.v3.core.Jdbi jdbi;
  private final AuctionWebSocketHandler wsHandler;

  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "auction-scheduler");
            t.setDaemon(true);
            return t;
          });

  private ScheduledFuture<?> scheduledTask;
  private final AtomicBoolean running = new AtomicBoolean(false);

  public AuctionScheduler(
      AuctionDao auctionDao,
      UserDao userDao,
      ItemDao itemDao,
      AuctionEventManager eventManager,
      org.jdbi.v3.core.Jdbi jdbi,
      AuctionWebSocketHandler wsHandler) {
    this.auctionDao = auctionDao;
    this.userDao = userDao;
    this.itemDao = itemDao;
    this.eventManager = eventManager;
    this.jdbi = jdbi;
    this.wsHandler = wsHandler;
  }

  public void start() {
    if (!running.compareAndSet(false, true)) {
      LOG.warn("AuctionScheduler is already running, ignoring duplicate start() call");
      return;
    }

    scheduledTask =
        scheduler.scheduleAtFixedRate(
            this::scanAndTransition, 0, SCAN_INTERVAL_SECONDS, TimeUnit.SECONDS);

    LOG.info("AuctionScheduler started — scanning every {}s", SCAN_INTERVAL_SECONDS);
  }

  public void stop() {
    if (scheduledTask != null) {
      scheduledTask.cancel(false);
    }
    scheduler.shutdown();
    try {
      if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
        scheduler.shutdownNow();
      }
    } catch (InterruptedException ie) {
      scheduler.shutdownNow();
      Thread.currentThread().interrupt();
    }
    running.set(false);
    LOG.info("AuctionScheduler stopped");
  }

  void scanAndTransition() {
    try {
      LocalDateTime now = LocalDateTime.now();
      LOG.debug("Scheduler scan at {}", now);

      openToRunning(now);
      runningToFinished(now);
    } catch (Exception e) {
      LOG.error("Error in AuctionScheduler.scanAndTransition()", e);
    }
  }

  private void openToRunning(LocalDateTime now) {
    List<Long> ids = auctionDao.findDueAuctionIds("OPEN", now);
    for (Long id : ids) {
      try {
        boolean ok = auctionDao.atomicTransition(id, "OPEN", "RUNNING");
        if (ok) {
          LOG.info("Auction #{} OPEN → RUNNING", id);
        } else {
          LOG.debug("Auction #{} skipped — already transitioned", id);
        }
      } catch (Exception e) {
        LOG.error("Cannot transition auction #{} to RUNNING", id, e);
      }
    }
  }

  private void runningToFinished(LocalDateTime now) {
    List<Long> ids = auctionDao.findExpiredAuctionIds("RUNNING", now);
    for (Long id : ids) {
      try {
        SettlementResult result = settleAndClose(id, now);
        if (result != null) {
          notifyAuctionEnded(result.auction);
          if (result.userNotifications != null) {
            for (UserNotification note : result.userNotifications) {
              try {
                wsHandler.pushUserNotification(note.userId, note.message);
              } catch (Exception e) {
                LOG.error(
                    "Cannot push USER_NOTIFICATION to user #{}: {}", note.userId, e.getMessage());
              }
            }
          }
          if (result.balanceChanges != null) {
            for (BalanceChange change : result.balanceChanges) {
              try {
                wsHandler.notifyBalanceChange(
                    change.userId,
                    change.newBalance,
                    change.delta,
                    change.message,
                    change.notificationType);
              } catch (Exception e) {
                LOG.error(
                    "Cannot notify BALANCE_UPDATED to user #{}: {}", change.userId, e.getMessage());
              }
            }
          }
        }
      } catch (Exception e) {
        LOG.error("Cannot finalize auction #{}", id, e);
      }
    }
  }

  private record BalanceChange(
      Long userId,
      BigDecimal newBalance,
      BigDecimal delta,
      String message,
      String notificationType) {}

  private record UserNotification(Long userId, String message, String notificationType) {}

  private record SettlementResult(
      Auction auction,
      List<UserNotification> userNotifications,
      List<BalanceChange> balanceChanges) {}

  private SettlementResult settleAndClose(Long auctionId, LocalDateTime now) {
    MDC.put("auctionId", String.valueOf(auctionId));
    try {
      final Auction[] settledAuction = new Auction[1];
      final List<BalanceChange> balanceChanges = new java.util.ArrayList<>();
      final List<UserNotification> userNotifications = new java.util.ArrayList<>();
      jdbi.useTransaction(
          handle -> {
            int claimed =
                handle
                    .createUpdate(
                        """
                        UPDATE auctions
                        SET status = 'SETTLING', updated_at = NOW()
                        WHERE id = :id AND status = 'RUNNING' AND end_time <= :now
                        """)
                    .bind("id", auctionId)
                    .bind("now", now)
                    .execute();
            if (claimed == 0) {
              LOG.info(
                  "Auction #{} not eligible for SETTLING at {}, may have been extended or already processed",
                  auctionId,
                  now);
              return;
            }

            Optional<Auction> refetchedAuction =
                auctionDao.findByIdForUpdateOptional(handle, auctionId);
            if (refetchedAuction.isEmpty()) {
              LOG.warn(
                  "Auction #{} not found after claiming SETTLING, skipping settlement", auctionId);
              return;
            }

            Auction auction = refetchedAuction.get();
            Long winnerId = auction.getLeadingBidderId();
            Long sellerId = auction.getSellerId();
            String finalItemStatus;

            String itemName =
                handle
                    .createQuery("SELECT name FROM items WHERE id = :id")
                    .bind("id", auction.getItemId())
                    .mapTo(String.class)
                    .findOne()
                    .orElse(null);
            String auctionLabel = NotificationFormat.auctionName(auction.getId(), itemName);

            List<Long> bidderAudience =
                handle
                    .createQuery(
                        "SELECT DISTINCT bidder_id FROM bid_transactions WHERE auction_id = :id")
                    .bind("id", auction.getId())
                    .mapTo(Long.class)
                    .list();

            if (winnerId != null) {
              BigDecimal price = auction.getCurrentPrice();

              User winner = userDao.findByIdForUpdate(handle, winnerId);
              BigDecimal balance =
                  winner.getBalance() != null ? winner.getBalance() : BigDecimal.ZERO;
              String winnerLabel = NotificationFormat.user(winner.getUsername());

              if (balance.compareTo(price) >= 0) {
                BigDecimal balanceBefore = balance;
                int winnerRows =
                    handle
                        .createUpdate(
                            """
                        UPDATE users
                        SET balance = balance - :price,
                            reserved_balance = reserved_balance - :price
                        WHERE id = :userId
                          AND reserved_balance >= :price
                        """)
                        .bind("price", price)
                        .bind("userId", winnerId)
                        .execute();
                if (winnerRows == 0) {
                  throw new IllegalStateException(
                      "Cannot settle auction #"
                          + auction.getId()
                          + ": bidder #"
                          + winnerId
                          + " has insufficient reserved balance");
                }
                WalletTransactionDao.insert(
                    handle,
                    winnerId,
                    auction.getId(),
                    null,
                    "WIN_CONSUME",
                    price,
                    "settlement:" + auction.getId());
                LOG.info(
                    "Auction #{}: deducted {} from bidder #{}", auction.getId(), price, winnerId);
                LOG.info(
                    "Auction #{}: bidder #{} balance {} → {}",
                    auction.getId(),
                    winnerId,
                    balanceBefore,
                    balanceBefore.subtract(price));

                BigDecimal winnerNewBalance = balanceBefore.subtract(price);
                BigDecimal winnerDelta = price.negate();
                String winMsg =
                    String.format(
                        Locale.GERMANY,
                        "You won auction %s at %,d VND."
                            + " Payment completed successfully."
                            + " Balance change: - %,d VND",
                        auctionLabel,
                        price.longValue(),
                        price.longValue());
                balanceChanges.add(
                    new BalanceChange(
                        winnerId, winnerNewBalance, winnerDelta, winMsg, "AUCTION_WON"));

                if (sellerId != null) {
                  User seller = userDao.findByIdForUpdate(handle, sellerId);
                  BigDecimal sellerBalanceBefore =
                      seller.getBalance() != null ? seller.getBalance() : BigDecimal.ZERO;
                  handle
                      .createUpdate(
                          "UPDATE users SET balance = balance + :price WHERE id = :userId")
                      .bind("price", price)
                      .bind("userId", sellerId)
                      .execute();
                  WalletTransactionDao.insert(
                      handle,
                      sellerId,
                      auction.getId(),
                      null,
                      "SELLER_PAYOUT",
                      price,
                      "settlement:" + auction.getId());
                  LOG.info(
                      "Auction #{}: credited {} to seller #{}", auction.getId(), price, sellerId);
                  BigDecimal sellerNewBalance = sellerBalanceBefore.add(price);
                  LOG.info(
                      "Auction #{}: seller #{} balance {} → {}",
                      auction.getId(),
                      sellerId,
                      sellerBalanceBefore,
                      sellerNewBalance);
                  String sellerPayoutMsg =
                      String.format(
                          Locale.GERMANY,
                          "You have successfully received payment from %s for auction %s."
                              + " Balance change: + %,d VND",
                          winnerLabel,
                          auctionLabel,
                          price.longValue());
                  balanceChanges.add(
                      new BalanceChange(
                          sellerId, sellerNewBalance, price, sellerPayoutMsg, "SELLER_PAYOUT"));
                }

                String commonMsg =
                    String.format(
                        Locale.GERMANY,
                        "Auction %s has ended. Winner: %s at %,d VND",
                        auctionLabel,
                        winnerLabel,
                        price.longValue());
                broadcastAuctionResult(
                    handle, userNotifications, bidderAudience, sellerId, commonMsg);

                auction.setStatus(AuctionStatus.PAID);
                finalItemStatus = "SOLD";
              } else {
                LOG.warn(
                    "Auction #{}: bidder #{} has insufficient balance ({}) to pay {}. Transitioning to FINISHED.",
                    auction.getId(),
                    winnerId,
                    balance,
                    price);
                userDao.releaseReservedBalanceInTransaction(handle, winnerId, price);
                WalletTransactionDao.insert(
                    handle,
                    winnerId,
                    auction.getId(),
                    null,
                    "RELEASE",
                    price,
                    "settlement_insufficient_balance:" + auction.getId());

                String commonMsg =
                    String.format(
                        Locale.GERMANY,
                        "Auction %s has ended, but payment failed: winner %s did not have enough funds to pay %,d VND",
                        auctionLabel,
                        winnerLabel,
                        price.longValue());
                broadcastAuctionResult(
                    handle, userNotifications, bidderAudience, sellerId, commonMsg);

                auction.setStatus(AuctionStatus.FINISHED);
                finalItemStatus = "AVAILABLE";
              }
            } else {
              String commonMsg =
                  String.format(
                      Locale.GERMANY,
                      "Auction %s has ended with no bids. The auction was unsuccessful.",
                      auctionLabel);
              broadcastAuctionResult(
                  handle, userNotifications, bidderAudience, sellerId, commonMsg);
              auction.setStatus(AuctionStatus.FINISHED);
              finalItemStatus = "AVAILABLE";
            }

            deactivateActiveAutoBidsInTransaction(handle, auction.getId());
            updateItemStatusInSettlement(handle, auction, finalItemStatus);
            auctionDao.updateInTransaction(handle, auction);
            settledAuction[0] = auction;
          });

      if (settledAuction[0] == null) {
        return null;
      }
      LOG.info(
          "Auction #{} → {} (endTime={}, winner={})",
          settledAuction[0].getId(),
          settledAuction[0].getStatus(),
          settledAuction[0].getEndTime(),
          settledAuction[0].getLeadingBidderId());
      return new SettlementResult(settledAuction[0], userNotifications, balanceChanges);
    } finally {
      MDC.remove("auctionId");
    }
  }

  private void deactivateActiveAutoBidsInTransaction(Handle handle, Long auctionId) {
    handle
        .createUpdate(
            """
            UPDATE auto_bid_configs
            SET active = false,
                status = 'STOPPED',
                failure_reason = NULL
            WHERE auction_id = :auctionId AND status = 'ACTIVE'
            """)
        .bind("auctionId", auctionId)
        .execute();
  }

  private void updateItemStatusInSettlement(Handle handle, Auction auction, String status) {
    if (auction.getItemId() == null || status == null) {
      return;
    }
    itemDao.updateStatusInTransaction(handle, auction.getItemId(), status);
  }

  private void broadcastAuctionResult(
      Handle handle,
      List<UserNotification> userNotifications,
      List<Long> bidderAudience,
      Long sellerId,
      String message) {
    java.util.Set<Long> seen = new java.util.LinkedHashSet<>();
    if (bidderAudience != null) {
      seen.addAll(bidderAudience);
    }
    if (sellerId != null) {
      seen.add(sellerId);
    }
    for (Long recipientId : seen) {
      handle.execute(
          "INSERT INTO notifications (user_id, message, notification_type)"
              + " VALUES (?, ?, 'AUCTION_RESULT')",
          recipientId,
          message);
      userNotifications.add(new UserNotification(recipientId, message, "AUCTION_RESULT"));
    }
  }

  private void notifyAuctionEnded(Auction auction) {
    try {
      String winnerName = null;
      if (auction.getLeadingBidderId() != null) {
        winnerName =
            userDao.findById(auction.getLeadingBidderId()).map(User::getUsername).orElse(null);
      }
      BidUpdateMessage msg =
          BidUpdateMessage.auctionEnded(
              auction.getId(), auction.getCurrentPrice(), auction.getLeadingBidderId(), winnerName);
      eventManager.notifyAuctionEnd(auction.getId(), msg);
    } catch (Exception e) {
      LOG.error("Cannot broadcast AUCTION_ENDED for auction #{}", auction.getId(), e);
    }
  }

  public boolean isRunning() {
    return running.get();
  }
}
