package com.auction.service;

import com.auction.dao.ItemDao;
import com.auction.dto.CreateItemRequest;
import com.auction.exception.NotFoundException;
import com.auction.exception.UnauthorizedException;
import com.auction.model.Item;
import com.auction.pattern.factory.ItemFactory;
import java.util.List;

/** Service for item business rules and persistence orchestration. */
public class ItemService {

  private final ItemDao itemDao;

  public ItemService(ItemDao itemDao) {
    this.itemDao = itemDao;
  }

  public Item create(CreateItemRequest req, Long sellerId) {
    Item item = ItemFactory.create(req, sellerId);
    return itemDao.insert(item);
  }

  public List<Item> getAll() {
    return itemDao.findAll();
  }

  public List<Item> getBySellerId(Long sellerId) {
    return itemDao.findBySellerId(sellerId);
  }

  public Item getById(Long id) {
    return itemDao
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Sản phẩm #" + id + " không tồn tại"));
  }

  public Item update(Long id, CreateItemRequest request, Long requesterId) {
    Item existing = getById(id);
    checkOwnership(existing, requesterId, "cập nhật");

    Item updatedItem = ItemFactory.create(request, existing.getSellerId());
    updatedItem.setId(id);
    updatedItem.setStatus(existing.getStatus());
    itemDao.update(updatedItem);
    return updatedItem;
  }

  public void delete(Long id, Long requesterId, String requesterRole) {
    Item existing = getById(id);

    if (!"ADMIN".equals(requesterRole)) {
      checkOwnership(existing, requesterId, "xóa");
    }

    itemDao.delete(id);
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
