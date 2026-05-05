package com.auction.exception;

public class UnauthorizedException extends AuctionException {

    private static final long serialVersionUID = 1L;
  public UnauthorizedException(String message) {
    super(message);
  }
}
