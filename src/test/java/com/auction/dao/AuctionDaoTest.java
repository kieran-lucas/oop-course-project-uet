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

class AuctionDaoTest {
    
    private static Jdbi jdbi;
    private static UserDao userDao;
    private static ItemDao itemDao;
    private static AuctionDao auctionDao;
    private static User testSeller;
    private static User testBidder;
    private static Item testItem;
    
    @BeforeAll
    static void setup() {
        jdbi = DatabaseConfig.create();
        userDao = new UserDao(jdbi);
        itemDao = new ItemDao(jdbi);
        auctionDao = new AuctionDao(jdbi);
        
        // Tạo seller với username/email unique
        String timestamp = String.valueOf(System.currentTimeMillis());
        testSeller = userDao.insert(new Seller(
            "auction_seller_" + timestamp, 
            "hash", 
            "auction_seller_" + timestamp + "@test.com"
        ));
        
        // Tạo bidder để dùng cho test update
        testBidder = userDao.insert(new Bidder(
            "auction_bidder_" + timestamp,
            "hash",
            "auction_bidder_" + timestamp + "@test.com"
        ));
        
        // Tạo item cho test
        testItem = itemDao.insert(new Electronics(
            "Auction Item " + timestamp, 
            "For auction", 
            testSeller.getId(), 
            "Brand"
        ));
        
        System.out.println("Created test seller with id: " + testSeller.getId());
        System.out.println("Created test bidder with id: " + testBidder.getId());
        System.out.println("Created test item with id: " + testItem.getId());
    }
    
    @BeforeEach
    void cleanup() {
        // Xóa auctions cũ
        jdbi.useHandle(handle -> 
            handle.createUpdate("DELETE FROM auctions WHERE item_id = :itemId")
                .bind("itemId", testItem.getId())
                .execute()
        );
    }
    
    @Test
    @DisplayName("Insert should create new auction")
    void testInsert() {
        Auction auction = new Auction(
            testItem.getId(),
            new BigDecimal("100000"),
            LocalDateTime.now(),
            LocalDateTime.now().plusHours(24)
        );
        
        Auction saved = auctionDao.insert(auction);
        
        assertNotNull(saved.getId());
        assertEquals("OPEN", saved.getStatus());
        assertEquals(new BigDecimal("100000"), saved.getCurrentPrice());
        assertNull(saved.getLeadingBidderId());
    }
    
   @Test
    @DisplayName("FindById should return auction")
    void testFindById() {
    LocalDateTime startTime = LocalDateTime.now();
    LocalDateTime endTime = startTime.plusHours(24);
    
    Auction auction = new Auction(
        testItem.getId(),
        new BigDecimal("200000"),
        startTime,
        endTime
    );
    Auction saved = auctionDao.insert(auction);
    
    Optional<Auction> found = auctionDao.findById(saved.getId());
    
    assertTrue(found.isPresent());
    assertEquals(saved.getId(), found.get().getId());
    // So sánh BigDecimal đúng cách
    assertEquals(0, new BigDecimal("200000").compareTo(found.get().getStartingPrice()));
}
    
    @Test
    @DisplayName("FindAll should return all auctions")
    void testFindAll() {
        auctionDao.insert(new Auction(testItem.getId(), new BigDecimal("100"), LocalDateTime.now(), LocalDateTime.now().plusHours(1)));
        auctionDao.insert(new Auction(testItem.getId(), new BigDecimal("200"), LocalDateTime.now(), LocalDateTime.now().plusHours(2)));
        
        List<Auction> auctions = auctionDao.findAll();
        
        assertTrue(auctions.size() >= 2);
    }
    
    @Test
    @DisplayName("FindByStatus should filter by status")
    void testFindByStatus() {
        Auction open = new Auction(testItem.getId(), new BigDecimal("100"), LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        open.setStatus("OPEN");
        auctionDao.insert(open);
        
        List<Auction> openAuctions = auctionDao.findByStatus("OPEN");
        
        assertTrue(openAuctions.stream().allMatch(a -> "OPEN".equals(a.getStatus())));
    }
    
    @Test
    @DisplayName("FindByItemId should return auctions for specific item")
    void testFindByItemId() {
        auctionDao.insert(new Auction(testItem.getId(), new BigDecimal("100"), LocalDateTime.now(), LocalDateTime.now().plusHours(1)));
        
        List<Auction> auctions = auctionDao.findByItemId(testItem.getId());
        
        assertEquals(1, auctions.size());
        assertEquals(testItem.getId(), auctions.get(0).getItemId());
    }
    
    @Test
    @DisplayName("Update should modify auction")
    void testUpdate() {
        Auction auction = new Auction(
            testItem.getId(),
            new BigDecimal("100000"),
            LocalDateTime.now(),
            LocalDateTime.now().plusHours(24)
        );
        Auction saved = auctionDao.insert(auction);
        
        saved.setCurrentPrice(new BigDecimal("150000"));
        saved.setLeadingBidderId(testBidder.getId()); // Dùng bidder thực tế
        saved.setStatus("RUNNING");
        
        boolean updated = auctionDao.update(saved);
        
        assertTrue(updated);
        
        Optional<Auction> found = auctionDao.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals(new BigDecimal("150000"), found.get().getCurrentPrice());
        assertEquals(testBidder.getId(), found.get().getLeadingBidderId());
        assertEquals("RUNNING", found.get().getStatus());
    }
    
    @Test
    @DisplayName("Delete should remove auction")
    void testDelete() {
        Auction auction = new Auction(
            testItem.getId(),
            new BigDecimal("100000"),
            LocalDateTime.now(),
            LocalDateTime.now().plusHours(24)
        );
        Auction saved = auctionDao.insert(auction);
        
        boolean deleted = auctionDao.delete(saved.getId());
        
        assertTrue(deleted);
        Optional<Auction> found = auctionDao.findById(saved.getId());
        assertFalse(found.isPresent());
    }
    
    @Test
    @DisplayName("StartScheduledAuctions should update status from OPEN to RUNNING")
    void testStartScheduledAuctions() {
        // Tạo auction với start_time trong quá khứ
        Auction auction = new Auction(
            testItem.getId(),
            new BigDecimal("100000"),
            LocalDateTime.now().minusHours(1),
            LocalDateTime.now().plusHours(23)
        );
        auction.setStatus("OPEN");
        auctionDao.insert(auction);
        
        int started = auctionDao.startScheduledAuctions();
        
        assertTrue(started >= 1);
        
        // Kiểm tra auction đã chuyển sang RUNNING
        List<Auction> running = auctionDao.findByStatus("RUNNING");
        assertTrue(running.stream().anyMatch(a -> a.getItemId().equals(testItem.getId())));
    }
    
    @Test
    @DisplayName("CloseExpiredAuctions should update status from RUNNING to FINISHED")
    void testCloseExpiredAuctions() {
        // Tạo auction với end_time trong quá khứ
        Auction auction = new Auction(
            testItem.getId(),
            new BigDecimal("100000"),
            LocalDateTime.now().minusHours(2),
            LocalDateTime.now().minusHours(1)
        );
        auction.setStatus("RUNNING");
        auctionDao.insert(auction);
        
        int closed = auctionDao.closeExpiredAuctions();
        
        assertTrue(closed >= 1);
        
        // Kiểm tra auction đã chuyển sang FINISHED
        List<Auction> finished = auctionDao.findByStatus("FINISHED");
        assertTrue(finished.stream().anyMatch(a -> a.getItemId().equals(testItem.getId())));
    }
    
    @Test
    @DisplayName("ExistsById should return true for existing auction")
    void testExistsById() {
        Auction auction = new Auction(
            testItem.getId(),
            new BigDecimal("100000"),
            LocalDateTime.now(),
            LocalDateTime.now().plusHours(24)
        );
        Auction saved = auctionDao.insert(auction);
        
        assertTrue(auctionDao.existsById(saved.getId()));
        assertFalse(auctionDao.existsById(99999L));
    }
    
    @Test
    @DisplayName("GetCurrentPrice should return correct price")
    void testGetCurrentPrice() {
        Auction auction = new Auction(
            testItem.getId(),
            new BigDecimal("500000"),
            LocalDateTime.now(),
            LocalDateTime.now().plusHours(24)
        );
        Auction saved = auctionDao.insert(auction);
        
        Optional<BigDecimal> price = auctionDao.getCurrentPrice(saved.getId());
        
        assertTrue(price.isPresent());
        assertEquals(new BigDecimal("500000"), price.get());
    }
}