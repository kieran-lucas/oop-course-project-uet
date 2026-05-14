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
 * Controller xu ly cac endpoint dat gia va lich su bid.
 *
 * <p>Danh sach endpoints:
 *
 * <ul>
 *   <li>{@code POST /api/auctions/{id}/bid} - Dat gia, chi BIDDER
 *   <li>{@code GET /api/auctions/{id}/bids} - Lay lich su bid cua phien
 * </ul>
 *
 * <p>Controller nay delegate business logic sang {@link BidService}: validate gia, anti-sniping,
 * auto-bid chain, va WebSocket broadcast.
 */
public class BidController {

  private static final Logger LOGGER = LoggerFactory.getLogger(BidController.class);

  private final BidService bidService;

  public BidController(BidService bidService) {
    this.bidService = bidService;
  }

  public void register(Javalin app) {
    // POST /api/auctions/{id}/bid - chi BIDDER duoc dat gia thu cong.
    app.post("/api/auctions/{id}/bid", this::handleManualBid);

    // GET /api/auctions/{id}/bids - lich su bid.
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
      throw new UnauthorizedException("Chi BIDDER moi duoc tham gia dat gia");
    }

    Long auctionId = Long.parseLong(ctx.pathParam("id"));
    Long bidderId = ctx.attribute("userId");
    BidRequest request = ctx.bodyAsClass(BidRequest.class);

    BidTransaction bid = bidService.placeBid(auctionId, bidderId, request.getAmount(), false);

    LOGGER.info("Bid dat thanh cong qua API: auction={}, bidder={}", auctionId, bidderId);
    ctx.status(201).json(bid);
  }
}
