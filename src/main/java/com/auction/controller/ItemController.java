package com.auction.controller;

import com.auction.dto.CreateItemRequest;
import com.auction.exception.UnauthorizedException;
import com.auction.model.Item;
import com.auction.service.ItemService;
import io.javalin.Javalin;
import java.util.List;

/**
 * Controller xử lý HTTP request cho Item (sản phẩm đấu giá).
 *
 * <p>5 endpoints:
 * <ul>
 *   <li>GET /api/items — list tất cả items (public, filter theo sellerId)</li>
 *   <li>GET /api/items/:id — chi tiết 1 item</li>
 *   <li>POST /api/items — seller tạo item (cần JWT, role=SELLER)</li>
 *   <li>PUT /api/items/:id — seller sửa item (cần JWT, kiểm tra ownership)</li>
 *   <li>DELETE /api/items/:id — seller/admin xóa item</li>
 * </ul>
 *
 * <p>Controller KHÔNG chứa logic nghiệp vụ — chỉ:
 * 1. Đọc request (path param, query param, body)
 * 2. Đọc thông tin user từ JWT (ctx.attribute)
 * 3. Gọi Service
 * 4. Trả response
 */
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    public void register(Javalin app) {

        // ════════════════════════════════════════════════════════
        // GET /api/items — List tất cả items
        // ════════════════════════════════════════════════════════
        //
        // Public endpoint (ai cũng xem được danh sách sản phẩm).
        // Hỗ trợ filter: GET /api/items?sellerId=5 → chỉ lấy items của seller 5.
        //
        // ctx.queryParam("sellerId") đọc query parameter từ URL.
        // Nếu không có ?sellerId=... thì trả về null → lấy tất cả.
        app.get("/api/items", ctx -> {
            String sellerIdParam = ctx.queryParam("sellerId");
            Long sellerId = null;

            if (sellerIdParam != null) {
                try {
                    sellerId = Long.parseLong(sellerIdParam);
                } catch (NumberFormatException e) {
                    // Bỏ qua nếu sellerId không phải số → lấy tất cả
                }
            }

            List<Item> items = itemService.getAllItems(sellerId);
            ctx.json(items);
        });

        // ════════════════════════════════════════════════════════
        // GET /api/items/:id — Chi tiết 1 item
        // ════════════════════════════════════════════════════════
        //
        // :id là path parameter.
        // ctx.pathParam("id") đọc giá trị từ URL.
        // Ví dụ: GET /api/items/42 → ctx.pathParam("id") = "42"
        app.get("/api/items/{id}", ctx -> {
            Long id = Long.parseLong(ctx.pathParam("id"));
            Item item = itemService.getItemById(id);
            ctx.json(item);
        });

        // ════════════════════════════════════════════════════════
        // POST /api/items — Seller tạo item mới
        // ════════════════════════════════════════════════════════
        //
        // Cần JWT token (middleware đã kiểm tra).
        // Chỉ SELLER mới được tạo item.
        //
        // ctx.attribute("role") — lấy role từ JWT (middleware đã lưu).
        // ctx.attribute("userId") — lấy userId từ JWT.
        app.post("/api/items", ctx -> {
            // Kiểm tra role từ JWT
            String role = ctx.attribute("role");
            if (!"SELLER".equals(role)) {
                throw new UnauthorizedException("Only sellers can create items");
            }

            // Lấy userId từ JWT (đây là sellerId)
            Long userId = ctx.attribute("userId");

            // Parse request body
            CreateItemRequest request = ctx.bodyAsClass(CreateItemRequest.class);

            // Gọi service tạo item
            Item created = itemService.createItem(request, userId);

            // Trả 201 Created
            ctx.status(201).json(created);
        });

        // ════════════════════════════════════════════════════════
        // PUT /api/items/:id — Seller sửa item
        // ════════════════════════════════════════════════════════
        //
        // Kiểm tra ownership: sellerId trong DB phải == userId từ JWT.
        // Seller A không được sửa item của Seller B.
        app.put("/api/items/{id}", ctx -> {
            Long itemId = Long.parseLong(ctx.pathParam("id"));
            Long userId = ctx.attribute("userId");

            CreateItemRequest request = ctx.bodyAsClass(CreateItemRequest.class);
            Item updated = itemService.updateItem(itemId, request, userId);

            ctx.json(updated);
        });

        // ════════════════════════════════════════════════════════
        // DELETE /api/items/:id — Seller/Admin xóa item
        // ════════════════════════════════════════════════════════
        //
        // Seller chỉ xóa item của mình.
        // Admin xóa được tất cả.
        app.delete("/api/items/{id}", ctx -> {
            Long itemId = Long.parseLong(ctx.pathParam("id"));
            Long userId = ctx.attribute("userId");
            String role = ctx.attribute("role");

            itemService.deleteItem(itemId, userId, role);

            // 204 No Content: xóa thành công, không có body trả về
            ctx.status(204);
        });
    }
}
