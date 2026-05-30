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

/**
 * Unit test kiểm tra các thao tác CRUD item và kiểm soát quyền sở hữu của {@link ItemService}.
 *
 * <p>Xác nhận rằng: chỉ chủ sở hữu (sellerId khớp) mới được cập nhật/xóa item; ADMIN có thể xóa bất
 * kỳ item nào; các thao tác ủy quyền đúng cho {@link ItemDao}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ItemService — CRUD item và kiểm soát quyền sở hữu")
class ItemServiceTest {

  @Mock private ItemDao itemDao;
  @Mock private AuctionService auctionService;

  private ItemService service;

  private static final Long SELLER_ID = 1L;
  private static final Long OTHER_SELLER_ID = 2L;
  private static final Long ITEM_ID = 10L;

  @BeforeEach
  void setUp() {
    service = new ItemService(itemDao, auctionService);
  }

  private Item buildItem(Long sellerId) {
    Electronics item = new Electronics("Laptop Dell", "High-end laptop", sellerId, "Dell");
    item.setId(ITEM_ID);
    return item;
  }

  private Item buildItem(Long sellerId, String status) {
    Item item = buildItem(sellerId);
    item.setStatus(status);
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
    @DisplayName("tạo item và trả về bản ghi đã được lưu")
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
    @DisplayName("ủy quyền cho itemDao.findAll()")
    void returnsAllItems() {
      List<Item> items = List.of(buildItem(SELLER_ID), buildItem(OTHER_SELLER_ID));
      when(itemDao.findAll()).thenReturn(items);

      assertEquals(2, service.getAll().size());
    }

    @Test
    @DisplayName("trả về danh sách rỗng khi không có item")
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
    @DisplayName("trả về chỉ item của seller được chỉ định")
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
    @DisplayName("trả về item khi tồn tại")
    void returnsExistingItem() {
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.of(buildItem(SELLER_ID)));

      Item result = service.getById(ITEM_ID);

      assertEquals(ITEM_ID, result.getId());
    }

    @Test
    @DisplayName("ném NotFoundException khi item không tồn tại")
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
    @DisplayName("chủ sở hữu được cập nhật item của mình")
    void ownerCanUpdate() {
      Item existing = buildItem(SELLER_ID);
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.of(existing));

      Item result = service.update(ITEM_ID, buildRequest(), SELLER_ID);

      assertNotNull(result);
      verify(auctionService).updateItemWithoutBids(any(Item.class));
    }

    @Test
    @DisplayName("người không sở hữu không được cập nhật — ném UnauthorizedException")
    void nonOwnerCannotUpdate() {
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.of(buildItem(SELLER_ID)));

      assertThrows(
          UnauthorizedException.class,
          () -> service.update(ITEM_ID, buildRequest(), OTHER_SELLER_ID));
      verify(itemDao, never()).update(any());
    }

    @Test
    @DisplayName("không được cập nhật item đang trong phiên đấu giá")
    void cannotUpdateItemInAuction() {
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.of(buildItem(SELLER_ID, "IN_AUCTION")));
      doThrow(new IllegalStateException("auction already has bids"))
          .when(auctionService)
          .ensureItemCanBeModified(ITEM_ID);

      assertThrows(
          IllegalStateException.class, () -> service.update(ITEM_ID, buildRequest(), SELLER_ID));
      verify(itemDao, never()).update(any());
    }

    @Test
    @DisplayName("được cập nhật item đang đấu giá khi chưa có bid")
    void canUpdateItemInAuctionWithoutBids() {
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.of(buildItem(SELLER_ID, "IN_AUCTION")));

      assertDoesNotThrow(() -> service.update(ITEM_ID, buildRequest(), SELLER_ID));
      verify(auctionService).ensureItemCanBeModified(ITEM_ID);
      verify(auctionService).updateItemWithoutBids(any(Item.class));
    }

    @Test
    @DisplayName("không được cập nhật item đã bán")
    void cannotUpdateSoldItem() {
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.of(buildItem(SELLER_ID, "SOLD")));

      assertThrows(
          IllegalStateException.class, () -> service.update(ITEM_ID, buildRequest(), SELLER_ID));
      verify(itemDao, never()).update(any());
    }

    @Test
    @DisplayName("không được cập nhật item đã bị xóa mềm")
    void cannotUpdateRemovedItem() {
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.of(buildItem(SELLER_ID, "REMOVED")));

      assertThrows(
          IllegalStateException.class, () -> service.update(ITEM_ID, buildRequest(), SELLER_ID));
      verify(itemDao, never()).update(any());
    }

    @Test
    @DisplayName("ném NotFoundException khi item không tồn tại")
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
    @DisplayName("chủ sở hữu được xóa item của mình")
    void ownerCanDelete() {
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.of(buildItem(SELLER_ID)));

      assertDoesNotThrow(() -> service.delete(ITEM_ID, SELLER_ID, "SELLER"));
      verify(auctionService).removeItemWithoutBids(ITEM_ID, SELLER_ID, "SELLER");
    }

    @Test
    @DisplayName("admin được xóa bất kỳ item nào không phân biệt chủ sở hữu")
    void adminCanDeleteAnyItem() {
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.of(buildItem(SELLER_ID)));

      assertDoesNotThrow(() -> service.delete(ITEM_ID, OTHER_SELLER_ID, "ADMIN"));
      verify(auctionService).removeItemWithoutBids(ITEM_ID, OTHER_SELLER_ID, "ADMIN");
    }

    @Test
    @DisplayName("seller không sở hữu không được xóa — ném UnauthorizedException")
    void nonOwnerSellerCannotDelete() {
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.of(buildItem(SELLER_ID)));

      assertThrows(
          UnauthorizedException.class, () -> service.delete(ITEM_ID, OTHER_SELLER_ID, "SELLER"));
      verify(itemDao, never()).delete(anyLong());
    }

    @Test
    @DisplayName("không được xóa item đang trong phiên đấu giá")
    void cannotDeleteItemInAuction() {
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.of(buildItem(SELLER_ID, "IN_AUCTION")));
      doThrow(new IllegalStateException("auction already has bids"))
          .when(auctionService)
          .ensureItemCanBeModified(ITEM_ID);

      assertThrows(IllegalStateException.class, () -> service.delete(ITEM_ID, SELLER_ID, "SELLER"));
      verify(itemDao, never()).delete(anyLong());
    }

    @Test
    @DisplayName("được xóa item đang đấu giá khi chưa có bid và hủy phiên trước")
    void canDeleteItemInAuctionWithoutBids() {
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.of(buildItem(SELLER_ID, "IN_AUCTION")));

      assertDoesNotThrow(() -> service.delete(ITEM_ID, SELLER_ID, "SELLER"));
      verify(auctionService).ensureItemCanBeModified(ITEM_ID);
      verify(auctionService).removeItemWithoutBids(ITEM_ID, SELLER_ID, "SELLER");
    }

    @Test
    @DisplayName("không được xóa item đã bán")
    void cannotDeleteSoldItem() {
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.of(buildItem(SELLER_ID, "SOLD")));

      assertThrows(IllegalStateException.class, () -> service.delete(ITEM_ID, SELLER_ID, "SELLER"));
      verify(itemDao, never()).delete(anyLong());
    }

    @Test
    @DisplayName("không được xóa item đã bị xóa mềm")
    void cannotDeleteRemovedItem() {
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.of(buildItem(SELLER_ID, "REMOVED")));

      assertThrows(IllegalStateException.class, () -> service.delete(ITEM_ID, SELLER_ID, "SELLER"));
      verify(itemDao, never()).delete(anyLong());
    }

    @Test
    @DisplayName("ném NotFoundException khi item không tồn tại")
    void throwsNotFoundWhenAbsent() {
      when(itemDao.findById(999L)).thenReturn(Optional.empty());

      assertThrows(NotFoundException.class, () -> service.delete(999L, SELLER_ID, "SELLER"));
    }
  }
}
