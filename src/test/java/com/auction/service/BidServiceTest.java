package com.auction.service;

import com.auction.dao.AuctionDao;
import com.auction.dao.BidTransactionDao;
import com.auction.exception.AuctionClosedException;
import com.auction.exception.InvalidBidException;
import com.auction.model.Auction;
import com.auction.pattern.observer.AuctionEventManager;
import com.auction.pattern.strategy.ManualBidStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BidServiceTest {

    @Mock private AuctionDao auctionDao;
    @Mock private BidTransactionDao bidTransactionDao;
    @Mock private AuctionEventManager eventManager;
    @Mock private ManualBidStrategy manualBidStrategy;

    @InjectMocks private BidService bidService;

    private Auction testAuction;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Tạo một phiên đấu giá giả lập đang diễn ra (RUNNING)
        testAuction = new Auction();
        testAuction.setId(1L);
        testAuction.setSellerId(10L); // Người bán có ID là 10
        testAuction.setStatus("RUNNING");
        testAuction.setCurrentPrice(new BigDecimal("100000")); // Giá gốc 100k
    }

    @Test
    void testBidSuccess() {
        when(auctionDao.findById(1L)).thenReturn(testAuction);

        // Đặt giá 500k (Hợp lệ vì > 100k và người mua ID 99 khác người bán ID 10)
        assertDoesNotThrow(() -> {
            bidService.placeBid(1L, 99L, new BigDecimal("500000"), false);
        });
    }

    @Test
    void testBidTooLow() {
        when(auctionDao.findById(1L)).thenReturn(testAuction);
        
        // Giả lập thuật toán Strategy từ chối vì giá thấp
        doThrow(new InvalidBidException("Giá đặt phải lớn hơn giá hiện tại"))
            .when(manualBidStrategy).execute(any(), anyLong(), any(), anyBoolean());

        // Đặt giá 50k (< 100k) -> Phải ném lỗi InvalidBidException
        Exception exception = assertThrows(InvalidBidException.class, () -> {
            bidService.placeBid(1L, 99L, new BigDecimal("50000"), false);
        });
        assertTrue(exception.getMessage().contains("lớn hơn"));
    }

    @Test
    void testBidWhenFinished() {
        // Cố tình chỉnh phiên thành đã kết thúc
        testAuction.setStatus("FINISHED");
        when(auctionDao.findById(1L)).thenReturn(testAuction);

        // Bắt buộc ném lỗi, không cho bid
        assertThrows(AuctionClosedException.class, () -> {
            bidService.placeBid(1L, 99L, new BigDecimal("150000"), false);
        });
    }

    @Test
    void testBidOwnAuction() {
        when(auctionDao.findById(1L)).thenReturn(testAuction);
        
        // Giả lập thuật toán phát hiện người bán tự buff giá
        doThrow(new InvalidBidException("Không thể bid sản phẩm của mình"))
            .when(manualBidStrategy).execute(any(), eq(10L), any(), anyBoolean());

        // Người mua có ID 10 (Trùng với sellerId 10) -> Ném lỗi
        Exception exception = assertThrows(InvalidBidException.class, () -> {
            bidService.placeBid(1L, 10L, new BigDecimal("150000"), false);
        });
        assertTrue(exception.getMessage().contains("sản phẩm của mình"));
    }
}