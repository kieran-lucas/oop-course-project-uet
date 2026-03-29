package com.auction.dao;

import com.auction.config.DatabaseConfig;
import com.auction.model.*;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ItemDaoTest {
    
    private static Jdbi jdbi;
    private static UserDao userDao;
    private static ItemDao itemDao;
    private static User testSeller;
    
    @BeforeAll
    static void setup() {
        jdbi = DatabaseConfig.create();
        userDao = new UserDao(jdbi);
        itemDao = new ItemDao(jdbi);
        
        // Tạo seller để dùng cho test
        testSeller = userDao.insert(new Seller("seller_for_items", "hash", "seller_items@test.com"));
    }
    
    @Test
    @DisplayName("Insert Electronics should work")
    void testInsertElectronics() {
        Electronics electronics = new Electronics("iPhone 15", "New phone", testSeller.getId(), "Apple");
        
        Item saved = itemDao.insert(electronics);
        
        assertNotNull(saved.getId());
        assertEquals("ELECTRONICS", saved.getCategory());
        assertTrue(saved instanceof Electronics);
        assertEquals("Apple", ((Electronics) saved).getBrand());
    }
    
    @Test
    @DisplayName("Insert Art should work")
    void testInsertArt() {
        Art art = new Art("Mona Lisa", "Famous painting", testSeller.getId(), "Da Vinci");
        
        Item saved = itemDao.insert(art);
        
        assertNotNull(saved.getId());
        assertEquals("ART", saved.getCategory());
        assertTrue(saved instanceof Art);
        assertEquals("Da Vinci", ((Art) saved).getArtist());
    }
    
    @Test
    @DisplayName("Insert Vehicle should work")
    void testInsertVehicle() {
        Vehicle vehicle = new Vehicle("Camry", "Sedan", testSeller.getId(), 2022);
        
        Item saved = itemDao.insert(vehicle);
        
        assertNotNull(saved.getId());
        assertEquals("VEHICLE", saved.getCategory());
        assertTrue(saved instanceof Vehicle);
        assertEquals(2022, ((Vehicle) saved).getYear());
    }
    
    @Test
    @DisplayName("FindById should return correct item type")
    void testFindById() {
        Electronics electronics = new Electronics("Test Laptop", "Gaming laptop", testSeller.getId(), "Dell");
        Item saved = itemDao.insert(electronics);
        
        Optional<Item> found = itemDao.findById(saved.getId());
        
        assertTrue(found.isPresent());
        assertTrue(found.get() instanceof Electronics);
        assertEquals("Test Laptop", found.get().getName());
        assertEquals("Dell", ((Electronics) found.get()).getBrand());
    }
    
    @Test
    @DisplayName("FindBySellerId should return all items of a seller")
    void testFindBySellerId() {
        // Xóa items cũ của seller
        itemDao.deleteBySellerId(testSeller.getId());
        
        itemDao.insert(new Electronics("Laptop", "Dell", testSeller.getId(), "Dell"));
        itemDao.insert(new Art("Painting", "Art", testSeller.getId(), "Artist"));
        
        List<Item> items = itemDao.findBySellerId(testSeller.getId());
        
        assertEquals(2, items.size());
    }
    
    @Test
    @DisplayName("FindAll should return all items")
    void testFindAll() {
        List<Item> items = itemDao.findAll();
        assertNotNull(items);
    }
    
    @Test
    @DisplayName("FindByCategory should filter by category")
    void testFindByCategory() {
        // Tạo items với các category khác nhau
        itemDao.insert(new Electronics("Phone", "Smartphone", testSeller.getId(), "Apple"));
        itemDao.insert(new Art("Sculpture", "Art", testSeller.getId(), "Artist"));
        
        List<Item> electronics = itemDao.findByCategory("ELECTRONICS");
        List<Item> arts = itemDao.findByCategory("ART");
        
        assertTrue(electronics.stream().allMatch(i -> "ELECTRONICS".equals(i.getCategory())));
        assertTrue(arts.stream().allMatch(i -> "ART".equals(i.getCategory())));
    }
    
    @Test
    @DisplayName("SearchByName should find items by keyword")
    void testSearchByName() {
        itemDao.insert(new Electronics("iPhone 15 Pro", "Phone", testSeller.getId(), "Apple"));
        itemDao.insert(new Electronics("iPad Pro", "Tablet", testSeller.getId(), "Apple"));
        itemDao.insert(new Art("Mona Lisa", "Painting", testSeller.getId(), "Da Vinci"));
        
        List<Item> results = itemDao.searchByName("Pro");
        
        assertTrue(results.size() >= 2);
        assertTrue(results.stream().anyMatch(i -> i.getName().contains("Pro")));
    }
    
    @Test
    @DisplayName("Update should modify item details")
    void testUpdate() {
        Electronics electronics = new Electronics("Old Name", "Old desc", testSeller.getId(), "Old Brand");
        Item saved = itemDao.insert(electronics);
        
        saved.setName("New Name");
        saved.setDescription("New desc");
        ((Electronics) saved).setBrand("New Brand");
        
        boolean updated = itemDao.update(saved);
        
        assertTrue(updated);
        
        Optional<Item> found = itemDao.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("New Name", found.get().getName());
        assertEquals("New desc", found.get().getDescription());
        assertEquals("New Brand", ((Electronics) found.get()).getBrand());
    }
    
    @Test
    @DisplayName("Delete should remove item")
    void testDelete() {
        Electronics electronics = new Electronics("To Delete", "Temp", testSeller.getId(), "Brand");
        Item saved = itemDao.insert(electronics);
        
        boolean deleted = itemDao.delete(saved.getId());
        
        assertTrue(deleted);
        Optional<Item> found = itemDao.findById(saved.getId());
        assertFalse(found.isPresent());
    }
    
    @Test
    @DisplayName("BelongsToSeller should verify ownership")
    void testBelongsToSeller() {
        Electronics electronics = new Electronics("My Item", "Desc", testSeller.getId(), "Brand");
        Item saved = itemDao.insert(electronics);
        
        assertTrue(itemDao.belongsToSeller(saved.getId(), testSeller.getId()));
        assertFalse(itemDao.belongsToSeller(saved.getId(), 99999L));
    }
}