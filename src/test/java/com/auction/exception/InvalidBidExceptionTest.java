package com.auction.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InvalidBidExceptionTest {

    @Test
    void shouldCarryMessage() {
        InvalidBidException ex = new InvalidBidException("bid too low");
        assertEquals("bid too low", ex.getMessage());
    }

    @Test
    void shouldChainCause() {
        Throwable root = new IllegalArgumentException("negative amount");
        InvalidBidException ex = new InvalidBidException("validation failed", root);
        assertSame(root, ex.getCause());
        assertEquals("validation failed", ex.getMessage());
    }

    @Test
    void shouldBeAnAuctionException() {
        InvalidBidException ex = new InvalidBidException("x");
        assertTrue(ex instanceof AuctionException);
    }

    @Test
    void shouldBeARuntimeException() {
        InvalidBidException ex = new InvalidBidException("x");
        assertTrue(ex instanceof RuntimeException);
    }

    @Test
    void toStringShouldIncludeClassName() {
        InvalidBidException ex = new InvalidBidException("invalid");
        assertTrue(ex.toString().contains("InvalidBidException"));
        assertTrue(ex.toString().contains("invalid"));
    }
}
