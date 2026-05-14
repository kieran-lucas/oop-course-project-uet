package com.auction.controller;

import com.auction.config.JwtUtil;
import com.auction.dao.UserDao;
import com.auction.dto.BidUpdateMessage;
import com.auction.exception.UnauthorizedException;
import com.auction.model.User;
import com.auction.pattern.observer.AuctionEventManager;
import com.auction.pattern.observer.WebSocketObserver;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.websocket.WsCloseContext;
import io.javalin.websocket.WsConnectContext;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsErrorContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WebSocket handler — quản lý kết nối realtime giữa server và client.
 *
 * <p><b>Vai trò trong hệ thống:</b> AuctionWebSocketHandler là cầu nối giữa Observer pattern
 * (AuctionEventManager) và transport layer (WebSocket). Khi client kết nối:
 *
 * <ol>
 *   <li>Xác thực JWT token từ query parameter.
 *   <li>Lưu WebSocket session vào map theo auctionId.
 *   <li>Tạo {@link WebSocketObserver} và subscribe vào {@link AuctionEventManager}.
 * </ol>
 *
 * <p>Khi có sự kiện (bid mới, gia hạn, kết thúc):
 *
 * <pre>
 *   BidService → EventManager.notify() → WebSocketObserver.onBidUpdate()
 *     → AuctionWebSocketHandler.broadcast() → gửi JSON đến tất cả client
 * </pre>
 *
 * <p><b>Liên kết với các file khác:</b>
 *
 * <ul>
 *   <li>{@link AuctionEventManager} — subscribe/unsubscribe observer khi client connect/disconnect
 *   <li>{@link WebSocketObserver} — observer nhận events và gọi broadcast()
 *   <li>{@link com.auction.App} — đăng ký WebSocket route /ws/auction/{id}
 * </ul>
 */
public class AuctionWebSocketHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuctionWebSocketHandler.class);
  private final Jdbi jdbi;
  private final UserDao userDao;

  /**
   * Map auctionId → set WebSocket sessions đang kết nối. ConcurrentHashMap + CopyOnWriteArraySet để
   * thread-safe.
   */
  private final Map<Long, Set<WsContext>> connections = new ConcurrentHashMap<>();

  /**
   * Map userId → set WebSocket sessions của kênh riêng user ({@code /ws/user/{id}}). Dùng để push
   * thông báo biến động số dư khi Admin duyệt/từ chối nạp tiền.
   */
  private final Map<Long, Set<WsContext>> userConnections = new ConcurrentHashMap<>();

  /** Map session → WebSocketObserver tương ứng. Dùng để unsubscribe khi client disconnect. */
  private final Map<WsContext, WebSocketObserver> observers = new ConcurrentHashMap<>();

  private final Map<WsContext, Instant> sessionExpiresAt = new ConcurrentHashMap<>();
  private final Map<WsContext, ScheduledFuture<?>> expirationTasks = new ConcurrentHashMap<>();

  private final ScheduledExecutorService expirationScheduler =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "ws-token-expiration");
            t.setDaemon(true);
            return t;
          });

  private final AuctionEventManager eventManager;
  private final ObjectMapper objectMapper;

  public AuctionWebSocketHandler(AuctionEventManager eventManager, Jdbi jdbi) {
    this.eventManager = eventManager;
    this.jdbi = jdbi;
    this.userDao = new UserDao(jdbi);
    this.objectMapper = new ObjectMapper();
    this.objectMapper.registerModule(new JavaTimeModule());
  }

  /**
   * Xử lý client kết nối WebSocket
   *
   * <p>Luồng:
   *
   * <ol>
   *   <li>Lấy auctionId từ URL path ({@code /ws/auction/{id}}).
   *   <li>Verify JWT từ query param {@code token}.
   *   <li>Lưu session vào connections map.
   *   <li>Tạo WebSocketObserver và subscribe vào EventManager.
   * </ol>
   */
  public void onConnect(WsConnectContext ctx) {
    try {
      Long auctionId = Long.parseLong(ctx.pathParam("id"));
      String token = ctx.queryParam("token");

      if (token == null || token.isEmpty()) {
        LOGGER.warn("WebSocket từ chối: thiếu token");
        ctx.session.close(4001, "Missing token");
        return;
      }

      DecodedJWT decoded = JwtUtil.verifyToken(token);
      verifyTokenVersion(decoded);
      String username = decoded.getClaim("username").asString();
      registerExpiration(ctx, decoded);

      // Lưu connection
      connections.computeIfAbsent(auctionId, k -> new CopyOnWriteArraySet<>()).add(ctx);

      // Tạo observer và subscribe vào EventManager
      WebSocketObserver observer = new WebSocketObserver(this, auctionId);
      observers.put(ctx, observer);
      eventManager.subscribe(auctionId, observer);

      LOGGER.info("WebSocket kết nối: user={}, phiên=#{}", username, auctionId);

    } catch (JWTVerificationException | UnauthorizedException e) {
      LOGGER.warn("WebSocket từ chối: token không hợp lệ");
      ctx.session.close(4001, "Invalid token");
    } catch (Exception e) {
      LOGGER.error("Lỗi kết nối WebSocket: {}", e.getMessage());
      ctx.session.close(4000, "Connection error");
    }
  }

  /**
   * Lưu thông báo vào bảng notifications. Đảm bảo user nhận được thông báo khi reconnect hoặc qua
   * REST API.
   */
  public void saveNotificationToDatabase(Long userId, String message, String type) {
    try {
      jdbi.useHandle(
          handle ->
              handle.execute(
                  """
                INSERT INTO notifications (user_id, message, notification_type)
                VALUES (?, ?, ?)
                """,
                  userId,
                  message,
                  type));
      LOGGER.info("Đã lưu notification vào DB cho userId={}, type={}", userId, type);
    } catch (Exception e) {
      LOGGER.error("Lỗi lưu notification vào DB: {}", e.getMessage());
    }
  }

  /** Xử lý client ngắt kết nối — dọn dẹp connection và unsubscribe observer. */
  public void onClose(WsCloseContext ctx) {
    removeConnection(ctx);
    LOGGER.debug("WebSocket ngắt kết nối: status={}, reason={}", ctx.status(), ctx.reason());
  }

  /** Xử lý lỗi WebSocket — dọn dẹp connection và unsubscribe observer. */
  public void onError(WsErrorContext ctx) {
    removeConnection(ctx);
    if (ctx.error() != null) {
      LOGGER.error("Lỗi WebSocket: {}", ctx.error().getMessage());
    }
  }

  /**
   * Gửi BidUpdateMessage JSON đến tất cả client đang xem phiên đấu giá.
   *
   * <p>Serialize message 1 lần, gửi đến tất cả connections của phiên đó. Tự động dọn dẹp các
   * session đã đóng.
   *
   * @param auctionId ID phiên đấu giá
   * @param message message cần gửi (BID_UPDATE, TIME_EXTENDED, AUCTION_ENDED)
   */
  public void broadcast(Long auctionId, BidUpdateMessage message) {
    Set<WsContext> auctionConnections = connections.get(auctionId);
    if (auctionConnections == null || auctionConnections.isEmpty()) {
      return;
    }

    try {
      String json = objectMapper.writeValueAsString(message);

      for (WsContext wsCtx : auctionConnections) {
        try {
          if (isSessionExpired(wsCtx)) {
            closeExpiredSession(wsCtx);
            removeConnection(wsCtx);
          } else if (wsCtx.session.isOpen()) {
            wsCtx.send(json);
          } else {
            auctionConnections.remove(wsCtx);
          }
        } catch (Exception e) {
          LOGGER.error("Lỗi gửi WebSocket message: {}", e.getMessage());
          auctionConnections.remove(wsCtx);
        }
      }

      LOGGER.debug(
          "Broadcast {} đến {} client cho phiên #{}",
          message.getType(),
          auctionConnections.size(),
          auctionId);

    } catch (Exception e) {
      LOGGER.error("Lỗi serialize WebSocket message: {}", e.getMessage());
    }
  }

  // ── Kênh WebSocket riêng cho user (/ws/user/{id}) ──────────────────────────

  /**
   * Xử lý client kết nối kênh user — xác thực JWT và lưu session theo userId. Route: {@code
   * /ws/user/{id}}
   */
  public void onUserConnect(WsConnectContext ctx) {
    try {
      Long userId = Long.parseLong(ctx.pathParam("id"));
      String token = ctx.queryParam("token");

      if (token == null || token.isEmpty()) {
        ctx.session.close(4001, "Missing token");
        return;
      }

      DecodedJWT decoded = JwtUtil.verifyToken(token);
      verifyTokenVersion(decoded);
      String username = decoded.getClaim("username").asString();
      Long tokenUserId = decoded.getClaim("userId").asLong();
      if (!tokenUserId.equals(userId)) {
        LOGGER.warn(
            "IDOR attempt blocked: token userId={} tried to open /ws/user/{}", tokenUserId, userId);
        ctx.session.close(4003, "Forbidden: userId mismatch");
        return;
      }

      registerExpiration(ctx, decoded);
      userConnections.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(ctx);
      LOGGER.info("User WebSocket kết nối: user={}, userId=#{}", username, userId);

    } catch (JWTVerificationException | UnauthorizedException e) {
      ctx.session.close(4001, "Invalid token");
    } catch (Exception e) {
      LOGGER.error("Lỗi kết nối User WebSocket: {}", e.getMessage());
      ctx.session.close(4000, "Connection error");
    }
  }

  /** Xử lý ngắt kết nối kênh user. */
  public void onUserClose(WsCloseContext ctx) {
    removeUserConnection(ctx);
    LOGGER.debug("User WebSocket ngắt kết nối: status={}", ctx.status());
  }

  /** Xử lý lỗi kênh user. */
  public void onUserError(WsErrorContext ctx) {
    removeUserConnection(ctx);
    if (ctx.error() != null) {
      LOGGER.error("Lỗi User WebSocket: {}", ctx.error().getMessage());
    }
  }

  /**
   * Push thông báo biến động số dư đến user cụ thể qua kênh {@code /ws/user/{id}}. Luôn lưu vào
   * database trước để đảm bảo tính bền vững.
   *
   * @param userId ID user cần notify
   * @param newBalance số dư mới (null nếu bị từ chối)
   * @param balanceDelta số tiền vừa được cộng (null nếu bị từ chối)
   * @param approved true = duyệt (cộng tiền), false = từ chối
   */
  public void notifyBalanceUpdate(
      Long userId, BigDecimal newBalance, BigDecimal balanceDelta, boolean approved) {
    // 1. Luôn lưu vào DB trước
    String message =
        approved
            ? String.format(
                "Yêu cầu nạp tiền đã được duyệt. Số dư biến động: + %,d VND",
                balanceDelta != null ? balanceDelta.longValue() : 0)
            : "Yêu cầu nạp tiền đã bị từ chối.";
    saveNotificationToDatabase(userId, message, "BALANCE_UPDATED");

    // 2. Push qua WebSocket nếu user đang online
    Set<WsContext> sessions = userConnections.get(userId);
    if (sessions == null || sessions.isEmpty()) {
      return;
    }
    try {
      BidUpdateMessage msg =
          BidUpdateMessage.balanceUpdated(userId, newBalance, balanceDelta, approved);
      String json = objectMapper.writeValueAsString(msg);
      for (WsContext wsCtx : sessions) {
        try {
          if (isSessionExpired(wsCtx)) {
            closeExpiredSession(wsCtx);
            removeUserConnection(wsCtx);
          } else if (wsCtx.session.isOpen()) {
            wsCtx.send(json);
          } else {
            sessions.remove(wsCtx);
          }
        } catch (Exception e) {
          LOGGER.error("Lỗi gửi User WebSocket message: {}", e.getMessage());
          sessions.remove(wsCtx);
        }
      }
      LOGGER.info(
          "Đã notify BALANCE_UPDATED → userId={}, approved={}, newBalance={}",
          userId,
          approved,
          newBalance);
    } catch (Exception e) {
      LOGGER.error("Lỗi serialize BALANCE_UPDATED message: {}", e.getMessage());
    }
  }

  /** Xóa connection và unsubscribe observer khỏi EventManager. */
  private void removeConnection(WsContext ctx) {
    connections.values().forEach(set -> set.remove(ctx));
    clearExpirationTracking(ctx);

    WebSocketObserver observer = observers.remove(ctx);
    if (observer != null) {
      eventManager.unsubscribe(observer.getAuctionId(), observer);
    }
  }

  private void removeUserConnection(WsContext ctx) {
    userConnections.values().forEach(set -> set.remove(ctx));
    clearExpirationTracking(ctx);
  }

  private void registerExpiration(WsContext ctx, DecodedJWT decoded) {
    Instant expiresAt = decoded.getExpiresAtAsInstant();
    if (expiresAt == null) {
      ctx.session.close(4001, "Missing token expiration");
      return;
    }

    sessionExpiresAt.put(ctx, expiresAt);
    ScheduledFuture<?> task =
        expirationScheduler.schedule(
            () -> {
              if (isSessionExpired(ctx)) {
                closeExpiredSession(ctx);
                removeConnection(ctx);
                removeUserConnection(ctx);
              }
            },
            millisUntilExpiration(decoded, Instant.now()),
            TimeUnit.MILLISECONDS);
    expirationTasks.put(ctx, task);
  }

  private boolean isSessionExpired(WsContext ctx) {
    Instant expiresAt = sessionExpiresAt.get(ctx);
    return expiresAt != null && !Instant.now().isBefore(expiresAt);
  }

  private void closeExpiredSession(WsContext ctx) {
    try {
      if (ctx.session.isOpen()) {
        ctx.session.close(4001, "Token expired");
      }
    } catch (Exception e) {
      LOGGER.debug("Could not close expired WebSocket session: {}", e.getMessage());
    }
  }

  private void clearExpirationTracking(WsContext ctx) {
    sessionExpiresAt.remove(ctx);
    ScheduledFuture<?> task = expirationTasks.remove(ctx);
    if (task != null) {
      task.cancel(false);
    }
  }

  private void verifyTokenVersion(DecodedJWT decoded) {
    Long userId = decoded.getClaim("userId").asLong();
    if (userId == null) {
      throw new UnauthorizedException("Token is missing userId.");
    }

    User currentUser =
        userDao
            .findById(userId)
            .orElseThrow(() -> new UnauthorizedException("Token user no longer exists."));
    if (currentUser.getTokenVersion() != extractTokenVersion(decoded)) {
      throw new UnauthorizedException("Token has been invalidated.");
    }
  }

  private static int extractTokenVersion(DecodedJWT decoded) {
    Claim claim = decoded.getClaim("tokenVersion");
    if (claim.isMissing() || claim.isNull()) {
      return 0;
    }
    Integer version = claim.asInt();
    return version != null ? version : 0;
  }

  static long millisUntilExpiration(DecodedJWT decoded, Instant now) {
    Instant expiresAt = decoded.getExpiresAtAsInstant();
    if (expiresAt == null) {
      return 0L;
    }
    return Math.max(0L, expiresAt.toEpochMilli() - now.toEpochMilli());
  }

  /** Lấy số lượng kết nối đang active cho một phiên - dùng cho health check. */
  public int getConnectionCount(Long auctionId) {
    Set<WsContext> set = connections.get(auctionId);
    return set != null ? set.size() : 0;
  }
}
