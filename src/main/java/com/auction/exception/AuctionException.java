package com.auction.exception;

public abstract class AuctionException extends RuntimeException {

    protected AuctionException(String message) {
        super(message);
    }

    protected AuctionException(String message, Throwable cause) {
        super(message, cause);
    }
}
