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
        
        // Tạo dữ liệu test với timestamp để unique
        String timestamp = String.valueOf(System.currentTimeMillis());
        
        testSeller = userDao.insert(new Seller(
            "bid_tx_seller_" + timestamp, 
            "hash", 
            "bid_tx_seller_" + timestamp + "@test.com"
        ));
        
        testBidder = userDao.insert(new Bidder(
            "bid_tx_bidder_" + timestamp, 
            "hash", 
            "bid_tx_bidder_" + timestamp + "@test.com"
        ));
        
        testItem = itemDao.insert(new Electronics(
            "Bid Tx Item " + timestamp, 
            "Test", 
            testSeller.getId(), 
            "Brand"
        ));
        
        testAuction = auctionDao.insert(new Auction(
            testItem.getId(),
            new BigDecimal("100000"),
            LocalDateTime.now(),
            LocalDateTime.now().plusHours(24)
        ));
        
        System.out.println("Created test seller id: " + testSeller.getId());
        System.out.println("Created test bidder id: " + testBidder.getId());
        System.out.println("Created test item id: " + testItem.getId());
        System.out.println("Created test auction id: " + testAuction.getId());
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
        assertEquals(0, new BigDecimal("150000").compareTo(saved.getAmount()));
        assertFalse(saved.isAutoBid());
        assertNotNull(saved.getCreatedAt());
    }
    
    @Test
    @DisplayName("FindByAuctionId should return all bids for an auction")
    void testFindByAuctionId() {
        bidDao.insert(new BidTransaction(testAuction.getId(), testBidder.getId(), new BigDecimal("150000"), false));
        bidDao.insert(new BidTransaction(testAuction.getId(), testBidder.getId(), new BigDecimal("200000"), true));
        
        List<BidTransaction> bids = bidDao.findByAuctionId(testAuction.getId());
        
        assertEquals(2, bids.size());
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
        
        // Đợi 1ms để đảm bảo thời gian khác nhau
        try { Thread.sleep(1); } catch (InterruptedException e) {}
        
        BidTransaction last = bidDao.insert(new BidTransaction(testAuction.getId(), testBidder.getId(), new BigDecimal("250000"), false));
        
        Optional<BidTransaction> found = bidDao.findLastBid(testAuction.getId());
        
        assertTrue(found.isPresent());
        assertEquals(last.getId(), found.get().getId());
        assertEquals(0, new BigDecimal("250000").compareTo(found.get().getAmount()));
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
        assertEquals(0, new BigDecimal("350000").compareTo(highest.get()));
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
        
        // Kiểm tra bằng cách lấy tất cả bids của auction
        List<BidTransaction> bids = bidDao.findByAuctionId(testAuction.getId());
        assertTrue(bids.stream().anyMatch(b -> b.getId().equals(saved.getId()) && b.isAutoBid()));
    }
}