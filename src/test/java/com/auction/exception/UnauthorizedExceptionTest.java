package com.auction.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UnauthorizedExceptionTest {

    @Test
    void shouldCarryMessage() {
        UnauthorizedException ex = new UnauthorizedException("forbidden");
        assertEquals("forbidden", ex.getMessage());
    }

    @Test
    void shouldChainCause() {
        Throwable root = new SecurityException("token expired");
        UnauthorizedException ex = new UnauthorizedException("auth failed", root);
        assertSame(root, ex.getCause());
        assertEquals("auth failed", ex.getMessage());
    }

    @Test
    void shouldBeAnAuctionException() {
        UnauthorizedException ex = new UnauthorizedException("x");
        assertTrue(ex instanceof AuctionException);
    }

    @Test
    void shouldBeARuntimeException() {
        UnauthorizedException ex = new UnauthorizedException("x");
        assertTrue(ex instanceof RuntimeException);
    }

    @Test
    void toStringShouldIncludeClassName() {
        UnauthorizedException ex = new UnauthorizedException("denied");
        assertTrue(ex.toString().contains("UnauthorizedException"));
        assertTrue(ex.toString().contains("denied"));
    }
}
