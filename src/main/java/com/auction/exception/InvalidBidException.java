package com.auction.exception;

public class InvalidBidException extends RuntimeException {
  public InvalidBidException(String messenge) {
    super(messenge);
  }
}
