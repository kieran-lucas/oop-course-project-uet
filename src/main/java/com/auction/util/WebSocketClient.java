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
 * WebSocket manager cho JavaFX client.
 *
 * <p>Quản lý độc lập:
 *
 * <ul>
 *   <li>Auction WebSocket (/ws/auction/{id})
 *   <li>User WebSocket (/ws/user/{id})
 * </ul>
 *
 * <p>Hỗ trợ:
 *
 * <ul>
 *   <li>Auto reconnect với exponential backoff
 *   <li>Reconnect độc lập cho từng channel
 *   <li>Tách biệt hoàn toàn state giữa auction và user socket
 * </ul>
 */
public class WebSocketClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketClient.class);

  private static final String WS_BASE_URL = "ws://localhost:8080/ws/auction/";
  private static final String WS_USER_BASE_URL = "ws://localhost:8080/ws/user/";

  private static final int MAX_RETRIES = 5;

  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());

  // =========================================================
  // Auction WebSocket state
  // =========================================================

  private WebSocket auctionSocket;

  private Long currentAuctionId;

  private Consumer<String> auctionMessageHandler;

  private int auctionRetryCount;

  private boolean intentionalAuctionClose;

  private ScheduledFuture<?> auctionReconnectTask;

  // =========================================================
  // User WebSocket state
  // =========================================================

  private WebSocket userSocket;

  private Long currentUserId;

  private Consumer<String> userMessageHandler;

  private int userRetryCount;

  private boolean intentionalUserClose;

  private ScheduledFuture<?> userReconnectTask;

  // =========================================================
  // Shared state
  // =========================================================

  private String currentToken;

  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "ws-reconnect");
            t.setDaemon(true);
            return t;
          });

  // =========================================================
  // AUCTION WEBSOCKET
  // =========================================================

  /**
   * Kết nối vào channel auction realtime.
   *
   * @param auctionId ID phiên đấu giá
   * @param jwtToken JWT token xác thực
   * @param onMessage callback nhận JSON message
   */
  public void connect(Long auctionId, String jwtToken, Consumer<String> onMessage) {

    cancelAuctionReconnect();

    this.currentAuctionId = auctionId;
    this.currentToken = jwtToken;
    this.auctionMessageHandler = onMessage;

    this.auctionRetryCount = 0;
    this.intentionalAuctionClose = false;

    doConnectAuction();
  }

  private void doConnectAuction() {

    String uri = WS_BASE_URL + currentAuctionId + "?token=" + currentToken;

    HttpClient.newHttpClient()
        .newWebSocketBuilder()
        .buildAsync(
            URI.create(uri),
            new WebSocket.Listener() {

              @Override
              public void onOpen(WebSocket ws) {

                auctionSocket = ws;

                auctionRetryCount = 0;

                LOGGER.info(
                    "Auction WebSocket kết nối thành công: auctionId=#{}", currentAuctionId);

                ws.request(1);
              }

              @Override
              public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {

                if (!intentionalAuctionClose) {

                  String json = data.toString();

                  LOGGER.debug("Auction WS nhận message: {}", json);

                  auctionMessageHandler.accept(json);
                }

                ws.request(1);

                return null;
              }

              @Override
              public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {

                LOGGER.info("Auction WebSocket đóng: status={}, reason={}", statusCode, reason);

                auctionSocket = null;

                if (!intentionalAuctionClose) {
                  scheduleAuctionReconnect();
                }

                return null;
              }

              @Override
              public void onError(WebSocket ws, Throwable error) {

                LOGGER.error("Lỗi Auction WebSocket: {}", error.getMessage());

                auctionSocket = null;

                if (!intentionalAuctionClose) {
                  scheduleAuctionReconnect();
                }
              }
            })
        .exceptionally(
            e -> {
              LOGGER.error("Không thể kết nối Auction WebSocket: {}", e.getMessage());

              if (!intentionalAuctionClose) {
                scheduleAuctionReconnect();
              }

              return null;
            });
  }

  private void scheduleAuctionReconnect() {

    if (auctionRetryCount >= MAX_RETRIES) {

      LOGGER.warn("Auction WebSocket: đã thử {} lần, dừng reconnect.", MAX_RETRIES);

      return;
    }

    long delaySec = Math.min(30L, 1L << (auctionRetryCount + 1));

    auctionRetryCount++;

    LOGGER.info("Auction WebSocket: reconnect lần {} sau {}s...", auctionRetryCount, delaySec);

    auctionReconnectTask = scheduler.schedule(this::doConnectAuction, delaySec, TimeUnit.SECONDS);
  }

  private void cancelAuctionReconnect() {

    if (auctionReconnectTask != null && !auctionReconnectTask.isDone()) {

      auctionReconnectTask.cancel(false);

      auctionReconnectTask = null;
    }
  }

  /** Đóng Auction WebSocket. */
  public void disconnectAuction() {

    intentionalAuctionClose = true;

    cancelAuctionReconnect();

    if (auctionSocket != null && !auctionSocket.isInputClosed()) {

      auctionSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Auction screen closed");

      auctionSocket = null;

      LOGGER.info("Auction WebSocket đã đóng.");
    }
  }

  public boolean isAuctionConnected() {

    return auctionSocket != null && !auctionSocket.isInputClosed();
  }

  // =========================================================
  // USER WEBSOCKET
  // =========================================================

  /**
   * Kết nối vào user notification channel.
   *
   * @param userId ID user hiện tại
   * @param jwtToken JWT token xác thực
   * @param onMessage callback nhận JSON message
   */
  public void connectUser(Long userId, String jwtToken, Consumer<String> onMessage) {

    cancelUserReconnect();

    this.currentUserId = userId;
    this.currentToken = jwtToken;
    this.userMessageHandler = onMessage;

    this.userRetryCount = 0;
    this.intentionalUserClose = false;

    doConnectUser();
  }

  private void doConnectUser() {

    String uri = WS_USER_BASE_URL + currentUserId + "?token=" + currentToken;

    HttpClient.newHttpClient()
        .newWebSocketBuilder()
        .buildAsync(
            URI.create(uri),
            new WebSocket.Listener() {

              @Override
              public void onOpen(WebSocket ws) {

                userSocket = ws;

                userRetryCount = 0;

                LOGGER.info("User WebSocket kết nối thành công: userId=#{}", currentUserId);

                ws.request(1);
              }

              @Override
              public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {

                if (!intentionalUserClose) {

                  String json = data.toString();

                  LOGGER.debug("User WS nhận message: {}", json);

                  userMessageHandler.accept(json);
                }

                ws.request(1);

                return null;
              }

              @Override
              public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {

                LOGGER.info("User WebSocket đóng: status={}, reason={}", statusCode, reason);

                userSocket = null;

                if (!intentionalUserClose) {
                  scheduleUserReconnect();
                }

                return null;
              }

              @Override
              public void onError(WebSocket ws, Throwable error) {

                LOGGER.error("Lỗi User WebSocket: {}", error.getMessage());

                userSocket = null;

                if (!intentionalUserClose) {
                  scheduleUserReconnect();
                }
              }
            })
        .exceptionally(
            e -> {
              LOGGER.error("Không thể kết nối User WebSocket: {}", e.getMessage());

              if (!intentionalUserClose) {
                scheduleUserReconnect();
              }

              return null;
            });
  }

  private void scheduleUserReconnect() {

    if (userRetryCount >= MAX_RETRIES) {

      LOGGER.warn("User WebSocket: đã thử {} lần, dừng reconnect.", MAX_RETRIES);

      return;
    }

    long delaySec = Math.min(30L, 1L << (userRetryCount + 1));

    userRetryCount++;

    LOGGER.info("User WebSocket: reconnect lần {} sau {}s...", userRetryCount, delaySec);

    userReconnectTask = scheduler.schedule(this::doConnectUser, delaySec, TimeUnit.SECONDS);
  }

  private void cancelUserReconnect() {

    if (userReconnectTask != null && !userReconnectTask.isDone()) {

      userReconnectTask.cancel(false);

      userReconnectTask = null;
    }
  }

  /** Đóng User WebSocket. */
  public void disconnectUser() {

    intentionalUserClose = true;

    cancelUserReconnect();

    if (userSocket != null && !userSocket.isInputClosed()) {

      userSocket.sendClose(WebSocket.NORMAL_CLOSURE, "User websocket closed");

      userSocket = null;

      LOGGER.info("User WebSocket đã đóng.");
    }
  }

  public boolean isUserConnected() {

    return userSocket != null && !userSocket.isInputClosed();
  }

  // =========================================================
  // GLOBAL
  // =========================================================

  /** Đóng toàn bộ websocket connections. */
  public void disconnectAll() {

    disconnectAuction();

    disconnectUser();
  }
}
