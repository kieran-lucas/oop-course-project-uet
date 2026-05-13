package com.auction.pattern.observer;

import com.auction.controller.AuctionWebSocketHandler;
import com.auction.dto.BidUpdateMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** ConcreteObserver trong Observer Pattern — gửi sự kiện đấu giá đến client qua WebSocket. */
public class WebSocketObserver implements AuctionEventListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketObserver.class);

  private final AuctionWebSocketHandler handler;
  private final Long auctionId;

  public WebSocketObserver(AuctionWebSocketHandler handler, Long auctionId) {
    this.handler = handler;
    this.auctionId = auctionId;
  }

  @Override
  public void onBidUpdate(BidUpdateMessage msg) {
    handler.broadcast(auctionId, msg);
  }

  @Override
  public void onTimeExtended(BidUpdateMessage msg) {
    handler.broadcast(auctionId, msg);
  }

  @Override
  public void onAuctionEnd(BidUpdateMessage msg) {
    handler.broadcast(auctionId, msg);
  }

  public Long getAuctionId() {
    return auctionId;
  }
}
