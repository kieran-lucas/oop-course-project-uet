package com.auction.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client WebSocket cho ứng dụng JavaFX — nhận realtime updates từ server Javalin.
 *
 * <p>Hỗ trợ tự động reconnect với exponential backoff khi mất kết nối (tối đa {@value #MAX_RETRIES}
 * lần thử lại).
 */
public class WebSocketClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketClient.class);
  private static final String WS_BASE_URL = "ws://localhost:8080/ws/auction/";
  private static final String WS_USER_BASE_URL = "ws://localhost:8080/ws/user/";
  private static final int MAX_RETRIES = 5;

  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());

  private WebSocket webSocket;
  private Long currentAuctionId;
  private String currentToken;
  private Consumer<String> currentOnMessage;
  private int retryCount;
  private boolean intentionalClose;
  private ScheduledFuture<?> pendingReconnect;

  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "ws-reconnect");
            t.setDaemon(true);
            return t;
          });

  /**
   * Mở kết nối WebSocket đến server và đăng ký callback.
   *
   * @param auctionId ID phiên đấu giá để subscribe
   * @param jwtToken JWT token cho xác thực (gửi qua query param)
   * @param onMessage callback nhận JSON string khi có message từ server
   */
  public void connect(Long auctionId, String jwtToken, Consumer<String> onMessage) {
    cancelPendingReconnect();
    this.currentAuctionId = auctionId;
    this.currentToken = jwtToken;
    this.currentOnMessage = onMessage;
    this.retryCount = 0;
    this.intentionalClose = false;
    doConnect();
  }

  private void doConnect() {
    String uri = WS_BASE_URL + currentAuctionId + "?token=" + currentToken;

    HttpClient.newHttpClient()
        .newWebSocketBuilder()
        .buildAsync(
            URI.create(uri),
            new WebSocket.Listener() {

              @Override
              public void onOpen(WebSocket ws) {
                webSocket = ws;
                retryCount = 0;
                LOGGER.info("WebSocket kết nối thành công: phiên #{}", currentAuctionId);
                ws.request(1);
              }

              @Override
              public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                // Discard messages that arrive after an intentional disconnect to prevent
                // duplicates when a BackgroundBidWatcher connection is starting up in parallel.
                if (!intentionalClose) {
                  String json = data.toString();
                  LOGGER.debug("WS nhận message: {}", json);
                  currentOnMessage.accept(json);
                }
                ws.request(1);
                return null;
              }

              @Override
              public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                LOGGER.info("WebSocket đóng kết nối: status={}, reason={}", statusCode, reason);
                webSocket = null;
                if (!intentionalClose) {
                  scheduleReconnect();
                }
                return null;
              }

              @Override
              public void onError(WebSocket ws, Throwable error) {
                LOGGER.error("Lỗi WebSocket: {}", error.getMessage());
                webSocket = null;
                if (!intentionalClose) {
                  scheduleReconnect();
                }
              }
            })
        .exceptionally(
            e -> {
              LOGGER.error("Không thể kết nối WebSocket: {}", e.getMessage());
              if (!intentionalClose) {
                scheduleReconnect();
              }
              return null;
            });
  }

  private void scheduleReconnect() {
    if (retryCount >= MAX_RETRIES) {
      LOGGER.warn("WebSocket: đã thử {} lần, dừng kết nối lại.", MAX_RETRIES);
      return;
    }
    long delaySec = Math.min(30L, 1L << (retryCount + 1));
    retryCount++;
    LOGGER.info("WebSocket: thử kết nối lại lần {} sau {}s...", retryCount, delaySec);
    pendingReconnect = scheduler.schedule(this::doConnect, delaySec, TimeUnit.SECONDS);
  }

  private void cancelPendingReconnect() {
    if (pendingReconnect != null && !pendingReconnect.isDone()) {
      pendingReconnect.cancel(false);
      pendingReconnect = null;
    }
  }

  /**
   * Mở kết nối WebSocket đến kênh riêng của user ({@code /ws/user/{id}}) để nhận thông báo biến
   * động số dư khi Admin duyệt hoặc từ chối yêu cầu nạp tiền.
   *
   * @param userId ID của user hiện tại (từ SceneManager)
   * @param jwtToken JWT token để xác thực
   * @param onMessage callback nhận JSON string khi có message từ server
   */
  public void connectUser(Long userId, String jwtToken, Consumer<String> onMessage) {
    cancelPendingReconnect();
    this.currentAuctionId = userId; // tái dùng field để lưu userId
    this.currentToken = jwtToken;
    this.currentOnMessage = onMessage;
    this.retryCount = 0;
    this.intentionalClose = false;
    doConnectUser();
  }

  private void doConnectUser() {
    String uri = WS_USER_BASE_URL + currentAuctionId + "?token=" + currentToken;

    HttpClient.newHttpClient()
        .newWebSocketBuilder()
        .buildAsync(
            URI.create(uri),
            new WebSocket.Listener() {

              @Override
              public void onOpen(WebSocket ws) {
                webSocket = ws;
                retryCount = 0;
                LOGGER.info("User WebSocket kết nối thành công: userId=#{}", currentAuctionId);
                ws.request(1);
              }

              @Override
              public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                if (!intentionalClose) {
                  currentOnMessage.accept(data.toString());
                }
                ws.request(1);
                return null;
              }

              @Override
              public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                LOGGER.info("User WebSocket đóng: status={}", statusCode);
                webSocket = null;
                if (!intentionalClose) {
                  scheduleReconnectUser();
                }
                return null;
              }

              @Override
              public void onError(WebSocket ws, Throwable error) {
                LOGGER.error("Lỗi User WebSocket: {}", error.getMessage());
                webSocket = null;
                if (!intentionalClose) {
                  scheduleReconnectUser();
                }
              }
            })
        .exceptionally(
            e -> {
              LOGGER.error("Không thể kết nối User WebSocket: {}", e.getMessage());
              if (!intentionalClose) {
                scheduleReconnectUser();
              }
              return null;
            });
  }

  private void scheduleReconnectUser() {
    if (retryCount >= MAX_RETRIES) {
      LOGGER.warn("User WebSocket: đã thử {} lần, dừng.", MAX_RETRIES);
      return;
    }
    long delaySec = Math.min(30L, 1L << (retryCount + 1));
    retryCount++;
    pendingReconnect = scheduler.schedule(this::doConnectUser, delaySec, TimeUnit.SECONDS);
  }

  /** Đóng kết nối WebSocket nếu đang mở. Nên gọi trong {@code Navigable.onNavigatedFrom()}. */
  public void disconnect() {
    intentionalClose = true;
    cancelPendingReconnect();
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
