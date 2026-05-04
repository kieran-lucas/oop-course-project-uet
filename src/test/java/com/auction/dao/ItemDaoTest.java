package com.auction.dao;

import static org.junit.jupiter.api.Assertions.*;

import com.auction.config.DatabaseConfig;
import com.auction.model.*;
import java.util.List;
import java.util.Optional;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Assumptions;

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
    // 1. Dọn dẹp DB và reset ID về 1 trước mỗi test case
    jdbi.useHandle(
        handle -> {
          handle.execute("TRUNCATE TABLE auto_bid_configs CASCADE");
          handle.execute("TRUNCATE TABLE bid_transactions CASCADE");
          handle.execute("TRUNCATE TABLE auctions CASCADE");
          handle.execute("TRUNCATE TABLE items CASCADE");
          handle.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
        });

    // 2. Tạo seller mặc định (Lúc này ID chắc chắn là 1)
    testSeller = userDao.insert(new Seller("test_seller", "password123", "seller@test.com"));

    System.out.println("Cleaned DB & Created test seller with id: " + testSeller.getId());
  }

  @Test
  @DisplayName("Insert Electronics should work")
  void testInsertElectronics() {
    Electronics electronics =
        new Electronics("iPhone 15", "New phone", testSeller.getId(), "Apple");

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
    Electronics electronics =
        new Electronics("Test Laptop", "Gaming laptop", testSeller.getId(), "Dell");
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
    itemDao.insert(new Electronics("Laptop", "Dell", testSeller.getId(), "Dell"));
    itemDao.insert(new Art("Painting", "Art", testSeller.getId(), "Artist"));

    List<Item> items = itemDao.findBySellerId(testSeller.getId());

    assertEquals(2, items.size());
  }

  @Test
  @DisplayName("FindAll should return all items")
  void testFindAll() {
    itemDao.insert(new Electronics("Item 1", "Desc", testSeller.getId(), "Brand"));
    List<Item> items = itemDao.findAll();
    assertNotNull(items);
    assertTrue(items.size() > 0);
  }

  @Test
  @DisplayName("FindByCategory should filter by category")
  void testFindByCategory() {
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
    itemDao.insert(new Electronics("iPhone Pro Max", "Phone", testSeller.getId(), "Apple"));
    itemDao.insert(new Electronics("iPad Air Pro", "Tablet", testSeller.getId(), "Apple"));

    List<Item> results = itemDao.searchByName("Pro");

    assertTrue(results.size() >= 2);
    assertTrue(results.stream().allMatch(i -> i.getName().contains("Pro")));
  }

  @Test
  @DisplayName("Update should modify item details")
  void testUpdate() {
    Electronics electronics =
        new Electronics("Old Name", "Old desc", testSeller.getId(), "Old Brand");
    Item saved = itemDao.insert(electronics);

    saved.setName("New Name");
    saved.setDescription("New desc");
    ((Electronics) saved).setBrand("New Brand");

    boolean updated = itemDao.update(saved);

    assertTrue(updated);

    Optional<Item> found = itemDao.findById(saved.getId());
    assertTrue(found.isPresent());
    assertEquals("New Name", found.get().getName());
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
    assertFalse(itemDao.belongsToSeller(saved.getId(), 999L));
  }
}
