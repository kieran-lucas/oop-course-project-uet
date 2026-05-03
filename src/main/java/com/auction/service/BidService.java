package com.auction.service;

import com.auction.dao.AuctionDao;
import com.auction.dao.AutoBidConfigDao;
import com.auction.dao.BidTransactionDao;
import com.auction.dto.BidUpdateMessage;
import com.auction.exception.AuctionClosedException;
import com.auction.exception.InvalidBidException;
import com.auction.exception.NotFoundException;
import com.auction.model.Auction;
import com.auction.model.BidTransaction;
import com.auction.pattern.observer.AuctionEventManager;
import com.auction.pattern.strategy.AutoBidStrategy;
import com.auction.pattern.strategy.ManualBidStrategy;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service xử lý logic đặt giá — trung tâm của toàn bộ hệ thống đấu giá.
 *
 * <p><b>BidService là class phức tạp nhất trong project</b> vì nó kết hợp nhiều pattern:
 * <ul>
 *   <li><b>State pattern</b> — kiểm tra trạng thái phiên (chỉ RUNNING mới cho bid)</li>
 *   <li><b>Strategy pattern</b> — {@link ManualBidStrategy} validate và cập nhật giá</li>
 *   <li><b>Observer pattern</b> — {@link AuctionEventManager} notify client qua WebSocket</li>
 *   <li><b>Anti-sniping</b> — gia hạn phiên nếu bid trong 30 giây cuối</li>
 * </ul>
 *
 * <p><b>Luồng xử lý {@code placeBid()}:</b>
 * <pre>
 *   1. Validate input (giá > 0)
 *   2. Lấy auction từ DB → kiểm tra status (phải RUNNING)
 *   3. ManualBidStrategy.execute() → validate giá + cập nhật auction in-memory
 *   4. auctionDao.update() → lưu giá mới vào DB
 *   5. bidTransactionDao.insert() → ghi lịch sử bid
 *   6. Anti-sniping: nếu còn &lt; 30s → gia hạn 60s → notify TIME_EXTENDED
 *   7. eventManager.notifyBidUpdate() → WebSocket broadcast BID_UPDATE đến client
 *   8. autoBidStrategy.executeAll() → kích hoạt chuỗi auto-bid nếu có
 * </pre>
 *
 * <p><b>Concurrency:</b> Bước 2-5 cần đồng bộ. Trong production nên dùng SELECT FOR UPDATE.
 * Trong version này dùng {@code synchronized} để đảm bảo thread-safe ở tầng application.
 *
 * <p><b>Liên kết với các file khác:</b>
 * <ul>
 *   <li>{@link ManualBidStrategy} — validate và update auction cho manual bid</li>
 *   <li>{@link AutoBidStrategy} — xử lý chuỗi auto-bid sau manual bid</li>
 *   <li>{@link AuctionEventManager} — broadcast sự kiện qua WebSocket</li>
 *   <li>{@link com.auction.controller.BidController} — gọi {@code placeBid()} khi nhận HTTP request</li>
 * </ul>
 */
public class BidService {

  private static final Logger LOGGER = LoggerFactory.getLogger(BidService.class);

  /** Ngưỡng thời gian còn lại (ms) để kích hoạt anti-sniping: 30 giây = 30,000ms */
  private static final long ANTI_SNIPE_THRESHOLD_MS = 30_000L;

  /** Thời gian gia hạn khi anti-sniping kích hoạt: 60 giây */
  private static final long ANTI_SNIPE_EXTENSION_SECONDS = 60L;

  private final AuctionDao auctionDao;
  private final BidTransactionDao bidTransactionDao;
  private final AutoBidConfigDao autoBidConfigDao;
  private final AuctionEventManager eventManager;

  private final ManualBidStrategy manualBidStrategy;
  private final AutoBidStrategy autoBidStrategy;

  /**
   * Khởi tạo BidService với đầy đủ dependency.
   *
   * @param auctionDao        DAO truy cập bảng auctions
   * @param bidTransactionDao DAO ghi lịch sử bid
   * @param autoBidConfigDao  DAO quản lý cấu hình auto-bid
   * @param eventManager      Observer Manager để broadcast sự kiện WebSocket
   */
  public BidService(
      AuctionDao auctionDao,
      BidTransactionDao bidTransactionDao,
      AutoBidConfigDao autoBidConfigDao,
      AuctionEventManager eventManager) {
    this.auctionDao = auctionDao;
    this.bidTransactionDao = bidTransactionDao;
    this.autoBidConfigDao = autoBidConfigDao;
    this.eventManager = eventManager;
    this.manualBidStrategy = new ManualBidStrategy();
    this.autoBidStrategy = new AutoBidStrategy(autoBidConfigDao);
  }

  /**
   * Đặt giá cho một phiên đấu giá.
   *
   * <p>Method này là trung tâm xử lý bid. Tất cả validation, cập nhật DB, và thông báo
   * đều diễn ra ở đây.
   *
   * @param auctionId ID phiên đấu giá
   * @param bidderId  ID người đặt giá (từ JWT token)
   * @param amount    số tiền đặt giá (phải > currentPrice)
   * @param isAutoBid {@code true} nếu đây là auto-bid, {@code false} nếu manual
   * @return BidTransaction đã được lưu vào DB
   * @throws NotFoundException       nếu auction không tồn tại
   * @throws AuctionClosedException  nếu phiên chưa bắt đầu hoặc đã kết thúc
   * @throws InvalidBidException     nếu giá không hợp lệ (thấp hơn giá hiện tại, seller tự bid)
   */
  public BidTransaction placeBid(Long auctionId, Long bidderId, BigDecimal amount,
      boolean isAutoBid) {
    // 1. Validate input cơ bản
    if (amount == null || amount.signum() <= 0) {
      throw new InvalidBidException("Giá bid phải lớn hơn 0");
    }

    // 2. Lấy auction và kiểm tra trạng thái
    Auction auction = auctionDao.findById(auctionId)
        .orElseThrow(() -> new NotFoundException("Auction not found with id: " + auctionId));

    // 3. Kiểm tra trạng thái phiên (State pattern logic)
    checkAuctionStatus(auction);

    // 4. Thực thi bid qua Strategy (validate giá + cập nhật auction in-memory)
    BidTransaction transaction = manualBidStrategy.execute(auction, bidderId, amount, isAutoBid);

    // 5. Lưu thay đổi vào DB
    auctionDao.update(auction);
    bidTransactionDao.insert(transaction);

    LOGGER.info("Bid đặt thành công: auction={}, bidder={}, amount={}, autoBid={}",
        auctionId, bidderId, amount, isAutoBid);

    // 6. Anti-sniping: gia hạn nếu bid trong 30 giây cuối
    applyAntiSniping(auction, auctionId);

    // 7. Notify observers qua WebSocket (BID_UPDATE)
    notifyBidUpdate(auction, auctionId, bidderId, amount, isAutoBid);

    // 8. Kích hoạt chuỗi auto-bid (chỉ sau manual bid để tránh vòng lặp vô tận)
    if (!isAutoBid) {
      triggerAutoBid(auctionId, amount);
    }

    return transaction;
  }

  /**
   * Lấy lịch sử bid của một phiên đấu giá.
   *
   * <p>Dùng cho Bid History Chart (JavaFX LineChart) và Bid History ListView.
   *
   * @param auctionId ID phiên đấu giá
   * @return danh sách BidTransaction sắp xếp theo thời gian tăng dần (cũ → mới)
   * @throws NotFoundException nếu auction không tồn tại
   */
  public List<BidTransaction> getBidHistory(Long auctionId) {
    if (!auctionDao.existsById(auctionId)) {
      throw new NotFoundException("Auction not found with id: " + auctionId);
    }
    return bidTransactionDao.findByAuctionId(auctionId);
  }

  // ── Private helpers ──────────────────────────────────────

  /**
   * Kiểm tra trạng thái phiên — chỉ RUNNING mới cho phép bid.
   *
   * @param auction phiên cần kiểm tra
   * @throws AuctionClosedException nếu phiên không ở trạng thái RUNNING
   */
  private void checkAuctionStatus(Auction auction) {
    switch (auction.getStatus()) {
      case "OPEN" -> throw new AuctionClosedException(
          "Phiên chưa bắt đầu — vui lòng chờ đến giờ khai mạc");
      case "FINISHED" -> throw new AuctionClosedException(
          "Phiên đã kết thúc — không thể đặt giá");
      case "CANCELED" -> throw new AuctionClosedException(
          "Phiên đã bị hủy — không thể đặt giá");
      case "PAID" -> throw new AuctionClosedException(
          "Phiên đã thanh toán xong — không thể đặt giá");
      case "RUNNING" -> { /* tiếp tục */ }
      default -> throw new AuctionClosedException(
          "Trạng thái phiên không hợp lệ: " + auction.getStatus());
    }
  }

  /**
   * Anti-sniping: gia hạn phiên nếu bid trong 30 giây cuối.
   *
   * <p>Nếu bid xảy ra khi còn &lt; {@value #ANTI_SNIPE_THRESHOLD_MS}ms:
   * - endTime += {@value #ANTI_SNIPE_EXTENSION_SECONDS} giây
   * - Lưu vào DB
   * - Broadcast TIME_EXTENDED cho tất cả client
   *
   * @param auction   phiên vừa được bid
   * @param auctionId ID phiên (để notify observer)
   */
  private void applyAntiSniping(Auction auction, Long auctionId) {
    if (auction.getRemainingTimeMs() < ANTI_SNIPE_THRESHOLD_MS) {
      auction.setEndTime(auction.getEndTime().plusSeconds(ANTI_SNIPE_EXTENSION_SECONDS));
      auctionDao.update(auction);

      BidUpdateMessage timeMsg = BidUpdateMessage.timeExtended(auctionId, auction.getEndTime());
      eventManager.notifyTimeExtended(auctionId, timeMsg);

      LOGGER.info("Anti-sniping kích hoạt cho phiên #{}: gia hạn thêm {}s",
          auctionId, ANTI_SNIPE_EXTENSION_SECONDS);
    }
  }

  /**
   * Tạo và broadcast BID_UPDATE message qua WebSocket.
   *
   * @param auction   phiên vừa được cập nhật
   * @param auctionId ID phiên
   * @param bidderId  ID người vừa bid
   * @param amount    giá bid mới
   * @param isAutoBid true nếu là auto-bid
   */
  private void notifyBidUpdate(Auction auction, Long auctionId, Long bidderId,
      BigDecimal amount, boolean isAutoBid) {
    try {
      BidUpdateMessage msg = BidUpdateMessage.bidUpdate(
          auctionId,
          amount,
          bidderId,
          null, // username sẽ được lookup bởi WebSocketObserver nếu cần
          auction.getEndTime(),
          isAutoBid
      );
      eventManager.notifyBidUpdate(auctionId, msg);
    } catch (Exception e) {
      LOGGER.error("Lỗi khi notify BID_UPDATE cho phiên #{}: {}", auctionId, e.getMessage());
    }
  }

  /**
   * Kích hoạt chuỗi auto-bid sau khi có manual bid thành công.
   *
   * <p>Gọi AutoBidStrategy.executeAll() với callback để thực thi từng auto-bid.
   * Dùng lambda {@code (aid, bid, amt) -> this.placeBid(aid, bid, amt, true)}
   * để tránh circular dependency.
   *
   * @param auctionId       ID phiên
   * @param currentPrice    giá hiện tại sau manual bid
   */
  private void triggerAutoBid(Long auctionId, BigDecimal currentPrice) {
    try {
      autoBidStrategy.executeAll(
          auctionId,
          currentPrice,
          (aid, bid, amt) -> this.placeBid(aid, bid, amt, true)
      );
    } catch (Exception e) {
      LOGGER.error("Lỗi khi xử lý auto-bid cho phiên #{}: {}", auctionId, e.getMessage());
    }
  }
}
