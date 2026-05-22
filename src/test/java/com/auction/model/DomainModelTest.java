package com.auction.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Lightweight tests for the domain model classes — getRole() polymorphism, Item subclass category
 * dispatch, and Entity identity semantics. Pure unit tests, no database.
 */
@DisplayName("Domain model")
class DomainModelTest {

  @Nested
  @DisplayName("User hierarchy")
  class UserHierarchy {

    @Test
    @DisplayName("Bidder.getRole() returns BIDDER")
    void bidderRole() {
      assertEquals("BIDDER", new Bidder().getRole());
    }

    @Test
    @DisplayName("Seller.getRole() returns SELLER")
    void sellerRole() {
      assertEquals("SELLER", new Seller().getRole());
    }

    @Test
    @DisplayName("Admin.getRole() returns ADMIN")
    void adminRole() {
      assertEquals("ADMIN", new Admin().getRole());
    }

    @Test
    @DisplayName("availableBalance = balance - reservedBalance")
    void availableBalanceFormula() {
      User user = new Bidder();
      user.setBalance(new BigDecimal("1000000"));
      user.setReservedBalance(new BigDecimal("300000"));

      assertEquals(0, new BigDecimal("700000").compareTo(user.getAvailableBalance()));
    }

    @Test
    @DisplayName("availableBalance treats null balance/reserved as zero")
    void availableBalanceHandlesNull() {
      User user = new Bidder();
      user.setBalance(null);
      user.setReservedBalance(null);

      assertEquals(0, BigDecimal.ZERO.compareTo(user.getAvailableBalance()));
    }
  }

  @Nested
  @DisplayName("Item hierarchy")
  class ItemHierarchy {

    @Test
    @DisplayName("Electronics.getCategory() returns ELECTRONICS and exposes brand")
    void electronics() {
      Electronics e = new Electronics("Laptop", "Mỏng nhẹ", 1L, "Dell");
      assertEquals("ELECTRONICS", e.getCategory());
      assertEquals("Dell", e.getBrand());
    }

    @Test
    @DisplayName("Art.getCategory() returns ART and exposes artist")
    void art() {
      Art a = new Art("Mona Lisa", "Sơn dầu", 1L, "Da Vinci");
      assertEquals("ART", a.getCategory());
      assertEquals("Da Vinci", a.getArtist());
    }

    @Test
    @DisplayName("Vehicle.getCategory() returns VEHICLE and exposes year")
    void vehicle() {
      Vehicle v = new Vehicle("Honda Civic", "Đời mới", 1L, 2024);
      assertEquals("VEHICLE", v.getCategory());
      assertEquals(2024, v.getYear());
    }

    @Test
    @DisplayName("Item.status defaults to AVAILABLE")
    void defaultStatus() {
      Item item = new Electronics("X", "Y", 1L, "Sony");
      assertEquals("AVAILABLE", item.getStatus());
    }
  }

  @Nested
  @DisplayName("Entity equality")
  class EntityEquality {

    @Test
    @DisplayName("two entities of the same class with the same id are equal")
    void sameIdEqual() {
      User a = new Bidder();
      a.setId(5L);
      User b = new Bidder();
      b.setId(5L);

      assertEquals(a, b);
      assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("different ids → not equal")
    void differentIdNotEqual() {
      User a = new Bidder();
      a.setId(1L);
      User b = new Bidder();
      b.setId(2L);

      assertNotEquals(a, b);
    }

    @Test
    @DisplayName("entity is not equal to null or a foreign type")
    void notEqualToNullOrOther() {
      User a = new Bidder();
      a.setId(1L);
      assertNotEquals(null, a);
      assertNotEquals("string", a);
    }
  }
}
