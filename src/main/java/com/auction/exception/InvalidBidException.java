package com.auction.exception;

public class InvalidBidException extends AuctionException {

    private static final long serialVersionUID = 1L;
  public InvalidBidException(String message) {
    super(message);
  }
}
