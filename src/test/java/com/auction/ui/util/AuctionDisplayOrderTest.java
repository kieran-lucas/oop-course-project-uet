package com.auction.ui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.auction.dto.AuctionResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuctionDisplayOrderTest {

  @Test
  void activeAuctionsComeFirstAndNewerListingsLeadEachGroup() {
    LocalDateTime now = LocalDateTime.now();
    AuctionResponse olderActive = auction(1L, "RUNNING", now.minusMinutes(4), now.plusHours(1));
    AuctionResponse newerActive = auction(2L, "OPEN", now.minusMinutes(1), now.plusHours(2));
    AuctionResponse newerFinished = auction(4L, "FINISHED", now, now.minusMinutes(1));
    AuctionResponse olderFinished = auction(3L, "CANCELED", now.minusMinutes(2), now.plusHours(3));

    List<Long> sortedIds =
        AuctionDisplayOrder.sort(List.of(olderFinished, newerFinished, olderActive, newerActive))
            .stream()
            .map(AuctionResponse::getId)
            .toList();

    assertEquals(List.of(2L, 1L, 4L, 3L), sortedIds);
  }

  @Test
  void expiredRunningAuctionIsPlacedWithFinishedAuctions() {
    LocalDateTime now = LocalDateTime.now();
    AuctionResponse expired = auction(2L, "RUNNING", now, now.minusSeconds(1));
    AuctionResponse active = auction(1L, "OPEN", now.minusMinutes(5), now.plusHours(1));

    assertEquals(List.of(active, expired), AuctionDisplayOrder.sort(List.of(expired, active)));
  }

  private AuctionResponse auction(
      Long id, String status, LocalDateTime createdAt, LocalDateTime endTime) {
    AuctionResponse auction = new AuctionResponse();
    auction.setId(id);
    auction.setStatus(status);
    auction.setCreatedAt(createdAt);
    auction.setEndTime(endTime);
    return auction;
  }
}
