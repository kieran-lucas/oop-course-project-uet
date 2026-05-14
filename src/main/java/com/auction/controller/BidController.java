package com.auction.controller;

import com.auction.dto.BidRequest;
import com.auction.exception.UnauthorizedException;
import com.auction.model.BidTransaction;
import com.auction.service.BidService;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller xử lý các endpoint đặt giá và lịch sử bid.
 *
 * <p>Danh sách endpoints:
 *
 * <ul>
 *   <li>{@code POST /api/auctions/{id}/bid} - Đặt giá, chỉ BIDDER
 *   <li>{@code GET /api/auctions/{id}/bids} - Lấy lịch sử bid của phiên
 * </ul>
 *
 * <p>Controller này delegate business logic sang {@link BidService}: validate giá, anti-sniping,
 * auto-bid chain, và WebSocket broadcast.
 */
public class BidController {

  private static final Logger LOGGER = LoggerFactory.getLogger(BidController.class);

  private final BidService bidService;

  public BidController(BidService bidService) {
    this.bidService = bidService;
  }

  public void register(Javalin app) {
    // POST /api/auctions/{id}/bid - chỉ BIDDER được đặt giá thủ công.
    app.post("/api/auctions/{id}/bid", this::handleManualBid);

    // GET /api/auctions/{id}/bids - lịch sử bid.
    app.get(
        "/api/auctions/{id}/bids",
        ctx -> {
          Long auctionId = Long.parseLong(ctx.pathParam("id"));
          List<BidTransaction> bids = bidService.getBidHistory(auctionId);
          ctx.json(bids);
        });
  }

  void handleManualBid(Context ctx) {
    String role = ctx.attribute("role");

    if (!"BIDDER".equals(role)) {
      throw new UnauthorizedException("Chỉ BIDDER mới được tham gia đặt giá");
    }

    Long auctionId = Long.parseLong(ctx.pathParam("id"));
    Long bidderId = ctx.attribute("userId");
    BidRequest request = ctx.bodyAsClass(BidRequest.class);

    BidTransaction bid = bidService.placeBid(auctionId, bidderId, request.getAmount(), false);

    LOGGER.info("Bid đặt thành công qua API: auction={}, bidder={}", auctionId, bidderId);
    ctx.status(201).json(bid);
  }
}
