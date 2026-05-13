package com.auction.controller;

import com.auction.dto.AuctionResponse;
import com.auction.dto.CreateAuctionRequest;
import com.auction.dto.PageRequest;
import com.auction.exception.UnauthorizedException;
import com.auction.model.Auction;
import com.auction.service.AuctionService;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller xử lý toàn bộ thao tác CRUD cho tài nguyên Auction (phiên đấu giá).
 *
 * <p>Danh sách endpoints và quyền truy cập:
 *
 * <pre>
 *   GET    /api/auctions           → Mọi user đã đăng nhập (filter ?status=RUNNING)
 *   GET    /api/auctions/:id       → Mọi user đã đăng nhập (trả về AuctionResponse đầy đủ)
 *   POST   /api/auctions           → Chỉ SELLER
 *   PUT    /api/auctions/:id       → Chỉ SELLER sở hữu phiên, chỉ khi status=OPEN
 *   DELETE /api/auctions/:id       → SELLER sở hữu phiên hoặc ADMIN
 * </pre>
 *
 * <p>Tích hợp State Pattern:
 *
 * <ul>
 *   <li>{@code PUT} (sửa phiên) → service gọi {@code state.edit()} → {@code OpenState} cho phép,
 *       {@code RunningState}/{@code FinishedState} ném {@code AuctionClosedException}.
 *   <li>{@code POST} bid (trong {@link BidController}) → service gọi {@code state.placeBid()} → chỉ
 *       {@code RunningState} cho phép.
 * </ul>
 *
 * <p>Khác biệt với {@link ItemController}: endpoint {@code GET /api/auctions/:id} trả về {@link
 * AuctionResponse} (enriched object) thay vì {@link com.auction.model.Auction} raw, bao gồm thêm:
 * {@code itemName}, {@code leadingBidderUsername}, {@code remainingTimeMs}.
 *
 * @see com.auction.service.AuctionService
 * @see com.auction.pattern.state.AuctionState
 * @see BidController
 */
public class AuctionController {

  /** Logger ghi lại các thao tác CRUD trên phiên đấu giá */
  private static final Logger LOGGER = LoggerFactory.getLogger(AuctionController.class);

  /** Hàm khởi tạo private — lớp này chỉ dùng static methods, không cần instance. */
  private AuctionController() {}

  /**
   * Đăng ký tất cả route của AuctionController vào Javalin instance.
   *
   * @param app Javalin instance đang chạy
   * @param auctionService service xử lý logic nghiệp vụ cho phiên đấu giá
   */
  public static void register(Javalin app, AuctionService auctionService) {
    app.get("/api/auctions", ctx -> handleGetAll(ctx, auctionService));
    app.get("/api/auctions/{id}", ctx -> handleGetById(ctx, auctionService));
    app.post("/api/auctions", ctx -> handleCreate(ctx, auctionService));
    app.put("/api/auctions/{id}", ctx -> handleUpdate(ctx, auctionService));
    app.delete("/api/auctions/{id}", ctx -> handleDelete(ctx, auctionService));

    LOGGER.info(
        "Đã đăng ký AuctionController: GET/POST /api/auctions, "
            + "GET/PUT/DELETE /api/auctions/:id");
  }

  /**
   * Lấy danh sách tất cả phiên đấu giá, có hỗ trợ lọc theo trạng thái.
   *
   * <p>Query parameter tùy chọn:
   *
   * <ul>
   *   <li>{@code ?status=RUNNING} — chỉ lấy phiên đang diễn ra.
   *   <li>{@code ?status=OPEN} — chỉ lấy phiên sắp mở.
   *   <li>{@code ?status=FINISHED} — chỉ lấy phiên đã kết thúc.
   *   <li>Không có parameter → trả về TẤT CẢ phiên (trừ CANCELED và PAID nếu không phải ADMIN).
   * </ul>
   *
   * <p>Ví dụ response (mảng {@link AuctionResponse}):
   *
   * <pre>
   *   [
   *     {
   *       "id": 1,
   *       "itemName": "iPhone 15 Pro",
   *       "currentPrice": 15000000,
   *       "status": "RUNNING",
   *       "remainingTimeMs": 1800000,
   *       "leadingBidderUsername": "alice"
   *     }
   *   ]
   * </pre>
   *
   * @param ctx Javalin context chứa HTTP request/response
   * @param auctionService service truy vấn danh sách phiên đấu giá
   */
  private static void handleGetAll(Context ctx, AuctionService auctionService) {
    // Lấy query param lọc trạng thái (có thể null nếu không truyền)
    String statusFilter = ctx.queryParam("status");
    int page = Integer.parseInt(ctx.queryParamAsClass("page", String.class).getOrDefault("0"));
    int size = Integer.parseInt(ctx.queryParamAsClass("size", String.class).getOrDefault("20"));
    PageRequest pageRequest = PageRequest.of(page, size);

    List<AuctionResponse> auctions = auctionService.getAll(statusFilter, pageRequest);
    ctx.status(200).json(auctions);
  }

  /**
   * Lấy chi tiết một phiên đấu giá theo ID — trả về {@link AuctionResponse} đầy đủ.
   *
   * <p>Khác với {@code GET /api/auctions} (trả về list), endpoint này trả về dữ liệu được
   * "enriched" (làm giàu thêm thông tin) bao gồm:
   *
   * <ul>
   *   <li>{@code itemName} — tên sản phẩm (join từ bảng items).
   *   <li>{@code leadingBidderUsername} — username của người đang dẫn đầu.
   *   <li>{@code remainingTimeMs} — thời gian còn lại (milliseconds).
   * </ul>
   *
   * <p>Client dùng endpoint này khi mở màn hình {@code auction-detail.fxml}.
   *
   * @param ctx Javalin context chứa HTTP request/response
   * @param auctionService service tìm kiếm và enrich dữ liệu phiên đấu giá
   */
  private static void handleGetById(Context ctx, AuctionService auctionService) {
    Long auctionId = Long.parseLong(ctx.pathParam("id"));
    AuctionResponse auction = auctionService.getById(auctionId);
    ctx.status(200).json(auction);
  }

  /**
   * Tạo phiên đấu giá mới — CHỈ SELLER được phép thực hiện.
   *
   * <p>Luồng xử lý:
   *
   * <ol>
   *   <li>Kiểm tra {@code role == "SELLER"} từ JWT context.
   *   <li>Parse {@link CreateAuctionRequest} từ body (chứa itemId, startingPrice, startTime,
   *       endTime).
   *   <li>Gọi {@code AuctionService.create()} — kiểm tra item thuộc seller, validate startTime <
   *       endTime, tạo Auction với status = "OPEN".
   * </ol>
   *
   * <p>Phiên mới tạo luôn có status = {@code OPEN}. {@link com.auction.service.AuctionScheduler} sẽ
   * tự động chuyển sang {@code RUNNING} khi đến {@code startTime}.
   *
   * <p>Ví dụ request body:
   *
   * <pre>
   *   {
   *     "itemId": 3,
   *     "startingPrice": 5000000,
   *     "startTime": "2025-06-01T10:00:00",
   *     "endTime": "2025-06-01T12:00:00"
   *   }
   * </pre>
   *
   * @param ctx Javalin context chứa HTTP request/response
   * @param auctionService service tạo phiên đấu giá mới
   * @throws UnauthorizedException nếu user không có role SELLER
   */
  private static void handleCreate(Context ctx, AuctionService auctionService) {
    // Kiểm tra quyền: chỉ SELLER mới được mở phiên đấu giá
    requireRole(ctx, "SELLER");

    Long sellerId = ctx.attribute("userId");

    // Parse JSON body
    CreateAuctionRequest request = ctx.bodyAsClass(CreateAuctionRequest.class);

    // Service kiểm tra item thuộc sellerId và tạo phiên mới với status=OPEN
    Auction created = auctionService.create(request, sellerId);

    LOGGER.info(
        "Tạo phiên đấu giá mới: sellerId={}, itemId={}, startingPrice={}",
        sellerId,
        request.getItemId(),
        request.getStartingPrice());

    ctx.status(201).json(created);
  }

  /**
   * Cập nhật thông tin phiên đấu giá — CHỈ SELLER sở hữu phiên và KHI PHIÊN CÒN Ở TRẠNG THÁI OPEN.
   *
   * <p>State Pattern được áp dụng tại đây:
   *
   * <ul>
   *   <li>Nếu phiên đang ở {@code OpenState} → {@code state.edit()} thành công → cập nhật DB.
   *   <li>Nếu phiên đang ở {@code RunningState} → {@code state.edit()} ném {@code
   *       AuctionClosedException("Không thể sửa khi phiên đang diễn ra")}.
   *   <li>Nếu phiên đang ở {@code FinishedState}/{@code PaidState}/{@code CanceledState} → tương
   *       tự, ném exception.
   * </ul>
   *
   * <p>Những trường có thể cập nhật khi OPEN: {@code startingPrice}, {@code startTime}, {@code
   * endTime}. Không thể đổi {@code itemId} sau khi tạo phiên.
   *
   * @param ctx Javalin context chứa HTTP request/response
   * @param auctionService service cập nhật phiên sau khi qua State pattern validation
   * @throws UnauthorizedException nếu không phải SELLER hoặc không sở hữu phiên
   */
  private static void handleUpdate(Context ctx, AuctionService auctionService) {
    requireRole(ctx, "SELLER");

    Long auctionId = Long.parseLong(ctx.pathParam("id"));
    Long userId = ctx.attribute("userId");

    // Parse body — chứa các trường muốn cập nhật
    CreateAuctionRequest request = ctx.bodyAsClass(CreateAuctionRequest.class);

    // Service: kiểm tra ownership + State pattern (chỉ OPEN mới cho sửa)
    AuctionResponse updated = auctionService.update(auctionId, request, userId);

    LOGGER.info("Cập nhật phiên đấu giá: auctionId={}, sellerId={}", auctionId, userId);

    ctx.status(200).json(updated);
  }

  /**
   * Hủy phiên đấu giá — SELLER sở hữu phiên hoặc ADMIN.
   *
   * <p>Logic phân quyền:
   *
   * <ul>
   *   <li>Role {@code ADMIN} → có thể hủy bất kỳ phiên nào ở mọi trạng thái.
   *   <li>Role {@code SELLER} → chỉ hủy được phiên của chính mình khi status là {@code OPEN} hoặc
   *       {@code RUNNING} (cho phép hủy trong trường hợp bất khả kháng).
   *   <li>Role {@code BIDDER} → không được phép → {@code UnauthorizedException}.
   * </ul>
   *
   * <p>Hệ thống không xóa cứng (hard delete) mà chuyển status sang {@code CANCELED} để đảm bảo toàn
   * vẹn lịch sử bid.
   *
   * @param ctx Javalin context chứa HTTP request/response
   * @param auctionService service xử lý hủy phiên đấu giá
   * @throws UnauthorizedException nếu không có quyền hủy
   */
  private static void handleDelete(Context ctx, AuctionService auctionService) {
    String role = ctx.attribute("role");
    Long userId = ctx.attribute("userId");
    Long auctionId = Long.parseLong(ctx.pathParam("id"));

    // Chỉ SELLER hoặc ADMIN mới được hủy phiên
    if (!"SELLER".equals(role) && !"ADMIN".equals(role)) {
      throw new UnauthorizedException(
          "Chỉ người bán hoặc quản trị viên mới có thể hủy phiên đấu giá");
    }

    // Service xử lý: kiểm tra ownership (với SELLER), chuyển status → CANCELED
    auctionService.delete(auctionId, userId, role);

    LOGGER.info(
        "Hủy phiên đấu giá: auctionId={}, thực hiện bởi userId={}, role={}",
        auctionId,
        userId,
        role);

    // 204 No Content — thành công, không có body
    ctx.status(204);
  }

  /**
   * Kiểm tra role của user hiện tại có khớp với role yêu cầu không.
   *
   * <p>Phương thức helper dùng nội bộ để tránh lặp code kiểm tra role. Attribute {@code "role"} đã
   * được {@code JwtMiddleware} set vào context trước đó.
   *
   * @param ctx Javalin context chứa role của user sau khi middleware xác thực
   * @param requiredRole role yêu cầu (ví dụ: "SELLER", "ADMIN")
   * @throws UnauthorizedException nếu role không khớp
   */
  private static void requireRole(Context ctx, String requiredRole) {
    String role = ctx.attribute("role");
    if (!requiredRole.equals(role)) {
      throw new UnauthorizedException(
          "Chỉ " + requiredRole + " mới có quyền thực hiện thao tác này");
    }
  }
}
