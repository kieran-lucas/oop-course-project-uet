package com.auction.ui.util;

import com.auction.dto.AuctionResponse;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Shared ordering for auction tables: active listings first, newest listings first within a group.
 */
public final class AuctionDisplayOrder {

  private AuctionDisplayOrder() {}

  public static List<AuctionResponse> sort(List<AuctionResponse> auctions) {
    LocalDateTime now = LocalDateTime.now();
    return auctions.stream().sorted(comparator(now)).toList();
  }

  private static Comparator<AuctionResponse> comparator(LocalDateTime now) {
    return Comparator.<AuctionResponse>comparingInt(auction -> finishedRank(auction, now))
        .thenComparing(
            AuctionResponse::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(AuctionResponse::getId, Comparator.nullsLast(Comparator.reverseOrder()));
  }

  private static int finishedRank(AuctionResponse auction, LocalDateTime now) {
    String status = auction.getStatus();
    if ("FINISHED".equals(status)
        || "CANCELED".equals(status)
        || "PAID".equals(status)
        || "SETTLING".equals(status)) {
      return 1;
    }
    LocalDateTime endTime = auction.getEndTime();
    return endTime != null && !now.isBefore(endTime) ? 1 : 0;
  }
}
