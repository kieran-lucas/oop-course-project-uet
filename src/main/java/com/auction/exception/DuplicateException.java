package com.auction.exception;

public class DuplicateException extends AuctionException {

    private static final long serialVersionUID = 1L;
  public DuplicateException(String message) {
    super(message);
  }

  public DuplicateException(String message, Throwable cause) {
    super(message, cause);
  }
}
