package com.auction.util;

import com.auction.dto.BidUpdateMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton theo dõi realtime các phiên đấu giá trong nền, dành cho trường hợp user đã đặt giá
 * nhưng đã rời khỏi màn hình chi tiết ({@code AuctionDetailController}).
 *
 * <h2>Vấn đề giải quyết</h2>
 *
 * Khi user đang xem {@code auction-detail.fxml}, controller đó tự quản lý kết nối WebSocket và hiển
 * thị cập nhật trực tiếp. Tuy nhiên khi user điều hướng sang màn hình khác, kết nối đó bị đóng
 * ({@code onNavigatedFrom}), và user không còn nhận được thông báo về các phiên mình đang đặt giá
 * nữa.
 *
 * <p>{@code BackgroundBidWatcher} giải quyết vấn đề này bằng cách duy trì kết nối WebSocket riêng
 * cho mỗi phiên đấu giá mà user đang tham gia, ngay cả khi user không ở màn hình đó.
 *
 * <h2>Luồng hoạt động</h2>
 *
 * <ol>
 *   <li>User đặt giá tại {@code AuctionDetailController} → khi rời màn hình, controller gọi {@link
 *       #watch(Long, String, String, Long)} để đăng ký theo dõi nền.
 *   <li>{@code BackgroundBidWatcher} mở một {@link WebSocketClient} riêng cho phiên đó và parse các
 *       message nhận được.
 *   <li>Khi có sự kiện liên quan (bị vượt giá, auto-bid kích hoạt, phiên kết thúc), một thông báo
 *       được đẩy vào {@link NotificationStore} qua {@code Platform.runLater()}.
 *   <li>Khi user quay lại màn hình chi tiết của phiên đó, {@code AuctionDetailController} gọi
 *       {@link #stopWatching(Long)} để tránh nhận event trùng với kết nối mới của controller.
 *   <li>Khi user đăng xuất, {@link com.auction.ui.util.SceneManager#logout()} gọi {@link
 *       #stopAll()} để đóng tất cả kết nối nền.
 * </ol>
 *
 * <h2>Các sự kiện được xử lý</h2>
 *
 * <ul>
 *   <li>{@code BID_UPDATE} (không phải auto-bid): thông báo khi người khác vượt giá của user.
 *   <li>{@code BID_UPDATE} (auto-bid của chính user): thông báo "auto-bid vừa đặt giá thay bạn" —
 *       surface bid lên bell khi user đang ở màn hình khác.
 *   <li>{@code AUCTION_ENDED}: thông báo khi phiên kết thúc, kèm tên người thắng. Watcher tự dừng
 *       sau sự kiện này.
 *   <li>Các type khác (ví dụ: {@code TIME_EXTENDED}): bỏ qua.
 * </ul>
 *
 * @see WebSocketClient
 * @see NotificationStore
 * @see com.auction.ui.util.SceneManager#logout()
 */
public class BackgroundBidWatcher {

  private static final BackgroundBidWatcher INSTANCE = new BackgroundBidWatcher();
  private static final Logger LOGGER = LoggerFactory.getLogger(BackgroundBidWatcher.class);
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());

  /**
   * Map lưu các watcher đang hoạt động.
   *
   * <ul>
   *   <li>Key: {@code auctionId} của phiên đang được theo dõi
   *   <li>Value: {@link WebSocketClient} đang kết nối đến channel của phiên đó
   * </ul>
   *
   * <p>Dùng {@link ConcurrentHashMap} vì {@code stopWatching} có thể được gọi từ FX thread trong
   * khi callback WebSocket chạy trên thread khác (ví dụ: khi {@code AUCTION_ENDED} tự gọi {@code
   * stopWatching} bên trong {@code Platform.runLater}).
   */
  private final Map<Long, WebSocketClient> watchers = new ConcurrentHashMap<>();

  private BackgroundBidWatcher() {}

  /**
   * Trả về instance duy nhất của {@code BackgroundBidWatcher}.
   *
   * @return singleton instance
   */
  public static BackgroundBidWatcher getInstance() {
    return INSTANCE;
  }

  /**
   * Bắt đầu theo dõi một phiên đấu giá trong nền.
   *
   * <p>Mở kết nối {@link WebSocketClient} mới cho phiên {@code auctionId} và lắng nghe các sự kiện
   * realtime. Khi nhận được sự kiện liên quan đến {@code userId}, thông báo được đẩy vào {@link
   * NotificationStore}.
   *
   * <p><b>Idempotent:</b> Nếu đã có watcher cho {@code auctionId}, method này là no-op — không tạo
   * kết nối thứ hai. Điều này đảm bảo an toàn khi gọi nhiều lần (ví dụ: user rời đi và quay lại rồi
   * rời đi lần nữa mà không ghé màn hình chi tiết).
   *
   * @param auctionId ID phiên đấu giá cần theo dõi
   * @param token JWT token của user dùng để xác thực WebSocket
   * @param itemName tên sản phẩm đấu giá — dùng để compose nội dung thông báo; nếu {@code null} thì
   *     fallback về {@code "Phiên #<auctionId>"}
   * @param userId ID của user hiện tại — dùng để phân biệt sự kiện liên quan đến chính user hay
   *     người khác
   */
  public void watch(Long auctionId, String token, String itemName, Long userId) {
    if (watchers.containsKey(auctionId)) {
      LOGGER.debug("BackgroundBidWatcher: đã watch auctionId={}, bỏ qua.", auctionId);
      return;
    }

    WebSocketClient client = new WebSocketClient();
    watchers.put(auctionId, client);

    String name = itemName != null ? itemName : "Phiên #" + auctionId;

    client.connect(
        auctionId,
        token,
        json -> {
          try {
            BidUpdateMessage msg = MAPPER.readValue(json, BidUpdateMessage.class);
            String type = msg.getType();
            if (type == null) {
              return;
            }

            switch (type) {
              case BidUpdateMessage.TYPE_BID_UPDATE -> {
                Long leaderId = msg.getLeadingBidderId();
                BigDecimal price = msg.getCurrentPrice();
                NumberFormat fmt = NumberFormat.getNumberInstance(Locale.of("vi", "VN"));
                String priceStr = price != null ? fmt.format(price) + " VND" : "?";
                String notification;
                if (leaderId == null) {
                  break;
                }
                if (leaderId.equals(userId)) {
                  // The user's own auto-bid placed this bid while they're away from the
                  // detail screen — surface it in the bell so they know the chain is working.
                  if (!msg.isAutoBid()) {
                    break;
                  }
                  notification =
                      "Auto-bid của bạn vừa đặt giá tại phiên ["
                          + name
                          + "]. Giá hiện tại: "
                          + priceStr;
                } else {
                  notification =
                      "Bạn đã bị vượt giá tại phiên [" + name + "]. Giá hiện tại: " + priceStr;
                }
                final String finalNotification = notification;
                Platform.runLater(() -> NotificationStore.getInstance().add(finalNotification));
              }
              case BidUpdateMessage.TYPE_AUCTION_ENDED -> {
                String winner = msg.getLeadingBidderUsername();
                String notification =
                    "🏁 Phiên kết thúc: ["
                        + name
                        + "]"
                        + (winner != null ? " — Người thắng: " + winner : "");
                Platform.runLater(
                    () -> {
                      NotificationStore.getInstance().add(notification);
                      stopWatching(auctionId);
                    });
              }
              default -> {
                // TYPE_TIME_EXTENDED và các type khác: bỏ qua
              }
            }
          } catch (Exception e) {
            LOGGER.debug(
                "BackgroundBidWatcher parse error (auction={}): {}", auctionId, e.getMessage());
          }
        });

    LOGGER.info("BackgroundBidWatcher: bắt đầu watch auctionId={}", auctionId);
  }

  /**
   * Dừng theo dõi một phiên đấu giá cụ thể và đóng kết nối WebSocket tương ứng.
   *
   * <p>Nếu không có watcher nào cho {@code auctionId}, method này là no-op.
   *
   * <p>Thường được gọi trong hai tình huống:
   *
   * <ul>
   *   <li>User điều hướng <em>trở lại</em> màn hình chi tiết của phiên đó — tránh nhận event trùng
   *       với kết nối WebSocket mới của {@code AuctionDetailController}.
   *   <li>Phiên đấu giá kết thúc ({@code AUCTION_ENDED}) — watcher tự dọn dẹp từ bên trong
   *       callback.
   * </ul>
   *
   * @param auctionId ID phiên đấu giá cần dừng theo dõi
   */
  public void stopWatching(Long auctionId) {
    WebSocketClient client = watchers.remove(auctionId);
    if (client != null) {
      client.disconnect();
      LOGGER.info("BackgroundBidWatcher: dừng watch auctionId={}", auctionId);
    }
  }

  /**
   * Dừng tất cả watcher và đóng toàn bộ kết nối WebSocket đang hoạt động.
   *
   * <p>Được gọi bởi {@link com.auction.ui.util.SceneManager#logout()} khi người dùng đăng xuất, đảm
   * bảo không còn kết nối nền nào giữ lại sau phiên.
   */
  public void stopAll() {
    watchers.forEach(
        (id, client) -> {
          client.disconnect();
          LOGGER.info("BackgroundBidWatcher: dừng watch auctionId={} (stopAll)", id);
        });
    watchers.clear();
  }
}
