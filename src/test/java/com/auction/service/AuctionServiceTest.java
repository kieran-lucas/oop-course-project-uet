package com.auction.service;

import com.auction.exception.AuctionClosedException;
import com.auction.model.Auction; // Giả định package chứa Model
import com.auction.dao.AuctionDao; // Giả định package chứa DAO
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AuctionServiceTest {

    // Tạo một DAO "giả" để không đụng vào Database thật
    @Mock
    private AuctionDao auctionDao;

    // Bơm cái DAO giả đó vào Service thật để test
    @InjectMocks
    private AuctionService auctionService;

    private Auction testAuction;

    @BeforeEach
    void setUp() {
        // Khởi tạo các Mock
        MockitoAnnotations.openMocks(this);
        
        // Chuẩn bị dữ liệu mẫu trước mỗi bài test
        testAuction = new Auction();
        testAuction.setId(1L);
        testAuction.setItemId(100L);
        testAuction.setSellerId(10L);
        testAuction.setStatus("OPEN"); // Trạng thái mặc định ban đầu
    }

    @Test
    void testCreateAuction() {
        // Hành động: Gọi hàm tạo phiên đấu giá (giả định các tham số đầu vào)
        Auction createdAuction = auctionService.create(100L, 10L, new BigDecimal("100000"));
        
        // Xác nhận (Verify): Trạng thái phải là OPEN
        assertNotNull(createdAuction);
        assertEquals("OPEN", createdAuction.getStatus(), "Phiên đấu giá mới tạo phải có trạng thái OPEN");
    }

    @Test
    void testEditOpenAuction() {
        // Chuẩn bị: Phiên đang ở trạng thái OPEN
        testAuction.setStatus("OPEN");
        when(auctionDao.findById(1L)).thenReturn(testAuction);

        // Hành động & Xác nhận: Cho phép sửa thành công, không ném ra lỗi
        assertDoesNotThrow(() -> {
            auctionService.update(1L, "New Description");
        });
    }

    @Test
    void testEditRunningAuction() {
        // Chuẩn bị: Phiên đang diễn ra (RUNNING)
        testAuction.setStatus("RUNNING");
        when(auctionDao.findById(1L)).thenReturn(testAuction);

        // Xác nhận: Bắt buộc phải ném ra AuctionClosedException khi cố tình sửa
        AuctionClosedException exception = assertThrows(AuctionClosedException.class, () -> {
            auctionService.update(1L, "Try to change description");
        });
        
        // Kiểm tra đúng câu báo lỗi hệ thống yêu cầu
        assertTrue(exception.getMessage().contains("Không thể sửa khi đang diễn ra"));
    }

    @Test
    void testBidOpenAuction() {
        // Chuẩn bị: Phiên chưa bắt đầu (chỉ mới OPEN)
        testAuction.setStatus("OPEN");
        when(auctionDao.findById(1L)).thenReturn(testAuction);

        // Xác nhận: Bắt buộc ném lỗi từ chối lượt bid
        AuctionClosedException exception = assertThrows(AuctionClosedException.class, () -> {
            // Giả định hàm bid nhận: auctionId, bidderId, bidAmount
            auctionService.placeBid(1L, 99L, new BigDecimal("150000")); 
        });
        assertTrue(exception.getMessage().contains("Phiên chưa bắt đầu"));
    }

    @Test
    void testBidFinishedAuction() {
        // Chuẩn bị: Phiên đã kết thúc (FINISHED)
        testAuction.setStatus("FINISHED");
        when(auctionDao.findById(1L)).thenReturn(testAuction);

        // Xác nhận: Bắt buộc ném lỗi
        assertThrows(AuctionClosedException.class, () -> {
            auctionService.placeBid(1L, 99L, new BigDecimal("150000"));
        });
    }

    @Test
    void testStateTransitions() {
        // Kiểm tra luồng: OPEN -> RUNNING -> FINISHED
        testAuction.setStatus("OPEN");
        
        // 1. Chuyển sang RUNNING
        auctionService.changeStatus(testAuction, "RUNNING");
        assertEquals("RUNNING", testAuction.getStatus());
        
        // 2. Chuyển sang FINISHED
        auctionService.changeStatus(testAuction, "FINISHED");
        assertEquals("FINISHED", testAuction.getStatus());
    }
}