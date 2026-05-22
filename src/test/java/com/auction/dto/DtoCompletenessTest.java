package com.auction.dto;

import static org.junit.jupiter.api.Assertions.*;

import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.Bidder;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DTO completeness — getters, setters, factory methods")
class DtoCompletenessTest {

  // ── LoginRequest ──────────────────────────────────────────

  @Nested
  @DisplayName("LoginRequest")
  class LoginRequestTest {

    @Test
    @DisplayName("all-args constructor sets fields correctly")
    void allArgsConstructor() {
      LoginRequest r = new LoginRequest("alice", "secret");
      assertEquals("alice", r.getUsername());
      assertEquals("secret", r.getPassword());
    }

    @Test
    @DisplayName("setters mutate fields")
    void setters() {
      LoginRequest r = new LoginRequest();
      r.setUsername("bob");
      r.setPassword("pass");
      assertEquals("bob", r.getUsername());
      assertEquals("pass", r.getPassword());
    }

    @Test
    @DisplayName("no-arg constructor produces null fields")
    void noArgConstructor() {
      LoginRequest r = new LoginRequest();
      assertNull(r.getUsername());
      assertNull(r.getPassword());
    }
  }

  // ── RegisterRequest ───────────────────────────────────────

  @Nested
  @DisplayName("RegisterRequest")
  class RegisterRequestTest {

    @Test
    @DisplayName("all-args constructor")
    void allArgsConstructor() {
      RegisterRequest r = new RegisterRequest("alice", "pass123", "alice@ex.com", "BIDDER");
      assertEquals("alice", r.getUsername());
      assertEquals("pass123", r.getPassword());
      assertEquals("alice@ex.com", r.getEmail());
      assertEquals("BIDDER", r.getRole());
    }

    @Test
    @DisplayName("setters roundtrip")
    void settersRoundtrip() {
      RegisterRequest r = new RegisterRequest();
      r.setUsername("bob");
      r.setPassword("pw");
      r.setEmail("bob@ex.com");
      r.setRole("SELLER");
      assertEquals("bob", r.getUsername());
      assertEquals("pw", r.getPassword());
      assertEquals("bob@ex.com", r.getEmail());
      assertEquals("SELLER", r.getRole());
    }
  }

  // ── BidRequest ────────────────────────────────────────────

  @Nested
  @DisplayName("BidRequest")
  class BidRequestTest {

    @Test
    @DisplayName("all-args constructor")
    void allArgsConstructor() {
      BidRequest r = new BidRequest(new BigDecimal("1500000"));
      assertEquals(0, new BigDecimal("1500000").compareTo(r.getAmount()));
    }

    @Test
    @DisplayName("setter mutates amount")
    void setter() {
      BidRequest r = new BidRequest();
      r.setAmount(new BigDecimal("2000000"));
      assertEquals(0, new BigDecimal("2000000").compareTo(r.getAmount()));
    }
  }

  // ── ChangePasswordRequest ─────────────────────────────────

  @Nested
  @DisplayName("ChangePasswordRequest")
  class ChangePasswordRequestTest {

    @Test
    @DisplayName("setters and getters roundtrip")
    void settersGetters() {
      ChangePasswordRequest r = new ChangePasswordRequest();
      r.setCurrentPassword("old");
      r.setNewPassword("new");
      assertEquals("old", r.getCurrentPassword());
      assertEquals("new", r.getNewPassword());
    }
  }

  // ── DepositRequest ────────────────────────────────────────

  @Nested
  @DisplayName("DepositRequest")
  class DepositRequestTest {

    @Test
    @DisplayName("setter and getter for amount")
    void setterGetter() {
      DepositRequest r = new DepositRequest();
      r.setAmount(new BigDecimal("500000"));
      assertEquals(0, new BigDecimal("500000").compareTo(r.getAmount()));
    }
  }

  // ── ForgotPasswordRequest ─────────────────────────────────

  @Nested
  @DisplayName("ForgotPasswordRequest")
  class ForgotPasswordRequestTest {

    @Test
    @DisplayName("setter and getter for email")
    void setterGetter() {
      ForgotPasswordRequest r = new ForgotPasswordRequest();
      r.setEmail("alice@ex.com");
      assertEquals("alice@ex.com", r.getEmail());
    }
  }

  // ── AutoBidRequest ────────────────────────────────────────

  @Nested
  @DisplayName("AutoBidRequest")
  class AutoBidRequestTest {

    @Test
    @DisplayName("all-args constructor")
    void allArgsConstructor() {
      AutoBidRequest r = new AutoBidRequest(new BigDecimal("5000000"), new BigDecimal("100000"));
      assertEquals(0, new BigDecimal("5000000").compareTo(r.getMaxBid()));
      assertEquals(0, new BigDecimal("100000").compareTo(r.getIncrement()));
    }

    @Test
    @DisplayName("setters roundtrip")
    void setters() {
      AutoBidRequest r = new AutoBidRequest();
      r.setMaxBid(new BigDecimal("3000000"));
      r.setIncrement(new BigDecimal("50000"));
      assertEquals(0, new BigDecimal("3000000").compareTo(r.getMaxBid()));
      assertEquals(0, new BigDecimal("50000").compareTo(r.getIncrement()));
    }
  }

  // ── CreateItemRequest ─────────────────────────────────────

  @Nested
  @DisplayName("CreateItemRequest")
  class CreateItemRequestTest {

    @Test
    @DisplayName("all-args constructor")
    void allArgsConstructor() {
      CreateItemRequest r = new CreateItemRequest("Laptop", "Fast", "ELECTRONICS", "Dell");
      assertEquals("Laptop", r.getName());
      assertEquals("Fast", r.getDescription());
      assertEquals("ELECTRONICS", r.getCategory());
      assertEquals("Dell", r.getCategoryDetail());
    }

    @Test
    @DisplayName("setters roundtrip")
    void setters() {
      CreateItemRequest r = new CreateItemRequest();
      r.setName("Painting");
      r.setDescription("Nice art");
      r.setCategory("ART");
      r.setCategoryDetail("Van Gogh");
      assertEquals("Painting", r.getName());
      assertEquals("Nice art", r.getDescription());
      assertEquals("ART", r.getCategory());
      assertEquals("Van Gogh", r.getCategoryDetail());
    }
  }

  // ── CreateAuctionRequest ──────────────────────────────────

  @Nested
  @DisplayName("CreateAuctionRequest")
  class CreateAuctionRequestTest {

    @Test
    @DisplayName("all-args constructor")
    void allArgsConstructor() {
      LocalDateTime start = LocalDateTime.now().plusHours(1);
      LocalDateTime end = LocalDateTime.now().plusHours(3);
      CreateAuctionRequest r = new CreateAuctionRequest(42L, new BigDecimal("1000000"), start, end);
      assertEquals(42L, r.getItemId());
      assertEquals(0, new BigDecimal("1000000").compareTo(r.getStartingPrice()));
      assertEquals(start, r.getStartTime());
      assertEquals(end, r.getEndTime());
    }

    @Test
    @DisplayName("setters roundtrip")
    void setters() {
      CreateAuctionRequest r = new CreateAuctionRequest();
      LocalDateTime start = LocalDateTime.now().plusHours(2);
      LocalDateTime end = LocalDateTime.now().plusHours(5);
      r.setItemId(10L);
      r.setStartingPrice(new BigDecimal("2000000"));
      r.setStartTime(start);
      r.setEndTime(end);
      assertEquals(10L, r.getItemId());
      assertEquals(0, new BigDecimal("2000000").compareTo(r.getStartingPrice()));
      assertEquals(start, r.getStartTime());
      assertEquals(end, r.getEndTime());
    }
  }

  // ── PageRequest ───────────────────────────────────────────

  @Nested
  @DisplayName("PageRequest")
  class PageRequestTest {

    @Test
    @DisplayName("offset() computes page * size")
    void offsetComputation() {
      PageRequest r = new PageRequest(2, 10);
      assertEquals(20, r.offset());
    }

    @Test
    @DisplayName("PageRequest.of() factory method")
    void factoryMethod() {
      PageRequest r = PageRequest.of(0, 20);
      assertEquals(0, r.page());
      assertEquals(20, r.size());
      assertEquals(0, r.offset());
    }

    @Test
    @DisplayName("page 0 offset is 0")
    void firstPageOffset() {
      assertEquals(0, new PageRequest(0, 15).offset());
    }
  }

  // ── AuctionResponse ───────────────────────────────────────

  @Nested
  @DisplayName("AuctionResponse")
  class AuctionResponseTest {

    private Auction buildAuction() {
      Auction a = new Auction();
      a.setId(5L);
      a.setItemId(10L);
      a.setSellerId(1L);
      a.setStartingPrice(new BigDecimal("1000000"));
      a.setCurrentPrice(new BigDecimal("1500000"));
      a.setLeadingBidderId(2L);
      a.setStartTime(LocalDateTime.now().minusHours(1));
      a.setEndTime(LocalDateTime.now().plusHours(2));
      a.setStatus(AuctionStatus.RUNNING);
      return a;
    }

    @Test
    @DisplayName("fromAuction maps all fields from the Auction model")
    void fromAuctionMapsFields() {
      Auction a = buildAuction();
      AuctionResponse r = AuctionResponse.fromAuction(a);
      assertEquals(5L, r.getId());
      assertEquals(10L, r.getItemId());
      assertEquals(1L, r.getSellerId());
      assertEquals("RUNNING", r.getStatus());
      assertEquals(2L, r.getLeadingBidderId());
      assertEquals(0, new BigDecimal("1000000").compareTo(r.getStartingPrice()));
      assertEquals(0, new BigDecimal("1500000").compareTo(r.getCurrentPrice()));
    }

    @Test
    @DisplayName("all setters roundtrip correctly")
    void settersRoundtrip() {
      AuctionResponse r = new AuctionResponse();
      r.setId(1L);
      r.setItemName("Laptop");
      r.setItemCategory("ELECTRONICS");
      r.setItemDescription("Fast machine");
      r.setItemBrand("Dell");
      r.setItemArtist("Some artist");
      r.setItemYear(2023);
      r.setLeadingBidderUsername("alice");
      r.setRemainingTimeMs(3600000L);
      assertEquals(1L, r.getId());
      assertEquals("Laptop", r.getItemName());
      assertEquals("ELECTRONICS", r.getItemCategory());
      assertEquals("Fast machine", r.getItemDescription());
      assertEquals("Dell", r.getItemBrand());
      assertEquals("Some artist", r.getItemArtist());
      assertEquals(2023, r.getItemYear());
      assertEquals("alice", r.getLeadingBidderUsername());
      assertEquals(3600000L, r.getRemainingTimeMs());
    }
  }

  // ── UserResponse ──────────────────────────────────────────

  @Nested
  @DisplayName("UserResponse")
  class UserResponseTest {

    @Test
    @DisplayName("from() factory maps all User fields")
    void fromFactoryMapsFields() {
      Bidder user = new Bidder("alice", "hash", "alice@ex.com");
      user.setId(42L);
      user.setBalance(new BigDecimal("500000"));

      UserResponse r = UserResponse.from(user);

      assertEquals(42L, r.getId());
      assertEquals("alice", r.getUsername());
      assertEquals("alice@ex.com", r.getEmail());
      assertEquals("BIDDER", r.getRole());
      assertEquals(0, new BigDecimal("500000").compareTo(r.getBalance()));
    }

    @Test
    @DisplayName("setters mutate all fields")
    void setters() {
      UserResponse r = new UserResponse();
      LocalDateTime now = LocalDateTime.now();
      r.setId(1L);
      r.setUsername("bob");
      r.setEmail("bob@ex.com");
      r.setRole("SELLER");
      r.setBalance(new BigDecimal("100000"));
      r.setAvailableBalance(new BigDecimal("80000"));
      r.setCreatedAt(now);
      assertEquals(1L, r.getId());
      assertEquals("bob", r.getUsername());
      assertEquals("bob@ex.com", r.getEmail());
      assertEquals("SELLER", r.getRole());
      assertEquals(0, new BigDecimal("100000").compareTo(r.getBalance()));
      assertEquals(0, new BigDecimal("80000").compareTo(r.getAvailableBalance()));
      assertEquals(now, r.getCreatedAt());
    }
  }

  // ── ErrorResponse ─────────────────────────────────────────

  @Nested
  @DisplayName("ErrorResponse")
  class ErrorResponseTest {

    @Test
    @DisplayName("of() factory sets error code and message")
    void ofFactory() {
      ErrorResponse r = ErrorResponse.of("NOT_FOUND", "Item not found");
      assertEquals("NOT_FOUND", r.getError());
      assertEquals("Item not found", r.getMessage());
      assertNotNull(r.getTimestamp());
    }

    @Test
    @DisplayName("setters mutate error, message, and timestamp")
    void setters() {
      ErrorResponse r = new ErrorResponse();
      LocalDateTime ts = LocalDateTime.now();
      r.setError("DUPLICATE");
      r.setMessage("Already exists");
      r.setTimestamp(ts);
      assertEquals("DUPLICATE", r.getError());
      assertEquals("Already exists", r.getMessage());
      assertEquals(ts, r.getTimestamp());
    }

    @Test
    @DisplayName("toString includes error code")
    void toStringContainsErrorCode() {
      ErrorResponse r = ErrorResponse.of("INVALID_BID", "Too low");
      assertTrue(r.toString().contains("INVALID_BID"));
    }
  }
}
