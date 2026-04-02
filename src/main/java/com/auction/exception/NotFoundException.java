package com.auction.exception;

public class NotFoundException extends RuntimeException {
  public NotFoundException(String messenge) {
    super(messenge);
  }
}
