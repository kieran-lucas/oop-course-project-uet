package com.auction.util;

import com.auction.dto.BidUpdateMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton duy trì các WebSocket nền cho phiên đấu giá người dùng đã từng bid.
 *
 * <p>Khi người dùng rời màn hình chi tiết đấu giá, {@code AuctionDetailController} đăng ký phiên
 * này vào đây. Mọi sự kiện BID_UPDATE và AUCTION_ENDED nhận được sẽ được thêm vào {@link
 * NotificationStore} — chuông thông báo trên màn hình danh sách sẽ sáng lên tức thời.
 *
 * <p>Khi người dùng quay lại màn hình chi tiết, {@code stopWatching()} được gọi để nhường lại kết
 * nối cho controller (tránh nhận thông báo trùng). Khi đăng xuất, {@code stopAll()} dọn sạch tất cả
 * kết nối.
 */
public class BackgroundBidWatcher {

  private static final BackgroundBidWatcher INSTANCE = new BackgroundBidWatcher();
  private static final Logger LOGGER = LoggerFactory.getLogger(BackgroundBidWatcher.class);
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());
  private static final NumberFormat VND = NumberFormat.getCurrencyInstance(Locale.of("vi", "VN"));

  private final Map<Long, WebSocketClient> watchers = new ConcurrentHashMap<>();
  private final Map<Long, String> itemNames = new ConcurrentHashMap<>();

  private BackgroundBidWatcher() {}

  public static BackgroundBidWatcher getInstance() {
    return INSTANCE;
  }

  /**
   * Bắt đầu theo dõi nền cho một phiên đấu giá.
   *
   * @param auctionId ID phiên cần theo dõi
   * @param token JWT token để xác thực WebSocket
   * @param itemName tên sản phẩm (dùng trong nội dung thông báo)
   * @param currentUserId ID người dùng hiện tại (để phân biệt bid của mình)
   */
  public void watch(Long auctionId, String token, String itemName, Long currentUserId) {
    if (auctionId == null || token == null || watchers.containsKey(auctionId)) {
      return;
    }
    String label = itemName != null ? itemName : "Phiên #" + auctionId;
    itemNames.put(auctionId, label);
    WebSocketClient ws = new WebSocketClient();
    watchers.put(auctionId, ws);
    ws.connect(
        auctionId,
        token,
        json -> {
          try {
            BidUpdateMessage msg = MAPPER.readValue(json, BidUpdateMessage.class);
            handleMessage(auctionId, msg, currentUserId);
          } catch (Exception e) {
            LOGGER.debug(
                "BackgroundBidWatcher parse error phiên #{}: {}", auctionId, e.getMessage());
          }
        });
    LOGGER.info("BackgroundBidWatcher: theo dõi nền phiên #{}", auctionId);
  }

  /** Dừng theo dõi một phiên — gọi khi người dùng quay lại màn hình chi tiết của phiên đó. */
  public void stopWatching(Long auctionId) {
    WebSocketClient ws = watchers.remove(auctionId);
    itemNames.remove(auctionId);
    if (ws != null) {
      ws.disconnect();
      LOGGER.info("BackgroundBidWatcher: dừng theo dõi phiên #{}", auctionId);
    }
  }

  /** Dừng tất cả kết nối nền — gọi khi người dùng đăng xuất. */
  public void stopAll() {
    watchers.forEach((id, ws) -> ws.disconnect());
    watchers.clear();
    itemNames.clear();
  }

  private void handleMessage(Long auctionId, BidUpdateMessage msg, Long currentUserId) {
    if (msg == null) {
      return;
    }
    String label = "[" + itemNames.getOrDefault(auctionId, "Phiên #" + auctionId) + "] ";

    switch (msg.getType()) {
      case BidUpdateMessage.TYPE_BID_UPDATE -> {
        if (msg.getCurrentPrice() == null) {
          return;
        }
        boolean isOwnBid =
            msg.getLeadingBidderId() != null && msg.getLeadingBidderId().equals(currentUserId);
        String price = VND.format(msg.getCurrentPrice());
        String text;
        if (isOwnBid && msg.isAutoBid()) {
          text = label + "Auto-bid đặt " + price + " cho bạn";
        } else if (!isOwnBid) {
          String bidder =
              msg.getLeadingBidderUsername() != null ? msg.getLeadingBidderUsername() : "Ẩn danh";
          text = label + bidder + " vừa bid " + price;
        } else {
          return; // own manual bid — người dùng tự biết
        }
        final String notification = text;
        // Guard: skip if stopWatching() was already called for this auction
        Platform.runLater(
            () -> {
              if (watchers.containsKey(auctionId)) {
                NotificationStore.getInstance().add(notification);
              }
            });
      }
      case BidUpdateMessage.TYPE_AUCTION_ENDED -> {
        String winner =
            msg.getLeadingBidderUsername() != null
                ? msg.getLeadingBidderUsername()
                : "Không có người thắng";
        String price = msg.getCurrentPrice() != null ? VND.format(msg.getCurrentPrice()) : "—";
        String text = label + "Phiên đã kết thúc — " + winner + " thắng với " + price;
        Platform.runLater(
            () -> {
              if (watchers.containsKey(auctionId)) {
                NotificationStore.getInstance().add(text);
                stopWatching(auctionId);
              }
            });
      }
      default -> {
        /* TIME_EXTENDED không cần thông báo nền */
      }
    }
  }
}
