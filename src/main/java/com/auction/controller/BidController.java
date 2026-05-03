package com.auction.controller;

import com.auction.dto.BidRequest;
import com.auction.exception.UnauthorizedException;
import com.auction.model.BidTransaction;
import com.auction.service.BidService;
import io.javalin.Javalin;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller xử lý các endpoint đặt giá và lịch sử bid.
 *
 * <p>Danh sách endpoints:
 * <ul>
 *   <li>{@code POST /api/auctions/{id}/bid} — Đặt giá, chỉ BIDDER.</li>
 *   <li>{@code GET  /api/auctions/{id}/bids} — Lấy lịch sử bid của phiên (để vẽ chart).</li>
 * </ul>
 *
 * <p>Controller này delegate toàn bộ business logic sang {@link BidService}:
 * validate giá, anti-sniping, auto-bid chain, và WebSocket broadcast.
 */
public class BidController {

  private static final Logger LOGGER = LoggerFactory.getLogger(BidController.class);

  private final BidService bidService;

  public BidController(BidService bidService) {
    this.bidService = bidService;
  }

  public void register(Javalin app) {
    // POST /api/auctions/{id}/bid — Đặt giá
    // Yêu cầu: role = BIDDER (SELLER không được tự bid)
    // Body: {"amount": 500000}
    // Response 201: BidTransaction JSON
    app.post(
        "/api/auctions/{id}/bid",
        ctx -> {
          String role = ctx.attribute("role");
          if (!"BIDDER".equals(role)) {
            throw new UnauthorizedException("Chỉ BIDDER mới được đặt giá");
          }

          Long auctionId = Long.parseLong(ctx.pathParam("id"));
          Long bidderId = ctx.attribute("userId");
          BidRequest request = ctx.bodyAsClass(BidRequest.class);

          // Gọi BidService với signature mới
          BidTransaction bid = bidService.placeBid(
              auctionId, bidderId, request.getAmount(), false);

          LOGGER.info("Bid đặt thành công qua API: auction={}, bidder={}", auctionId, bidderId);
          ctx.status(201).json(bid);
        });

    // GET /api/auctions/{id}/bids — Lịch sử bid
    // Dùng cho Bid History Chart (trục X = thời gian, trục Y = giá)
    // Response: List<BidTransaction> sắp xếp theo createdAt ASC
    app.get(
        "/api/auctions/{id}/bids",
        ctx -> {
          Long auctionId = Long.parseLong(ctx.pathParam("id"));
          List<BidTransaction> bids = bidService.getBidHistory(auctionId);
          ctx.json(bids);
        });
  }
}
