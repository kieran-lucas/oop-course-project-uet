package com.auction.dao;

import static org.junit.jupiter.api.Assertions.*;

import com.auction.config.DatabaseConfig;
import com.auction.model.*;
import java.util.List;
import java.util.Optional;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Assumptions;

/**
 * Test suite kiểm tra toàn bộ các thao tác của {@link ItemDao} — lớp DAO quản lý vật phẩm đấu giá
 * trên bảng {@code items}.
 */
class ItemDaoTest {

  private static Jdbi jdbi;
  private static UserDao userDao;
  private static ItemDao itemDao;

  private User testSeller;

  @BeforeAll
  static void setup() {
    try {
      jdbi = DatabaseConfig.create();
    } catch (Exception e) {
      Assumptions.abort("No DB available, skipping: " + e.getMessage());
    }
    userDao = new UserDao(jdbi);
    itemDao = new ItemDao(jdbi);
  }

  @BeforeEach
  void init() {
    jdbi.useHandle(
        handle -> {
          handle.execute("TRUNCATE TABLE auto_bid_configs CASCADE");
          handle.execute("TRUNCATE TABLE bid_transactions CASCADE");
          handle.execute("TRUNCATE TABLE auctions CASCADE");
          handle.execute("TRUNCATE TABLE items CASCADE");
          handle.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
        });

    testSeller = userDao.insert(new Seller("test_seller", "password123", "seller@test.com"));
  }

  @Test
  @DisplayName("Insert Item should work")
  void testInsertItem() {
    Item item = new Item("iPhone 15", "New phone", testSeller.getId(), "ELECTRONICS");
    item.setBrand("Apple");

    Item saved = itemDao.insert(item);

    assertNotNull(saved.getId());
    assertEquals("ELECTRONICS", saved.getCategory());
    assertEquals("Apple", saved.getBrand());
    assertEquals("AVAILABLE", saved.getStatus());
  }

  @Test
  @DisplayName("FindById should return correct item")
  void testFindById() {
    Item item = new Item("Test Laptop", "Gaming laptop", testSeller.getId(), "ELECTRONICS");
    item.setBrand("Dell");
    Item saved = itemDao.insert(item);

    Optional<Item> found = itemDao.findById(saved.getId());

    assertTrue(found.isPresent());
    assertEquals("Test Laptop", found.get().getName());
    assertEquals("Dell", found.get().getBrand());
    assertEquals("AVAILABLE", found.get().getStatus());
  }

  @Test
  @DisplayName("Item status should round-trip through DAO")
  void testItemStatusRoundTrip() {
    Item item = new Item("Painting", "Art", testSeller.getId(), "ART");
    item.setStatus("IN_AUCTION");

    Item saved = itemDao.insert(item);
    Optional<Item> found = itemDao.findById(saved.getId());

    assertTrue(found.isPresent());
    assertEquals("IN_AUCTION", found.get().getStatus());
  }

  @Test
  @DisplayName("FindBySellerId should return all items of a seller")
  void testFindBySellerId() {
    itemDao.insert(new Item("Laptop", "Dell", testSeller.getId(), "ELECTRONICS"));
    itemDao.insert(new Item("Painting", "Art", testSeller.getId(), "ART"));

    List<Item> items = itemDao.findBySellerId(testSeller.getId());

    assertEquals(2, items.size());
  }

  @Test
  @DisplayName("FindByCategory should filter by category")
  void testFindByCategory() {
    itemDao.insert(new Item("Phone", "Smartphone", testSeller.getId(), "ELECTRONICS"));
    itemDao.insert(new Item("Sculpture", "Art", testSeller.getId(), "ART"));

    List<Item> electronics = itemDao.findByCategory("ELECTRONICS");
    List<Item> arts = itemDao.findByCategory("ART");

    assertTrue(electronics.stream().allMatch(i -> "ELECTRONICS".equals(i.getCategory())));
    assertTrue(arts.stream().allMatch(i -> "ART".equals(i.getCategory())));
  }

  @Test
  @DisplayName("Update should modify item details")
  void testUpdate() {
    Item item = new Item("Old Name", "Old desc", testSeller.getId(), "ELECTRONICS");
    item.setBrand("Old Brand");
    Item saved = itemDao.insert(item);

    saved.setName("New Name");
    saved.setBrand("New Brand");
    saved.setStatus("SOLD");

    boolean updated = itemDao.update(saved);

    assertTrue(updated);

    Optional<Item> found = itemDao.findById(saved.getId());
    assertTrue(found.isPresent());
    assertEquals("New Name", found.get().getName());
    assertEquals("New Brand", found.get().getBrand());
    assertEquals("SOLD", found.get().getStatus());
  }

  @Test
  @DisplayName("Delete should mark item as REMOVED")
  void testDeleteMarksItemRemoved() {
    Item item = new Item("To Delete", "Temp", testSeller.getId(), "ELECTRONICS");
    Item saved = itemDao.insert(item);

    boolean deleted = itemDao.delete(saved.getId());

    assertTrue(deleted);
    Optional<Item> found = itemDao.findById(saved.getId());
    assertTrue(found.isPresent());
    assertEquals("REMOVED", found.get().getStatus());
  }
}
