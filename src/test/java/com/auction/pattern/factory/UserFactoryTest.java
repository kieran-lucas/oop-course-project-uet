package com.auction.pattern.factory;

import static org.junit.jupiter.api.Assertions.*;

import com.auction.model.Admin;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import com.auction.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử {@link UserFactory} — factory tạo đúng kiểu con User từ chuỗi vai trò.
 *
 * <p>Bao phủ ba vai trò hợp lệ (BIDDER, SELLER, ADMIN), không phân biệt hoa thường, và trường hợp
 * vai trò không hợp lệ. Không cần kết nối DB — pure unit test.
 */
@DisplayName("UserFactory")
class UserFactoryTest {

  @Test
  @DisplayName("tạo Bidder khi role là 'BIDDER'")
  void createsBidder() {
    User user = UserFactory.create("BIDDER");
    assertInstanceOf(Bidder.class, user);
    assertEquals("BIDDER", user.getRole());
  }

  @Test
  @DisplayName("tạo Seller khi role là 'SELLER'")
  void createsSeller() {
    User user = UserFactory.create("SELLER");
    assertInstanceOf(Seller.class, user);
    assertEquals("SELLER", user.getRole());
  }

  @Test
  @DisplayName("tạo Admin khi role là 'ADMIN'")
  void createsAdmin() {
    User user = UserFactory.create("ADMIN");
    assertInstanceOf(Admin.class, user);
    assertEquals("ADMIN", user.getRole());
  }

  @Test
  @DisplayName("chấp nhận role chữ thường (case-insensitive)")
  void caseInsensitive() {
    assertInstanceOf(Bidder.class, UserFactory.create("bidder"));
    assertInstanceOf(Seller.class, UserFactory.create("seller"));
  }

  @Test
  @DisplayName("trả về instance mới mỗi lần gọi (không cache)")
  void returnsFreshInstance() {
    User first = UserFactory.create("BIDDER");
    User second = UserFactory.create("BIDDER");
    assertNotSame(first, second);
  }

  @Test
  @DisplayName("ném IllegalArgumentException cho role không hợp lệ")
  void rejectsUnknownRole() {
    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> UserFactory.create("GUEST"));
    assertTrue(ex.getMessage().contains("GUEST"));
  }
}
