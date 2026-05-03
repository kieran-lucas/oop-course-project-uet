package com.auction.service;

import com.auction.dao.AuctionDao;
import com.auction.dto.BidUpdateMessage;
import com.auction.model.Auction;
import com.auction.pattern.observer.AuctionEventManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
 *   <li>{@code AuctionDao} — query + update trạng thái trong DB</li>
 *   <li>{@code AuctionEventManager} — broadcast thông báo đến client qua WebSocket</li>
 *   <li>{@code BidUpdateMessage} — định dạng message gửi khi phiên kết thúc</li>
 *   <li>{@code App.java} — khởi động scheduler khi server start</li>
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
      Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "auction-scheduler");
        t.setDaemon(true); // không ngăn JVM tắt khi main thread kết thúc
        return t;
      });

  /** Future của task đang chạy — dùng để cancel khi shutdown */
  private ScheduledFuture<?> scheduledTask;

  /** Guard tránh start() gọi nhiều lần */
  private final AtomicBoolean running = new AtomicBoolean(false);

  // ── Constructor ──────────────────────────────────────────

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
   * Nếu task trước chưa xong mà đến chu kỳ mới → task mới chờ task cũ xong
   * (vì chỉ có 1 thread).
   */
  public void start() {
    if (!running.compareAndSet(false, true)) {
      log.warn("AuctionScheduler đã chạy rồi, bỏ qua lệnh start() thứ hai");
      return;
    }

    scheduledTask = scheduler.scheduleAtFixedRate(
        this::scanAndTransition,
        0,                        // delay ban đầu: chạy ngay khi start
        SCAN_INTERVAL_SECONDS,
        TimeUnit.SECONDS
    );

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
   * <p>Thứ tự xử lý quan trọng: chuyển OPEN→RUNNING trước, rồi mới RUNNING→FINISHED.
   * Điều này đảm bảo phiên bắt đầu và kết thúc cùng một tick (edge case: startTime ==
   * endTime) được xử lý đúng.
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
   * Chuyển các phiên từ {@code OPEN} sang {@code RUNNING} khi đến giờ bắt đầu.
   *
   * <p>Query: {@code SELECT * FROM auctions WHERE status='OPEN' AND start_time <= now}
   *
   * @param now thời điểm hiện tại (truyền vào để test dễ inject)
   */
  private void openToRunning(LocalDateTime now) {
    List<Auction> openAuctions = auctionDao.findByStatus("OPEN");

    for (Auction auction : openAuctions) {
      if (auction.getStartTime() == null) {
        continue; // bỏ qua phiên thiếu startTime (data lỗi)
      }

      boolean shouldStart = !auction.getStartTime().isAfter(now);
      if (!shouldStart) {
        continue;
      }

      try {
        auction.setStatus("RUNNING");
        auctionDao.update(auction);
        log.info("Phiên #{} chuyển OPEN → RUNNING (startTime={})",
            auction.getId(), auction.getStartTime());

      } catch (Exception e) {
        log.error("Không thể chuyển phiên #{} sang RUNNING", auction.getId(), e);
      }
    }
  }

  // ── RUNNING → FINISHED ───────────────────────────────────

  /**
   * Chuyển các phiên từ {@code RUNNING} sang {@code FINISHED} khi hết giờ.
   *
   * <p>Query: {@code SELECT * FROM auctions WHERE status='RUNNING' AND end_time <= now}
   *
   * <p>Sau khi cập nhật DB, broadcast {@code AUCTION_ENDED} message qua WebSocket để tất cả
   * client đang xem phiên nhận được thông báo ngay lập tức — không cần client polling.
   *
   * @param now thời điểm hiện tại
   */
  private void runningToFinished(LocalDateTime now) {
    List<Auction> runningAuctions = auctionDao.findByStatus("RUNNING");

    for (Auction auction : runningAuctions) {
      if (auction.getEndTime() == null) {
        continue;
      }

      boolean hasEnded = !auction.getEndTime().isAfter(now);
      if (!hasEnded) {
        continue;
      }

      try {
        auction.setStatus("FINISHED");
        auctionDao.update(auction);

        log.info("Phiên #{} chuyển RUNNING → FINISHED (endTime={}, winner={})",
            auction.getId(), auction.getEndTime(), auction.getLeadingBidderId());

        notifyAuctionEnded(auction);

      } catch (Exception e) {
        log.error("Không thể chuyển phiên #{} sang FINISHED", auction.getId(), e);
      }
    }
  }

  // ── Notification ─────────────────────────────────────────

  /**
   * Gửi thông báo {@code AUCTION_ENDED} đến tất cả client đang xem phiên này.
   *
   * <p>Message chứa: auctionId, winnerId (leadingBidderId), winningPrice (currentPrice).
   * Client nhận message → disable nút bid → hiển thị "Người thắng: ...".
   *
   * @param auction phiên vừa kết thúc
   */
  private void notifyAuctionEnded(Auction auction) {
    try {
      BidUpdateMessage msg = BidUpdateMessage.auctionEnded(
          auction.getId(),
          auction.getLeadingBidderId(),
          auction.getCurrentPrice()
      );
      eventManager.notifyAuctionEnd(auction.getId(), msg);

    } catch (Exception e) {
      // Thông báo thất bại không ảnh hưởng đến việc đổi trạng thái DB
      // → chỉ log, không re-throw
      log.error("Không thể broadcast AUCTION_ENDED cho phiên #{}", auction.getId(), e);
    }
  }

  // ── Getters (dùng cho test) ──────────────────────────────

  /**
   * Trả về trạng thái chạy hiện tại — dùng trong health check endpoint.
   *
   * @return {@code true} nếu scheduler đang chạy
   */
  public boolean isRunning() {
    return running.get();
  }
}
