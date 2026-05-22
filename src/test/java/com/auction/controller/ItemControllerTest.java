package com.auction.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.auction.dto.CreateItemRequest;
import com.auction.exception.UnauthorizedException;
import com.auction.model.Electronics;
import com.auction.model.Item;
import com.auction.service.ItemService;
import io.javalin.http.Context;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ItemController — handler method coverage via reflection")
class ItemControllerTest {

  @Mock(answer = Answers.RETURNS_SELF)
  private Context ctx;

  @Mock private ItemService itemService;

  private static final Long ITEM_ID = 10L;
  private static final Long SELLER_ID = 1L;

  private static void invoke(String methodName, Context ctx, ItemService svc) throws Exception {
    Method m = ItemController.class.getDeclaredMethod(methodName, Context.class, ItemService.class);
    m.setAccessible(true);
    try {
      m.invoke(null, ctx, svc);
    } catch (InvocationTargetException ite) {
      Throwable cause = ite.getCause();
      if (cause instanceof RuntimeException re) throw re;
      throw ite;
    }
  }

  private Item buildItem() {
    Electronics e = new Electronics("Laptop", "desc", SELLER_ID, "Dell");
    e.setId(ITEM_ID);
    return e;
  }

  private CreateItemRequest buildRequest() {
    CreateItemRequest req = new CreateItemRequest();
    req.setName("Laptop");
    req.setDescription("desc");
    req.setCategory("ELECTRONICS");
    req.setCategoryDetail("Dell");
    return req;
  }

  // ── handleGetAll() ────────────────────────────────────────

  @Nested
  @DisplayName("handleGetAll()")
  class HandleGetAll {

    @Test
    @DisplayName("returns all items when no sellerId param")
    void returnsAllItemsWithoutFilter() throws Exception {
      when(ctx.queryParam("sellerId")).thenReturn(null);
      when(itemService.getAll()).thenReturn(List.of(buildItem()));

      invoke("handleGetAll", ctx, itemService);

      verify(itemService).getAll();
      verify(ctx).status(200);
      verify(ctx).json(any(List.class));
    }

    @Test
    @DisplayName("filters by sellerId when param is present")
    void filtersBySellerId() throws Exception {
      when(ctx.queryParam("sellerId")).thenReturn("1");
      when(itemService.getBySellerId(1L)).thenReturn(List.of(buildItem()));

      invoke("handleGetAll", ctx, itemService);

      verify(itemService).getBySellerId(1L);
      verify(itemService, never()).getAll();
    }
  }

  // ── handleGetById() ───────────────────────────────────────

  @Nested
  @DisplayName("handleGetById()")
  class HandleGetById {

    @Test
    @DisplayName("returns item when found")
    void returnsFoundItem() throws Exception {
      when(ctx.pathParam("id")).thenReturn("10");
      when(itemService.getById(10L)).thenReturn(buildItem());

      invoke("handleGetById", ctx, itemService);

      verify(itemService).getById(10L);
      verify(ctx).status(200);
    }
  }

  // ── handleCreate() ────────────────────────────────────────

  @Nested
  @DisplayName("handleCreate()")
  class HandleCreate {

    @Test
    @DisplayName("SELLER role can create an item")
    void sellerCanCreateItem() throws Exception {
      when(ctx.attribute("role")).thenReturn("SELLER");
      when(ctx.attribute("userId")).thenReturn(SELLER_ID);
      when(ctx.bodyAsClass(CreateItemRequest.class)).thenReturn(buildRequest());
      when(itemService.create(any(CreateItemRequest.class), eq(SELLER_ID))).thenReturn(buildItem());

      invoke("handleCreate", ctx, itemService);

      verify(itemService).create(any(CreateItemRequest.class), eq(SELLER_ID));
      verify(ctx).status(201);
    }

    @Test
    @DisplayName("non-SELLER role throws UnauthorizedException")
    void bidderCannotCreate() throws Exception {
      when(ctx.attribute("role")).thenReturn("BIDDER");

      assertThrows(UnauthorizedException.class, () -> invoke("handleCreate", ctx, itemService));
      verify(itemService, never()).create(any(), any());
    }
  }

  // ── handleUpdate() ────────────────────────────────────────

  @Nested
  @DisplayName("handleUpdate()")
  class HandleUpdate {

    @Test
    @DisplayName("SELLER role can update their item")
    void sellerCanUpdate() throws Exception {
      when(ctx.attribute("role")).thenReturn("SELLER");
      when(ctx.attribute("userId")).thenReturn(SELLER_ID);
      when(ctx.pathParam("id")).thenReturn("10");
      when(ctx.bodyAsClass(CreateItemRequest.class)).thenReturn(buildRequest());
      when(itemService.update(eq(ITEM_ID), any(CreateItemRequest.class), eq(SELLER_ID)))
          .thenReturn(buildItem());

      invoke("handleUpdate", ctx, itemService);

      verify(itemService).update(eq(ITEM_ID), any(CreateItemRequest.class), eq(SELLER_ID));
      verify(ctx).status(200);
    }

    @Test
    @DisplayName("non-SELLER role throws UnauthorizedException")
    void adminCannotUpdate() throws Exception {
      when(ctx.attribute("role")).thenReturn("ADMIN");

      assertThrows(UnauthorizedException.class, () -> invoke("handleUpdate", ctx, itemService));
      verify(itemService, never()).update(anyLong(), any(), anyLong());
    }
  }

  // ── handleDelete() ────────────────────────────────────────

  @Nested
  @DisplayName("handleDelete()")
  class HandleDelete {

    @Test
    @DisplayName("SELLER can delete their own item")
    void sellerCanDelete() throws Exception {
      when(ctx.attribute("role")).thenReturn("SELLER");
      when(ctx.attribute("userId")).thenReturn(SELLER_ID);
      when(ctx.pathParam("id")).thenReturn("10");

      invoke("handleDelete", ctx, itemService);

      verify(itemService).delete(ITEM_ID, SELLER_ID, "SELLER");
      verify(ctx).status(204);
    }

    @Test
    @DisplayName("ADMIN can delete any item")
    void adminCanDelete() throws Exception {
      when(ctx.attribute("role")).thenReturn("ADMIN");
      when(ctx.attribute("userId")).thenReturn(99L);
      when(ctx.pathParam("id")).thenReturn("10");

      invoke("handleDelete", ctx, itemService);

      verify(itemService).delete(ITEM_ID, 99L, "ADMIN");
    }

    @Test
    @DisplayName("BIDDER role throws UnauthorizedException")
    void bidderCannotDelete() throws Exception {
      when(ctx.attribute("role")).thenReturn("BIDDER");
      when(ctx.attribute("userId")).thenReturn(SELLER_ID);
      when(ctx.pathParam("id")).thenReturn("10");

      assertThrows(UnauthorizedException.class, () -> invoke("handleDelete", ctx, itemService));
      verify(itemService, never()).delete(anyLong(), anyLong(), anyString());
    }
  }
}
