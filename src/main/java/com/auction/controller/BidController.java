package com.auction.controller;

import com.auction.dto.BidRequest;
import com.auction.exception.UnauthorizedException;
import com.auction.model.BidTransaction;
import com.auction.service.BidService;
import io.javalin.Javalin;
import java.util.List;

public class BidController {

  private final BidService bidService;

  public BidController(BidService bidService) {
    this.bidService = bidService;
  }

  public void register(Javalin app) {

    // Chỉ BIDDER mới được đặt giá.
    // SELLER không được bid vào auction của chính mình (conflict of interest).
    //
    // Input:
    //   URL: /api/auctions/5/bid (auctionId = 5)
    //   Body: {"amount": 500000}
    //   Header: Authorization: Bearer eyJ...
    //
    // Output thành công (201):
    //   BidTransaction JSON
    //
    // Output lỗi:
    //   400 InvalidBidException: giá thấp, phiên chưa bắt đầu/đã kết thúc
    //   401 UnauthorizedException: không phải BIDDER
    //   404 NotFoundException: auction không tồn tại
    app.post(
        "/api/auctions/{id}/bid",
        ctx -> {
          // Kiểm tra role từ JWT
          String role = ctx.attribute("role");
          if (!"BIDDER".equals(role)) {
            throw new UnauthorizedException("Only bidders can place bids");
          }

          // Lấy auctionId từ URL path
          Long auctionId = Long.parseLong(ctx.pathParam("id"));

          // Lấy userId từ JWT
          Long bidderId = ctx.attribute("userId");

          // Parse request body
          BidRequest request = ctx.bodyAsClass(BidRequest.class);

          // Gọi service đặt giá
          BidTransaction bid = bidService.placeBid(auctionId, request, bidderId);

          // Trả 201 Created
          ctx.status(201).json(bid);
        });

    // Trả về danh sách tất cả bid của một phiên.
    // Dùng cho Bid History Chart trên client:
    //   - Trục X: createdAt (thời gian)
    //   - Trục Y: amount (giá)
    //   - Mỗi data point = 1 BidTransaction
    //
    // Sorted by createdAt ASC (cũ → mới) để vẽ chart từ trái qua phải.
    app.get(
        "/api/auctions/{id}/bids",
        ctx -> {
          Long auctionId = Long.parseLong(ctx.pathParam("id"));
          List<BidTransaction> bids = bidService.getBidHistory(auctionId);
          ctx.json(bids);
        });
  }
}
