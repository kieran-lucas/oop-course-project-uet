package com.auction.dao;

import com.auction.config.DatabaseConfig;
import com.auction.model.*;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BidTransactionDaoTest {
    
    private static Jdbi jdbi;
    private static UserDao userDao;
    private static ItemDao itemDao;
    private static AuctionDao auctionDao;
    private static BidTransactionDao bidDao;
    private static User testSeller;
    private static User testBidder;
    private static Item testItem;
    private static Auction testAuction;
    
    @BeforeAll
    static void setup() {
        jdbi = DatabaseConfig.create();
        userDao = new UserDao(jdbi);
        itemDao = new ItemDao(jdbi);
        auctionDao = new AuctionDao(jdbi);
        bidDao = new BidTransactionDao(jdbi);
        
        // Tạo dữ liệu test
        testSeller = userDao.insert(new Seller("bid_tx_seller", "hash", "bid_tx_seller@test.com"));
        testBidder = userDao.insert(new Bidder("bid_tx_bidder", "hash", "bid_tx_bidder@test.com"));
        testItem = itemDao.insert(new Electronics("Bid Tx Item", "Test", testSeller.getId(), "Brand"));
        testAuction = auctionDao.insert(new Auction(
            testItem.getId(),
            new BigDecimal("100000"),
            LocalDateTime.now(),
            LocalDateTime.now().plusHours(24)
        ));
    }
    
    @BeforeEach
    void cleanup() {
        // Xóa bid transactions cũ
        bidDao.deleteByAuctionId(testAuction.getId());
    }
    
    @Test
    @DisplayName("Insert should create bid transaction")
    void testInsert() {
        BidTransaction tx = new BidTransaction(
            testAuction.getId(),
            testBidder.getId(),
            new BigDecimal("150000"),
            false
        );
        
        BidTransaction saved = bidDao.insert(tx);
        
        assertNotNull(saved.getId());
        assertEquals(testAuction.getId(), saved.getAuctionId());
        assertEquals(testBidder.getId(), saved.getBidderId());
        assertEquals(new BigDecimal("150000"), saved.getAmount());
        assertFalse(saved.isAutoBid());
        assertNotNull(saved.getCreatedAt());
    }
    
    @Test
    @DisplayName("FindByAuctionId should return all bids for an auction")
    void testFindByAuctionId() {
        bidDao.insert(new BidTransaction(testAuction.getId(), testBidder.getId(), new BigDecimal("150000"), false));
        bidDao.insert(new BidTransaction(testAuction.getId(), testBidder.getId(), new BigDecimal("200000"), true));
        bidDao.insert(new BidTransaction(testAuction.getId(), testBidder.getId(), new BigDecimal("250000"), false));
        
        List<BidTransaction> bids = bidDao.findByAuctionId(testAuction.getId());
        
        assertEquals(3, bids.size());
        // Kiểm tra sắp xếp theo thời gian tăng dần
        assertTrue(bids.get(0).getCreatedAt().isBefore(bids.get(1).getCreatedAt()) ||
                   bids.get(0).getCreatedAt().equals(bids.get(1).getCreatedAt()));
    }
    
    @Test
    @DisplayName("FindByBidderId should return all bids by a bidder")
    void testFindByBidderId() {
        bidDao.insert(new BidTransaction(testAuction.getId(), testBidder.getId(), new BigDecimal("150000"), false));
        bidDao.insert(new BidTransaction(testAuction.getId(), testBidder.getId(), new BigDecimal("200000"), false));
        
        List<BidTransaction> bids = bidDao.findByBidderId(testBidder.getId());
        
        assertTrue(bids.size() >= 2);
        assertTrue(bids.stream().allMatch(b -> b.getBidderId().equals(testBidder.getId())));
    }
    
    @Test
    @DisplayName("FindLastBid should return the most recent bid")
    void testFindLastBid() {
        bidDao.insert(new BidTransaction(testAuction.getId(), testBidder.getId(), new BigDecimal("150000"), false));
        bidDao.insert(new BidTransaction(testAuction.getId(), testBidder.getId(), new BigDecimal("200000"), false));
        
        // Đợi 1ms để đảm bảo thời gian khác nhau
        try { Thread.sleep(1); } catch (InterruptedException e) {}
        
        BidTransaction last = bidDao.insert(new BidTransaction(testAuction.getId(), testBidder.getId(), new BigDecimal("250000"), false));
        
        Optional<BidTransaction> found = bidDao.findLastBid(testAuction.getId());
        
        assertTrue(found.isPresent());
        assertEquals(last.getId(), found.get().getId());
        assertEquals(new BigDecimal("250000"), found.get().getAmount());
    }
    
    @Test
    @DisplayName("CountByAuctionId should return correct count")
    void testCountByAuctionId() {
        bidDao.insert(new BidTransaction(testAuction.getId(), testBidder.getId(), new BigDecimal("150000"), false));
        bidDao.insert(new BidTransaction(testAuction.getId(), testBidder.getId(), new BigDecimal("200000"), false));
        
        int count = bidDao.countByAuctionId(testAuction.getId());
        
        assertEquals(2, count);
    }
    
    @Test
    @DisplayName("GetHighestPrice should return max bid amount")
    void testGetHighestPrice() {
        bidDao.insert(new BidTransaction(testAuction.getId(), testBidder.getId(), new BigDecimal("150000"), false));
        bidDao.insert(new BidTransaction(testAuction.getId(), testBidder.getId(), new BigDecimal("350000"), false));
        bidDao.insert(new BidTransaction(testAuction.getId(), testBidder.getId(), new BigDecimal("250000"), false));
        
        Optional<BigDecimal> highest = bidDao.getHighestPrice(testAuction.getId());
        
        assertTrue(highest.isPresent());
        assertEquals(new BigDecimal("350000"), highest.get());
    }
    
    @Test
    @DisplayName("Insert with autoBid flag should work")
    void testInsertAutoBid() {
        BidTransaction tx = new BidTransaction(
            testAuction.getId(),
            testBidder.getId(),
            new BigDecimal("180000"),
            true
        );
        
        BidTransaction saved = bidDao.insert(tx);
        
        assertTrue(saved.isAutoBid());
        
        Optional<BidTransaction> found = bidDao.findById(saved.getId());
        assertTrue(found.isPresent());
        assertTrue(found.get().isAutoBid());
    }
}