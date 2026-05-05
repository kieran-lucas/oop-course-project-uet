package com.auction.exception;

public abstract class AuctionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    protected AuctionException(String message) {
        super(message);
    }

    protected AuctionException(String message, Throwable cause) {
        super(message, cause);
    }
}
