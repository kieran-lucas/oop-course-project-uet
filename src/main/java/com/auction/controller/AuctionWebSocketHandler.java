package com.auction.controller;

import com.auction.config.JwtUtil;
import com.auction.dto.BidUpdateMessage;
import com.auction.service.BidService;
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

public class AuctionWebSocketHandler implements BidService.AuctionWebSocketBroadcaster {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuctionWebSocketHandler.class);

  // Map lưu tất cả WebSocket connections theo auctionId
  // ConcurrentHashMap: thread-safe (nhiều client connect/disconnect cùng lúc)
  // CopyOnWriteArraySet: thread-safe cho Set (iterate + modify đồng thời)
  private final Map<Long, Set<WsContext>> connections = new ConcurrentHashMap<>();

  // Jackson ObjectMapper để serialize BidUpdateMessage → JSON
  private final ObjectMapper objectMapper;

  public AuctionWebSocketHandler() {
    this.objectMapper = new ObjectMapper();
    this.objectMapper.registerModule(new JavaTimeModule());
  }

  public void onConnect(WsConnectContext ctx) {
    try {
      // Lấy auctionId từ path
      Long auctionId = Long.parseLong(ctx.pathParam("id"));

      // Lấy JWT token từ query parameter
      String token = ctx.queryParam("token");

      if (token == null || token.isEmpty()) {
        LOGGER.warn("WebSocket connection rejected: no token");
        ctx.session.close(4001, "Missing token");
        return;
      }

      // Verify JWT token
      DecodedJWT decoded = JwtUtil.verifyToken(token);
      String username = decoded.getSubject();

      // Lưu connection vào map
      // computeIfAbsent: nếu chưa có Set cho auctionId này → tạo mới
      connections.computeIfAbsent(auctionId, k -> new CopyOnWriteArraySet<>()).add(ctx);

      LOGGER.info("WebSocket connected: user={}, auction={}", username, auctionId);

    } catch (JWTVerificationException e) {
      LOGGER.warn("WebSocket connection rejected: invalid token");
      ctx.session.close(4001, "Invalid token");
    } catch (Exception e) {
      LOGGER.error("WebSocket connection error: {}", e.getMessage());
      ctx.session.close(4000, "Connection error");
    }
  }

  public void onClose(WsCloseContext ctx) {
    removeConnection(ctx);
    LOGGER.debug("WebSocket disconnected: statusCode={}, reason={}", ctx.status(), ctx.reason());
  }

  public void onError(WsErrorContext ctx) {
    removeConnection(ctx);
    if (ctx.error() != null) {
      LOGGER.error("WebSocket error: {}", ctx.error().getMessage());
    }
  }

  @Override
  public void broadcast(Long auctionId, BidUpdateMessage message) {
    Set<WsContext> auctionConnections = connections.get(auctionId);

    if (auctionConnections == null || auctionConnections.isEmpty()) {
      return; // Không có ai đang xem → không cần gửi
    }

    try {
      // Serialize message thành JSON 1 lần (không serialize lại cho mỗi client)
      String json = objectMapper.writeValueAsString(message);

      // Gửi cho tất cả connections
      for (WsContext wsCtx : auctionConnections) {
        try {
          if (wsCtx.session.isOpen()) {
            wsCtx.send(json);
          } else {
            // Connection đã đóng nhưng chưa được cleanup → xóa
            auctionConnections.remove(wsCtx);
          }
        } catch (Exception e) {
          LOGGER.error("Failed to send WebSocket message to client: {}", e.getMessage());
          auctionConnections.remove(wsCtx);
        }
      }

      LOGGER.debug(
          "Broadcast to {} clients for auction {}: {}",
          auctionConnections.size(),
          auctionId,
          message.getType());

    } catch (Exception e) {
      LOGGER.error("Failed to serialize WebSocket message: {}", e.getMessage());
    }
  }

  private void removeConnection(WsContext ctx) {
    connections.values().forEach(set -> set.remove(ctx));
  }

  public int getConnectionCount(Long auctionId) {
    Set<WsContext> set = connections.get(auctionId);
    return set != null ? set.size() : 0;
  }
}
