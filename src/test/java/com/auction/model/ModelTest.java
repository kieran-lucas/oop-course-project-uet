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

/**
 * Kiểm thử tầng Model — kế thừa, đa hình, đóng gói, logic nghiệp vụ cơ bản.
 *
 * <p>Không cần kết nối DB — tất cả test khởi tạo model trực tiếp (pure unit test).
 */
@DisplayName("Tầng Model")
class ModelTest {

  @Nested
  @DisplayName("Kế thừa — Entity → User → Bidder/Seller/Admin")
  class UserHierarchy {

    @Test
    @DisplayName("Bidder kế thừa id và createdAt từ Entity")
    void bidderInheritsEntity() {
      Bidder bidder = new Bidder("quan", "hash123", "quan@example.com");
      // createdAt được set tự động bởi Entity constructor
      assertNotNull(bidder.getCreatedAt());
      // id chưa có vì chưa lưu DB
      assertEquals(null, bidder.getId());
    }

    @Test
    @DisplayName("Bidder kế thừa username và email từ User")
    void bidderInheritsUser() {
      Bidder bidder = new Bidder("quan", "hash123", "quan@example.com");
      assertEquals("quan", bidder.getUsername());
      assertEquals("quan@example.com", bidder.getEmail());
    }
  }

  @Nested
  @DisplayName("Đa hình — getRole() và getCategory()")
  class Polymorphism {

    @Test
    @DisplayName("Mỗi subclass User trả về role khác nhau")
    void userRoles() {
      User bidder = new Bidder("a", "h", "a@x.com");
      User seller = new Seller("b", "h", "b@x.com");
      User admin = new Admin("c", "h", "c@x.com");

      assertEquals("BIDDER", bidder.getRole());
      assertEquals("SELLER", seller.getRole());
      assertEquals("ADMIN", admin.getRole());
    }

    @Test
    @DisplayName("Có thể xử lý tất cả User đa hình qua interface chung")
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

    @Test
    @DisplayName("Mỗi subclass Item trả về category khác nhau")
    void itemCategories() {
      Item electronics = new Electronics("iPhone", "Phone", 1L, "Apple");
      Item art = new Art("Mona Lisa", "Painting", 1L, "Da Vinci");
      Item vehicle = new Vehicle("Camry", "Car", 1L, 2022);

      assertEquals("ELECTRONICS", electronics.getCategory());
      assertEquals("ART", art.getCategory());
      assertEquals("VEHICLE", vehicle.getCategory());
    }
  }

  @Nested
  @DisplayName("Đóng gói — trường private + getter/setter")
  class Encapsulation {

    @Test
    @DisplayName("Không thể truy cập trực tiếp, phải dùng getter")
    void fieldsArePrivate() {
      // Test này kiểm tra getter/setter hoạt động đúng
      Bidder bidder = new Bidder();
      bidder.setUsername("quan");
      bidder.setEmail("quan@example.com");

      assertEquals("quan", bidder.getUsername());
      assertEquals("quan@example.com", bidder.getEmail());
    }
  }

  @Nested
  @DisplayName("Auction — logic nghiệp vụ")
  class AuctionTests {

    @Test
    @DisplayName("Phiên mới có status OPEN và currentPrice = startingPrice")
    void newAuctionDefaults() {
      Auction auction =
          new Auction(
              1L, new BigDecimal("100000"), LocalDateTime.now(), LocalDateTime.now().plusHours(2));

      assertEquals(AuctionStatus.OPEN, auction.getStatus());
      assertEquals(new BigDecimal("100000"), auction.getCurrentPrice());
      assertEquals(null, auction.getLeadingBidderId());
    }

    @Test
    @DisplayName("isExpired trả về true sau endTime")
    void expiredAuction() {
      Auction auction =
          new Auction(
              1L,
              new BigDecimal("100000"),
              LocalDateTime.now().minusHours(3),
              LocalDateTime.now().minusHours(1)); // đã kết thúc 1 giờ trước

      assertTrue(auction.isExpired());
    }

    @Test
    @DisplayName("isExpired trả về false khi chưa đến endTime")
    void activeAuction() {
      Auction auction =
          new Auction(
              1L,
              new BigDecimal("100000"),
              LocalDateTime.now(),
              LocalDateTime.now().plusHours(2)); // còn 2 giờ nữa mới kết thúc

      assertFalse(auction.isExpired());
    }
  }

  @Nested
  @DisplayName("Item — trạng thái vòng đời")
  class ItemTests {

    @Test
    @DisplayName("Item mới có status AVAILABLE mặc định")
    void newItemDefaultsToAvailable() {
      Item item = new Electronics("Phone", "New phone", 1L, "Apple");

      assertEquals("AVAILABLE", item.getStatus());
    }
  }

  @Nested
  @DisplayName("AutoBidConfig — kiểm tra ngân sách")
  class AutoBidTests {

    @Test
    @DisplayName("canBidAt trả về true khi còn trong ngân sách")
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
    @DisplayName("canBidAt trả về false khi vượt ngân sách")
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
    @DisplayName("getNextBidAmount tính đúng currentPrice + increment")
    void nextBidAmount() {
      AutoBidConfig config =
          new AutoBidConfig(1L, 1L, new BigDecimal("1000000"), new BigDecimal("50000"));

      BigDecimal next = config.getNextBidAmount(new BigDecimal("500000"));
      assertEquals(new BigDecimal("550000"), next);
    }
  }

  @Nested
  @DisplayName("Entity — equals và hashCode")
  class EqualityTests {

    @Test
    @DisplayName("Hai entity cùng id thì bằng nhau")
    void sameIdEquals() {
      Bidder a = new Bidder(1L, "quan", "h", "q@x.com", LocalDateTime.now());
      Bidder b = new Bidder(1L, "different", "h", "d@x.com", LocalDateTime.now());

      assertEquals(a, b); // cùng id → bằng nhau, bất kể trường khác
    }

    @Test
    @DisplayName("Hai entity khác id thì không bằng nhau")
    void differentIdNotEquals() {
      Bidder a = new Bidder(1L, "quan", "h", "q@x.com", LocalDateTime.now());
      Bidder b = new Bidder(2L, "quan", "h", "q@x.com", LocalDateTime.now());

      assertNotEquals(a, b);
    }
  }
}
