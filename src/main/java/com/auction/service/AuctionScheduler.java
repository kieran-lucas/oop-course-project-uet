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
 *   OPEN ──(startTime đến)──► RUNNING ──(endTime qua)──► FINISHED
 * </pre>
 *
 * <p><b>Tại sao cần class này?</b> Trạng thái phiên không tự thay đổi — nếu không có scheduler,
 * phiên sẽ mãi ở OPEN hoặc RUNNING dù thời gian đã qua. AuctionScheduler thay thế cron job ở tầng
 * application, phù hợp với project không deploy production server riêng.
 *
 * <p><b>Concurrency note:</b> ScheduledExecutorService đảm bảo mỗi lần chỉ chạy 1 task (không
 * overlap). Dùng {@code scheduleAtFixedRate} thay vì {@code scheduleWithFixedDelay} để giữ chu kỳ
 * ổn định dù task mất bao lâu.
 *
 * <p><b>Liên kết file khác:</b>
 *
 * <ul>
 *   <li>{@link AuctionDao} — query + update trạng thái trong DB
 *   <li>{@link AuctionEventManager} — broadcast thông báo đến client qua WebSocket
 *   <li>{@link BidUpdateMessage} — định dạng message gửi khi phiên kết thúc
 *   <li>{@link com.auction.App} — khởi động scheduler khi server start
 * </ul>
 */
public class AuctionScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(AuctionScheduler.class);

  /** Chu kỳ quét (giây) — đủ nhạy mà không overload DB */
  private static final int SCAN_INTERVAL_SECONDS = 5;

  private final AuctionDao auctionDao;
  private final UserDao userDao;
  private final ItemDao itemDao;
  private final AuctionEventManager eventManager;
  private final org.jdbi.v3.core.Jdbi jdbi;
  private final AuctionWebSocketHandler wsHandler;

  /** Thread pool 1 thread — đủ cho scheduler đơn giản này */
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "auction-scheduler");
            t.setDaemon(true); // không ngăn JVM tắt khi main thread kết thúc
            return t;
          });

  /** Future của task đang chạy — dùng để cancel khi shutdown */
  private ScheduledFuture<?> scheduledTask;

  /** Guard tránh start() gọi nhiều lần */
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

  // ── Lifecycle ────────────────────────────────────────────

  /**
   * Khởi động scheduler. Gọi một lần duy nhất khi server start.
   *
   * <p>Dùng {@code scheduleAtFixedRate} để task chạy đều đặn mỗi {@value #SCAN_INTERVAL_SECONDS}
   * giây, bất kể task trước mất bao lâu.
   */
  public void start() {
    if (!running.compareAndSet(false, true)) {
      LOG.warn("AuctionScheduler đã chạy rồi, bỏ qua lệnh start() thứ hai");
      return;
    }

    scheduledTask =
        scheduler.scheduleAtFixedRate(
            this::scanAndTransition,
            0, // delay ban đầu: chạy ngay khi start
            SCAN_INTERVAL_SECONDS,
            TimeUnit.SECONDS);

    LOG.info("AuctionScheduler đã khởi động — quét mỗi {}s", SCAN_INTERVAL_SECONDS);
  }

  /**
   * Dừng scheduler khi server shutdown.
   *
   * <p>Gọi trong shutdown hook của {@code App.java} để giải phóng thread pool.
   */
  public void stop() {
    if (scheduledTask != null) {
      scheduledTask.cancel(false); // không interrupt task đang chạy giữa chừng
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
    LOG.info("AuctionScheduler đã dừng");
  }

  // ── Core scan logic ──────────────────────────────────────

  /**
   * Hàm chính được gọi mỗi {@value #SCAN_INTERVAL_SECONDS} giây.
   *
   * <p>Thứ tự xử lý: chuyển OPEN→RUNNING trước, rồi RUNNING→FINISHED. Điều này xử lý đúng edge case
   * startTime == endTime.
   */
  void scanAndTransition() {
    try {
      LocalDateTime now = LocalDateTime.now();
      LOG.debug("Scheduler scan tại {}", now);

      openToRunning(now);
      runningToFinished(now);

    } catch (Exception e) {
      // Bắt tất cả exception để scheduler không chết âm thầm.
      // Nếu không có try-catch này, một RuntimeException sẽ cancel scheduledTask.
      LOG.error("Lỗi trong AuctionScheduler.scanAndTransition()", e);
    }
  }

  // ── OPEN → RUNNING ───────────────────────────────────────

  /**
   * Chuyển các phiên từ OPEN sang RUNNING khi đến giờ bắt đầu.
   *
   * @param now thời điểm hiện tại
   */
  private void openToRunning(LocalDateTime now) {
    List<Long> ids = auctionDao.findDueAuctionIds("OPEN", now);
    for (Long id : ids) {
      try {
        boolean ok = auctionDao.atomicTransition(id, "OPEN", "RUNNING");
        if (ok) {
          LOG.info("Phiên #{} OPEN → RUNNING", id);
        } else {
          LOG.debug("Phiên #{} skip — đã transition trước", id);
        }
      } catch (Exception e) {
        LOG.error("Không thể chuyển phiên #{} sang RUNNING", id, e);
      }
    }
  }

  // ── RUNNING → FINISHED ───────────────────────────────────

  /**
   * Chuyển các phiên từ RUNNING sang FINISHED khi hết giờ.
   *
   * <p>Sau khi cập nhật DB, broadcast AUCTION_ENDED qua WebSocket để tất cả client đang xem phiên
   * nhận được thông báo ngay lập tức.
   *
   * @param now thời điểm hiện tại
   */
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
                    "Không thể push USER_NOTIFICATION cho user #{}: {}",
                    note.userId,
                    e.getMessage());
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
                    "Không thể notify BALANCE_UPDATED cho user #{}: {}",
                    change.userId,
                    e.getMessage());
              }
            }
          }
        }
      } catch (Exception e) {
        LOG.error("Không thể kết thúc phiên #{}", id, e);
      }
    }
  }

  /** Một sự kiện biến động số dư cần push qua WS sau khi transaction commit. */
  private record BalanceChange(
      Long userId,
      BigDecimal newBalance,
      BigDecimal delta,
      String message,
      String notificationType) {}

  /** Thông báo chung (không kèm biến động số dư) cần push trước balance change. */
  private record UserNotification(Long userId, String message, String notificationType) {}

  /**
   * Kết quả của một lần settlement: phiên đã đóng + danh sách user notifications (kết quả phiên)
   * cần phát trước balance changes.
   */
  private record SettlementResult(
      Auction auction,
      List<UserNotification> userNotifications,
      List<BalanceChange> balanceChanges) {}

  /**
   * Thanh toán và đóng phiên: - Nếu có người thắng: trừ tiền bidder, cộng tiền seller, status →
   * PAID. - Nếu không ai bid: status → FINISHED.
   */
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
                  "Phiên #{} chưa đủ điều kiện SETTLING tại {}, có thể đã được gia hạn hoặc xử lý trước đó",
                  auctionId,
                  now);
              return;
            }

            Optional<Auction> refetchedAuction =
                auctionDao.findByIdForUpdateOptional(handle, auctionId);
            if (refetchedAuction.isEmpty()) {
              LOG.warn(
                  "Không tìm thấy phiên #{} sau khi claim SETTLING, bỏ qua settlement", auctionId);
              return;
            }

            Auction auction = refetchedAuction.get();
            Long winnerId = auction.getLeadingBidderId();
            Long sellerId = auction.getSellerId();

            if (winnerId != null) {
              BigDecimal price = auction.getCurrentPrice();

              // Khóa row winner để chuyển khoản giữ chỗ thành thanh toán an toàn
              User winner = userDao.findByIdForUpdate(handle, winnerId);
              BigDecimal balance =
                  winner.getBalance() != null ? winner.getBalance() : BigDecimal.ZERO;

              if (balance.compareTo(price) >= 0) {
                BigDecimal balanceBefore = balance;
                // Trừ tiền bidder và release phần tiền đã giữ cho bid thắng.
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
                      "Không thể thanh toán phiên #"
                          + auction.getId()
                          + ": tiền giữ chỗ của bidder #"
                          + winnerId
                          + " không đủ");
                }
                WalletTransactionDao.insert(
                    handle,
                    winnerId,
                    auction.getId(),
                    null,
                    "WIN_CONSUME",
                    price,
                    "settlement:" + auction.getId());
                LOG.info("Phiên #{}: trừ {} từ bidder #{}", auction.getId(), price, winnerId);
                LOG.info(
                    "Phiên #{}: bidder #{} balance {} → {}",
                    auction.getId(),
                    winnerId,
                    balanceBefore,
                    balanceBefore.subtract(price));

                BigDecimal winnerNewBalance = balanceBefore.subtract(price);
                BigDecimal winnerDelta = price.negate();
                String winMsg =
                    String.format(
                        Locale.of("vi", "VN"),
                        "Bạn đã thắng phiên đấu giá #%d với giá %,d VND."
                            + " Số dư biến động: - %,d VND",
                        auction.getId(),
                        price.longValue(),
                        price.longValue());
                balanceChanges.add(
                    new BalanceChange(
                        winnerId, winnerNewBalance, winnerDelta, winMsg, "AUCTION_WON"));

                // Cộng tiền seller
                if (sellerId != null) {
                  // Khóa row seller để cộng tiền
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
                  LOG.info("Phiên #{}: cộng {} cho seller #{}", auction.getId(), price, sellerId);
                  BigDecimal sellerNewBalance = sellerBalanceBefore.add(price);
                  LOG.info(
                      "Phiên #{}: seller #{} balance {} → {}",
                      auction.getId(),
                      sellerId,
                      sellerBalanceBefore,
                      sellerNewBalance);
                  String sellerMsg =
                      String.format(
                          Locale.of("vi", "VN"),
                          "Phiên đấu giá #%d đã thanh toán." + " Số dư biến động: + %,d VND",
                          auction.getId(),
                          price.longValue());
                  String winnerName =
                      winner != null && winner.getUsername() != null
                          ? winner.getUsername()
                          : "Người dùng #" + winnerId;
                  String sellerResultMsg =
                      String.format(
                          Locale.of("vi", "VN"),
                          "Phiên #%d đấu giá thành công! Người thắng: %s với giá %,d VND.",
                          auction.getId(),
                          winnerName,
                          price.longValue());
                  handle.execute(
                      "INSERT INTO notifications (user_id, message, notification_type)"
                          + " VALUES (?, ?, 'AUCTION_RESULT')",
                      sellerId,
                      sellerResultMsg);
                  userNotifications.add(
                      new UserNotification(sellerId, sellerResultMsg, "AUCTION_RESULT"));
                }

                auction.setStatus(AuctionStatus.PAID);
              } else {
                LOG.warn(
                    "Phiên #{}: bidder #{} không đủ số dư ({}) để thanh toán {}. Chuyển FINISHED (nợ).",
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

                if (sellerId != null) {
                  String winnerName =
                      winner != null && winner.getUsername() != null
                          ? winner.getUsername()
                          : "Người dùng #" + winnerId;
                  String sellerFailMsg =
                      String.format(
                          Locale.of("vi", "VN"),
                          "Phiên #%d kết thúc thất bại: người thắng (%s) không đủ tiền thanh toán %,d VND.",
                          auction.getId(),
                          winnerName,
                          price.longValue());
                  handle.execute(
                      "INSERT INTO notifications (user_id, message, notification_type)"
                          + " VALUES (?, ?, 'AUCTION_RESULT')",
                      sellerId,
                      sellerFailMsg);
                  userNotifications.add(
                      new UserNotification(sellerId, sellerFailMsg, "AUCTION_RESULT"));
                }

                auction.setStatus(AuctionStatus.FINISHED);
              }
            } else {
              if (sellerId != null) {
                String sellerFailMsg =
                    String.format(
                        Locale.of("vi", "VN"),
                        "Phiên #%d kết thúc — không có ai đặt giá. Phiên đấu giá thất bại.",
                        auction.getId());
                handle.execute(
                    "INSERT INTO notifications (user_id, message, notification_type)"
                        + " VALUES (?, ?, 'AUCTION_RESULT')",
                    sellerId,
                    sellerFailMsg);
                userNotifications.add(
                    new UserNotification(sellerId, sellerFailMsg, "AUCTION_RESULT"));
              }
              auction.setStatus(AuctionStatus.FINISHED);
            }

            // Cập nhật trạng thái auction cuối cùng
            auctionDao.updateInTransaction(handle, auction);
            settledAuction[0] = auction;
          });

      if (settledAuction[0] == null) {
        return null;
      }
      LOG.info(
          "Phiên #{} → {} (endTime={}, winner={})",
          settledAuction[0].getId(),
          settledAuction[0].getStatus(),
          settledAuction[0].getEndTime(),
          settledAuction[0].getLeadingBidderId());
      return new SettlementResult(settledAuction[0], userNotifications, balanceChanges);
    } finally {
      MDC.remove("auctionId");
    }
  }

  // ── Notification ─────────────────────────────────────────

  /**
   * Gửi thông báo AUCTION_ENDED đến tất cả client đang xem phiên này.
   *
   * <p>Message chứa: auctionId, winnerId, winningPrice. Client nhận message → disable nút bid →
   * hiển thị "Người thắng: ...".
   *
   * @param auction phiên vừa kết thúc
   */
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
      LOG.error("Không thể broadcast AUCTION_ENDED cho phiên #{}", auction.getId(), e);
    }
  }

  /** Trả về trạng thái chạy hiện tại — dùng trong health check endpoint. */
  public boolean isRunning() {
    return running.get();
  }
}
