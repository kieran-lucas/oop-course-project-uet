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

/**
 * Kiểm thử tính đầy đủ của các DTO: constructor, getter, setter và factory method.
 *
 * <p>Bao phủ toàn bộ DTO request/response trong hệ thống để đảm bảo không có trường nào bị bỏ sót
 * hoặc ánh xạ sai. Không cần kết nối DB — pure unit test.
 */
@DisplayName("Độ đầy đủ DTO — getter, setter, factory method")
class DtoCompletenessTest {

  @Nested
  @DisplayName("LoginRequest")
  class LoginRequestTest {

    @Test
    @DisplayName("constructor đầy đủ tham số set đúng trường")
    void allArgsConstructor() {
      LoginRequest r = new LoginRequest("alice", "secret");
      assertEquals("alice", r.getUsername());
      assertEquals("secret", r.getPassword());
    }

    @Test
    @DisplayName("setter thay đổi giá trị trường")
    void setters() {
      LoginRequest r = new LoginRequest();
      r.setUsername("bob");
      r.setPassword("pass");
      assertEquals("bob", r.getUsername());
      assertEquals("pass", r.getPassword());
    }

    @Test
    @DisplayName("constructor mặc định trả về null ở mọi trường")
    void noArgConstructor() {
      LoginRequest r = new LoginRequest();
      assertNull(r.getUsername());
      assertNull(r.getPassword());
    }
  }

  @Nested
  @DisplayName("RegisterRequest")
  class RegisterRequestTest {

    @Test
    @DisplayName("constructor đầy đủ tham số")
    void allArgsConstructor() {
      RegisterRequest r = new RegisterRequest("alice", "pass123", "alice@ex.com", "BIDDER");
      assertEquals("alice", r.getUsername());
      assertEquals("pass123", r.getPassword());
      assertEquals("alice@ex.com", r.getEmail());
      assertEquals("BIDDER", r.getRole());
    }

    @Test
    @DisplayName("setter/getter roundtrip")
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

  @Nested
  @DisplayName("BidRequest")
  class BidRequestTest {

    @Test
    @DisplayName("constructor đầy đủ tham số")
    void allArgsConstructor() {
      BidRequest r = new BidRequest(new BigDecimal("1500000"));
      assertEquals(0, new BigDecimal("1500000").compareTo(r.getAmount()));
    }

    @Test
    @DisplayName("setter thay đổi amount")
    void setter() {
      BidRequest r = new BidRequest();
      r.setAmount(new BigDecimal("2000000"));
      assertEquals(0, new BigDecimal("2000000").compareTo(r.getAmount()));
    }
  }

  @Nested
  @DisplayName("ChangePasswordRequest")
  class ChangePasswordRequestTest {

    @Test
    @DisplayName("setter/getter roundtrip cho cả hai mật khẩu")
    void settersGetters() {
      ChangePasswordRequest r = new ChangePasswordRequest();
      r.setCurrentPassword("old");
      r.setNewPassword("new");
      assertEquals("old", r.getCurrentPassword());
      assertEquals("new", r.getNewPassword());
    }
  }

  @Nested
  @DisplayName("DepositRequest")
  class DepositRequestTest {

    @Test
    @DisplayName("setter và getter cho amount")
    void setterGetter() {
      DepositRequest r = new DepositRequest();
      r.setAmount(new BigDecimal("500000"));
      assertEquals(0, new BigDecimal("500000").compareTo(r.getAmount()));
    }
  }

  @Nested
  @DisplayName("ForgotPasswordRequest")
  class ForgotPasswordRequestTest {

    @Test
    @DisplayName("setter và getter cho email")
    void setterGetter() {
      ForgotPasswordRequest r = new ForgotPasswordRequest();
      r.setEmail("alice@ex.com");
      assertEquals("alice@ex.com", r.getEmail());
    }
  }

  @Nested
  @DisplayName("AutoBidRequest")
  class AutoBidRequestTest {

    @Test
    @DisplayName("constructor đầy đủ tham số")
    void allArgsConstructor() {
      AutoBidRequest r = new AutoBidRequest(new BigDecimal("5000000"), new BigDecimal("100000"));
      assertEquals(0, new BigDecimal("5000000").compareTo(r.getMaxBid()));
      assertEquals(0, new BigDecimal("100000").compareTo(r.getIncrement()));
    }

    @Test
    @DisplayName("setter/getter roundtrip")
    void setters() {
      AutoBidRequest r = new AutoBidRequest();
      r.setMaxBid(new BigDecimal("3000000"));
      r.setIncrement(new BigDecimal("50000"));
      assertEquals(0, new BigDecimal("3000000").compareTo(r.getMaxBid()));
      assertEquals(0, new BigDecimal("50000").compareTo(r.getIncrement()));
    }
  }

  @Nested
  @DisplayName("CreateItemRequest")
  class CreateItemRequestTest {

    @Test
    @DisplayName("constructor đầy đủ tham số")
    void allArgsConstructor() {
      CreateItemRequest r = new CreateItemRequest("Laptop", "Fast", "ELECTRONICS", "Dell");
      assertEquals("Laptop", r.getName());
      assertEquals("Fast", r.getDescription());
      assertEquals("ELECTRONICS", r.getCategory());
      assertEquals("Dell", r.getCategoryDetail());
    }

    @Test
    @DisplayName("setter/getter roundtrip")
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

  @Nested
  @DisplayName("CreateAuctionRequest")
  class CreateAuctionRequestTest {

    @Test
    @DisplayName("constructor đầy đủ tham số")
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
    @DisplayName("setter/getter roundtrip")
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

  @Nested
  @DisplayName("PageRequest")
  class PageRequestTest {

    @Test
    @DisplayName("offset() tính đúng page * size")
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
    @DisplayName("trang đầu (page 0) có offset = 0")
    void firstPageOffset() {
      assertEquals(0, new PageRequest(0, 15).offset());
    }
  }

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
    @DisplayName("fromAuction ánh xạ đầy đủ trường từ Auction model")
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
    @DisplayName("tất cả setter/getter roundtrip đúng")
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

  @Nested
  @DisplayName("UserResponse")
  class UserResponseTest {

    @Test
    @DisplayName("from() factory ánh xạ đúng tất cả trường User")
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
    @DisplayName("tất cả setter thay đổi đúng trường")
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

  @Nested
  @DisplayName("ErrorResponse")
  class ErrorResponseTest {

    @Test
    @DisplayName("factory of() đặt đúng error code và message")
    void ofFactory() {
      ErrorResponse r = ErrorResponse.of("NOT_FOUND", "Item not found");
      assertEquals("NOT_FOUND", r.getError());
      assertEquals("Item not found", r.getMessage());
      assertNotNull(r.getTimestamp());
    }

    @Test
    @DisplayName("setter thay đổi error, message và timestamp")
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
    @DisplayName("toString chứa error code")
    void toStringContainsErrorCode() {
      ErrorResponse r = ErrorResponse.of("INVALID_BID", "Too low");
      assertTrue(r.toString().contains("INVALID_BID"));
    }
  }
}
