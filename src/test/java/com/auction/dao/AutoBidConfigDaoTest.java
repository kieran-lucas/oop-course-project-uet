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

class AutoBidConfigDaoTest {
   
    private static Jdbi jdbi;
    private static UserDao userDao;
    private static ItemDao itemDao;
    private static AuctionDao auctionDao;
    private static AutoBidConfigDao autoBidDao;
   
    private User testSeller;
    private User testBidder1;
    private User testBidder2;
    private Item testItem;
    private Auction testAuction;
   
    @BeforeAll
    static void setup() {
        jdbi = DatabaseConfig.create();
        userDao = new UserDao(jdbi);
        itemDao = new ItemDao(jdbi);
        auctionDao = new AuctionDao(jdbi);
        autoBidDao = new AutoBidConfigDao(jdbi);
    }
   
    @BeforeEach
    void init() {
        // 1. Dọn dẹp DB và Reset ID về 1 trước mỗi test case
        jdbi.useHandle(handle -> {
            handle.execute("TRUNCATE TABLE auto_bid_configs CASCADE");
            handle.execute("TRUNCATE TABLE bid_transactions CASCADE");
            handle.execute("TRUNCATE TABLE auctions CASCADE");
            handle.execute("TRUNCATE TABLE items CASCADE");
            handle.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
        });

        // 2. Khởi tạo dữ liệu mẫu cố định
        testSeller = userDao.insert(new Seller("auto_seller", "hash", "seller@test.com"));
        testBidder1 = userDao.insert(new Bidder("auto_bidder1", "hash", "bidder1@test.com"));
        testBidder2 = userDao.insert(new Bidder("auto_bidder2", "hash", "bidder2@test.com"));
       
        testItem = itemDao.insert(new Electronics("Auto Item", "Test", testSeller.getId(), "Brand"));
       
        testAuction = auctionDao.insert(new Auction(
            testItem.getId(),
            new BigDecimal("100000"),
            LocalDateTime.now(),
            LocalDateTime.now().plusHours(24)
        ));
       
        System.out.println("Cleaned DB & Initialized IDs -> Seller: " + testSeller.getId() + ", Auction: " + testAuction.getId());
    }
   
    @Test
    @DisplayName("Insert should create auto-bid config")
    void testInsert() {
        AutoBidConfig config = new AutoBidConfig(
            testAuction.getId(),
            testBidder1.getId(),
            new BigDecimal("1000000"),
            new BigDecimal("50000")
        );
       
        AutoBidConfig saved = autoBidDao.insert(config);
       
        assertNotNull(saved.getId());
        assertEquals(testAuction.getId(), saved.getAuctionId());
        assertEquals(testBidder1.getId(), saved.getBidderId());
        assertEquals(0, new BigDecimal("1000000").compareTo(saved.getMaxBid()));
        assertEquals(0, new BigDecimal("50000").compareTo(saved.getIncrement()));
        assertTrue(saved.isActive());
        assertNotNull(saved.getRegisteredAt());
    }
   
    @Test
    @DisplayName("FindById should return config")
    void testFindById() {
        AutoBidConfig config = new AutoBidConfig(
            testAuction.getId(),
            testBidder1.getId(),
            new BigDecimal("2000000"),
            new BigDecimal("100000")
        );
        AutoBidConfig saved = autoBidDao.insert(config);
       
        Optional<AutoBidConfig> found = autoBidDao.findById(saved.getId());
       
        assertTrue(found.isPresent());
        assertEquals(saved.getId(), found.get().getId());
        assertEquals(0, new BigDecimal("2000000").compareTo(found.get().getMaxBid()));
    }
   
    @Test
    @DisplayName("FindByAuctionAndBidder should return config for specific user")
    void testFindByAuctionAndBidder() {
        AutoBidConfig config = new AutoBidConfig(
            testAuction.getId(),
            testBidder1.getId(),
            new BigDecimal("3000000"),
            new BigDecimal("150000")
        );
        autoBidDao.insert(config);
       
        Optional<AutoBidConfig> found = autoBidDao.findByAuctionAndBidder(
            testAuction.getId(), testBidder1.getId());
       
        assertTrue(found.isPresent());
        assertEquals(testBidder1.getId(), found.get().getBidderId());
        assertEquals(0, new BigDecimal("3000000").compareTo(found.get().getMaxBid()));
    }
   
    @Test
    @DisplayName("FindActiveByAuctionId should return only active configs")
    void testFindActiveByAuctionId() {
        AutoBidConfig config1 = new AutoBidConfig(
            testAuction.getId(), testBidder1.getId(), 
            BigDecimal.valueOf(1_000_000L), BigDecimal.valueOf(50_000L)
        );
        autoBidDao.insert(config1);
       
        AutoBidConfig config2 = new AutoBidConfig(
            testAuction.getId(), testBidder2.getId(), 
            BigDecimal.valueOf(2_000_000L), BigDecimal.valueOf(100_000L)
        );
        AutoBidConfig saved2 = autoBidDao.insert(config2);
       
        // Vô hiệu hóa config2
        saved2.setActive(false);
        autoBidDao.update(saved2);
       
        List<AutoBidConfig> active = autoBidDao.findActiveByAuctionId(testAuction.getId());
       
        assertEquals(1, active.size());
        assertEquals(testBidder1.getId(), active.get(0).getBidderId());
    }
   
    @Test
    @DisplayName("FindByBidderId should return all configs of a bidder")
    void testFindByBidderId() {
        autoBidDao.insert(new AutoBidConfig(
            testAuction.getId(), testBidder1.getId(), 
            BigDecimal.valueOf(1_000_000L), BigDecimal.valueOf(50_000L)
        ));
       
        List<AutoBidConfig> configs = autoBidDao.findByBidderId(testBidder1.getId());
       
        assertEquals(1, configs.size());
        assertEquals(testBidder1.getId(), configs.get(0).getBidderId());
    }
   
    @Test
    @DisplayName("Update should modify config")
    void testUpdate() {
        AutoBidConfig config = new AutoBidConfig(
            testAuction.getId(), testBidder1.getId(), 
            BigDecimal.valueOf(1_000_000L), BigDecimal.valueOf(50_000L)
        );
        AutoBidConfig saved = autoBidDao.insert(config);
       
        saved.setMaxBid(BigDecimal.valueOf(1_500_000L));
        saved.setIncrement(BigDecimal.valueOf(75_000L));
        saved.setActive(false);
       
        boolean updated = autoBidDao.update(saved);
        assertTrue(updated);
       
        Optional<AutoBidConfig> found = autoBidDao.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals(0, BigDecimal.valueOf(1_500_000L).compareTo(found.get().getMaxBid()));
        assertFalse(found.get().isActive());
    }
   
    @Test
    @DisplayName("UpdateByAuctionAndBidder should work")
    void testUpdateByAuctionAndBidder() {
        autoBidDao.insert(new AutoBidConfig(
            testAuction.getId(), testBidder1.getId(), 
            BigDecimal.valueOf(1_000_000L), BigDecimal.valueOf(50_000L)
        ));
       
        boolean updated = autoBidDao.updateByAuctionAndBidder(
            testAuction.getId(),
            testBidder1.getId(),
            BigDecimal.valueOf(2_000_000L),
            BigDecimal.valueOf(100_000L),
            false
        );
       
        assertTrue(updated);
       
        Optional<AutoBidConfig> found = autoBidDao.findByAuctionAndBidder(testAuction.getId(), testBidder1.getId());
        assertTrue(found.isPresent());
        assertEquals(0, BigDecimal.valueOf(2_000_000L).compareTo(found.get().getMaxBid()));
        assertFalse(found.get().isActive());
    }
   
    @Test
    @DisplayName("Deactivate should set active to false")
    void testDeactivate() {
        autoBidDao.insert(new AutoBidConfig(
            testAuction.getId(), testBidder1.getId(), 
            BigDecimal.valueOf(1_000_000L), BigDecimal.valueOf(50_000L)
        ));
       
        boolean deactivated = autoBidDao.deactivate(testAuction.getId(), testBidder1.getId());
        assertTrue(deactivated);
       
        Optional<AutoBidConfig> found = autoBidDao.findByAuctionAndBidder(testAuction.getId(), testBidder1.getId());
        assertTrue(found.isPresent());
        assertFalse(found.get().isActive());
    }
   
    @Test
    @DisplayName("DeactivateAllByAuctionId should deactivate all configs for an auction")
    void testDeactivateAllByAuctionId() {
        autoBidDao.insert(new AutoBidConfig(
            testAuction.getId(), testBidder1.getId(), 
            BigDecimal.valueOf(1_000_000L), BigDecimal.valueOf(50_000L)
        ));
        autoBidDao.insert(new AutoBidConfig(
            testAuction.getId(), testBidder2.getId(), 
            BigDecimal.valueOf(2_000_000L), BigDecimal.valueOf(100_000L)
        ));
       
        int deactivated = autoBidDao.deactivateAllByAuctionId(testAuction.getId());
        assertEquals(2, deactivated);
       
        List<AutoBidConfig> active = autoBidDao.findActiveByAuctionId(testAuction.getId());
        assertEquals(0, active.size());
    }
   
    @Test
    @DisplayName("HasActiveConfig should return true if active config exists")
    void testHasActiveConfig() {
        autoBidDao.insert(new AutoBidConfig(
            testAuction.getId(), testBidder1.getId(), 
            BigDecimal.valueOf(1_000_000L), BigDecimal.valueOf(50_000L)
        ));
       
        assertTrue(autoBidDao.hasActiveConfig(testAuction.getId(), testBidder1.getId()));
        assertFalse(autoBidDao.hasActiveConfig(testAuction.getId(), testBidder2.getId()));
    }
   
    @Test
    @DisplayName("CountActiveByAuctionId should return correct count")
    void testCountActiveByAuctionId() {
        autoBidDao.insert(new AutoBidConfig(
            testAuction.getId(), testBidder1.getId(), 
            BigDecimal.valueOf(1_000_000L), BigDecimal.valueOf(50_000L)
        ));
        autoBidDao.insert(new AutoBidConfig(
            testAuction.getId(), testBidder2.getId(), 
            BigDecimal.valueOf(2_000_000L), BigDecimal.valueOf(100_000L)
        ));
       
        int count = autoBidDao.countActiveByAuctionId(testAuction.getId());
        assertEquals(2, count);
    }
}