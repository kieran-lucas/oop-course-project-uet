package com.auction.controller;

import com.auction.config.JwtUtil;
import com.auction.dto.BidUpdateMessage;
import com.auction.pattern.observer.AuctionEventManager;
import com.auction.pattern.observer.WebSocketObserver;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.websocket.WsCloseContext;
import io.javalin.websocket.WsConnectContext;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsErrorContext;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
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

  /**
   * Map auctionId → set WebSocket sessions đang kết nối. ConcurrentHashMap + CopyOnWriteArraySet để
   * thread-safe.
   */
  private final Map<Long, Set<WsContext>> connections = new ConcurrentHashMap<>();

  /** Map session → WebSocketObserver tương ứng. Dùng để unsubscribe khi client disconnect. */
  private final Map<WsContext, WebSocketObserver> observers = new ConcurrentHashMap<>();

  private final AuctionEventManager eventManager;
  private final ObjectMapper objectMapper;

  public AuctionWebSocketHandler(AuctionEventManager eventManager) {
    this.eventManager = eventManager;
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
      String username = decoded.getSubject();

      // Lưu connection
      connections.computeIfAbsent(auctionId, k -> new CopyOnWriteArraySet<>()).add(ctx);

      // Tạo observer và subscribe vào EventManager
      WebSocketObserver observer = new WebSocketObserver(this, auctionId);
      observers.put(ctx, observer);
      eventManager.subscribe(auctionId, observer);

      LOGGER.info("WebSocket kết nối: user={}, phiên=#{}", username, auctionId);

    } catch (JWTVerificationException e) {
      LOGGER.warn("WebSocket từ chối: token không hợp lệ");
      ctx.session.close(4001, "Invalid token");
    } catch (Exception e) {
      LOGGER.error("Lỗi kết nối WebSocket: {}", e.getMessage());
      ctx.session.close(4000, "Connection error");
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
          if (wsCtx.session.isOpen()) {
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

  /** Xóa connection và unsubscribe observer khỏi EventManager. */
  private void removeConnection(WsContext ctx) {
    connections.values().forEach(set -> set.remove(ctx));

    WebSocketObserver observer = observers.remove(ctx);
    if (observer != null) {
      eventManager.unsubscribe(observer.getAuctionId(), observer);
    }
  }

  /** Lấy số lượng kết nối đang active cho một phiên — dùng cho health check. */
  public int getConnectionCount(Long auctionId) {
    Set<WsContext> set = connections.get(auctionId);
    return set != null ? set.size() : 0;
  }
}
