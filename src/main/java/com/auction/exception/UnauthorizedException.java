package com.auction.exception;

public class UnauthorizedException extends RuntimeException {
  public UnauthorizedException(String messenge) {
    super(messenge);
  }
}
