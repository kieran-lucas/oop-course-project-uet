package com.auction.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử nhẹ cho các lớp domain model: đa hình {@code getRole()}, phân loại theo category của
 * Item, và ngữ nghĩa identity của Entity. Không cần kết nối DB ��� pure unit test.
 */
@DisplayName("Domain model")
class DomainModelTest {

  @Nested
  @DisplayName("Cây kế thừa User")
  class UserHierarchy {

    @Test
    @DisplayName("Bidder.getRole() trả về BIDDER")
    void bidderRole() {
      assertEquals("BIDDER", new Bidder().getRole());
    }

    @Test
    @DisplayName("Seller.getRole() trả về SELLER")
    void sellerRole() {
      assertEquals("SELLER", new Seller().getRole());
    }

    @Test
    @DisplayName("Admin.getRole() trả về ADMIN")
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
    @DisplayName("availableBalance coi null balance/reserved là 0")
    void availableBalanceHandlesNull() {
      User user = new Bidder();
      user.setBalance(null);
      user.setReservedBalance(null);

      assertEquals(0, BigDecimal.ZERO.compareTo(user.getAvailableBalance()));
    }
  }

  @Nested
  @DisplayName("Cây kế thừa Item")
  class ItemHierarchy {

    @Test
    @DisplayName("Electronics.getCategory() trả về ELECTRONICS và có brand")
    void electronics() {
      Electronics e = new Electronics("Laptop", "Mỏng nhẹ", 1L, "Dell");
      assertEquals("ELECTRONICS", e.getCategory());
      assertEquals("Dell", e.getBrand());
    }

    @Test
    @DisplayName("Art.getCategory() trả về ART và có artist")
    void art() {
      Art a = new Art("Mona Lisa", "Sơn dầu", 1L, "Da Vinci");
      assertEquals("ART", a.getCategory());
      assertEquals("Da Vinci", a.getArtist());
    }

    @Test
    @DisplayName("Vehicle.getCategory() trả về VEHICLE và có year")
    void vehicle() {
      Vehicle v = new Vehicle("Honda Civic", "Đời mới", 1L, 2024);
      assertEquals("VEHICLE", v.getCategory());
      assertEquals(2024, v.getYear());
    }

    @Test
    @DisplayName("Item.status mặc ��ịnh là AVAILABLE")
    void defaultStatus() {
      Item item = new Electronics("X", "Y", 1L, "Sony");
      assertEquals("AVAILABLE", item.getStatus());
    }
  }

  @Nested
  @DisplayName("Entity equality")
  class EntityEquality {

    @Test
    @DisplayName("hai entity cùng lớp, cùng id thì bằng nhau")
    void sameIdEqual() {
      User a = new Bidder();
      a.setId(5L);
      User b = new Bidder();
      b.setId(5L);

      assertEquals(a, b);
      assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("khác id → không bằng nhau")
    void differentIdNotEqual() {
      User a = new Bidder();
      a.setId(1L);
      User b = new Bidder();
      b.setId(2L);

      assertNotEquals(a, b);
    }

    @Test
    @DisplayName("entity không bằng null hay kiểu khác")
    void notEqualToNullOrOther() {
      User a = new Bidder();
      a.setId(1L);
      assertNotEquals(null, a);
      assertNotEquals("string", a);
    }
  }
}
