package com.auction.dao;

import com.auction.config.DatabaseConfig;
import com.auction.model.*;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cho UserDao - Đảm bảo các nghiệp vụ CRUD và tính đa hình.
 * Liên kết: model/User.java, config/DatabaseConfig.java
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserDaoTest {
    
    private static Jdbi jdbi;
    private static UserDao userDao;
    
    @BeforeAll
    static void setup() {
        // Khởi tạo Jdbi từ DatabaseConfig
        jdbi = DatabaseConfig.create();
        userDao = new UserDao(jdbi);
    }
    
    /**
     * Dọn dẹp bảng users trước mỗi test case.
     * Sử dụng Transaction để đảm bảo tính toàn vẹn và tránh lỗi Permission.
     */
    @BeforeEach
    void cleanup() {
        jdbi.useTransaction(handle -> {
            // Xóa tất cả các user phục vụ mục đích testing
            handle.createUpdate("DELETE FROM users WHERE username LIKE 'test_%' OR username LIKE 'poly_%' OR username = 'duplicate_user'")
                  .execute();
        });
    }

    // ============================================================
    // 1. KIỂM TRA LƯU TRỮ (CREATE)
    // ============================================================
    
    @Test
    @Order(1)
    @DisplayName("Nên lưu thành công Bidder và trả về ID")
    void testInsert() {
        Bidder bidder = new Bidder("test_bidder", "$2a$10$hash", "test@example.com");
        User saved = userDao.insert(bidder);
        
        assertNotNull(saved.getId());
        assertEquals("test_bidder", saved.getUsername());
        assertEquals("BIDDER", saved.getRole());
    }
    
    @Test
    @Order(2)
    @DisplayName("Nên báo lỗi khi trùng Username")
    void testInsertDuplicateUsername() {
        Bidder b1 = new Bidder("duplicate_user", "hash", "e1@test.com");
        Bidder b2 = new Bidder("duplicate_user", "hash", "e2@test.com");
        
        userDao.insert(b1);
        // Jdbi/Postgres sẽ throw exception do ràng buộc UNIQUE
        assertThrows(Exception.class, () -> userDao.insert(b2));
    }

    // ============================================================
    // 2. KIỂM TRA TRUY VẤN (READ)
    // ============================================================
    
    @Test
    @Order(3)
    @DisplayName("Nên tìm thấy User theo ID")
    void testFindById() {
        User saved = userDao.insert(new Bidder("test_find", "hash", "find@test.com"));
        Optional<User> found = userDao.findById(saved.getId());
        
        assertTrue(found.isPresent());
        assertEquals("test_find", found.get().getUsername());
    }

    @Test
    @Order(4)
    @DisplayName("Nên trả về danh sách User theo Role")
    void testFindByRole() {
        userDao.insert(new Bidder("test_b1", "hash", "b1@test.com"));
        userDao.insert(new Seller("test_s1", "hash", "s1@test.com"));
        
        List<User> bidders = userDao.findByRole("BIDDER");
        assertTrue(bidders.stream().allMatch(u -> u instanceof Bidder));
    }

    // ============================================================
    // 3. KIỂM TRA ĐA HÌNH (POLYMORPHISM) - Mục tiêu 0.5 điểm Rubric
    // ============================================================
    
    @Test
    @Order(5)
    @DisplayName("Kiểm tra tính đa hình: Trả về đúng subclass khi truy vấn")
    void testPolymorphism() {
        // Lưu 3 loại user khác nhau
        User b = userDao.insert(new Bidder("poly_bidder", "h", "b@p.com"));
        User s = userDao.insert(new Seller("poly_seller", "h", "s@p.com"));
        User a = userDao.insert(new Admin("poly_admin", "h", "a@p.com"));
        
        // Khi lấy ra từ DB, Jdbi phải map đúng class con
        assertTrue(userDao.findById(b.getId()).get() instanceof Bidder);
        assertTrue(userDao.findById(s.getId()).get() instanceof Seller);
        assertTrue(userDao.findById(a.getId()).get() instanceof Admin);
    }

    // ============================================================
    // 4. KIỂM TRA CẬP NHẬT & XÓA (UPDATE & DELETE)
    // ============================================================
    
    @Test
    @Order(6)
    @DisplayName("Nên cập nhật được email User")
    void testUpdate() {
        User saved = userDao.insert(new Bidder("test_up", "hash", "old@test.com"));
        saved.setEmail("new@test.com");
        
        boolean success = userDao.update(saved);
        assertTrue(success);
        assertEquals("new@test.com", userDao.findById(saved.getId()).get().getEmail());
    }

    @Test
    @Order(7)
    @DisplayName("Nên xóa User thành công")
    void testDelete() {
        User saved = userDao.insert(new Bidder("test_del", "hash", "del@test.com"));
        boolean deleted = userDao.delete(saved.getId());
        
        assertTrue(deleted);
        assertFalse(userDao.findById(saved.getId()).isPresent());
    }
}