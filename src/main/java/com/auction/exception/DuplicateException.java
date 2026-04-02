package com.auction.exception;

public class DuplicateException extends RuntimeException {
  public DuplicateException(String messenge) {
    super(messenge);
  }
}
