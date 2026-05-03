package com.auction.service;

import com.auction.dao.AuctionDao;
import com.auction.dto.BidUpdateMessage;
import com.auction.model.Auction;
import com.auction.pattern.observer.AuctionEventManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bộ lập lịch nền — tự động chuyển trạng thái phiên đấu giá theo thời gian.
 *
 * <p><b>Vai trò trong hệ thống:</b> AuctionScheduler là "đồng hồ" của hệ thống. Nó chạy liên tục
 * trong nền (background thread), cứ mỗi {@value #SCAN_INTERVAL_SECONDS} giây lại quét toàn bộ
 * các phiên đấu giá đang hoạt động và thực hiện chuyển trạng thái nếu điều kiện thỏa mãn:
 *
 * <pre>
 *   OPEN ──(startTime đến)──► RUNNING ──(endTime qua)──► FINISHED
 * </pre>
 *
 * <p><b>Tại sao cần class này?</b> Trạng thái phiên không tự thay đổi — nếu không có scheduler,
 * phiên sẽ mãi ở OPEN hoặc RUNNING dù thời gian đã qua. AuctionScheduler thay thế cron job ở
 * tầng application, phù hợp với project không deploy production server riêng.
 *
 * <p><b>Concurrency note:</b> ScheduledExecutorService đảm bảo mỗi lần chỉ chạy 1 task (không
 * overlap). Dùng {@code scheduleAtFixedRate} thay vì {@code scheduleWithFixedDelay} để giữ chu kỳ
 * ổn định dù task mất bao lâu.
 *
 * <p><b>Liên kết file khác:</b>
 * <ul>
 *   <li>{@link AuctionDao} — query + update trạng thái trong DB</li>
 *   <li>{@link AuctionEventManager} — broadcast thông báo đến client qua WebSocket</li>
 *   <li>{@link BidUpdateMessage} — định dạng message gửi khi phiên kết thúc</li>
 *   <li>{@link com.auction.App} — khởi động scheduler khi server start</li>
 * </ul>
 */
public class AuctionScheduler {

  private static final Logger log = LoggerFactory.getLogger(AuctionScheduler.class);

  /** Chu kỳ quét (giây) — đủ nhạy mà không overload DB */
  private static final int SCAN_INTERVAL_SECONDS = 5;

  private final AuctionDao auctionDao;
  private final AuctionEventManager eventManager;

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

  public AuctionScheduler(AuctionDao auctionDao, AuctionEventManager eventManager) {
    this.auctionDao = auctionDao;
    this.eventManager = eventManager;
  }

  // ── Lifecycle ────────────────────────────────────────────

  /**
   * Khởi động scheduler. Gọi một lần duy nhất khi server start.
   *
   * <p>Dùng {@code scheduleAtFixedRate} để task chạy đều đặn mỗi
   * {@value #SCAN_INTERVAL_SECONDS} giây, bất kể task trước mất bao lâu.
   */
  public void start() {
    if (!running.compareAndSet(false, true)) {
      log.warn("AuctionScheduler đã chạy rồi, bỏ qua lệnh start() thứ hai");
      return;
    }

    scheduledTask =
        scheduler.scheduleAtFixedRate(
            this::scanAndTransition,
            0, // delay ban đầu: chạy ngay khi start
            SCAN_INTERVAL_SECONDS,
            TimeUnit.SECONDS);

    log.info("AuctionScheduler đã khởi động — quét mỗi {}s", SCAN_INTERVAL_SECONDS);
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
    running.set(false);
    log.info("AuctionScheduler đã dừng");
  }

  // ── Core scan logic ──────────────────────────────────────

  /**
   * Hàm chính được gọi mỗi {@value #SCAN_INTERVAL_SECONDS} giây.
   *
   * <p>Thứ tự xử lý: chuyển OPEN→RUNNING trước, rồi RUNNING→FINISHED.
   * Điều này xử lý đúng edge case startTime == endTime.
   */
  void scanAndTransition() {
    try {
      LocalDateTime now = LocalDateTime.now();
      log.debug("Scheduler scan tại {}", now);

      openToRunning(now);
      runningToFinished(now);

    } catch (Exception e) {
      // Bắt tất cả exception để scheduler không chết âm thầm.
      // Nếu không có try-catch này, một RuntimeException sẽ cancel scheduledTask.
      log.error("Lỗi trong AuctionScheduler.scanAndTransition()", e);
    }
  }

  // ── OPEN → RUNNING ───────────────────────────────────────

  /**
   * Chuyển các phiên từ OPEN sang RUNNING khi đến giờ bắt đầu.
   *
   * @param now thời điểm hiện tại
   */
  private void openToRunning(LocalDateTime now) {
    List<Auction> openAuctions = auctionDao.findByStatus("OPEN");

    for (Auction auction : openAuctions) {
      if (auction.getStartTime() == null) {
        continue;
      }

      if (!auction.getStartTime().isAfter(now)) {
        try {
          auction.setStatus("RUNNING");
          auctionDao.update(auction);
          log.info(
              "Phiên #{} chuyển OPEN → RUNNING (startTime={})",
              auction.getId(),
              auction.getStartTime());
        } catch (Exception e) {
          log.error("Không thể chuyển phiên #{} sang RUNNING", auction.getId(), e);
        }
      }
    }
  }

  // ── RUNNING → FINISHED ───────────────────────────────────

  /**
   * Chuyển các phiên từ RUNNING sang FINISHED khi hết giờ.
   *
   * <p>Sau khi cập nhật DB, broadcast AUCTION_ENDED qua WebSocket để tất cả client
   * đang xem phiên nhận được thông báo ngay lập tức.
   *
   * @param now thời điểm hiện tại
   */
  private void runningToFinished(LocalDateTime now) {
    List<Auction> runningAuctions = auctionDao.findByStatus("RUNNING");

    for (Auction auction : runningAuctions) {
      if (auction.getEndTime() == null) {
        continue;
      }

      if (!auction.getEndTime().isAfter(now)) {
        try {
          auction.setStatus("FINISHED");
          auctionDao.update(auction);

          log.info(
              "Phiên #{} chuyển RUNNING → FINISHED (endTime={}, winner={})",
              auction.getId(),
              auction.getEndTime(),
              auction.getLeadingBidderId());

          notifyAuctionEnded(auction);

        } catch (Exception e) {
          log.error("Không thể chuyển phiên #{} sang FINISHED", auction.getId(), e);
        }
      }
    }
  }

  // ── Notification ─────────────────────────────────────────

  /**
   * Gửi thông báo AUCTION_ENDED đến tất cả client đang xem phiên này.
   *
   * <p>Message chứa: auctionId, winnerId, winningPrice.
   * Client nhận message → disable nút bid → hiển thị "Người thắng: ...".
   *
   * @param auction phiên vừa kết thúc
   */
  private void notifyAuctionEnded(Auction auction) {
    try {
      BidUpdateMessage msg =
          BidUpdateMessage.auctionEnded(
              auction.getId(),
              auction.getCurrentPrice(),
              auction.getLeadingBidderId(),
              null // username không cần thiết cho server-side notification
              );
      eventManager.notifyAuctionEnd(auction.getId(), msg);

    } catch (Exception e) {
      log.error("Không thể broadcast AUCTION_ENDED cho phiên #{}", auction.getId(), e);
    }
  }

  /** Trả về trạng thái chạy hiện tại — dùng trong health check endpoint. */
  public boolean isRunning() {
    return running.get();
  }
}
