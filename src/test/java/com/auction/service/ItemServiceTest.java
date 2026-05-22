package com.auction.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.auction.dao.ItemDao;
import com.auction.dto.CreateItemRequest;
import com.auction.exception.NotFoundException;
import com.auction.exception.UnauthorizedException;
import com.auction.model.Electronics;
import com.auction.model.Item;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ItemService — item CRUD and ownership enforcement")
class ItemServiceTest {

  @Mock private ItemDao itemDao;

  private ItemService service;

  private static final Long SELLER_ID = 1L;
  private static final Long OTHER_SELLER_ID = 2L;
  private static final Long ITEM_ID = 10L;

  @BeforeEach
  void setUp() {
    service = new ItemService(itemDao);
  }

  private Item buildItem(Long sellerId) {
    Electronics item = new Electronics("Laptop Dell", "High-end laptop", sellerId, "Dell");
    item.setId(ITEM_ID);
    return item;
  }

  private CreateItemRequest buildRequest() {
    CreateItemRequest req = new CreateItemRequest();
    req.setName("Laptop Dell");
    req.setDescription("High-end laptop");
    req.setCategory("ELECTRONICS");
    req.setCategoryDetail("Dell");
    return req;
  }

  // ── create() ──────────────────────────────────────────────

  @Nested
  @DisplayName("create()")
  class CreateItem {

    @Test
    @DisplayName("inserts item and returns the persisted entity")
    void insertsAndReturnsItem() {
      Item persisted = buildItem(SELLER_ID);
      when(itemDao.insert(any(Item.class))).thenReturn(persisted);

      Item result = service.create(buildRequest(), SELLER_ID);

      assertNotNull(result);
      assertEquals(ITEM_ID, result.getId());
      verify(itemDao).insert(any(Item.class));
    }
  }

  // ── getAll() ──────────────────────────────────────────────

  @Nested
  @DisplayName("getAll()")
  class GetAll {

    @Test
    @DisplayName("delegates to itemDao.findAll()")
    void returnsAllItems() {
      List<Item> items = List.of(buildItem(SELLER_ID), buildItem(OTHER_SELLER_ID));
      when(itemDao.findAll()).thenReturn(items);

      assertEquals(2, service.getAll().size());
    }

    @Test
    @DisplayName("returns empty list when no items exist")
    void returnsEmptyList() {
      when(itemDao.findAll()).thenReturn(List.of());

      assertTrue(service.getAll().isEmpty());
    }
  }

  // ── getBySellerId() ───────────────────────────────────────

  @Nested
  @DisplayName("getBySellerId()")
  class GetBySellerId {

    @Test
    @DisplayName("returns only items belonging to the given seller")
    void returnsSellerItems() {
      when(itemDao.findBySellerId(SELLER_ID)).thenReturn(List.of(buildItem(SELLER_ID)));

      List<Item> result = service.getBySellerId(SELLER_ID);

      assertEquals(1, result.size());
      assertEquals(SELLER_ID, result.get(0).getSellerId());
    }
  }

  // ── getById() ─────────────────────────────────────────────

  @Nested
  @DisplayName("getById()")
  class GetById {

    @Test
    @DisplayName("returns the item when it exists")
    void returnsExistingItem() {
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.of(buildItem(SELLER_ID)));

      Item result = service.getById(ITEM_ID);

      assertEquals(ITEM_ID, result.getId());
    }

    @Test
    @DisplayName("throws NotFoundException when item does not exist")
    void throwsNotFoundWhenAbsent() {
      when(itemDao.findById(999L)).thenReturn(Optional.empty());

      assertThrows(NotFoundException.class, () -> service.getById(999L));
    }
  }

  // ── update() ──────────────────────────────────────────────

  @Nested
  @DisplayName("update()")
  class UpdateItem {

    @Test
    @DisplayName("owner can update their own item")
    void ownerCanUpdate() {
      Item existing = buildItem(SELLER_ID);
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.of(existing));

      Item result = service.update(ITEM_ID, buildRequest(), SELLER_ID);

      assertNotNull(result);
      verify(itemDao).update(any(Item.class));
    }

    @Test
    @DisplayName("non-owner cannot update — throws UnauthorizedException")
    void nonOwnerCannotUpdate() {
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.of(buildItem(SELLER_ID)));

      assertThrows(
          UnauthorizedException.class,
          () -> service.update(ITEM_ID, buildRequest(), OTHER_SELLER_ID));
      verify(itemDao, never()).update(any());
    }

    @Test
    @DisplayName("throws NotFoundException when item does not exist")
    void throwsNotFoundWhenAbsent() {
      when(itemDao.findById(999L)).thenReturn(Optional.empty());

      assertThrows(NotFoundException.class, () -> service.update(999L, buildRequest(), SELLER_ID));
    }
  }

  // ── delete() ──────────────────────────────────────────────

  @Nested
  @DisplayName("delete()")
  class DeleteItem {

    @Test
    @DisplayName("owner can delete their own item")
    void ownerCanDelete() {
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.of(buildItem(SELLER_ID)));

      assertDoesNotThrow(() -> service.delete(ITEM_ID, SELLER_ID, "SELLER"));
      verify(itemDao).delete(ITEM_ID);
    }

    @Test
    @DisplayName("admin can delete any item regardless of ownership")
    void adminCanDeleteAnyItem() {
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.of(buildItem(SELLER_ID)));

      assertDoesNotThrow(() -> service.delete(ITEM_ID, OTHER_SELLER_ID, "ADMIN"));
      verify(itemDao).delete(ITEM_ID);
    }

    @Test
    @DisplayName("non-owner seller cannot delete — throws UnauthorizedException")
    void nonOwnerSellerCannotDelete() {
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.of(buildItem(SELLER_ID)));

      assertThrows(
          UnauthorizedException.class, () -> service.delete(ITEM_ID, OTHER_SELLER_ID, "SELLER"));
      verify(itemDao, never()).delete(anyLong());
    }

    @Test
    @DisplayName("throws NotFoundException when item does not exist")
    void throwsNotFoundWhenAbsent() {
      when(itemDao.findById(999L)).thenReturn(Optional.empty());

      assertThrows(NotFoundException.class, () -> service.delete(999L, SELLER_ID, "SELLER"));
    }
  }
}
