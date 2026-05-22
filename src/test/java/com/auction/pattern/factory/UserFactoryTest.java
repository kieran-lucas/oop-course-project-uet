package com.auction.pattern.factory;

import static org.junit.jupiter.api.Assertions.*;

import com.auction.model.Admin;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import com.auction.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UserFactory")
class UserFactoryTest {

  @Test
  @DisplayName("creates a Bidder when role is 'BIDDER'")
  void createsBidder() {
    User user = UserFactory.create("BIDDER");
    assertInstanceOf(Bidder.class, user);
    assertEquals("BIDDER", user.getRole());
  }

  @Test
  @DisplayName("creates a Seller when role is 'SELLER'")
  void createsSeller() {
    User user = UserFactory.create("SELLER");
    assertInstanceOf(Seller.class, user);
    assertEquals("SELLER", user.getRole());
  }

  @Test
  @DisplayName("creates an Admin when role is 'ADMIN'")
  void createsAdmin() {
    User user = UserFactory.create("ADMIN");
    assertInstanceOf(Admin.class, user);
    assertEquals("ADMIN", user.getRole());
  }

  @Test
  @DisplayName("accepts lowercase role input")
  void caseInsensitive() {
    assertInstanceOf(Bidder.class, UserFactory.create("bidder"));
    assertInstanceOf(Seller.class, UserFactory.create("seller"));
  }

  @Test
  @DisplayName("returns a new instance for every call (no caching)")
  void returnsFreshInstance() {
    User first = UserFactory.create("BIDDER");
    User second = UserFactory.create("BIDDER");
    assertNotSame(first, second);
  }

  @Test
  @DisplayName("rejects unknown role with IllegalArgumentException")
  void rejectsUnknownRole() {
    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> UserFactory.create("GUEST"));
    assertTrue(ex.getMessage().contains("GUEST"));
  }
}
