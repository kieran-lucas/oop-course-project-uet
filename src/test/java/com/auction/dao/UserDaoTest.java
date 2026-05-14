package com.auction.dao;

import static org.junit.jupiter.api.Assertions.*;

import com.auction.config.DatabaseConfig;
import com.auction.model.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Assumptions;

/**
 * Test suite kiểm tra toàn bộ các thao tác CRUD và tính đa hình của {@link UserDao} — lớp DAO quản
 * lý người dùng trên bảng {@code users}.
 *
 * <p><b>Phạm vi kiểm tra (theo nhóm):</b>
 *
 * <ol>
 *   <li><b>CREATE:</b> Insert thành công, ràng buộc UNIQUE username.
 *   <li><b>READ:</b> findById, findByRole.
 *   <li><b>POLYMORPHISM:</b> findById phải trả về đúng subclass ({@link Bidder}, {@link Seller},
 *       {@link Admin}) — đây là test quan trọng nhất, kiểm tra cơ chế mapping đa hình của DAO.
 *   <li><b>UPDATE & DELETE:</b> Cập nhật email, xóa user theo ID.
 * </ol>
 *
 * <p><b>Chiến lược dữ liệu:</b> Mỗi test chạy trong trạng thái DB sạch. {@code cleanup()} TRUNCATE
 * toàn bộ bảng trước từng test để đảm bảo test isolation tuyệt đối.
 *
 * <p><b>Thứ tự thực thi:</b> Được đánh số {@code @Order} để phản ánh trình tự logic — insert trước,
 * rồi đến read, polymorphism và cuối cùng là update/delete.
 *
 * <p><b>Điều kiện tiên quyết:</b> PostgreSQL phải đang chạy với thông tin kết nối được cấu hình
 * trong {@link DatabaseConfig}. Nếu không kết nối được, toàn bộ class bị bỏ qua (ABORTED).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserDaoTest {

  private static Jdbi jdbi;
  private static UserDao userDao;

  /**
   * Khởi tạo JDBI và {@link UserDao} một lần duy nhất cho cả class.
   *
   * <p>Nếu DB không khả dụng, class bị bỏ qua hoàn toàn qua {@link Assumptions#abort} để tránh báo
   * lỗi giả trong môi trường không có DB.
   */
  @BeforeAll
  static void setup() {
    try {
      jdbi = DatabaseConfig.create();
    } catch (Exception e) {
      Assumptions.abort("No DB available, skipping: " + e.getMessage());
    }
    userDao = new UserDao(jdbi);
  }

  /**
   * Xóa sạch toàn bộ dữ liệu và đặt lại bộ đếm ID về 1 trước mỗi test.
   *
   * <p>TRUNCATE theo thứ tự con → cha để tuân thủ FK constraint. {@code RESTART IDENTITY} trên
   * {@code users} đưa sequence BIGSERIAL về 1, đảm bảo các assertion về ID cụ thể luôn nhất quán và
   * không bị ảnh hưởng bởi các lần chạy trước.
   */
  @BeforeEach
  void cleanup() {
    // Xóa các bảng con trước, sau đó xóa bảng cha với RESTART IDENTITY để reset sequence.
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

  /** Kiểm tra insert Bidder thành công: ID được sinh tự động, username và role được lưu đúng. */
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

  /**
   * Kiểm tra ràng buộc UNIQUE trên cột {@code username}: insert user thứ hai với username trùng
   * phải bị DB từ chối và ném ngoại lệ. Test này xác nhận constraint đang hoạt động đúng ở tầng DB,
   * không chỉ ở tầng validation logic.
   */
  @Test
  @Order(2)
  @DisplayName("Nên báo lỗi khi trùng Username")
  void testInsertDuplicateUsername() {
    userDao.insert(new Bidder("duplicate_user", "hash", "e1@test.com"));

    // JDBI/PostgreSQL ném ngoại lệ do vi phạm ràng buộc UNIQUE trên cột username.
    assertThrows(
        Exception.class,
        () -> {
          userDao.insert(new Bidder("duplicate_user", "hash", "e2@test.com"));
        });
  }

  // ============================================================
  // 2. KIỂM TRA TRUY VẤN (READ)
  // ============================================================

  /** Kiểm tra findById: user vừa insert phải tìm lại được đúng username. */
  @Test
  @Order(3)
  @DisplayName("Nên tìm thấy User theo ID")
  void testFindById() {
    User saved = userDao.insert(new Bidder("test_find", "hash", "find@test.com"));
    Optional<User> found = userDao.findById(saved.getId());

    assertTrue(found.isPresent());
    assertEquals("test_find", found.get().getUsername());
  }

  /**
   * Kiểm tra findByRole: sau khi insert cả Bidder và Seller, kết quả lọc theo role {@code BIDDER}
   * phải không rỗng và toàn bộ phần tử phải là instance của {@link Bidder} — không lẫn Seller.
   */
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
  // 3. KIỂM TRA ĐA HÌNH (POLYMORPHISM) — Quan trọng nhất
  // ============================================================

  /**
   * Kiểm tra tính đa hình trong DAO: sau khi lưu 3 loại user khác nhau ({@link Bidder}, {@link
   * Seller}, {@link Admin}), khi đọc lại từ DB qua findById, DAO phải tự động map về đúng subclass
   * tương ứng — không phải kiểu cha {@link User}.
   *
   * <p>Đây là test quan trọng nhất của class, xác nhận cơ chế single-table inheritance (hoặc
   * discriminator column) trong {@link UserDao} hoạt động đúng với cả 3 role.
   */
  @Test
  @Order(5)
  @DisplayName("Kiểm tra tính đa hình: Trả về đúng subclass khi truy vấn")
  void testPolymorphism() {
    // Lưu 3 loại user khác nhau vào DB.
    User b = userDao.insert(new Bidder("poly_bidder", "h", "b@p.com"));
    User s = userDao.insert(new Seller("poly_seller", "h", "s@p.com"));
    User a = userDao.insert(new Admin("poly_admin", "h", "a@p.com"));

    // Khi lấy ra từ DB, hệ thống phải tự động map về đúng subclass dựa trên cột role.
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

  /**
   * Kiểm tra update email: sau khi thay đổi email, giá trị mới phải được persist và đọc lại chính
   * xác từ DB. {@code update()} phải trả về {@code true} khi thành công.
   */
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

  /**
   * Kiểm tra delete: user bị xóa không còn tìm thấy qua findById. {@code delete()} phải trả về
   * {@code true} khi xóa thành công.
   */
  @Test
  @Order(7)
  @DisplayName("Nên xóa User thành công")
  void testDelete() {
    User saved = userDao.insert(new Bidder("test_del", "hash", "del@test.com"));
    boolean deleted = userDao.delete(saved.getId());

    assertTrue(deleted);
    assertFalse(userDao.findById(saved.getId()).isPresent());
  }

  @Test
  @Order(8)
  @DisplayName("Seller with item/auction history cannot be hard-deleted")
  void sellerWithItemAndAuctionBlocksHardDelete() {
    User seller = userDao.insert(new Seller("seller_history", "hash", "seller_history@test.com"));

    jdbi.useHandle(
        handle -> {
          Long itemId =
              handle
                  .createQuery(
                      """
                      INSERT INTO items (seller_id, name, category)
                      VALUES (:sellerId, 'Camera', 'ELECTRONICS')
                      RETURNING id
                      """)
                  .bind("sellerId", seller.getId())
                  .mapTo(Long.class)
                  .one();
          handle
              .createUpdate(
                  """
                  INSERT INTO auctions (
                    item_id, seller_id, starting_price, current_price, start_time, end_time, status
                  )
                  VALUES (:itemId, :sellerId, 100, 100, NOW(), NOW() + INTERVAL '1 hour', 'OPEN')
                  """)
              .bind("itemId", itemId)
              .bind("sellerId", seller.getId())
              .execute();
        });

    assertTrue(userDao.hasDeleteBlockingReferences(seller.getId()));
    assertThrows(Exception.class, () -> userDao.delete(seller.getId()));
    assertTrue(userDao.findById(seller.getId()).isPresent());
  }

  @Test
  @Order(11)
  @DisplayName("Bidder with bid history cannot be hard-deleted")
  void bidderWithBidHistoryBlocksHardDelete() {
    User seller = userDao.insert(new Seller("bid_seller", "hash", "bid_seller@test.com"));
    User bidder = userDao.insert(new Bidder("bidder_history", "hash", "bidder_history@test.com"));

    jdbi.useHandle(
        handle -> {
          Long itemId =
              handle
                  .createQuery(
                      """
                      INSERT INTO items (seller_id, name, category)
                      VALUES (:sellerId, 'Watch', 'ELECTRONICS')
                      RETURNING id
                      """)
                  .bind("sellerId", seller.getId())
                  .mapTo(Long.class)
                  .one();
          Long auctionId =
              handle
                  .createQuery(
                      """
                      INSERT INTO auctions (
                        item_id, seller_id, starting_price, current_price, leading_bidder_id,
                        start_time, end_time, status
                      )
                      VALUES (
                        :itemId, :sellerId, 100, 150, :bidderId, NOW(),
                        NOW() + INTERVAL '1 hour', 'RUNNING'
                      )
                      RETURNING id
                      """)
                  .bind("itemId", itemId)
                  .bind("sellerId", seller.getId())
                  .bind("bidderId", bidder.getId())
                  .mapTo(Long.class)
                  .one();
          handle
              .createUpdate(
                  """
                  INSERT INTO bid_transactions (auction_id, bidder_id, amount)
                  VALUES (:auctionId, :bidderId, 150)
                  """)
              .bind("auctionId", auctionId)
              .bind("bidderId", bidder.getId())
              .execute();
        });

    assertTrue(userDao.hasDeleteBlockingReferences(bidder.getId()));
    assertThrows(Exception.class, () -> userDao.delete(bidder.getId()));
    assertTrue(userDao.findById(bidder.getId()).isPresent());
  }

  @Test
  @Order(12)
  @DisplayName("Release reserved_balance bằng đúng amount thì về 0")
  void releaseReservedBalanceExactAmountSucceeds() {
    User saved = userDao.insert(new Bidder("reserve_exact", "hash", "exact@test.com"));

    jdbi.useTransaction(
        handle -> {
          userDao.updateReservedBalanceInTransaction(handle, saved.getId(), new BigDecimal("100"));
          userDao.releaseReservedBalanceInTransaction(handle, saved.getId(), new BigDecimal("100"));
        });

    User found = userDao.findById(saved.getId()).orElseThrow();
    assertEquals(0, BigDecimal.ZERO.compareTo(found.getReservedBalance()));
  }

  @Test
  @Order(9)
  @DisplayName("Release reserved_balance nhỏ hơn số đang giữ thì trừ đúng amount")
  void releaseReservedBalancePartialAmountSucceeds() {
    User saved = userDao.insert(new Bidder("reserve_partial", "hash", "partial@test.com"));

    jdbi.useTransaction(
        handle -> {
          userDao.updateReservedBalanceInTransaction(handle, saved.getId(), new BigDecimal("150"));
          userDao.releaseReservedBalanceInTransaction(handle, saved.getId(), new BigDecimal("40"));
        });

    User found = userDao.findById(saved.getId()).orElseThrow();
    assertEquals(0, new BigDecimal("110").compareTo(found.getReservedBalance()));
  }

  @Test
  @Order(10)
  @DisplayName("Release reserved_balance lớn hơn số đang giữ thì throw và rollback")
  void releaseReservedBalanceUnderflowThrowsAndRollsBack() {
    User saved = userDao.insert(new Bidder("reserve_underflow", "hash", "underflow@test.com"));
    jdbi.useTransaction(
        handle ->
            userDao.updateReservedBalanceInTransaction(
                handle, saved.getId(), new BigDecimal("50")));

    assertThrows(
        IllegalStateException.class,
        () ->
            jdbi.useTransaction(
                handle ->
                    userDao.releaseReservedBalanceInTransaction(
                        handle, saved.getId(), new BigDecimal("100"))));

    User found = userDao.findById(saved.getId()).orElseThrow();
    assertEquals(0, new BigDecimal("50").compareTo(found.getReservedBalance()));
  }
}
