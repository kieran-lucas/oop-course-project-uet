package com.auction.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.auction.dto.BidRequest;
import com.auction.exception.UnauthorizedException;
import com.auction.model.BidTransaction;
import com.auction.service.BidService;
import io.javalin.http.Context;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BidControllerTest {

  private static final Long AUCTION_ID = 10L;
  private static final Long USER_ID = 20L;
  private static final BigDecimal BID_AMOUNT = new BigDecimal("500000");

  @Mock private BidService bidService;
  @Mock private Context ctx;

  private BidController bidController;

  @BeforeEach
  void setUp() {
    bidController = new BidController(bidService);
  }

  @Test
  @DisplayName("SELLER role is rejected for manual bid endpoint")
  void sellerCannotPlaceManualBid() {
    when(ctx.attribute("role")).thenReturn("SELLER");

    assertThrows(UnauthorizedException.class, () -> bidController.handleManualBid(ctx));

    verifyNoInteractions(bidService);
  }

  @Test
  @DisplayName("ADMIN role is rejected for manual bid endpoint")
  void adminCannotPlaceManualBid() {
    when(ctx.attribute("role")).thenReturn("ADMIN");

    assertThrows(UnauthorizedException.class, () -> bidController.handleManualBid(ctx));

    verifyNoInteractions(bidService);
  }

  @Test
  @DisplayName("BIDDER role can place manual bid")
  void bidderCanPlaceManualBid() {
    BidRequest request = new BidRequest(BID_AMOUNT);
    BidTransaction bid = new BidTransaction(AUCTION_ID, USER_ID, BID_AMOUNT, false);

    when(ctx.attribute("role")).thenReturn("BIDDER");
    when(ctx.attribute("userId")).thenReturn(USER_ID);
    when(ctx.pathParam("id")).thenReturn(AUCTION_ID.toString());
    when(ctx.bodyAsClass(BidRequest.class)).thenReturn(request);
    when(bidService.placeBid(AUCTION_ID, USER_ID, BID_AMOUNT, false)).thenReturn(bid);
    when(ctx.status(201)).thenReturn(ctx);
    when(ctx.json(bid)).thenReturn(ctx);

    bidController.handleManualBid(ctx);

    verify(bidService).placeBid(AUCTION_ID, USER_ID, BID_AMOUNT, false);
    verify(ctx).status(201);
    verify(ctx).json(bid);
    verify(ctx, never()).status(401);
  }
}
