package com.auction.service;

import com.auction.dao.AuctionDao;
import com.auction.dao.AutoBidConfigDao;
import com.auction.dao.BidTransactionDao;
import com.auction.dto.BidUpdateMessage;
import com.auction.exception.InvalidBidException;
import com.auction.exception.NotFoundException;
import com.auction.model.Auction;
import com.auction.model.BidTransaction;
import com.auction.pattern.observer.AuctionEventManager;
import com.auction.pattern.strategy.AutoBidStrategy;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service xử lý logic đặt giá — trung tâm của toàn bộ hệ thống đấu giá.
 *
 * <p><b>Luồng xử lý {@code placeBid()}:</b>
 *
 * <pre>
 *   1. Validate input (giá > 0)
 *   2. jdbi.inTransaction():
 *      a. findByIdForUpdate → SELECT FOR UPDATE khóa row
 *      b. auctionService.getState(auction).placeBid() → State pattern validate + update in-memory
 *      c. Anti-sniping: nếu còn &lt; 30s → gia hạn 60s
 *      d. updateInTransaction → lưu giá + endTime atomically
 *      e. bidTransactionDao.insert(handle) → ghi bid trong cùng transaction
 *   3. Notify TIME_EXTENDED nếu anti-sniping triggered
 *   4. Notify BID_UPDATE qua WebSocket
 *   5. triggerAutoBid() nếu là manual bid
 * </pre>
 *
 * <p><b>Concurrency:</b> {@code SELECT FOR UPDATE} trong transaction bảo vệ khi nhiều server
 * instance chạy song song. Không cần {@code synchronized} trong ứng dụng.
 */
public class BidService {

  private static final Logger LOGGER = LoggerFactory.getLogger(BidService.class);

  private static final long ANTI_SNIPE_THRESHOLD_MS = 30_000L;
  private static final long ANTI_SNIPE_EXTENSION_SECONDS = 60L;

  private final AuctionDao auctionDao;
  private final BidTransactionDao bidTransactionDao;
  private final AutoBidConfigDao autoBidConfigDao;
  private final AuctionEventManager eventManager;
  private final Jdbi jdbi;
  private final AuctionService auctionService;
  private final AutoBidStrategy autoBidStrategy;

  public BidService(
      AuctionDao auctionDao,
      BidTransactionDao bidTransactionDao,
      AutoBidConfigDao autoBidConfigDao,
      AuctionEventManager eventManager,
      Jdbi jdbi,
      AuctionService auctionService) {
    this.auctionDao = auctionDao;
    this.bidTransactionDao = bidTransactionDao;
    this.autoBidConfigDao = autoBidConfigDao;
    this.eventManager = eventManager;
    this.jdbi = jdbi;
    this.auctionService = auctionService;
    this.autoBidStrategy = new AutoBidStrategy(autoBidConfigDao);
  }

  /**
   * Đặt giá cho một phiên đấu giá.
   *
   * @param auctionId ID phiên đấu giá
   * @param bidderId ID người đặt giá (từ JWT token)
   * @param amount số tiền đặt giá (phải > currentPrice)
   * @param isAutoBid {@code true} nếu đây là auto-bid
   * @return BidTransaction đã được lưu vào DB
   */
  public BidTransaction placeBid(
      Long auctionId, Long bidderId, BigDecimal amount, boolean isAutoBid) {

    if (amount == null || amount.signum() <= 0) {
      throw new InvalidBidException("Giá bid phải lớn hơn 0");
    }

    AtomicReference<Auction> auctionRef = new AtomicReference<>();
    AtomicBoolean antiSnipeTriggered = new AtomicBoolean(false);

    BidTransaction savedTransaction =
        jdbi.inTransaction(
            handle -> {
              Auction auction;
              try {
                auction = auctionDao.findByIdForUpdate(handle, auctionId);
              } catch (IllegalStateException e) {
                throw new NotFoundException("Auction not found with id: " + auctionId);
              }
              auctionRef.set(auction);

              // State pattern: validates amount > currentPrice, bidderId != sellerId,
              // and updates auction in-memory. Throws on failure.
              auctionService.getState(auction).placeBid(auction, amount, bidderId);

              if (auction.getRemainingTimeMs() < ANTI_SNIPE_THRESHOLD_MS) {
                auction.setEndTime(auction.getEndTime().plusSeconds(ANTI_SNIPE_EXTENSION_SECONDS));
                antiSnipeTriggered.set(true);
              }

              // Single atomic write: persists both price update and any endTime extension
              auctionDao.updateInTransaction(handle, auction);
              BidTransaction tx = new BidTransaction(auctionId, bidderId, amount, isAutoBid);
              return bidTransactionDao.insert(handle, tx);
            });

    Auction auction = auctionRef.get();

    LOGGER.info(
        "Bid đặt thành công: auction={}, bidder={}, amount={}, autoBid={}",
        auctionId,
        bidderId,
        amount,
        isAutoBid);

    if (antiSnipeTriggered.get()) {
      BidUpdateMessage timeMsg = BidUpdateMessage.timeExtended(auctionId, auction.getEndTime());
      eventManager.notifyTimeExtended(auctionId, timeMsg);
      LOGGER.info(
          "Anti-sniping kích hoạt cho phiên #{}: gia hạn thêm {}s",
          auctionId,
          ANTI_SNIPE_EXTENSION_SECONDS);
    }

    notifyBidUpdate(auction, auctionId, bidderId, amount, isAutoBid);

    if (!isAutoBid) {
      triggerAutoBid(auctionId, amount);
    }

    return savedTransaction;
  }

  /**
   * Lấy lịch sử bid của một phiên đấu giá.
   *
   * @param auctionId ID phiên đấu giá
   * @return danh sách BidTransaction sắp xếp theo thời gian tăng dần
   * @throws NotFoundException nếu auction không tồn tại
   */
  public List<BidTransaction> getBidHistory(Long auctionId) {
    if (!auctionDao.existsById(auctionId)) {
      throw new NotFoundException("Auction not found with id: " + auctionId);
    }
    return bidTransactionDao.findByAuctionId(auctionId);
  }

  // ── Private helpers ──────────────────────────────────────

  private void notifyBidUpdate(
      Auction auction, Long auctionId, Long bidderId, BigDecimal amount, boolean isAutoBid) {
    try {
      BidUpdateMessage msg =
          BidUpdateMessage.bidUpdate(
              auctionId, amount, bidderId, null, auction.getEndTime(), isAutoBid);
      eventManager.notifyBidUpdate(auctionId, msg);
    } catch (Exception e) {
      LOGGER.error("Lỗi khi notify BID_UPDATE cho phiên #{}: {}", auctionId, e.getMessage());
    }
  }

  private void triggerAutoBid(Long auctionId, BigDecimal currentPrice) {
    try {
      autoBidStrategy.executeAll(
          auctionId, currentPrice, (aid, bid, amt) -> this.placeBid(aid, bid, amt, true));
    } catch (Exception e) {
      LOGGER.error("Lỗi khi xử lý auto-bid cho phiên #{}: {}", auctionId, e.getMessage());
    }
  }
}
