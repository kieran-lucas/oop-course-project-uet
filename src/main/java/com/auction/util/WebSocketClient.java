package com.auction.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client WebSocket cho ứng dụng JavaFX — nhận realtime updates từ server Javalin.
 *
 * <p><b>Mục đích:</b> Kết nối WebSocket đến {@code
 * ws://localhost:8080/ws/auction/{id}?token=<jwt>}, nhận JSON message từ server (BID_UPDATE,
 * TIME_EXTENDED, AUCTION_ENDED), và gọi callback để AuctionDetailController xử lý cập nhật UI.
 *
 * <p><b>Các phương thức chính:</b>
 *
 * <ul>
 *   <li>{@link #connect(Long, String, Consumer)} — Mở kết nối WS, đăng ký callback nhận message.
 *   <li>{@link #disconnect()} — Đóng kết nối WS (gọi khi rời màn hình auction-detail).
 *   <li>{@link #isConnected()} — Kiểm tra trạng thái kết nối.
 * </ul>
 *
 * <p><b>Vị trí trong kiến trúc:</b> WebSocketClient được dùng bởi {@code AuctionDetailController}
 * để nhận realtime bid updates từ server. Kết nối được mở khi vào màn hình chi tiết và đóng khi rời
 * màn hình (qua {@code Navigable.onNavigatedFrom()}).
 *
 * <p><b>Luồng dữ liệu:</b>
 *
 * <pre>
 *   Server BidService → AuctionEventManager → WebSocketObserver
 *     → AuctionWebSocketHandler.broadcast()
 *     → WebSocketClient.onMessage() → callback (AuctionDetailController)
 *     → Platform.runLater() → cập nhật UI JavaFX
 * </pre>
 */
public class WebSocketClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketClient.class);
  private static final String WS_BASE_URL = "ws://localhost:8080/ws/auction/";

  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());

  private WebSocket webSocket;

  /**
   * Mở kết nối WebSocket đến server và đăng ký callback.
   *
   * @param auctionId ID phiên đấu giá để subscribe
   * @param jwtToken JWT token cho xác thực (gửi qua query param)
   * @param onMessage callback nhận JSON string khi có message từ server
   */
  public void connect(Long auctionId, String jwtToken, Consumer<String> onMessage) {
    String uri = WS_BASE_URL + auctionId + "?token=" + jwtToken;

    HttpClient.newHttpClient()
        .newWebSocketBuilder()
        .buildAsync(
            URI.create(uri),
            new WebSocket.Listener() {

              @Override
              public void onOpen(WebSocket ws) {
                webSocket = ws;
                LOGGER.info("WebSocket kết nối thành công: phiên #{}", auctionId);
                ws.request(1);
              }

              @Override
              public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                String json = data.toString();
                LOGGER.debug("WS nhận message: {}", json);
                onMessage.accept(json);
                ws.request(1);
                return null;
              }

              @Override
              public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                LOGGER.info("WebSocket đóng kết nối: status={}, reason={}", statusCode, reason);
                webSocket = null;
                return null;
              }

              @Override
              public void onError(WebSocket ws, Throwable error) {
                LOGGER.error("Lỗi WebSocket: {}", error.getMessage());
                webSocket = null;
              }
            })
        .exceptionally(
            e -> {
              LOGGER.error("Không thể kết nối WebSocket: {}", e.getMessage());
              return null;
            });
  }

  /**
   * Đóng kết nối WebSocket nếu đang mở. Nên gọi trong {@code Navigable.onNavigatedFrom()} để giải
   * phóng tài nguyên.
   */
  public void disconnect() {
    if (webSocket != null && !webSocket.isInputClosed()) {
      webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client rời màn hình");
      webSocket = null;
      LOGGER.info("WebSocket đã đóng kết nối.");
    }
  }

  /**
   * @return true nếu đang kết nối với server
   */
  public boolean isConnected() {
    return webSocket != null && !webSocket.isInputClosed();
  }
}
