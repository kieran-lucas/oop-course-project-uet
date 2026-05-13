package com.auction.service;

import com.auction.dao.AuctionDao;
import com.auction.dao.AutoBidConfigDao;
import com.auction.dao.BidTransactionDao;
import com.auction.dao.UserDao;
import com.auction.dto.BidUpdateMessage;
import com.auction.exception.InvalidBidException;
import com.auction.exception.NotFoundException;
import com.auction.model.Auction;
import com.auction.model.BidTransaction;
import com.auction.model.User;
import com.auction.pattern.observer.AuctionEventManager;
import com.auction.pattern.strategy.AutoBidStrategy;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

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
 *      c. Chống snipe: nếu còn &lt; 30s → gia hạn 60s
 *      d. updateInTransaction → lưu giá + endTime nguyên tử
 *      e. bidTransactionDao.insert(handle) → ghi bid trong cùng transaction
 *   3. Thông báo TIME_EXTENDED nếu chống snipe được kích hoạt
 *   4. Thông báo BID_UPDATE qua WebSocket
 *   5. triggerAutoBid() nếu là đặt giá thủ công
 * </pre>
 *
 * <p><b>Xử lý đồng thời:</b> {@code SELECT FOR UPDATE} trong transaction bảo vệ khi nhiều server
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
  private final UserDao userDao;
  private final AutoBidStrategy autoBidStrategy;

  public BidService(
      AuctionDao auctionDao,
      BidTransactionDao bidTransactionDao,
      AutoBidConfigDao autoBidConfigDao,
      AuctionEventManager eventManager,
      Jdbi jdbi,
      AuctionService auctionService,
      UserDao userDao,
      AutoBidStrategy autoBidStrategy) {
    this.auctionDao = auctionDao;
    this.bidTransactionDao = bidTransactionDao;
    this.autoBidConfigDao = autoBidConfigDao;
    this.eventManager = eventManager;
    this.jdbi = jdbi;
    this.auctionService = auctionService;
    this.userDao = userDao;
    this.autoBidStrategy = autoBidStrategy;
  }

  /**
   * Đặt giá cho một phiên đấu giá.
   *
   * @param auctionId ID phiên đấu giá
   * @param bidderId ID người đặt giá (lấy từ JWT token)
   * @param amount số tiền đặt giá (phải lớn hơn giá hiện tại)
   * @param isAutoBid {@code true} nếu đây là đặt giá tự động
   * @return BidTransaction đã được lưu vào cơ sở dữ liệu
   */
  public BidTransaction placeBid(
      Long auctionId, Long bidderId, BigDecimal amount, boolean isAutoBid) {
    MDC.put("auctionId", String.valueOf(auctionId));
    MDC.put("userId", String.valueOf(bidderId));
    try {

      if (amount == null || amount.signum() <= 0) {
        throw new InvalidBidException("Giá bid phải lớn hơn 0");
      }

      AtomicReference<Auction> auctionRef = new AtomicReference<>();
      AtomicBoolean antiSnipeTriggered = new AtomicBoolean(false);

      BidTransaction savedTransaction =
          jdbi.inTransaction(
              handle -> {
                // 1. Khóa row auction trước để mọi bid trên cùng phiên được serialize.
                Auction auction;
                try {
                  auction = auctionDao.findByIdForUpdate(handle, auctionId);
                } catch (IllegalStateException e) {
                  throw new NotFoundException("Không tìm thấy phiên đấu giá với id: " + auctionId);
                }
                auctionRef.set(auction);

                // 2. State pattern: kiểm tra amount > currentPrice, bidderId != sellerId,
                // và cập nhật auction trong bộ nhớ. Ném exception nếu không hợp lệ.
                Long previousLeaderId = auction.getLeadingBidderId();
                BigDecimal previousPrice = auction.getCurrentPrice();
                auctionService.getState(auction).placeBid(auction, amount, bidderId);

                // 3. Khóa bidder để kiểm tra phần balance còn khả dụng sau các khoản đang giữ.
                User bidder = userDao.findByIdForUpdate(handle, bidderId);
                BigDecimal availableBalance = bidder.getAvailableBalance();
                if (availableBalance.compareTo(amount) < 0) {
                  throw new InvalidBidException(
                      "Số dư khả dụng không đủ. Số dư khả dụng hiện tại: "
                          + availableBalance
                          + ", giá bid: "
                          + amount
                          + ". Vui lòng nạp thêm tiền.");
                }

                if (auction.getRemainingTimeMs() < ANTI_SNIPE_THRESHOLD_MS) {
                  auction.setEndTime(
                      auction.getEndTime().plusSeconds(ANTI_SNIPE_EXTENSION_SECONDS));
                  antiSnipeTriggered.set(true);
                }

                // 4. Release khoản giữ của người dẫn đầu cũ, rồi giữ tiền cho người dẫn đầu mới.
                if (previousLeaderId != null) {
                  userDao.releaseReservedBalanceInTransaction(
                      handle, previousLeaderId, previousPrice);
                }
                userDao.updateReservedBalanceInTransaction(handle, bidderId, amount);

                // 5. Ghi nguyên tử: lưu cả cập nhật giá lẫn gia hạn endTime (nếu có) trong một lần
                auctionDao.updateInTransaction(handle, auction);

                // 6. Lưu thông báo cho người bị vượt giá (nếu có và không phải chính mình)
                if (previousLeaderId != null && !previousLeaderId.equals(bidderId)) {
                  String outbidMsg =
                      String.format(
                          "Bạn đã bị vượt giá tại phiên #%d. Giá hiện tại: %,d VNĐ",
                          auctionId, amount.longValue());
                  handle.execute(
                      "INSERT INTO notifications (user_id, message, notification_type) VALUES (?, ?, 'OUTBID')",
                      previousLeaderId,
                      outbidMsg);
                }

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
            "Chống snipe kích hoạt cho phiên #{}: gia hạn thêm {}s",
            auctionId,
            ANTI_SNIPE_EXTENSION_SECONDS);
      }

      notifyBidUpdate(auction, auctionId, bidderId, amount, isAutoBid);

      if (!isAutoBid) {
        triggerAutoBid(auctionId, amount, bidderId);
      }

      return savedTransaction;
    } finally {
      MDC.clear();
    }
  }

  /**
   * Lấy lịch sử bid của một phiên đấu giá.
   *
   * @param auctionId ID phiên đấu giá
   * @return danh sách BidTransaction sắp xếp theo thời gian tăng dần
   * @throws NotFoundException nếu phiên đấu giá không tồn tại
   */
  public List<BidTransaction> getBidHistory(Long auctionId) {
    if (!auctionDao.existsById(auctionId)) {
      throw new NotFoundException("Không tìm thấy phiên đấu giá với id: " + auctionId);
    }
    return bidTransactionDao.findByAuctionId(auctionId);
  }

  // ── Phương thức nội bộ ────────────────────────────────────

  private void notifyBidUpdate(
      Auction auction, Long auctionId, Long bidderId, BigDecimal amount, boolean isAutoBid) {
    try {
      String username = userDao.findById(bidderId).map(User::getUsername).orElse(null);
      BidUpdateMessage msg =
          BidUpdateMessage.bidUpdate(
              auctionId, amount, bidderId, username, auction.getEndTime(), isAutoBid);
      eventManager.notifyBidUpdate(auctionId, msg);
    } catch (Exception e) {
      LOGGER.error("Lỗi khi gửi thông báo BID_UPDATE cho phiên #{}: {}", auctionId, e.getMessage());
    }
  }

  private void triggerAutoBid(Long auctionId, BigDecimal currentPrice, Long leadingBidderId) {
    try {
      autoBidStrategy.executeAll(
          auctionId,
          currentPrice,
          leadingBidderId,
          (aid, bid, amt) -> this.placeBid(aid, bid, amt, true));
    } catch (Exception e) {
      LOGGER.error("Lỗi khi xử lý đặt giá tự động cho phiên #{}: {}", auctionId, e.getMessage());
    }
  }
}
