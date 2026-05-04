package com.auction.dao;

import static org.junit.jupiter.api.Assertions.*;

import com.auction.config.DatabaseConfig;
import com.auction.model.*;
import java.util.List;
import java.util.Optional;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Assumptions;

/** Test cho UserDao - Đảm bảo các nghiệp vụ CRUD và tính đa hình. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserDaoTest {

  private static Jdbi jdbi;
  private static UserDao userDao;

  @BeforeAll
  static void setup() {
    try {
      jdbi = DatabaseConfig.create();
    } catch (Exception e) {
      Assumptions.abort("No DB available, skipping: " + e.getMessage());
    }
    userDao = new UserDao(jdbi);
  }

  @BeforeEach
  void cleanup() {
    // Reset sạch bảng users và khởi động lại ID từ 1 để tránh cộng dồn qua các lần chạy
    jdbi.useHandle(
        handle -> {
          handle.execute("TRUNCATE TABLE auto_bid_configs CASCADE");
          handle.execute("TRUNCATE TABLE bid_transactions CASCADE");
          handle.execute("TRUNCATE TABLE auctions CASCADE");
          handle.execute("TRUNCATE TABLE items CASCADE");
          handle.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
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
    userDao.insert(new Bidder("duplicate_user", "hash", "e1@test.com"));

    // Jdbi/Postgres sẽ throw exception do ràng buộc UNIQUE
    assertThrows(
        Exception.class,
        () -> {
          userDao.insert(new Bidder("duplicate_user", "hash", "e2@test.com"));
        });
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
    assertFalse(bidders.isEmpty());
    assertTrue(bidders.stream().allMatch(u -> u instanceof Bidder));
  }

  // ============================================================
  // 3. KIỂM TRA ĐA HÌNH (POLYMORPHISM) - Quan trọng nhất
  // ============================================================

  @Test
  @Order(5)
  @DisplayName("Kiểm tra tính đa hình: Trả về đúng subclass khi truy vấn")
  void testPolymorphism() {
    // Lưu 3 loại user khác nhau
    User b = userDao.insert(new Bidder("poly_bidder", "h", "b@p.com"));
    User s = userDao.insert(new Seller("poly_seller", "h", "s@p.com"));
    User a = userDao.insert(new Admin("poly_admin", "h", "a@p.com"));

    // Khi lấy ra từ DB, hệ thống phải tự động map đúng class con (Subclass)
    User foundB = userDao.findById(b.getId()).orElseThrow();
    User foundS = userDao.findById(s.getId()).orElseThrow();
    User foundA = userDao.findById(a.getId()).orElseThrow();

    assertTrue(foundB instanceof Bidder, "Phải là thực thể Bidder");
    assertTrue(foundS instanceof Seller, "Phải là thực thể Seller");
    assertTrue(foundA instanceof Admin, "Phải là thực thể Admin");
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

    User updated = userDao.findById(saved.getId()).get();
    assertEquals("new@test.com", updated.getEmail());
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
