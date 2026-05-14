package com.auction.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Model layer")
class ModelTest {

  @Nested
  @DisplayName("Inheritance — Entity → User → Bidder/Seller/Admin")
  class UserHierarchy {

    @Test
    @DisplayName("Bidder inherits id and createdAt from Entity")
    void bidderInheritsEntity() {
      Bidder bidder = new Bidder("quan", "hash123", "quan@example.com");
      // createdAt được set tự động bởi Entity constructor
      assertNotNull(bidder.getCreatedAt());
      // id chưa có vì chưa lưu DB
      assertEquals(null, bidder.getId());
    }

    @Test
    @DisplayName("Bidder inherits username and email from User")
    void bidderInheritsUser() {
      Bidder bidder = new Bidder("quan", "hash123", "quan@example.com");
      assertEquals("quan", bidder.getUsername());
      assertEquals("quan@example.com", bidder.getEmail());
    }
  }

  @Nested
  @DisplayName("Polymorphism — getRole() and getCategory()")
  class Polymorphism {

    @Test
    @DisplayName("Each User subclass returns different role")
    void userRoles() {
      User bidder = new Bidder("a", "h", "a@x.com");
      User seller = new Seller("b", "h", "b@x.com");
      User admin = new Admin("c", "h", "c@x.com");

      assertEquals("BIDDER", bidder.getRole());
      assertEquals("SELLER", seller.getRole());
      assertEquals("ADMIN", admin.getRole());
    }

    @Test
    @DisplayName("Can treat all Users polymorphically")
    void polymorphicList() {
      // Đây chính xác là cách DAO trả về danh sách users:
      // mỗi user có thể là Bidder, Seller, hoặc Admin
      User[] users = {
        new Bidder("a", "h", "a@x.com"),
        new Seller("b", "h", "b@x.com"),
        new Admin("c", "h", "c@x.com")
      };

      // Gọi getRole() trên mỗi user — polymorphism tự trả đúng
      for (User user : users) {
        assertNotNull(user.getRole());
        assertNotNull(user.getUsername());
        assertNotNull(user.getCreatedAt());
      }
    }
  }

  @Nested
  @DisplayName("Encapsulation — private fields + getters/setters")
  class Encapsulation {

    @Test
    @DisplayName("Cannot access fields directly, must use getters")
    void fieldsArePrivate() {
      // Nếu ai đó đổi private thành public, test này vẫn pass
      // nhưng checkstyle sẽ bắt lỗi.
      // Test này kiểm tra getter/setter hoạt động đúng.
      Bidder bidder = new Bidder();
      bidder.setUsername("quan");
      bidder.setEmail("quan@example.com");

      assertEquals("quan", bidder.getUsername());
      assertEquals("quan@example.com", bidder.getEmail());
    }
  }

  @Nested
  @DisplayName("Auction — business logic")
  class AuctionTests {

    @Test
    @DisplayName("New auction has status OPEN and currentPrice = startingPrice")
    void newAuctionDefaults() {
      Auction auction =
          new Auction(
              1L, new BigDecimal("100000"), LocalDateTime.now(), LocalDateTime.now().plusHours(2));

      assertEquals(AuctionStatus.OPEN, auction.getStatus());
      assertEquals(new BigDecimal("100000"), auction.getCurrentPrice());
      assertEquals(null, auction.getLeadingBidderId());
    }

    @Test
    @DisplayName("isExpired returns true after endTime")
    void expiredAuction() {
      Auction auction =
          new Auction(
              1L,
              new BigDecimal("100000"),
              LocalDateTime.now().minusHours(3),
              LocalDateTime.now().minusHours(1)); // ended 1 hour ago

      assertTrue(auction.isExpired());
    }

    @Test
    @DisplayName("isExpired returns false before endTime")
    void activeAuction() {
      Auction auction =
          new Auction(
              1L,
              new BigDecimal("100000"),
              LocalDateTime.now(),
              LocalDateTime.now().plusHours(2)); // ends in 2 hours

      assertFalse(auction.isExpired());
    }
  }

  @Nested
  @DisplayName("Item — lifecycle status")
  class ItemTests {

    @Test
    @DisplayName("New item has status AVAILABLE")
    void newItemDefaultsToAvailable() {
      Item item = new Item("Phone", "New phone", 1L, "ELECTRONICS");

      assertEquals("AVAILABLE", item.getStatus());
    }
  }

  @Nested
  @DisplayName("AutoBidConfig — budget check")
  class AutoBidTests {

    @Test
    @DisplayName("canBidAt returns true when within budget")
    void withinBudget() {
      AutoBidConfig config =
          new AutoBidConfig(
              1L,
              1L,
              new BigDecimal("1000000"), // max 1 triệu
              new BigDecimal("50000")); // increment 50k

      // Giá hiện tại 500k + increment 50k = 550k < 1 triệu → OK
      assertTrue(config.canBidAt(new BigDecimal("500000")));
    }

    @Test
    @DisplayName("canBidAt returns false when over budget")
    void overBudget() {
      AutoBidConfig config =
          new AutoBidConfig(
              1L,
              1L,
              new BigDecimal("1000000"), // max 1 triệu
              new BigDecimal("50000")); // increment 50k

      // Giá hiện tại 980k + increment 50k = 1030k > 1 triệu → KHÔNG OK
      assertFalse(config.canBidAt(new BigDecimal("980000")));
    }

    @Test
    @DisplayName("getNextBidAmount calculates correctly")
    void nextBidAmount() {
      AutoBidConfig config =
          new AutoBidConfig(1L, 1L, new BigDecimal("1000000"), new BigDecimal("50000"));

      BigDecimal next = config.getNextBidAmount(new BigDecimal("500000"));
      assertEquals(new BigDecimal("550000"), next);
    }
  }

  @Nested
  @DisplayName("Entity — equals and hashCode")
  class EqualityTests {

    @Test
    @DisplayName("Two entities with same id are equal")
    void sameIdEquals() {
      Bidder a = new Bidder(1L, "quan", "h", "q@x.com", LocalDateTime.now());
      Bidder b = new Bidder(1L, "different", "h", "d@x.com", LocalDateTime.now());

      assertEquals(a, b); // same id → equal, regardless of other fields
    }

    @Test
    @DisplayName("Two entities with different id are not equal")
    void differentIdNotEquals() {
      Bidder a = new Bidder(1L, "quan", "h", "q@x.com", LocalDateTime.now());
      Bidder b = new Bidder(2L, "quan", "h", "q@x.com", LocalDateTime.now());

      assertNotEquals(a, b);
    }
  }
}
