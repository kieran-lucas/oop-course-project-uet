package com.auction.pattern.strategy;

import com.auction.dao.AutoBidConfigDao;
import com.auction.exception.InvalidBidException;
import com.auction.model.Auction;
import com.auction.model.AutoBidConfig;
import com.auction.model.BidTransaction;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
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
 *   <li>{@link BidStrategy} — interface mà class này implements
 *   <li>{@link ManualBidStrategy} — dùng logic validate tương tự cho từng auto-bid
 *   <li>{@link AutoBidConfigDao} — lấy danh sách auto-bid configs từ database
 *   <li>{@link com.auction.service.BidService} — gọi {@code executeAll()} sau mỗi manual bid
 * </ul>
 */
public class AutoBidStrategy implements BidStrategy {

  private static final Logger LOGGER = LoggerFactory.getLogger(AutoBidStrategy.class);

  /** Giới hạn số lần auto-bid liên tiếp, tránh vòng lặp vô hạn. */
  private static final int MAX_AUTO_BIDS_PER_TRIGGER = 10;

  private final AutoBidConfigDao autoBidConfigDao;

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

  public AutoBidStrategy(AutoBidConfigDao autoBidConfigDao) {
    this.autoBidConfigDao = autoBidConfigDao;
  }

  /**
   * Thực thi một lần auto-bid đơn lẻ (implements BidStrategy).
   *
   * <p>Logic giống ManualBidStrategy nhưng bỏ qua kiểm tra "seller tự bid" vì auto-bid chỉ áp dụng
   * cho bidder (đã validate khi setup auto-bid config).
   *
   * @param auction phiên đấu giá đang RUNNING
   * @param bidderId ID người được auto-bid thay
   * @param amount số tiền auto-bid (= currentPrice + increment)
   * @param isAutoBid luôn {@code true} cho auto-bid
   * @return BidTransaction mới với autoBid = true
   */
  @Override
  public BidTransaction execute(
      Auction auction, Long bidderId, BigDecimal amount, boolean isAutoBid) {
    if (amount == null || amount.signum() <= 0) {
      throw new InvalidBidException("Giá auto-bid phải lớn hơn 0");
    }

    if (amount.compareTo(auction.getCurrentPrice()) <= 0) {
      throw new InvalidBidException(
          "Giá auto-bid phải cao hơn giá hiện tại: " + auction.getCurrentPrice());
    }

    auction.setCurrentPrice(amount);
    auction.setLeadingBidderId(bidderId);

    LOGGER.debug("Auto-bid: auction={}, bidder={}, amount={}", auction.getId(), bidderId, amount);

    return new BidTransaction(auction.getId(), bidderId, amount, true);
  }

  /**
   * Kích hoạt chuỗi auto-bid sau khi có bid thủ công thành công.
   *
   * <p>Mỗi lần auto-bid thành công có thể kích hoạt thêm auto-bid của người khác, tạo thành chuỗi
   * phản ứng. Chuỗi dừng khi không còn ai đủ budget.
   *
   * <p>Người đang dẫn đầu (initialLeaderId) bị bỏ qua tạm thời để tránh tự bid lại chính mình.
   * Nếu sau đó người khác bid và vượt qua họ, họ sẽ được xử lý lại trong cùng vòng lặp.
   *
   * @param auctionId ID phiên đấu giá
   * @param currentPriceAfterBid giá hiện tại sau bid vừa xảy ra
   * @param initialLeaderId ID người đang dẫn đầu (người vừa bid thủ công)
   * @param executor callback để thực thi từng auto-bid (thường là BidService.placeBid)
   */
  public void executeAll(
      Long auctionId, BigDecimal currentPriceAfterBid, Long initialLeaderId,
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

    while (!queue.isEmpty() && autoBidCount < MAX_AUTO_BIDS_PER_TRIGGER) {
      AutoBidConfig config = queue.poll();

      // Skip if this bidder is already the current leader (don't bid against yourself).
      // Re-add once so they can respond if someone else outbids them later in this chain.
      if (config.getBidderId().equals(currentLeaderId)) {
        if (skippedAsLeader.add(config.getBidderId())) {
          queue.offer(config); // put back for a possible later response
        }
        continue;
      }

      if (!config.canBidAt(currentPrice)) {
        config.setActive(false);
        try {
          autoBidConfigDao.update(config);
          LOGGER.info(
              "Auto-bid hết budget cho bidder={} trong phiên={}", config.getBidderId(), auctionId);
        } catch (Exception e) {
          LOGGER.error(
              "Không thể cập nhật auto-bid config id={}: {}", config.getId(), e.getMessage());
        }
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
        LOGGER.info(
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
}
