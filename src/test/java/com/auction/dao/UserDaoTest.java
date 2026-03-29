package com.auction.dao;

import com.auction.config.DatabaseConfig;
import com.auction.model.*;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cho UserDao.
 * 
 * <p>Test này kiểm tra các thao tác CRUD trên bảng users,
 * bao gồm insert, findById, findByUsername, update, delete.
 * 
 * <p>Mỗi test đều chạy trên database test (dùng transaction rollback
 * để không ảnh hưởng đến dữ liệu thật).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserDaoTest {
    
    private static Jdbi jdbi;
    private static UserDao userDao;
    
    @BeforeAll
    static void setup() {
        // Dùng DatabaseConfig để tạo kết nối
        jdbi = DatabaseConfig.create();
        userDao = new UserDao(jdbi);
    }
    
    /**
     * Dọn dẹp bảng users trước mỗi test.
     * Tránh dữ liệu từ test trước ảnh hưởng test sau.
     */
    @BeforeEach
    void cleanup() {
        jdbi.useHandle(handle -> 
            handle.createUpdate("DELETE FROM users WHERE username LIKE 'test_%'").execute()
        );
    }
    
    // ============================================================
    // TEST INSERT
    // ============================================================
    
    @Test
    @Order(1)
    @DisplayName("Insert should create new user and return with id")
    void testInsert() {
        // Given
        Bidder bidder = new Bidder("test_bidder", "$2a$10$fakehash", "test@example.com");
        
        // When
        User saved = userDao.insert(bidder);
        
        // Then
        assertNotNull(saved.getId(), "User should have id after insert");
        assertEquals("test_bidder", saved.getUsername());
        assertEquals("test@example.com", saved.getEmail());
        assertEquals("BIDDER", saved.getRole());
        assertNotNull(saved.getCreatedAt());
    }
    
    @Test
    @Order(2)
    @DisplayName("Insert should throw exception when username already exists")
    void testInsertDuplicateUsername() {
        // Given
        Bidder bidder1 = new Bidder("duplicate_user", "hash1", "email1@test.com");
        Bidder bidder2 = new Bidder("duplicate_user", "hash2", "email2@test.com");
        
        // When
        userDao.insert(bidder1);
        
        // Then
        assertThrows(Exception.class, () -> userDao.insert(bidder2),
                "Should throw exception when username already exists");
    }
    
    @Test
    @Order(3)
    @DisplayName("Insert should work for all user roles")
    void testInsertAllRoles() {
        // When
        User bidder = userDao.insert(new Bidder("test_bidder", "hash", "bidder@test.com"));
        User seller = userDao.insert(new Seller("test_seller", "hash", "seller@test.com"));
        User admin = userDao.insert(new Admin("test_admin", "hash", "admin@test.com"));
        
        // Then
        assertEquals("BIDDER", bidder.getRole());
        assertEquals("SELLER", seller.getRole());
        assertEquals("ADMIN", admin.getRole());
        
        assertNotNull(bidder.getId());
        assertNotNull(seller.getId());
        assertNotNull(admin.getId());
    }
    
    // ============================================================
    // TEST FIND BY ID
    // ============================================================
    
    @Test
    @Order(4)
    @DisplayName("FindById should return user when exists")
    void testFindById() {
        // Given
        Bidder bidder = new Bidder("find_by_id", "hash", "find@test.com");
        User saved = userDao.insert(bidder);
        
        // When
        Optional<User> found = userDao.findById(saved.getId());
        
        // Then
        assertTrue(found.isPresent());
        assertEquals("find_by_id", found.get().getUsername());
        assertEquals("find@test.com", found.get().getEmail());
        assertEquals("BIDDER", found.get().getRole());
    }
    
    @Test
    @Order(5)
    @DisplayName("FindById should return empty when user not exists")
    void testFindByIdNotFound() {
        // When
        Optional<User> found = userDao.findById(99999L);
        
        // Then
        assertFalse(found.isPresent());
    }
    
    // ============================================================
    // TEST FIND BY USERNAME
    // ============================================================
    
    @Test
    @Order(6)
    @DisplayName("FindByUsername should return user when exists")
    void testFindByUsername() {
        // Given
        Bidder bidder = new Bidder("unique_username", "hash", "unique@test.com");
        userDao.insert(bidder);
        
        // When
        Optional<User> found = userDao.findByUsername("unique_username");
        
        // Then
        assertTrue(found.isPresent());
        assertEquals("unique_username", found.get().getUsername());
        assertEquals("unique@test.com", found.get().getEmail());
    }
    
    @Test
    @Order(7)
    @DisplayName("FindByUsername should return empty when username not exists")
    void testFindByUsernameNotFound() {
        // When
        Optional<User> found = userDao.findByUsername("nonexistent_user");
        
        // Then
        assertFalse(found.isPresent());
    }
    
    // ============================================================
    // TEST FIND BY EMAIL
    // ============================================================
    
    @Test
    @Order(8)
    @DisplayName("FindByEmail should return user when exists")
    void testFindByEmail() {
        // Given
        Bidder bidder = new Bidder("email_user", "hash", "email_test@example.com");
        userDao.insert(bidder);
        
        // When
        Optional<User> found = userDao.findByEmail("email_test@example.com");
        
        // Then
        assertTrue(found.isPresent());
        assertEquals("email_user", found.get().getUsername());
        assertEquals("email_test@example.com", found.get().getEmail());
    }
    
    // ============================================================
    // TEST FIND ALL
    // ============================================================
    
    @Test
    @Order(9)
    @DisplayName("FindAll should return all users")
    void testFindAll() {
        // Given
        userDao.insert(new Bidder("all1", "hash", "all1@test.com"));
        userDao.insert(new Seller("all2", "hash", "all2@test.com"));
        userDao.insert(new Admin("all3", "hash", "all3@test.com"));
        
        // When
        List<User> users = userDao.findAll();
        
        // Then
        assertTrue(users.size() >= 3);
        assertTrue(users.stream().anyMatch(u -> "all1".equals(u.getUsername())));
        assertTrue(users.stream().anyMatch(u -> "all2".equals(u.getUsername())));
        assertTrue(users.stream().anyMatch(u -> "all3".equals(u.getUsername())));
    }
    
    // ============================================================
    // TEST FIND BY ROLE
    // ============================================================
    
    @Test
    @Order(10)
    @DisplayName("FindByRole should return only users with that role")
    void testFindByRole() {
        // Given
        userDao.insert(new Bidder("bidder1", "hash", "bidder1@test.com"));
        userDao.insert(new Bidder("bidder2", "hash", "bidder2@test.com"));
        userDao.insert(new Seller("seller1", "hash", "seller1@test.com"));
        
        // When
        List<User> bidders = userDao.findByRole("BIDDER");
        List<User> sellers = userDao.findByRole("SELLER");
        
        // Then
        assertEquals(2, bidders.size());
        assertEquals(1, sellers.size());
        assertTrue(bidders.stream().allMatch(u -> "BIDDER".equals(u.getRole())));
        assertTrue(sellers.stream().allMatch(u -> "SELLER".equals(u.getRole())));
    }
    
    // ============================================================
    // TEST UPDATE
    // ============================================================
    
    @Test
    @Order(11)
    @DisplayName("Update should change user email and password")
    void testUpdate() {
        // Given
        Bidder bidder = new Bidder("update_user", "old_hash", "old@test.com");
        User saved = userDao.insert(bidder);
        
        // When
        saved.setPasswordHash("new_hash");
        saved.setEmail("new@test.com");
        boolean updated = userDao.update(saved);
        
        // Then
        assertTrue(updated);
        
        Optional<User> found = userDao.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("new_hash", found.get().getPasswordHash());
        assertEquals("new@test.com", found.get().getEmail());
        // Username không thay đổi
        assertEquals("update_user", found.get().getUsername());
    }
    
    @Test
    @Order(12)
    @DisplayName("Update should return false when user not exists")
    void testUpdateNotFound() {
        // Given
        Bidder bidder = new Bidder("nonexistent", "hash", "nonexistent@test.com");
        bidder.setId(99999L);
        
        // When
        boolean updated = userDao.update(bidder);
        
        // Then
        assertFalse(updated);
    }
    
    // ============================================================
    // TEST DELETE
    // ============================================================
    
    @Test
    @Order(13)
    @DisplayName("Delete should remove user from database")
    void testDelete() {
        // Given
        Bidder bidder = new Bidder("delete_user", "hash", "delete@test.com");
        User saved = userDao.insert(bidder);
        
        // When
        boolean deleted = userDao.delete(saved.getId());
        
        // Then
        assertTrue(deleted);
        
        Optional<User> found = userDao.findById(saved.getId());
        assertFalse(found.isPresent());
    }
    
    @Test
    @Order(14)
    @DisplayName("Delete should return false when user not exists")
    void testDeleteNotFound() {
        // When
        boolean deleted = userDao.delete(99999L);
        
        // Then
        assertFalse(deleted);
    }
    
    // ============================================================
    // TEST EXISTS METHODS
    // ============================================================
    
    @Test
    @Order(15)
    @DisplayName("ExistsByUsername should return true for existing username")
    void testExistsByUsername() {
        // Given
        userDao.insert(new Bidder("exists_user", "hash", "exists@test.com"));
        
        // When
        boolean exists = userDao.existsByUsername("exists_user");
        
        // Then
        assertTrue(exists);
    }
    
    @Test
    @Order(16)
    @DisplayName("ExistsByUsername should return false for non-existing username")
    void testExistsByUsernameFalse() {
        // When
        boolean exists = userDao.existsByUsername("not_exists");
        
        // Then
        assertFalse(exists);
    }
    
    @Test
    @Order(17)
    @DisplayName("ExistsByEmail should return true for existing email")
    void testExistsByEmail() {
        // Given
        userDao.insert(new Bidder("email_exists", "hash", "email_exists@test.com"));
        
        // When
        boolean exists = userDao.existsByEmail("email_exists@test.com");
        
        // Then
        assertTrue(exists);
    }
    
    // ============================================================
    // TEST POLYMORPHISM
    // ============================================================
    
    @Test
    @Order(18)
    @DisplayName("FindById should return correct subclass (polymorphism)")
    void testPolymorphism() {
        // Given
        Bidder bidder = new Bidder("poly_bidder", "hash", "poly_bidder@test.com");
        Seller seller = new Seller("poly_seller", "hash", "poly_seller@test.com");
        Admin admin = new Admin("poly_admin", "hash", "poly_admin@test.com");
        
        User savedBidder = userDao.insert(bidder);
        User savedSeller = userDao.insert(seller);
        User savedAdmin = userDao.insert(admin);
        
        // When
        Optional<User> foundBidder = userDao.findById(savedBidder.getId());
        Optional<User> foundSeller = userDao.findById(savedSeller.getId());
        Optional<User> foundAdmin = userDao.findById(savedAdmin.getId());
        
        // Then
        assertTrue(foundBidder.isPresent());
        assertTrue(foundSeller.isPresent());
        assertTrue(foundAdmin.isPresent());
        
        // Kiểm tra đúng subclass
        assertTrue(foundBidder.get() instanceof Bidder);
        assertTrue(foundSeller.get() instanceof Seller);
        assertTrue(foundAdmin.get() instanceof Admin);
        
        // Kiểm tra role trả về đúng
        assertEquals("BIDDER", foundBidder.get().getRole());
        assertEquals("SELLER", foundSeller.get().getRole());
        assertEquals("ADMIN", foundAdmin.get().getRole());
    }
}