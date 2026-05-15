package com.auction.pattern.strategy;

import com.auction.dao.AutoBidConfigDao;
import com.auction.dao.UserDao;
import com.auction.exception.InvalidBidException;
import com.auction.model.AutoBidConfig;
import com.auction.model.AutoBidFailureReason;
import com.auction.model.AutoBidStatus;
import com.auction.model.BidTransaction;
import com.auction.model.User;
import com.auction.util.MoneyValidator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.Set;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ConcreteStrategy cho đặt giá tự động — tự động đặt giá thay người dùng khi có bid mới.
 *
 * <p><b>Pattern được áp dụng: Strategy (Behavioral Pattern)</b>
 *
 * <p>AutoBidStrategy xử lý chuỗi auto-bid sau khi có một bid thủ công:
 *
 * <ol>
 *   <li>Lấy tất cả {@link AutoBidConfig} active của phiên từ database.
 *   <li>Sắp xếp vào {@code PriorityQueue} theo {@code registeredAt} (ai đăng ký trước được ưu
 *       tiên).
 *   <li>Duyệt queue: ai còn budget ({@code canBidAt(currentPrice) == true}) → tự động bid.
 *   <li>Nếu budget bị vượt → đánh dấu config là {@code active = false} và cập nhật DB.
 *   <li>Giới hạn tối đa {@value #MAX_AUTO_BIDS_PER_TRIGGER} lần auto-bid liên tiếp để tránh vòng
 *       lặp vô hạn.
 * </ol>
 *
 * <p><b>Ví dụ chuỗi auto-bid:</b>
 *
 * <pre>
 *   User C bid thủ công 5,000,000đ
 *   → AutoBidStrategy.executeAll()
 *   → User A: maxBid=10tr, increment=100k → canBid(5M) = true → auto-bid 5,100,000đ
 *   → User B: maxBid=6tr, increment=200k → canBid(5.1M) = true → auto-bid 5,300,000đ
 *   → User A: canBid(5.3M) = true → auto-bid 5,400,000đ
 *   → ... tiếp tục cho đến khi không còn ai đủ budget
 * </pre>
 *
 * <p><b>Lý do dùng PriorityQueue:</b> Sắp xếp theo {@code registeredAt} đảm bảo tính công bằng:
 * user đăng ký auto-bid trước có ưu tiên khi cùng budget.
 *
 * <p><b>Functional interface AutoBidExecutor:</b> Để tránh circular dependency (AutoBidStrategy →
 * BidService → AutoBidStrategy), class này nhận một callback {@link AutoBidExecutor} thay vì inject
 * BidService trực tiếp. BidService truyền {@code (aid, bid, amt) -> this.placeBid(aid, bid, amt,
 * true)} khi gọi.
 *
 * <p><b>Liên kết với các file khác:</b>
 *
 * <ul>
 *   <li>{@link AutoBidConfigDao} — lấy danh sách auto-bid configs từ database
 *   <li>{@link com.auction.service.BidService} — gọi {@code executeAll()} sau mỗi manual bid
 * </ul>
 */
public class AutoBidStrategy {

  private static final Logger LOGGER = LoggerFactory.getLogger(AutoBidStrategy.class);

  /** Giới hạn số lần auto-bid liên tiếp, tránh vòng lặp vô hạn. */
  public static final int MAX_AUTO_BIDS_PER_TRIGGER = 100;

  private final AutoBidConfigDao autoBidConfigDao;
  private final UserDao userDao;

  /**
   * Functional interface để tránh circular dependency với BidService.
   *
   * <p>BidService truyền lambda: {@code (auctionId, bidderId, amount) -> this.placeBid(auctionId,
   * bidderId, amount, true)}
   */
  @FunctionalInterface
  public interface AutoBidExecutor {
    /**
     * Thực thi một lần auto-bid.
     *
     * @param auctionId ID phiên đấu giá
     * @param bidderId ID người được auto-bid thay
     * @param amount số tiền auto-bid
     * @return BidTransaction vừa được ghi lại
     */
    BidTransaction execute(Long auctionId, Long bidderId, BigDecimal amount);
  }

  /**
   * Functional interface cho executor chạy trong transaction đang mở. Khác với {@link
   * AutoBidExecutor}: nhận thêm {@link Handle} để thực thi trong cùng transaction.
   */
  @FunctionalInterface
  public interface InTransactionBidExecutor {
    BidTransaction execute(Handle handle, Long auctionId, Long bidderId, BigDecimal amount);
  }

  public AutoBidStrategy(AutoBidConfigDao autoBidConfigDao, UserDao userDao) {
    this.autoBidConfigDao = autoBidConfigDao;
    this.userDao = userDao;
  }

  /**
   * Kích hoạt chuỗi auto-bid sau khi có bid thủ công thành công.
   *
   * <p>Mỗi lần auto-bid thành công có thể kích hoạt thêm auto-bid của người khác, tạo thành chuỗi
   * phản ứng. Chuỗi dừng khi không còn ai đủ budget.
   *
   * <p>Người đang dẫn đầu (initialLeaderId) bị bỏ qua tạm thời để tránh tự bid lại chính mình. Nếu
   * sau đó người khác bid và vượt qua họ, họ sẽ được xử lý lại trong cùng vòng lặp.
   *
   * @param auctionId ID phiên đấu giá
   * @param currentPriceAfterBid giá hiện tại sau bid vừa xảy ra
   * @param initialLeaderId ID người đang dẫn đầu (người vừa bid thủ công)
   * @param executor callback để thực thi từng auto-bid (thường là BidService.placeBid)
   */
  public void executeAll(
      Long auctionId,
      BigDecimal currentPriceAfterBid,
      Long initialLeaderId,
      AutoBidExecutor executor) {
    // Load configs once — avoids N+1 query inside the loop
    List<AutoBidConfig> activeConfigs = autoBidConfigDao.findActiveByAuctionId(auctionId);

    PriorityQueue<AutoBidConfig> queue =
        new PriorityQueue<>(Comparator.comparing(AutoBidConfig::getRegisteredAt));
    queue.addAll(activeConfigs);

    int autoBidCount = 0;
    BigDecimal currentPrice = currentPriceAfterBid;
    Long currentLeaderId = initialLeaderId;
    // Tracks bidders skipped because they were the leader, reset when a bid is placed.
    // If a bidder is seen as leader twice without any bid in between, the chain is stuck.
    Set<Long> skippedAsLeader = new HashSet<>();
    List<AutoBidConfig> skippedLeaderConfigs = new ArrayList<>();

    while (!queue.isEmpty() && autoBidCount < MAX_AUTO_BIDS_PER_TRIGGER) {
      AutoBidConfig config = queue.poll();
      AutoBidConfig freshConfig = autoBidConfigDao.findById(config.getId()).orElse(null);
      if (freshConfig == null || !freshConfig.isActive()) {
        LOGGER.debug("Auto-bid config #{} was deactivated mid-chain — skipping", config.getId());
        continue;
      }
      config = freshConfig;

      // Skip if this bidder is already the current leader (don't bid against yourself).
      // Re-add once so they can respond if someone else outbids them later in this chain.
      if (config.getBidderId().equals(currentLeaderId)) {
        if (skippedAsLeader.add(config.getBidderId())) {
          skippedLeaderConfigs.add(config); // requeue after another bidder advances the price
        }
        continue;
      }

      if (!config.canBidAt(currentPrice)) {
        config.setStatus(AutoBidStatus.EXHAUSTED);
        config.setFailureReason(AutoBidFailureReason.MAX_PRICE_TOO_LOW);
        try {
          autoBidConfigDao.update(config);
          LOGGER.debug(
              "Auto-bid hết budget cho bidder={} trong phiên={}", config.getBidderId(), auctionId);
        } catch (Exception e) {
          LOGGER.error(
              "Không thể cập nhật auto-bid config id={}: {}", config.getId(), e.getMessage());
        }
        for (AutoBidConfig skippedConfig : new ArrayList<>(skippedLeaderConfigs)) {
          if (autoBidCount >= MAX_AUTO_BIDS_PER_TRIGGER) {
            break;
          }
          AutoBidConfig freshSkippedConfig =
              autoBidConfigDao.findById(skippedConfig.getId()).orElse(null);
          if (freshSkippedConfig == null
              || !freshSkippedConfig.isActive()
              || !freshSkippedConfig.canBidAt(currentPrice)) {
            continue;
          }

          BigDecimal nextAmount = freshSkippedConfig.getNextBidAmount(currentPrice);
          try {
            executor.execute(auctionId, freshSkippedConfig.getBidderId(), nextAmount);
            currentPrice = nextAmount;
            currentLeaderId = freshSkippedConfig.getBidderId();
            autoBidCount++;
            queue.offer(freshSkippedConfig);
            LOGGER.debug(
                "Auto-bid thành công: bidder={}, amount={}, phiên={}",
                freshSkippedConfig.getBidderId(),
                nextAmount,
                auctionId);
          } catch (Exception e) {
            LOGGER.warn(
                "Auto-bid thất bại cho bidder={}: {}",
                freshSkippedConfig.getBidderId(),
                e.getMessage());
          }
        }
        skippedAsLeader.clear();
        skippedLeaderConfigs.clear();
        continue;
      }

      BigDecimal nextAmount = config.getNextBidAmount(currentPrice);

      try {
        executor.execute(auctionId, config.getBidderId(), nextAmount);
        currentPrice = nextAmount;
        currentLeaderId = config.getBidderId();
        autoBidCount++;
        skippedAsLeader.clear(); // new bid → previous leader skip tracking is stale
        // Re-add so this bidder can respond to subsequent bids from others
        queue.offer(config);
        queue.addAll(skippedLeaderConfigs);
        skippedLeaderConfigs.clear();
        LOGGER.debug(
            "Auto-bid thành công: bidder={}, amount={}, phiên={}",
            config.getBidderId(),
            nextAmount,
            auctionId);
      } catch (Exception e) {
        LOGGER.warn("Auto-bid thất bại cho bidder={}: {}", config.getBidderId(), e.getMessage());
      }
    }

    if (autoBidCount >= MAX_AUTO_BIDS_PER_TRIGGER) {
      LOGGER.warn("Đạt giới hạn {} auto-bid cho phiên #{}", MAX_AUTO_BIDS_PER_TRIGGER, auctionId);
    }
  }

  /**
   * Thực thi toàn bộ chuỗi auto-bid trong cùng một transaction đang mở.
   *
   * <p>Mọi thao tác (đọc config, đọc balance, ghi bid, cập nhật trạng thái config, ghi thông báo)
   * đều dùng {@code handle} được truyền vào — không mở connection hoặc transaction mới. Điều này
   * đảm bảo tính nguyên tử: nếu có lỗi bất ngờ, toàn bộ chain sẽ rollback cùng với manual bid.
   *
   * <p><b>EXHAUSTED:</b> khi {@code nextBid > maxBid}, config bị đánh dấu EXHAUSTED và người dùng
   * nhận thông báo trong cùng transaction. Chain tiếp tục với các config khác.
   *
   * <p><b>FAILED:</b> khi balance không đủ {@code nextBid}, config bị đánh dấu FAILED và người dùng
   * nhận thông báo. Chain tiếp tục.
   *
   * <p>Exception bất ngờ từ executor KHÔNG bị nuốt — chúng sẽ propagate và gây rollback toàn bộ
   * transaction.
   *
   * @param handle JDBI handle của transaction đang mở
   * @param auctionId ID phiên đấu giá
   * @param currentPriceAfterBid giá sau bid vừa xảy ra
   * @param initialLeaderId ID người đang dẫn đầu
   * @param executor callback thực thi một lần auto-bid trong transaction
   */
  public void executeAllInTransaction(
      Handle handle,
      Long auctionId,
      BigDecimal currentPriceAfterBid,
      Long initialLeaderId,
      InTransactionBidExecutor executor) {

    List<AutoBidConfig> activeConfigs =
        autoBidConfigDao.findActiveByAuctionIdInTransaction(handle, auctionId);

    PriorityQueue<AutoBidConfig> queue =
        new PriorityQueue<>(Comparator.comparing(AutoBidConfig::getRegisteredAt));
    queue.addAll(activeConfigs);

    int autoBidCount = 0;
    BigDecimal currentPrice = currentPriceAfterBid;
    Long currentLeaderId = initialLeaderId;
    Set<Long> skippedAsLeader = new HashSet<>();
    List<AutoBidConfig> skippedLeaderConfigs = new ArrayList<>();

    while (!queue.isEmpty() && autoBidCount < MAX_AUTO_BIDS_PER_TRIGGER) {
      AutoBidConfig config = queue.poll();
      AutoBidConfig fresh =
          autoBidConfigDao.findByIdInTransaction(handle, config.getId()).orElse(null);
      if (fresh == null || !fresh.isActive()) {
        LOGGER.debug("Auto-bid config #{} deactivated mid-chain — skipping", config.getId());
        continue;
      }
      config = fresh;

      if (config.getBidderId().equals(currentLeaderId)) {
        if (skippedAsLeader.add(config.getBidderId())) {
          skippedLeaderConfigs.add(config);
        }
        continue;
      }

      if (!config.canBidAt(currentPrice)) {
        config.setStatus(AutoBidStatus.EXHAUSTED);
        config.setFailureReason(AutoBidFailureReason.MAX_PRICE_TOO_LOW);
        autoBidConfigDao.updateStatusInTransaction(handle, config);
        handle.execute(
            "INSERT INTO notifications (user_id, message, notification_type)"
                + " VALUES (?, ?, 'AUTOBID_EXHAUSTED')",
            config.getBidderId(),
            String.format(
                Locale.of("vi", "VN"),
                "Auto-bid của bạn cho phiên #%d đã dừng:"
                    + " giá hiện tại %,d VND đã vượt mức tối đa %,d VND của bạn.",
                auctionId,
                toIntegerVnd(currentPrice, "Current price"),
                toIntegerVnd(config.getMaxBid(), "Max bid")));
        LOGGER.debug("Auto-bid EXHAUSTED: bidder={}, auction={}", config.getBidderId(), auctionId);
        for (AutoBidConfig skipped : skippedLeaderConfigs) {
          queue.offer(skipped);
        }
        skippedAsLeader.clear();
        skippedLeaderConfigs.clear();
        continue;
      }

      BigDecimal nextAmount = config.getNextBidAmount(currentPrice);

      User bidder = userDao.findByIdForUpdate(handle, config.getBidderId());
      if (bidder.getAvailableBalance().compareTo(nextAmount) < 0) {
        config.setStatus(AutoBidStatus.FAILED);
        config.setFailureReason(AutoBidFailureReason.INSUFFICIENT_BALANCE);
        autoBidConfigDao.updateStatusInTransaction(handle, config);
        handle.execute(
            "INSERT INTO notifications (user_id, message, notification_type)"
                + " VALUES (?, ?, 'AUTOBID_FAILED')",
            config.getBidderId(),
            String.format(
                Locale.of("vi", "VN"),
                "Auto-bid của bạn cho phiên #%d thất bại: số dư không đủ."
                    + " Cần %,d VND, có %,d VND",
                auctionId,
                toIntegerVnd(nextAmount, "Next auto-bid amount"),
                toIntegerVnd(bidder.getAvailableBalance(), "Available balance")));
        LOGGER.debug(
            "Auto-bid FAILED (balance): bidder={}, auction={}", config.getBidderId(), auctionId);
        for (AutoBidConfig skipped : skippedLeaderConfigs) {
          queue.offer(skipped);
        }
        skippedAsLeader.clear();
        skippedLeaderConfigs.clear();
        continue;
      }

      executor.execute(handle, auctionId, config.getBidderId(), nextAmount);
      currentPrice = nextAmount;
      currentLeaderId = config.getBidderId();
      autoBidCount++;
      skippedAsLeader.clear();

      // If this bid reached the user's max so no further bid is possible, stop the auto-bid
      // now and notify the user immediately — don't wait for someone else to outbid them first.
      if (!config.canBidAt(currentPrice)) {
        config.setStatus(AutoBidStatus.EXHAUSTED);
        config.setFailureReason(AutoBidFailureReason.MAX_PRICE_TOO_LOW);
        autoBidConfigDao.updateStatusInTransaction(handle, config);
        handle.execute(
            "INSERT INTO notifications (user_id, message, notification_type)"
                + " VALUES (?, ?, 'AUTOBID_EXHAUSTED')",
            config.getBidderId(),
            String.format(
                Locale.of("vi", "VN"),
                "Auto-bid của bạn cho phiên #%d đã đạt mức tối đa %,d VND và đã được dừng.",
                auctionId,
                toIntegerVnd(config.getMaxBid(), "Max bid")));
        queue.addAll(skippedLeaderConfigs);
        skippedLeaderConfigs.clear();
        LOGGER.debug(
            "Auto-bid đạt max ngay sau bid: bidder={}, amount={}, phiên={}",
            config.getBidderId(),
            nextAmount,
            auctionId);
        continue;
      }

      queue.offer(config);
      queue.addAll(skippedLeaderConfigs);
      skippedLeaderConfigs.clear();
      LOGGER.debug(
          "Auto-bid thành công: bidder={}, amount={}, phiên={}",
          config.getBidderId(),
          nextAmount,
          auctionId);
    }

    if (autoBidCount >= MAX_AUTO_BIDS_PER_TRIGGER) {
      LOGGER.warn("Đạt giới hạn {} auto-bid cho phiên #{}", MAX_AUTO_BIDS_PER_TRIGGER, auctionId);
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
