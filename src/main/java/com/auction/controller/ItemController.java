package com.auction.controller;

import com.auction.dto.CreateItemRequest;
import com.auction.exception.UnauthorizedException;
import com.auction.model.Item;
import com.auction.service.ItemService;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller xử lý toàn bộ thao tác CRUD cho tài nguyên Item (sản phẩm đấu giá)
 *
 * <p>Danh sách endpoints và quyền truy cập:
 *
 * <pre>
 *   GET    /api/items          → Mọi user đã đăng nhập (có thể filter theo sellerId)
 *   GET    /api/items/:id      → Mọi user đã đăng nhập
 *   POST   /api/items          → Chỉ SELLER
 *   PUT    /api/items/:id      → Chỉ SELLER sở hữu item đó
 *   DELETE /api/items/:id      → SELLER sở hữu item hoặc ADMIN
 * </pre>
 *
 * <p>Cơ chế kiểm tra quyền:
 *
 * <ul>
 *   <li>JWT Middleware (cấu hình trong {@link com.auction.App}) đã xác thực token và set các
 *       attribute vào context: {@code userId}, {@code username}, {@code role}
 *   <li>Controller đọc {@code ctx.attribute("role")} để kiểm tra role
 *   <li>Controller đọc {@code ctx.attribute("userId")} để kiểm tra ownership
 * </ul>
 *
 * <p>Factory Method pattern được áp dụng trong {@link ItemService#create}: nhận {@code category} từ
 * request ("ELECTRONICS"/"ART"/"VEHICLE"), tạo đúng subclass tương ứng
 *
 * @see com.auction.service.ItemService
 * @see com.auction.pattern.factory.ItemFactory
 */
public class ItemController {

  /** Logger ghi lại các thao tác CRUD trên item. */
  private static final Logger LOGGER = LoggerFactory.getLogger(ItemController.class);

  /** Hàm khởi tạo private — lớp này chỉ dùng static methods, không cần instance. */
  private ItemController() {}

  /**
   * Đăng ký tất cả route của ItemController vào Javalin instance.
   *
   * @param app Javalin instance đang chạy
   * @param itemService service xử lý logic nghiệp vụ cho Item
   */
  public static void register(Javalin app, ItemService itemService) {
    app.get("/api/items", ctx -> handleGetAll(ctx, itemService));
    app.get("/api/items/{id}", ctx -> handleGetById(ctx, itemService));
    app.post("/api/items", ctx -> handleCreate(ctx, itemService));
    app.put("/api/items/{id}", ctx -> handleUpdate(ctx, itemService));
    app.delete("/api/items/{id}", ctx -> handleDelete(ctx, itemService));

    LOGGER.info(
        "Đã đăng ký ItemController: GET/POST /api/items, " + "GET/PUT/DELETE /api/items/:id");
  }

  /**
   * Lấy danh sách tất cả sản phẩm, có hỗ trợ lọc theo {@code sellerId}.
   *
   * <p>Query parameter tùy chọn:
   *
   * <ul>
   *   <li>{@code ?sellerId=5} — chỉ trả về item của seller có id=5.
   *   <li>Không có parameter → trả về tất cả item trong hệ thống.
   * </ul>
   *
   * <p>Ví dụ response:
   *
   * <pre>
   *   [
   *     {"id": 1, "name": "iPhone 15", "category": "ELECTRONICS",
   *      "sellerId": 3, "brand": "Apple"},
   *     {"id": 2, "name": "Tranh Sơn Dầu", "category": "ART",
   *      "sellerId": 5, "artist": "Nguyễn Văn A"}
   *   ]
   * </pre>
   *
   * @param ctx Javalin context chứa HTTP request/response
   * @param itemService service truy vấn danh sách item
   */
  private static void handleGetAll(Context ctx, ItemService itemService) {
    // Kiểm tra query param sellerId (nếu có) để lọc theo người bán
    String sellerIdParam = ctx.queryParam("sellerId");

    List<Item> items;
    if (sellerIdParam != null) {
      Long sellerId = Long.parseLong(sellerIdParam);
      items = itemService.getBySellerId(sellerId);
    } else {
      items = itemService.getAll();
    }

    ctx.status(200).json(items);
  }

  /**
   * Lấy chi tiết một sản phẩm theo ID.
   *
   * <p>Nếu không tìm thấy item với ID đã cho, service sẽ ném {@code NotFoundException} và exception
   * handler trong {@code App.java} sẽ trả về HTTP 404.
   *
   * @param ctx Javalin context chứa HTTP request/response
   * @param itemService service tìm kiếm item theo ID
   */
  private static void handleGetById(Context ctx, ItemService itemService) {
    Long itemId = Long.parseLong(ctx.pathParam("id"));
    Item item = itemService.getById(itemId);
    ctx.status(200).json(item);
  }

  /**
   * Tạo sản phẩm mới — CHỈ SELLER được phép thực hiện.
   *
   * <p>Factory Method pattern được áp dụng ở đây: {@code ItemService.create()} nhận {@code
   * category} từ request và gọi {@code ItemFactory.create()} để tạo đúng subclass ({@code
   * Electronics}, {@code Art}, hoặc {@code Vehicle}).
   *
   * <p>Luồng xử lý:
   *
   * <ol>
   *   <li>Kiểm tra {@code role == "SELLER"} từ JWT context — ném {@code UnauthorizedException} nếu
   *       không phải SELLER.
   *   <li>Lấy {@code userId} từ JWT context để gán làm {@code sellerId}.
   *   <li>Parse {@link CreateItemRequest} từ body.
   *   <li>Gọi {@code ItemService.create()} → Factory tạo đúng subclass → lưu DB.
   * </ol>
   *
   * <p>Ví dụ request body:
   *
   * <pre>
   *   {
   *     "name": "iPhone 15 Pro",
   *     "description": "Máy mới 100%, chưa kích hoạt",
   *     "category": "ELECTRONICS",
   *     "categoryDetail": "Apple"
   *   }
   * </pre>
   *
   * @param ctx Javalin context chứa HTTP request/response
   * @param itemService service tạo item qua Factory pattern
   * @throws UnauthorizedException nếu user không có role SELLER
   */
  private static void handleCreate(Context ctx, ItemService itemService) {
    // Kiểm tra quyền: chỉ SELLER mới được tạo sản phẩm
    requireRole(ctx, "SELLER");

    // Lấy sellerId từ JWT token (đã được middleware parse và set vào context)
    Long sellerId = ctx.attribute("userId");

    // Parse JSON body thành DTO
    CreateItemRequest request = ctx.bodyAsClass(CreateItemRequest.class);

    // Gọi service: dùng Factory Method để tạo đúng subclass Item
    Item createdItem = itemService.create(request, sellerId);

    LOGGER.info(
        "Tạo item mới: sellerId={}, category={}, name={}",
        sellerId,
        request.getCategory(),
        request.getName());

    ctx.status(201).json(createdItem);
  }

  /**
   * Cập nhật thông tin sản phẩm — CHỈ SELLER sở hữu item mới được phép.
   *
   * <p>Kiểm tra ownership: {@code item.sellerId == userId từ JWT}. Nếu một seller cố sửa item của
   * seller khác → {@code UnauthorizedException}.
   *
   * <p>Lưu ý: Item đang được dùng trong phiên đấu giá RUNNING sẽ KHÔNG thể sửa (logic này được xử
   * lý ở tầng Service, không phải Controller).
   *
   * @param ctx Javalin context chứa HTTP request/response
   * @param itemService service cập nhật item sau khi kiểm tra ownership
   * @throws UnauthorizedException nếu không phải SELLER hoặc không sở hữu item
   */
  private static void handleUpdate(Context ctx, ItemService itemService) {
    // Kiểm tra role cơ bản
    requireRole(ctx, "SELLER");

    Long itemId = Long.parseLong(ctx.pathParam("id"));
    Long userId = ctx.attribute("userId");

    // Parse body — chứa các trường cần cập nhật
    CreateItemRequest request = ctx.bodyAsClass(CreateItemRequest.class);

    // Service sẽ kiểm tra item.sellerId == userId trước khi update
    Item updatedItem = itemService.update(itemId, request, userId);

    LOGGER.info("Cập nhật item: itemId={}, sellerId={}", itemId, userId);

    ctx.status(200).json(updatedItem);
  }

  /**
   * Xóa sản phẩm — SELLER sở hữu item hoặc ADMIN được phép.
   *
   * <p>Logic phân quyền:
   *
   * <ul>
   *   <li>Role {@code ADMIN} → có thể xóa bất kỳ item nào.
   *   <li>Role {@code SELLER} → chỉ xóa được item của mình.
   *   <li>Role {@code BIDDER} → không được phép → {@code UnauthorizedException}.
   * </ul>
   *
   * <p>Nếu item đang liên kết với phiên đấu giá RUNNING, service sẽ ném lỗi để tránh mất tính toàn
   * vẹn dữ liệu.
   *
   * @param ctx Javalin context chứa HTTP request/response
   * @param itemService service xóa item sau khi kiểm tra quyền và tính toàn vẹn dữ liệu
   * @throws UnauthorizedException nếu không có quyền xóa
   */
  private static void handleDelete(Context ctx, ItemService itemService) {
    String role = ctx.attribute("role");
    Long userId = ctx.attribute("userId");
    Long itemId = Long.parseLong(ctx.pathParam("id"));

    // Chỉ SELLER hoặc ADMIN mới được xóa
    if (!"SELLER".equals(role) && !"ADMIN".equals(role)) {
      throw new UnauthorizedException("Chỉ người bán hoặc quản trị viên mới có thể xóa sản phẩm");
    }

    // Service xử lý logic phân quyền chi tiết (ADMIN bypass ownership check, SELLER thì không)
    itemService.delete(itemId, userId, role);

    LOGGER.info("Xóa item: itemId={}, thực hiện bởi userId={}, role={}", itemId, userId, role);

    // 204 No Content — xóa thành công, không có body trả về
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
