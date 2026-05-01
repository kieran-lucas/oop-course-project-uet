package com.auction.controller;

import com.auction.dto.AuctionResponse;
import com.auction.dto.CreateAuctionRequest;
import com.auction.model.Auction;
import com.auction.service.AuctionService;
import io.javalin.Javalin;
import java.util.List;

/**
 * Controller xử lý HTTP request cho Auction (phiên đấu giá).
 *
 * <p>5 endpoints:
 *
 * <ul>
 *   <li>GET /api/auctions — list tất cả (hỗ trợ ?status=RUNNING)
 *   <li>GET /api/auctions/:id — chi tiết, trả AuctionResponse
 *   <li>POST /api/auctions — seller tạo phiên (check role, check item ownership)
 *   <li>PUT /api/auctions/:id — seller sửa (chỉ khi OPEN)
 *   <li>DELETE /api/auctions/:id — seller/admin xóa
 * </ul>
 */
public class AuctionController {

  private final AuctionService auctionService;

  public AuctionController(AuctionService auctionService) {
    this.auctionService = auctionService;
  }

  public void register(Javalin app) {

    // ════════════════════════════════════════════════════════
    // GET /api/auctions — List tất cả auctions
    // ════════════════════════════════════════════════════════
    //
    // Hỗ trợ query param: GET /api/auctions?status=RUNNING
    // → chỉ lấy auctions đang chạy.
    // Không có ?status → lấy tất cả.
    app.get(
        "/api/auctions",
        ctx -> {
          String status = ctx.queryParam("status");
          List<Auction> auctions = auctionService.getAllAuctions(status);
          ctx.json(auctions);
        });

    // ════════════════════════════════════════════════════════
    // GET /api/auctions/:id — Chi tiết 1 auction
    // ════════════════════════════════════════════════════════
    //
    // Trả về AuctionResponse (không phải Auction model).
    // AuctionResponse bổ sung thêm: itemName, itemCategory, leadingBidderUsername.
    //
    // Tại sao không trả Auction trực tiếp?
    // → Auction model chỉ có itemId (số), client cần itemName (tên) để hiển thị.
    // → Auction model chỉ có leadingBidderId, client cần leadingBidderUsername.
    // → AuctionResponse gộp dữ liệu từ 3 bảng: auctions + items + users.
    app.get(
        "/api/auctions/{id}",
        ctx -> {
          Long id = Long.parseLong(ctx.pathParam("id"));
          AuctionResponse response = auctionService.getAuctionById(id);
          ctx.json(response);
        });

    // ════════════════════════════════════════════════════════
    // POST /api/auctions — Seller tạo phiên đấu giá
    // ════════════════════════════════════════════════════════
    //
    // Yêu cầu:
    // 1. Role phải là SELLER
    // 2. Item phải tồn tại
    // 3. Item phải thuộc seller này (không tạo auction cho item người khác)
    app.post(
        "/api/auctions",
        ctx -> {
          Long userId = ctx.attribute("userId");
          String role = ctx.attribute("role");

          CreateAuctionRequest request = ctx.bodyAsClass(CreateAuctionRequest.class);
          Auction created = auctionService.createAuction(request, userId, role);

          ctx.status(201).json(created);
        });

    // ════════════════════════════════════════════════════════
    // PUT /api/auctions/:id — Seller sửa phiên đấu giá
    // ════════════════════════════════════════════════════════
    //
    // Chỉ sửa được khi status = OPEN (chưa bắt đầu).
    // RUNNING/FINISHED → không sửa được (đang/đã đấu giá).
    app.put(
        "/api/auctions/{id}",
        ctx -> {
          Long auctionId = Long.parseLong(ctx.pathParam("id"));
          Long userId = ctx.attribute("userId");

          CreateAuctionRequest request = ctx.bodyAsClass(CreateAuctionRequest.class);
          Auction updated = auctionService.updateAuction(auctionId, request, userId);

          ctx.json(updated);
        });

    // ════════════════════════════════════════════════════════
    // DELETE /api/auctions/:id — Seller/Admin xóa
    // ════════════════════════════════════════════════════════
    app.delete(
        "/api/auctions/{id}",
        ctx -> {
          Long auctionId = Long.parseLong(ctx.pathParam("id"));
          Long userId = ctx.attribute("userId");
          String role = ctx.attribute("role");

          auctionService.deleteAuction(auctionId, userId, role);
          ctx.status(204);
        });
  }
}
