package com.auction.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.auction.dto.AuctionResponse;
import com.auction.dto.CreateAuctionRequest;
import com.auction.exception.UnauthorizedException;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.service.AuctionService;
import io.javalin.http.Context;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
@DisplayName("AuctionController — handler method coverage")
class AuctionControllerTest {

  @Mock(answer = Answers.RETURNS_SELF)
  private Context ctx;

  @Mock private AuctionService auctionService;

  private static final Long SELLER_ID = 1L;
  private static final Long AUCTION_ID = 10L;

  private static void invoke(String methodName, Context ctx, AuctionService svc) throws Exception {
    Method m =
        AuctionController.class.getDeclaredMethod(methodName, Context.class, AuctionService.class);
    m.setAccessible(true);
    try {
      m.invoke(null, ctx, svc);
    } catch (InvocationTargetException ite) {
      Throwable cause = ite.getCause();
      if (cause instanceof RuntimeException re) throw re;
      throw ite;
    }
  }

  private AuctionResponse buildResponse() {
    AuctionResponse r = new AuctionResponse();
    r.setId(AUCTION_ID);
    r.setStatus("OPEN");
    r.setStartingPrice(new BigDecimal("1000000"));
    r.setCurrentPrice(new BigDecimal("1000000"));
    return r;
  }

  private Auction buildAuction() {
    Auction a = new Auction();
    a.setId(AUCTION_ID);
    a.setItemId(5L);
    a.setSellerId(SELLER_ID);
    a.setStartingPrice(new BigDecimal("1000000"));
    a.setStatus(AuctionStatus.OPEN);
    return a;
  }

  private CreateAuctionRequest buildCreateRequest() {
    CreateAuctionRequest req = new CreateAuctionRequest();
    req.setItemId(5L);
    req.setStartingPrice(new BigDecimal("1000000"));
    req.setStartTime(LocalDateTime.now().plusHours(1));
    req.setEndTime(LocalDateTime.now().plusHours(4));
    return req;
  }

  // ── handleGetById() ───────────────────────────────────────

  @Nested
  @DisplayName("handleGetById()")
  class HandleGetById {

    @Test
    @DisplayName("returns enriched auction response for existing auction")
    void returnsAuctionResponse() throws Exception {
      when(ctx.pathParam("id")).thenReturn("10");
      when(auctionService.getById(AUCTION_ID)).thenReturn(buildResponse());

      invoke("handleGetById", ctx, auctionService);

      verify(auctionService).getById(AUCTION_ID);
      verify(ctx).status(200);
      verify(ctx).json(any(AuctionResponse.class));
    }
  }

  // ── handleCreate() ────────────────────────────────────────

  @Nested
  @DisplayName("handleCreate()")
  class HandleCreate {

    @Test
    @DisplayName("SELLER can create an auction")
    void sellerCanCreate() throws Exception {
      CreateAuctionRequest req = buildCreateRequest();
      when(ctx.attribute("role")).thenReturn("SELLER");
      when(ctx.attribute("userId")).thenReturn(SELLER_ID);
      when(ctx.bodyAsClass(CreateAuctionRequest.class)).thenReturn(req);
      when(auctionService.create(req, SELLER_ID)).thenReturn(buildAuction());

      invoke("handleCreate", ctx, auctionService);

      verify(auctionService).create(eq(req), eq(SELLER_ID));
      verify(ctx).status(201);
    }

    @Test
    @DisplayName("non-SELLER role throws UnauthorizedException")
    void bidderCannotCreate() throws Exception {
      when(ctx.attribute("role")).thenReturn("BIDDER");

      assertThrows(UnauthorizedException.class, () -> invoke("handleCreate", ctx, auctionService));
      verify(auctionService, never()).create(any(), any());
    }
  }

  // ── handleDelete() ────────────────────────────────────────

  @Nested
  @DisplayName("handleDelete()")
  class HandleDelete {

    @Test
    @DisplayName("SELLER can cancel their auction")
    void sellerCanDelete() throws Exception {
      when(ctx.attribute("role")).thenReturn("SELLER");
      when(ctx.attribute("userId")).thenReturn(SELLER_ID);
      when(ctx.pathParam("id")).thenReturn("10");

      invoke("handleDelete", ctx, auctionService);

      verify(auctionService).delete(AUCTION_ID, SELLER_ID, "SELLER");
      verify(ctx).status(204);
    }

    @Test
    @DisplayName("ADMIN can cancel any auction")
    void adminCanDelete() throws Exception {
      when(ctx.attribute("role")).thenReturn("ADMIN");
      when(ctx.attribute("userId")).thenReturn(99L);
      when(ctx.pathParam("id")).thenReturn("10");

      invoke("handleDelete", ctx, auctionService);

      verify(auctionService).delete(AUCTION_ID, 99L, "ADMIN");
    }

    @Test
    @DisplayName("BIDDER role throws UnauthorizedException")
    void bidderCannotDelete() throws Exception {
      when(ctx.attribute("role")).thenReturn("BIDDER");
      when(ctx.attribute("userId")).thenReturn(SELLER_ID);
      when(ctx.pathParam("id")).thenReturn("10");

      assertThrows(UnauthorizedException.class, () -> invoke("handleDelete", ctx, auctionService));
      verify(auctionService, never()).delete(anyLong(), anyLong(), anyString());
    }
  }

  // ── handleUpdate() ────────────────────────────────────────

  @Nested
  @DisplayName("handleUpdate()")
  class HandleUpdate {

    @Test
    @DisplayName("SELLER can update an OPEN auction")
    void sellerCanUpdate() throws Exception {
      CreateAuctionRequest req = buildCreateRequest();
      when(ctx.attribute("role")).thenReturn("SELLER");
      when(ctx.attribute("userId")).thenReturn(SELLER_ID);
      when(ctx.pathParam("id")).thenReturn("10");
      when(ctx.bodyAsClass(CreateAuctionRequest.class)).thenReturn(req);
      when(auctionService.update(eq(AUCTION_ID), eq(req), eq(SELLER_ID)))
          .thenReturn(buildResponse());

      invoke("handleUpdate", ctx, auctionService);

      verify(auctionService).update(eq(AUCTION_ID), eq(req), eq(SELLER_ID));
      verify(ctx).status(200);
    }

    @Test
    @DisplayName("non-SELLER role throws UnauthorizedException")
    void adminCannotUpdate() throws Exception {
      when(ctx.attribute("role")).thenReturn("ADMIN");

      assertThrows(UnauthorizedException.class, () -> invoke("handleUpdate", ctx, auctionService));
      verify(auctionService, never()).update(anyLong(), any(CreateAuctionRequest.class), anyLong());
    }
  }
}
