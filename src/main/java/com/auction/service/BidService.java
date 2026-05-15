package com.auction.service;

import com.auction.dao.AuctionDao;
import com.auction.dao.AutoBidConfigDao;
import com.auction.dao.BidTransactionDao;
import com.auction.dao.UserDao;
import com.auction.dao.WalletTransactionDao;
import com.auction.dto.BidUpdateMessage;
import com.auction.exception.InvalidBidException;
import com.auction.exception.NotFoundException;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.AutoBidConfig;
import com.auction.model.AutoBidFailureReason;
import com.auction.model.AutoBidStatus;
import com.auction.model.BidTransaction;
import com.auction.model.User;
import com.auction.pattern.observer.AuctionEventManager;
import com.auction.pattern.strategy.AutoBidStrategy;
import com.auction.util.MoneyValidator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jdbi.v3.core.Handle;
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
 *      f. Nếu là manual bid: executeAllInTransaction → toàn bộ chain auto-bid trong cùng handle
 *   3. Sau commit: phát WebSocket events (BID_UPDATE, TIME_EXTENDED)
 * </pre>
 *
 * <p><b>Tính nguyên tử của auto-bid chain:</b> Manual bid + toàn bộ chuỗi auto-bid chạy trong một
 * {@code jdbi.inTransaction()}. Nếu bất kỳ bước nào trong chain gặp lỗi bất ngờ, toàn bộ
 * transaction rollback — không có partial commit. EXHAUSTED và FAILED là trạng thái hợp lệ, được
 * commit cùng transaction.
 *
 * <p><b>WebSocket events:</b> Được phát SAU KHI transaction commit thành công, không trước.
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
   * <p>Nếu là manual bid: sau khi bid thành công, toàn bộ chuỗi auto-bid phản ứng chạy trong CÙNG
   * transaction. WebSocket events được phát sau khi transaction commit.
   *
   * @param auctionId ID phiên đấu giá
   * @param bidderId ID người đặt giá
   * @param amount số tiền đặt giá (phải lớn hơn giá hiện tại)
   * @param isAutoBid {@code true} nếu đây là đặt giá tự động
   * @return BidTransaction đã được lưu vào cơ sở dữ liệu
   */
  public BidTransaction placeBid(
      Long auctionId, Long bidderId, BigDecimal amount, boolean isAutoBid) {
    MDC.put("auctionId", String.valueOf(auctionId));
    MDC.put("userId", String.valueOf(bidderId));
    try {
      requirePositiveIntegerVnd(amount, "Bid amount");

      List<Runnable> postCommitEvents = new ArrayList<>();

      BidTransaction savedTransaction =
          jdbi.inTransaction(
              handle -> {
                Auction auction;
                try {
                  auction = auctionDao.findByIdForUpdate(handle, auctionId);
                } catch (IllegalStateException e) {
                  throw new NotFoundException("Không tìm thấy phiên đấu giá với id: " + auctionId);
                }

                if (!isAutoBid && autoBidConfigDao.hasActiveConfig(handle, auctionId, bidderId)) {
                  throw new InvalidBidException(
                      "Bạn đang bật auto-bid cho phiên này."
                          + " Hãy tắt auto-bid trước khi đặt giá thủ công.");
                }

                Long previousLeaderId = auction.getLeadingBidderId();
                BigDecimal previousPrice = auction.getCurrentPrice();
                auctionService.getState(auction).placeBid(auction, amount, bidderId);

                User bidder = userDao.findByIdForUpdate(handle, bidderId);
                BigDecimal available = bidder.getAvailableBalance();
                if (available.compareTo(amount) < 0) {
                  throw new InvalidBidException(
                      "Số dư khả dụng không đủ. Số dư khả dụng hiện tại: "
                          + available
                          + ", giá bid: "
                          + amount
                          + ". Vui lòng nạp thêm tiền.");
                }

                if (auction.getRemainingTimeMs() < ANTI_SNIPE_THRESHOLD_MS) {
                  auction.setEndTime(
                      auction.getEndTime().plusSeconds(ANTI_SNIPE_EXTENSION_SECONDS));
                  final Auction snap = auction;
                  postCommitEvents.add(
                      () -> {
                        eventManager.notifyTimeExtended(
                            auctionId, BidUpdateMessage.timeExtended(auctionId, snap.getEndTime()));
                        LOGGER.info(
                            "Chống snipe kích hoạt cho phiên #{}: gia hạn thêm {}s",
                            auctionId,
                            ANTI_SNIPE_EXTENSION_SECONDS);
                      });
                }

                if (previousLeaderId != null) {
                  userDao.releaseReservedBalanceInTransaction(
                      handle, previousLeaderId, previousPrice);
                  WalletTransactionDao.insert(
                      handle,
                      previousLeaderId,
                      auctionId,
                      null,
                      "RELEASE",
                      previousPrice,
                      "outbid_by:" + bidderId);
                }
                userDao.updateReservedBalanceInTransaction(handle, bidderId, amount);
                auctionDao.updateInTransaction(handle, auction);

                if (previousLeaderId != null && !previousLeaderId.equals(bidderId)) {
                  handle.execute(
                      "INSERT INTO notifications (user_id, message, notification_type)"
                          + " VALUES (?, ?, 'OUTBID')",
                      previousLeaderId,
                      String.format(
                          Locale.of("vi", "VN"),
                          "Bạn đã bị vượt giá tại phiên #%d. Giá hiện tại: %,d VND",
                          auctionId,
                          toIntegerVnd(amount, "Bid amount")));
                }

                BidTransaction tx = new BidTransaction(auctionId, bidderId, amount, isAutoBid);
                BidTransaction saved = bidTransactionDao.insert(handle, tx);
                WalletTransactionDao.insert(
                    handle,
                    bidderId,
                    auctionId,
                    saved.getId(),
                    "FREEZE",
                    amount,
                    isAutoBid ? "auto_bid" : "manual_bid");

                final Auction auctionSnap = auction;
                postCommitEvents.add(
                    () -> notifyBidUpdate(auctionSnap, auctionId, bidderId, amount, isAutoBid));

                if (!isAutoBid) {
                  autoBidStrategy.executeAllInTransaction(
                      handle,
                      auctionId,
                      amount,
                      bidderId,
                      (h, aid, bid, amt) ->
                          executeChainBidInHandle(h, aid, bid, amt, postCommitEvents));
                }

                LOGGER.debug(
                    "Bid đặt thành công: auction={}, bidder={}, amount={}, autoBid={}",
                    auctionId,
                    bidderId,
                    amount,
                    isAutoBid);
                return saved;
              });

      postCommitEvents.forEach(Runnable::run);
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

  /**
   * Tạo cấu hình auto-bid và đặt giá ban đầu nguyên tử trong một transaction.
   *
   * <p>Sau khi đặt giá ban đầu thành công, toàn bộ chuỗi auto-bid phản ứng cũng chạy trong cùng
   * transaction. WebSocket events được phát sau commit.
   *
   * <ol>
   *   <li>Lock auction (SELECT FOR UPDATE).
   *   <li>Auction phải RUNNING.
   *   <li>Bidder không được là người đang dẫn đầu.
   *   <li>Không được có ACTIVE config trong phiên này.
   *   <li>initialBid = currentPrice + increment.
   *   <li>Nếu initialBid > maxBid → tạo config EXHAUSTED, không bid.
   *   <li>Nếu balance không đủ initialBid → tạo config FAILED, không bid.
   *   <li>Ngược lại → tạo config ACTIVE, insert BidTransaction, freeze initialBid, run chain.
   * </ol>
   *
   * @param auctionId ID phiên đấu giá
   * @param bidderId ID người đặt giá (đã được xác thực là BIDDER ở tầng endpoint)
   * @param maxBid giá tối đa người dùng chấp nhận trả
   * @param increment bước giá tự động mỗi lần bid
   * @return AutoBidConfig đã được lưu (status có thể là ACTIVE, EXHAUSTED hoặc FAILED)
   */
  public AutoBidConfig createAutoBid(
      Long auctionId, Long bidderId, BigDecimal maxBid, BigDecimal increment) {
    MDC.put("auctionId", String.valueOf(auctionId));
    MDC.put("userId", String.valueOf(bidderId));
    try {
      requirePositiveIntegerVnd(increment, "increment");
      requirePositiveIntegerVnd(maxBid, "maxBid");

      List<Runnable> postCommitEvents = new ArrayList<>();

      AutoBidConfig savedConfig =
          jdbi.inTransaction(
              handle -> {
                Auction auction;
                try {
                  auction = auctionDao.findByIdForUpdate(handle, auctionId);
                } catch (IllegalStateException e) {
                  throw new NotFoundException("Không tìm thấy phiên đấu giá: " + auctionId);
                }

                if (auction.getStatus() != AuctionStatus.RUNNING) {
                  throw new InvalidBidException(
                      "Auto-bid chỉ được tạo cho phiên RUNNING. Trạng thái hiện tại: "
                          + auction.getStatus());
                }

                if (bidderId.equals(auction.getLeadingBidderId())) {
                  throw new InvalidBidException(
                      "Bạn đang là người đặt giá cao nhất, không cần bật auto-bid");
                }

                if (autoBidConfigDao.hasActiveConfig(handle, auctionId, bidderId)) {
                  throw new InvalidBidException(
                      "Bạn đã có auto-bid đang hoạt động cho phiên này."
                          + " Hãy dừng trước khi tạo mới.");
                }

                BigDecimal initialBid = auction.getCurrentPrice().add(increment);

                if (initialBid.compareTo(maxBid) > 0) {
                  AutoBidConfig config = new AutoBidConfig(auctionId, bidderId, maxBid, increment);
                  config.setStatus(AutoBidStatus.EXHAUSTED);
                  config.setFailureReason(AutoBidFailureReason.MAX_PRICE_TOO_LOW);
                  autoBidConfigDao.upsertInTransaction(handle, config);
                  handle.execute(
                      "INSERT INTO notifications (user_id, message, notification_type)"
                          + " VALUES (?, ?, 'AUTOBID_EXHAUSTED')",
                      bidderId,
                      String.format(
                          Locale.of("vi", "VN"),
                          "Auto-bid cho phiên #%d không được kích hoạt:"
                              + " mức tối đa %,d VND thấp hơn giá đặt ban đầu cần thiết %,d VND.",
                          auctionId,
                          toIntegerVnd(maxBid, "Max bid"),
                          toIntegerVnd(initialBid, "Initial bid")));
                  return config;
                }

                User bidder = userDao.findByIdForUpdate(handle, bidderId);
                if (bidder.getAvailableBalance().compareTo(initialBid) < 0) {
                  AutoBidConfig config = new AutoBidConfig(auctionId, bidderId, maxBid, increment);
                  config.setStatus(AutoBidStatus.FAILED);
                  config.setFailureReason(AutoBidFailureReason.INSUFFICIENT_BALANCE);
                  autoBidConfigDao.upsertInTransaction(handle, config);
                  handle.execute(
                      "INSERT INTO notifications (user_id, message, notification_type)"
                          + " VALUES (?, ?, 'AUTOBID_FAILED')",
                      bidderId,
                      String.format(
                          Locale.of("vi", "VN"),
                          "Auto-bid cho phiên #%d không được kích hoạt:"
                              + " số dư không đủ. Cần %,d VND, có %,d VND",
                          auctionId,
                          toIntegerVnd(initialBid, "Initial bid"),
                          toIntegerVnd(bidder.getAvailableBalance(), "Available balance")));
                  return config;
                }

                Long previousLeaderId = auction.getLeadingBidderId();
                BigDecimal previousPrice = auction.getCurrentPrice();

                auction.setCurrentPrice(initialBid);
                auction.setLeadingBidderId(bidderId);

                if (auction.getRemainingTimeMs() < ANTI_SNIPE_THRESHOLD_MS) {
                  auction.setEndTime(
                      auction.getEndTime().plusSeconds(ANTI_SNIPE_EXTENSION_SECONDS));
                  final Auction snap = auction;
                  postCommitEvents.add(
                      () ->
                          eventManager.notifyTimeExtended(
                              auctionId,
                              BidUpdateMessage.timeExtended(auctionId, snap.getEndTime())));
                }

                if (previousLeaderId != null) {
                  userDao.releaseReservedBalanceInTransaction(
                      handle, previousLeaderId, previousPrice);
                  WalletTransactionDao.insert(
                      handle,
                      previousLeaderId,
                      auctionId,
                      null,
                      "RELEASE",
                      previousPrice,
                      "outbid_by:" + bidderId);
                }
                userDao.updateReservedBalanceInTransaction(handle, bidderId, initialBid);
                auctionDao.updateInTransaction(handle, auction);

                if (previousLeaderId != null && !previousLeaderId.equals(bidderId)) {
                  handle.execute(
                      "INSERT INTO notifications (user_id, message, notification_type)"
                          + " VALUES (?, ?, 'OUTBID')",
                      previousLeaderId,
                      String.format(
                          Locale.of("vi", "VN"),
                          "Bạn đã bị vượt giá tại phiên #%d. Giá hiện tại: %,d VND",
                          auctionId,
                          toIntegerVnd(initialBid, "Initial bid")));
                }

                BidTransaction tx = new BidTransaction(auctionId, bidderId, initialBid, true);
                BidTransaction savedBid = bidTransactionDao.insert(handle, tx);
                WalletTransactionDao.insert(
                    handle,
                    bidderId,
                    auctionId,
                    savedBid.getId(),
                    "FREEZE",
                    initialBid,
                    "auto_bid_initial");

                AutoBidConfig config = new AutoBidConfig(auctionId, bidderId, maxBid, increment);
                autoBidConfigDao.upsertInTransaction(handle, config);

                final Auction auctionSnap = auction;
                final BigDecimal initialBidSnap = initialBid;
                postCommitEvents.add(
                    () -> notifyBidUpdate(auctionSnap, auctionId, bidderId, initialBidSnap, true));

                autoBidStrategy.executeAllInTransaction(
                    handle,
                    auctionId,
                    initialBid,
                    bidderId,
                    (h, aid, bid, amt) ->
                        executeChainBidInHandle(h, aid, bid, amt, postCommitEvents));

                return config;
              });

      postCommitEvents.forEach(Runnable::run);
      return savedConfig;
    } finally {
      MDC.clear();
    }
  }

  // ── Phương thức nội bộ ────────────────────────────────────

  /**
   * Thực thi một bước auto-bid trong transaction đang mở.
   *
   * <p>Phương thức này được {@code AutoBidStrategy.executeAllInTransaction} gọi cho mỗi bước chain.
   * Balance đã được pre-check ở strategy layer; phương thức này chịu trách nhiệm: validate giá,
   * anti-snipe, reserve/release balance, ghi auction và bid transaction. Post-commit events được
   * đẩy vào {@code postCommitEvents} để phát sau khi transaction commit.
   */
  BidTransaction executeChainBidInHandle(
      Handle handle,
      Long auctionId,
      Long bidderId,
      BigDecimal amount,
      List<Runnable> postCommitEvents) {

    Auction auction;
    try {
      auction = auctionDao.findByIdForUpdate(handle, auctionId);
    } catch (IllegalStateException e) {
      throw new NotFoundException("Không tìm thấy phiên đấu giá: " + auctionId);
    }

    Long previousLeaderId = auction.getLeadingBidderId();
    BigDecimal previousPrice = auction.getCurrentPrice();
    auctionService.getState(auction).placeBid(auction, amount, bidderId);

    if (auction.getRemainingTimeMs() < ANTI_SNIPE_THRESHOLD_MS) {
      auction.setEndTime(auction.getEndTime().plusSeconds(ANTI_SNIPE_EXTENSION_SECONDS));
      final Auction snap = auction;
      postCommitEvents.add(
          () ->
              eventManager.notifyTimeExtended(
                  auctionId, BidUpdateMessage.timeExtended(auctionId, snap.getEndTime())));
    }

    if (previousLeaderId != null) {
      userDao.releaseReservedBalanceInTransaction(handle, previousLeaderId, previousPrice);
      WalletTransactionDao.insert(
          handle,
          previousLeaderId,
          auctionId,
          null,
          "RELEASE",
          previousPrice,
          "outbid_by:" + bidderId);
    }
    userDao.updateReservedBalanceInTransaction(handle, bidderId, amount);
    auctionDao.updateInTransaction(handle, auction);

    if (previousLeaderId != null && !previousLeaderId.equals(bidderId)) {
      handle.execute(
          "INSERT INTO notifications (user_id, message, notification_type) VALUES (?, ?, 'OUTBID')",
          previousLeaderId,
          String.format(
              Locale.of("vi", "VN"),
              "Bạn đã bị vượt giá tại phiên #%d. Giá hiện tại: %,d VND",
              auctionId,
              toIntegerVnd(amount, "Bid amount")));
    }

    BidTransaction tx = new BidTransaction(auctionId, bidderId, amount, true);
    BidTransaction saved = bidTransactionDao.insert(handle, tx);
    WalletTransactionDao.insert(
        handle, bidderId, auctionId, saved.getId(), "FREEZE", amount, "auto_bid_chain");

    final Auction auctionSnap = auction;
    postCommitEvents.add(() -> notifyBidUpdate(auctionSnap, auctionId, bidderId, amount, true));

    return saved;
  }

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

  private static void requirePositiveIntegerVnd(BigDecimal amount, String fieldName) {
    try {
      MoneyValidator.requirePositiveIntegerVnd(amount, fieldName);
    } catch (IllegalArgumentException e) {
      throw new InvalidBidException(e.getMessage());
    }
  }

  private static long toIntegerVnd(BigDecimal amount, String fieldName) {
    try {
      return MoneyValidator.toIntegerVndExact(amount, fieldName);
    } catch (IllegalArgumentException | ArithmeticException e) {
      throw new InvalidBidException(e.getMessage());
    }
  }
}
