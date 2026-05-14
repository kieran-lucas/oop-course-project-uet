package com.auction.service;

import com.auction.dao.ItemDao;
import com.auction.dto.CreateItemRequest;
import com.auction.exception.NotFoundException;
import com.auction.exception.UnauthorizedException;
import com.auction.model.Item;
import java.util.List;

/** Service xử lý toàn bộ business logic liên quan đến sản phẩm (Item). */
public class ItemService {

  private final ItemDao itemDao;

  public ItemService(ItemDao itemDao) {
    this.itemDao = itemDao;
  }

  /** Tạo sản phẩm mới cho seller. */
  public Item create(CreateItemRequest req, Long sellerId) {
    Item item =
        new Item(req.getName(), req.getDescription(), sellerId, req.getCategory().toUpperCase());
    mapCategoryDetail(item, req.getCategoryDetail());

    return itemDao.insert(item);
  }

  /** Lấy tất cả sản phẩm trong hệ thống. */
  public List<Item> getAll() {
    return itemDao.findAll();
  }

  /** Lấy tất cả sản phẩm của một seller cụ thể. */
  public List<Item> getBySellerId(Long sellerId) {
    return itemDao.findBySellerId(sellerId);
  }

  /** Lấy thông tin chi tiết một sản phẩm theo ID. */
  public Item getById(Long id) {
    return itemDao
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Sản phẩm #" + id + " không tồn tại"));
  }

  /** Cập nhật sản phẩm từ request DTO — dùng bởi ItemController. */
  public Item update(Long id, CreateItemRequest request, Long requesterId) {
    Item existing = getById(id);
    checkOwnership(existing, requesterId, "cập nhật");

    Item updatedItem =
        new Item(
            request.getName(),
            request.getDescription(),
            existing.getSellerId(),
            request.getCategory().toUpperCase());
    updatedItem.setId(id);
    updatedItem.setStatus(existing.getStatus());
    mapCategoryDetail(updatedItem, request.getCategoryDetail());

    itemDao.update(updatedItem);
    return updatedItem;
  }

  /** Xóa sản phẩm khỏi hệ thống. */
  public void delete(Long id, Long requesterId, String requesterRole) {
    Item existing = getById(id);

    if (!"ADMIN".equals(requesterRole)) {
      checkOwnership(existing, requesterId, "xóa");
    }

    itemDao.delete(id);
  }

  private void mapCategoryDetail(Item item, String detail) {
    if (detail == null || detail.trim().isEmpty()) {
      return;
    }

    String category = item.getCategory();
    switch (category) {
      case "ELECTRONICS" -> item.setBrand(detail);
      case "ART" -> item.setArtist(detail);
      case "VEHICLE" -> {
        try {
          int year = Integer.parseInt(detail.trim());
          if (year < 1886 || year > 2100) {
            throw new IllegalArgumentException("Năm sản xuất không hợp lệ (1886-2100)");
          }
          item.setYear(year);
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException("Năm sản xuất phải là số nguyên");
        }
      }
      default -> {
        /* category không có detail đặc thù — bỏ qua */
      }
    }
  }

  private void checkOwnership(Item item, Long requesterId, String action) {
    if (!item.getSellerId().equals(requesterId)) {
      throw new UnauthorizedException(
          "Bạn không có quyền "
              + action
              + " sản phẩm #"
              + item.getId()
              + " vì bạn không phải người tạo sản phẩm này");
    }
  }
}
