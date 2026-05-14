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
 * Quản lý hai kết nối WebSocket độc lập cho JavaFX client: kênh auction và kênh user.
 *
 * <h2>Hai kênh WebSocket</h2>
 *
 * <ul>
 *   <li><b>Auction channel</b> ({@code /ws/auction/{id}}): nhận cập nhật realtime của một phiên đấu
 *       giá cụ thể — giá hiện tại, trạng thái, thời gian gia hạn... Dùng bởi {@code
 *       AuctionDetailController} và {@link BackgroundBidWatcher}.
 *   <li><b>User channel</b> ({@code /ws/user/{id}}): nhận thông báo cá nhân của user — kết quả
 *       duyệt/từ chối nạp tiền, v.v. Dùng bởi {@link UserBalanceWatcher}, duy trì trong suốt phiên
 *       đăng nhập.
 * </ul>
 *
 * <h2>Auto reconnect với exponential backoff</h2>
 *
 * Khi kết nối bị đóng không chủ ý (network drop, server restart...), mỗi kênh tự lên lịch kết nối
 * lại với độ trễ tăng dần theo công thức {@code min(30, 2^retryCount)} giây:
 *
 * <pre>
 *   Lần 1: 2s — Lần 2: 4s — Lần 3: 8s — Lần 4: 16s — Lần 5: 30s → dừng
 * </pre>
 *
 * Sau {@link #MAX_RETRIES} lần thất bại, reconnect dừng hẳn. Mỗi kênh có bộ đếm retry riêng — lỗi
 * của kênh auction không ảnh hưởng đến kênh user và ngược lại.
 *
 * <h2>Phân biệt đóng chủ ý vs mất kết nối</h2>
 *
 * Cờ {@code intentionalAuctionClose} và {@code intentionalUserClose} được set {@code true} trước
 * khi gọi {@link WebSocket#sendClose} để phân biệt "người dùng chủ ý đóng" với "kết nối bị ngắt
 * ngoài ý muốn". Chỉ trường hợp thứ hai mới trigger reconnect.
 *
 * <h2>Thread model</h2>
 *
 * Callback {@link WebSocket.Listener} (onOpen, onText, onClose, onError) chạy trên thread của
 * {@link HttpClient} — không phải FX thread. Caller chịu trách nhiệm bọc trong {@code
 * Platform.runLater()} nếu cần update UI.
 */
public class WebSocketClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketClient.class);

  /** Endpoint auction WebSocket — nối thêm {@code auctionId?token=<jwt>}. */
  private static final String WS_BASE_URL = "ws://localhost:8080/ws/auction/";

  /** Endpoint user WebSocket — nối thêm {@code userId?token=<jwt>}. */
  private static final String WS_USER_BASE_URL = "ws://localhost:8080/ws/user/";

  /** Số lần thử kết nối lại tối đa cho mỗi kênh trước khi dừng hẳn. */
  private static final int MAX_RETRIES = 5;

  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());

  // =========================================================
  // Auction WebSocket state
  // =========================================================

  /** Kết nối WebSocket auction hiện tại; {@code null} nếu chưa kết nối hoặc đã đóng. */
  private WebSocket auctionSocket;

  /** ID phiên đấu giá đang kết nối — dùng để build lại URI khi reconnect. */
  private Long currentAuctionId;

  /** Callback nhận JSON message từ kênh auction — được gọi trên thread của HttpClient. */
  private Consumer<String> auctionMessageHandler;

  /** Số lần đã thử reconnect auction kể từ lần mất kết nối gần nhất. Reset về 0 khi onOpen. */
  private int auctionRetryCount;

  /**
   * {@code true} nếu việc đóng kết nối auction là do chủ ý (gọi {@link #disconnectAuction()}). Ngăn
   * reconnect được trigger sau khi chủ động đóng.
   */
  private boolean intentionalAuctionClose;

  /** Task reconnect auction đang được lên lịch — giữ reference để có thể hủy nếu cần. */
  private ScheduledFuture<?> auctionReconnectTask;

  // =========================================================
  // User WebSocket state
  // =========================================================

  /** Kết nối WebSocket user hiện tại; {@code null} nếu chưa kết nối hoặc đã đóng. */
  private WebSocket userSocket;

  /** ID user đang kết nối — dùng để build lại URI khi reconnect. */
  private Long currentUserId;

  /** Callback nhận JSON message từ kênh user — được gọi trên thread của HttpClient. */
  private Consumer<String> userMessageHandler;

  /** Số lần đã thử reconnect user kể từ lần mất kết nối gần nhất. Reset về 0 khi onOpen. */
  private int userRetryCount;

  /**
   * {@code true} nếu việc đóng kết nối user là do chủ ý (gọi {@link #disconnectUser()}). Ngăn
   * reconnect được trigger sau khi chủ động đóng.
   */
  private boolean intentionalUserClose;

  /** Task reconnect user đang được lên lịch — giữ reference để có thể hủy nếu cần. */
  private ScheduledFuture<?> userReconnectTask;

  // =========================================================
  // Shared state
  // =========================================================

  /**
   * JWT token dùng chung cho cả hai kênh — lưu lại để tái dùng khi reconnect mà không cần caller
   * truyền lại token.
   */
  private String currentToken;

  /**
   * Scheduler dùng chung để lên lịch reconnect cho cả hai kênh.
   *
   * <p>Single-thread, daemon — không block JVM shutdown. Thread được đặt tên {@code ws-reconnect}
   * để dễ nhận diện khi debug thread dump.
   */
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
   * Kết nối vào kênh auction realtime ({@code /ws/auction/{auctionId}?token=<jwt>}).
   *
   * <p>Nếu đang có task reconnect auction đang chờ, hủy ngay trước khi kết nối mới — tránh tình
   * trạng một kết nối cũ đang được lên lịch lại trong khi kết nối mới đã được thiết lập.
   *
   * <p>Reset {@code auctionRetryCount} và {@code intentionalAuctionClose} về trạng thái ban đầu
   * trước mỗi lần kết nối mới.
   *
   * @param auctionId ID phiên đấu giá cần theo dõi
   * @param jwtToken JWT token xác thực
   * @param onMessage callback nhận JSON message từ server; chạy trên thread của HttpClient
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

  /**
   * Thực hiện kết nối auction WebSocket — dùng chung cho lần đầu và mỗi lần reconnect.
   *
   * <p>Kết nối được thực hiện bất đồng bộ qua {@link HttpClient#newWebSocketBuilder()}. Kết quả
   * được xử lý qua {@link WebSocket.Listener}:
   *
   * <ul>
   *   <li>{@code onOpen}: lưu socket, reset retry count.
   *   <li>{@code onText}: forward message đến {@code auctionMessageHandler} nếu kết nối còn chủ ý.
   *   <li>{@code onClose} / {@code onError}: null socket, lên lịch reconnect nếu không chủ ý đóng.
   * </ul>
   *
   * <p>Nếu {@code buildAsync} bản thân ném exception (URI sai, network unreachable...), block
   * {@code exceptionally} bắt và lên lịch reconnect tương tự.
   */
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

                if (isAuthClose(statusCode, reason)) {
                  intentionalAuctionClose = true;
                  LOGGER.warn("Auction WebSocket auth closed by server: {}", reason);
                } else if (!intentionalAuctionClose) {
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

  /**
   * Lên lịch kết nối lại auction sau một khoảng thời gian theo exponential backoff.
   *
   * <p>Độ trễ: {@code min(30, 2^(retryCount+1))} giây — tăng dần từ 2s đến tối đa 30s. Dừng hẳn sau
   * {@link #MAX_RETRIES} lần thất bại liên tiếp.
   */
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

  /**
   * Hủy task reconnect auction đang chờ nếu có.
   *
   * <p>Dùng {@code cancel(false)} — không interrupt thread đang chạy, chỉ ngăn task chưa bắt đầu
   * khỏi việc chạy.
   */
  private void cancelAuctionReconnect() {

    if (auctionReconnectTask != null && !auctionReconnectTask.isDone()) {

      auctionReconnectTask.cancel(false);

      auctionReconnectTask = null;
    }
  }

  /**
   * Đóng kết nối auction WebSocket một cách chủ ý.
   *
   * <p>Set {@code intentionalAuctionClose = true} trước khi đóng để ngăn reconnect tự động. Gửi
   * close frame {@link WebSocket#NORMAL_CLOSURE} để server biết client đóng có chủ ý. Nếu socket đã
   * {@code null} hoặc input đã đóng, method này là no-op.
   */
  public void disconnectAuction() {

    intentionalAuctionClose = true;

    cancelAuctionReconnect();

    if (auctionSocket != null && !auctionSocket.isInputClosed()) {

      auctionSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Auction screen closed");

      auctionSocket = null;

      LOGGER.info("Auction WebSocket đã đóng.");
    }
  }

  /**
   * Kiểm tra kết nối auction WebSocket có đang hoạt động không.
   *
   * @return {@code true} nếu socket khác {@code null} và input chưa bị đóng
   */
  public boolean isAuctionConnected() {

    return auctionSocket != null && !auctionSocket.isInputClosed();
  }

  // =========================================================
  // USER WEBSOCKET
  // =========================================================

  /**
   * Kết nối vào kênh thông báo cá nhân của user ({@code /ws/user/{userId}?token=<jwt>}).
   *
   * <p>Nếu đang có task reconnect user đang chờ, hủy trước khi kết nối mới. Reset {@code
   * userRetryCount} và {@code intentionalUserClose}.
   *
   * @param userId ID của user cần kết nối
   * @param jwtToken JWT token xác thực
   * @param onMessage callback nhận JSON message từ server; chạy trên thread của HttpClient
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

  /**
   * Thực hiện kết nối user WebSocket — dùng chung cho lần đầu và mỗi lần reconnect.
   *
   * <p>Cơ chế giống {@link #doConnectAuction()} nhưng dùng URL và state riêng của kênh user. Xem
   * Javadoc của {@link #doConnectAuction()} để biết chi tiết về luồng xử lý Listener.
   */
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

                if (isAuthClose(statusCode, reason)) {
                  intentionalUserClose = true;
                  LOGGER.warn("User WebSocket auth closed by server: {}", reason);
                } else if (!intentionalUserClose) {
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

  /**
   * Lên lịch kết nối lại user WebSocket sau một khoảng thời gian theo exponential backoff.
   *
   * <p>Logic giống {@link #scheduleAuctionReconnect()} nhưng dùng state riêng của kênh user.
   */
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

  /**
   * Hủy task reconnect user đang chờ nếu có.
   *
   * <p>Logic giống {@link #cancelAuctionReconnect()} nhưng dùng {@link #userReconnectTask}.
   */
  private void cancelUserReconnect() {

    if (userReconnectTask != null && !userReconnectTask.isDone()) {

      userReconnectTask.cancel(false);

      userReconnectTask = null;
    }
  }

  /**
   * Đóng kết nối user WebSocket một cách chủ ý.
   *
   * <p>Logic giống {@link #disconnectAuction()} nhưng tác động lên kênh user. Set {@code
   * intentionalUserClose = true} trước khi đóng để ngăn reconnect tự động.
   */
  public void disconnectUser() {

    intentionalUserClose = true;

    cancelUserReconnect();

    if (userSocket != null && !userSocket.isInputClosed()) {

      userSocket.sendClose(WebSocket.NORMAL_CLOSURE, "User websocket closed");

      userSocket = null;

      LOGGER.info("User WebSocket đã đóng.");
    }
  }

  /**
   * Kiểm tra kết nối user WebSocket có đang hoạt động không.
   *
   * @return {@code true} nếu socket khác {@code null} và input chưa bị đóng
   */
  public boolean isUserConnected() {

    return userSocket != null && !userSocket.isInputClosed();
  }

  private boolean isAuthClose(int statusCode, String reason) {
    return statusCode == 4001
        && reason != null
        && reason.toLowerCase(java.util.Locale.ROOT).contains("token");
  }

  // =========================================================
  // GLOBAL
  // =========================================================

  /**
   * Đóng cả hai kết nối WebSocket (auction và user) cùng lúc.
   *
   * <p>Delegate sang {@link #disconnectAuction()} và {@link #disconnectUser()} — mỗi kênh tự xử lý
   * logic đóng và hủy reconnect riêng. Thường được gọi bởi {@link #disconnect()} hoặc trực tiếp khi
   * cần đóng toàn bộ kết nối (ví dụ: application shutdown).
   */
  public void disconnectAll() {

    disconnectAuction();

    disconnectUser();
  }

  /**
   * Alias của {@link #disconnectAll()} — cung cấp interface gọn hơn cho caller chỉ cần đóng toàn bộ
   * kết nối mà không cần biết về sự tồn tại của hai kênh riêng biệt.
   */
  public void disconnect() {
    disconnectAll();
  }
}
