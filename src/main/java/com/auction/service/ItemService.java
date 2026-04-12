package com.auction.service;

import com.auction.dao.ItemDao;
import com.auction.dto.CreateItemRequest;
import com.auction.exception.DuplicateException;
import com.auction.exception.NotFoundException;
import com.auction.exception.UnauthorizedException;
import com.auction.model.Art;
import com.auction.model.Electronics;
import com.auction.model.Item;
import com.auction.model.Vehicle;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service xử lý logic nghiệp vụ cho Item.
 *
 * <p>Controller gọi Service → Service gọi DAO → DAO gọi database.
 * Service chứa logic: validation, kiểm tra quyền, tạo đúng subclass...
 */
public class ItemService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ItemService.class);

    private final ItemDao itemDao;

    public ItemService(ItemDao itemDao) {
        this.itemDao = itemDao;
    }

    /**
     * Lấy tất cả items, có thể filter theo sellerId.
     *
     * <p>Nếu sellerId != null → chỉ lấy items của seller đó.
     * Nếu sellerId == null → lấy tất cả.
     */
    public List<Item> getAllItems(Long sellerId) {
        if (sellerId != null) {
            return itemDao.findBySellerId(sellerId);
        }
        return itemDao.findAll();
    }

    /**
     * Lấy chi tiết 1 item theo ID.
     *
     * @throws NotFoundException nếu không tìm thấy item
     */
    public Item getItemById(Long id) {
        return itemDao.findById(id)
            .orElseThrow(() -> new NotFoundException("Item not found with id: " + id));
    }

    /**
     * Seller tạo item mới.
     *
     * <p>Luồng:
     * 1. Validate input (name, description, category)
     * 2. Tạo đúng subclass dựa vào category (Polymorphism)
     * 3. Gán sellerId từ JWT token
     * 4. Lưu vào database
     *
     * @param request dữ liệu từ client
     * @param sellerId ID của seller (lấy từ JWT token trong middleware)
     * @throws IllegalArgumentException nếu input không hợp lệ
     */
    public Item createItem(CreateItemRequest request, Long sellerId) {
        // Validate input
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Item name must not be empty");
        }
        if (request.getCategory() == null || request.getCategory().isBlank()) {
            throw new IllegalArgumentException("Category must not be empty");
        }

        // Tạo đúng subclass dựa vào category (Polymorphism)
        // categoryDetail chứa thông tin riêng: brand (Electronics), artist (Art), year (Vehicle)
        LocalDateTime now = LocalDateTime.now();
        String category = request.getCategory().toUpperCase();

        Item newItem = switch (category) {
            case "ELECTRONICS" -> new Electronics(
                null, request.getName(), request.getDescription(),
                sellerId, request.getCategoryDetail(), now);
            case "ART" -> new Art(
                null, request.getName(), request.getDescription(),
                sellerId, request.getCategoryDetail(), now);
            case "VEHICLE" -> {
                int year = 0;
                try {
                    year = Integer.parseInt(request.getCategoryDetail());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Vehicle year must be a number");
                }
                yield new Vehicle(
                    null, request.getName(), request.getDescription(),
                    sellerId, year, now);
            }
            default -> throw new IllegalArgumentException(
                "Invalid category: " + category + ". Must be ELECTRONICS, ART, or VEHICLE");
        };

        Item saved = itemDao.insert(newItem);
        LOGGER.info("Item created: id={}, name={}, seller={}", saved.getId(), saved.getName(), sellerId);
        return saved;
    }

    /**
     * Seller cập nhật item.
     *
     * <p>Kiểm tra:
     * 1. Item có tồn tại không?
     * 2. Item có thuộc seller này không? (ownership check)
     *
     * @param itemId ID của item cần sửa
     * @param request dữ liệu mới
     * @param userId ID của user đang request (từ JWT)
     * @throws NotFoundException nếu item không tồn tại
     * @throws UnauthorizedException nếu item không thuộc seller này
     */
    public Item updateItem(Long itemId, CreateItemRequest request, Long userId) {
        // Tìm item
        Item item = itemDao.findById(itemId)
            .orElseThrow(() -> new NotFoundException("Item not found with id: " + itemId));

        // Kiểm tra ownership: sellerId trong DB == userId từ JWT
        if (!item.getSellerId().equals(userId)) {
            throw new UnauthorizedException("You can only edit your own items");
        }

        // Cập nhật thông tin
        if (request.getName() != null && !request.getName().isBlank()) {
            item.setName(request.getName());
        }
        if (request.getDescription() != null) {
            item.setDescription(request.getDescription());
        }

        itemDao.update(item);
        LOGGER.info("Item updated: id={}, name={}", itemId, item.getName());
        return item;
    }

    /**
     * Seller hoặc Admin xóa item.
     *
     * <p>Seller chỉ xóa được item của mình. Admin xóa được tất cả.
     *
     * @param itemId ID item cần xóa
     * @param userId ID user đang request (từ JWT)
     * @param role role của user (từ JWT)
     * @throws NotFoundException nếu item không tồn tại
     * @throws UnauthorizedException nếu không có quyền xóa
     */
    public void deleteItem(Long itemId, Long userId, String role) {
        Item item = itemDao.findById(itemId)
            .orElseThrow(() -> new NotFoundException("Item not found with id: " + itemId));

        // Admin xóa được tất cả, Seller chỉ xóa item của mình
        if (!"ADMIN".equals(role) && !item.getSellerId().equals(userId)) {
            throw new UnauthorizedException("You can only delete your own items");
        }

        itemDao.delete(itemId);
        LOGGER.info("Item deleted: id={}, by userId={}", itemId, userId);
    }
}
