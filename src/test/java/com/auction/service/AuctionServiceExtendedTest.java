package com.auction.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.auction.dao.AuctionDao;
import com.auction.dao.ItemDao;
import com.auction.dao.UserDao;
import com.auction.dto.AuctionResponse;
import com.auction.dto.CreateAuctionRequest;
import com.auction.dto.PageRequest;
import com.auction.exception.NotFoundException;
import com.auction.exception.UnauthorizedException;
import com.auction.model.Art;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.Bidder;
import com.auction.model.Electronics;
import com.auction.model.User;
import com.auction.model.Vehicle;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit test bổ sung cho {@link AuctionService} — kiểm tra luồng đọc, làm giàu dữ liệu và cập nhật.
 *
 * <p>Kiểm tra phương thức {@code getAll()}, {@code getAuctionById()}, {@code
 * enrichAuctionResponse()} (làm giàu theo loại item: Electronics/Art/Vehicle và gán username người
 * dẫn đầu), và {@code update()} với các kịch bản quyền sở hữu, trạng thái phiên, và null-field
 * handling.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuctionService — đọc, làm giàu dữ liệu và cập nhật phiên")
class AuctionServiceExtendedTest {

  @Mock private AuctionDao auctionDao;
  @Mock private ItemDao itemDao;
  @Mock private UserDao userDao;

  private AuctionService service;

  private static final Long SELLER_ID = 1L;
  private static final Long BIDDER_ID = 2L;
  private static final Long ITEM_ID = 10L;
  private static final Long AUCTION_ID = 99L;

  @BeforeEach
  void setUp() {
    service = new AuctionService(auctionDao, itemDao, userDao);
  }

  private Auction buildAuction() {
    Auction a = new Auction();
    a.setId(AUCTION_ID);
    a.setItemId(ITEM_ID);
    a.setSellerId(SELLER_ID);
    a.setStartingPrice(new BigDecimal("1000000"));
    a.setCurrentPrice(new BigDecimal("1200000"));
    a.setLeadingBidderId(BIDDER_ID);
    a.setStartTime(LocalDateTime.now().minusHours(1));
    a.setEndTime(LocalDateTime.now().plusHours(2));
    a.setStatus(AuctionStatus.RUNNING);
    return a;
  }

  private Auction buildOpenAuction() {
    Auction a = buildAuction();
    a.setLeadingBidderId(null);
    a.setStatus(AuctionStatus.OPEN);
    return a;
  }

  // ── getAll() ──────────────────────────────────────────────

  @Nested
  @DisplayName("getAll()")
  class GetAll {

    @Test
    @DisplayName("trả về danh sách đầy đủ khi không lọc status")
    void returnsAllAuctionsWhenNoFilter() {
      Auction a = buildAuction();
      Electronics item = new Electronics("Laptop", "desc", SELLER_ID, "Dell");
      item.setId(ITEM_ID);
      when(auctionDao.findAll(any(PageRequest.class))).thenReturn(List.of(a));
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.of(item));
      when(userDao.findById(BIDDER_ID)).thenReturn(Optional.empty());

      List<AuctionResponse> result = service.getAll(null);

      assertEquals(1, result.size());
      assertEquals(AUCTION_ID, result.get(0).getId());
    }

    @Test
    @DisplayName("trả về danh sách lọc khi có status")
    void returnsFilteredByStatus() {
      Auction a = buildAuction();
      when(auctionDao.findByStatus(eq("RUNNING"), any(PageRequest.class))).thenReturn(List.of(a));
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.empty());

      List<AuctionResponse> result = service.getAll("running");

      assertEquals(1, result.size());
      verify(auctionDao).findByStatus(eq("RUNNING"), any(PageRequest.class));
      verify(auctionDao, never()).findAll(any());
    }

    @Test
    @DisplayName("trả về danh sách rỗng khi không có kết quả")
    void returnsEmptyList() {
      when(auctionDao.findAll(any(PageRequest.class))).thenReturn(List.of());

      assertTrue(service.getAll(null).isEmpty());
    }

    @Test
    @DisplayName("dùng PageRequest tuỳ chỉnh khi được truyền vào")
    void respectsCustomPageRequest() {
      PageRequest page = PageRequest.of(1, 5);
      when(auctionDao.findAll(eq(page))).thenReturn(List.of());

      service.getAll(null, page);

      verify(auctionDao).findAll(page);
    }

    @Test
    @DisplayName("dùng page mặc định khi PageRequest là null")
    void usesDefaultPageWhenNull() {
      when(auctionDao.findAll(any(PageRequest.class))).thenReturn(List.of());

      service.getAll(null, null);

      verify(auctionDao).findAll(any(PageRequest.class));
    }
  }

  // ── getAuctionById() ─────────────────────────────────────

  @Nested
  @DisplayName("getAuctionById()")
  class GetAuctionById {

    @Test
    @DisplayName("trả về response đầy đủ cho auction tồn tại")
    void returnsEnrichedResponse() {
      Auction a = buildOpenAuction();
      when(auctionDao.findById(AUCTION_ID)).thenReturn(Optional.of(a));
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.empty());

      AuctionResponse r = service.getAuctionById(AUCTION_ID);

      assertEquals(AUCTION_ID, r.getId());
      assertEquals("OPEN", r.getStatus());
    }

    @Test
    @DisplayName("ném NotFoundException khi auction không tồn tại")
    void throwsNotFoundForMissingAuction() {
      when(auctionDao.findById(999L)).thenReturn(Optional.empty());

      assertThrows(NotFoundException.class, () -> service.getAuctionById(999L));
    }

    @Test
    @DisplayName("getById ủy quyền cho getAuctionById")
    void getByIdDelegatesToGetAuctionById() {
      Auction a = buildOpenAuction();
      when(auctionDao.findById(AUCTION_ID)).thenReturn(Optional.of(a));
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.empty());

      AuctionResponse r = service.getById(AUCTION_ID);

      assertEquals(AUCTION_ID, r.getId());
    }
  }

  // ── enrichAuctionResponse() — tested via getAll / getAuctionById ─

  @Nested
  @DisplayName("enrichAuctionResponse() — làm giàu theo loại item")
  class EnrichAuctionResponse {

    @Test
    @DisplayName("gán itemBrand khi item là Electronics")
    void setsItemBrandForElectronics() {
      Auction a = buildOpenAuction();
      Electronics item = new Electronics("Phone", "desc", SELLER_ID, "Samsung");
      item.setId(ITEM_ID);
      when(auctionDao.findById(AUCTION_ID)).thenReturn(Optional.of(a));
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.of(item));

      AuctionResponse r = service.getAuctionById(AUCTION_ID);

      assertEquals("Phone", r.getItemName());
      assertEquals("Samsung", r.getItemBrand());
    }

    @Test
    @DisplayName("gán itemArtist khi item là Art")
    void setsItemArtistForArt() {
      Auction a = buildOpenAuction();
      Art item = new Art("Portrait", "oil painting", SELLER_ID, "Van Gogh");
      item.setId(ITEM_ID);
      when(auctionDao.findById(AUCTION_ID)).thenReturn(Optional.of(a));
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.of(item));

      AuctionResponse r = service.getAuctionById(AUCTION_ID);

      assertEquals("Portrait", r.getItemName());
      assertEquals("Van Gogh", r.getItemArtist());
    }

    @Test
    @DisplayName("gán itemYear khi item là Vehicle")
    void setsItemYearForVehicle() {
      Auction a = buildOpenAuction();
      Vehicle item = new Vehicle("Toyota", "sedan", SELLER_ID, 2020);
      item.setId(ITEM_ID);
      when(auctionDao.findById(AUCTION_ID)).thenReturn(Optional.of(a));
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.of(item));

      AuctionResponse r = service.getAuctionById(AUCTION_ID);

      assertEquals("Toyota", r.getItemName());
      assertEquals(2020, r.getItemYear());
    }

    @Test
    @DisplayName("để item fields là null khi không tìm thấy item")
    void leavesItemFieldsNullWhenItemMissing() {
      Auction a = buildOpenAuction();
      when(auctionDao.findById(AUCTION_ID)).thenReturn(Optional.of(a));
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.empty());

      AuctionResponse r = service.getAuctionById(AUCTION_ID);

      assertNull(r.getItemName());
    }

    @Test
    @DisplayName("gán leadingBidderUsername khi leadingBidderId có giá trị")
    void setsLeadingBidderUsername() {
      Auction a = buildAuction(); // leadingBidderId = BIDDER_ID
      User bidder = new Bidder("bob", "hash", "bob@ex.com");
      bidder.setId(BIDDER_ID);
      when(auctionDao.findById(AUCTION_ID)).thenReturn(Optional.of(a));
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.empty());
      when(userDao.findById(BIDDER_ID)).thenReturn(Optional.of(bidder));

      AuctionResponse r = service.getAuctionById(AUCTION_ID);

      assertEquals("bob", r.getLeadingBidderUsername());
    }

    @Test
    @DisplayName("bỏ qua lookup leadingBidder khi leadingBidderId là null")
    void skipsLeadingBidderLookupWhenNull() {
      Auction a = buildOpenAuction(); // leadingBidderId = null
      when(auctionDao.findById(AUCTION_ID)).thenReturn(Optional.of(a));
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.empty());

      service.getAuctionById(AUCTION_ID);

      verify(userDao, never()).findById(any());
    }
  }

  // ── update(Long, CreateAuctionRequest, Long) ─────────────

  @Nested
  @DisplayName("update(auctionId, request, userId)")
  class UpdateAuction {

    private CreateAuctionRequest buildRequest() {
      CreateAuctionRequest req = new CreateAuctionRequest();
      req.setStartingPrice(new BigDecimal("1500000"));
      req.setStartTime(LocalDateTime.now().plusHours(1));
      req.setEndTime(LocalDateTime.now().plusHours(4));
      return req;
    }

    @Test
    @DisplayName("chủ sở hữu được cập nhật phiên OPEN")
    void ownerCanUpdateOpenAuction() {
      Auction a = buildOpenAuction();
      Electronics item = new Electronics("Laptop", "desc", SELLER_ID, "Dell");
      item.setId(ITEM_ID);
      when(auctionDao.findById(AUCTION_ID)).thenReturn(Optional.of(a));
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.of(item));

      AuctionResponse result = service.update(AUCTION_ID, buildRequest(), SELLER_ID);

      assertNotNull(result);
      verify(auctionDao).update(any(Auction.class));
    }

    @Test
    @DisplayName("ném IllegalStateException khi phiên không ở trạng thái OPEN")
    void throwsWhenAuctionNotOpen() {
      Auction a = buildAuction(); // RUNNING
      when(auctionDao.findById(AUCTION_ID)).thenReturn(Optional.of(a));

      assertThrows(
          IllegalStateException.class, () -> service.update(AUCTION_ID, buildRequest(), SELLER_ID));
      verify(auctionDao, never()).update(any());
    }

    @Test
    @DisplayName("ném UnauthorizedException khi người dùng không phải chủ sở hữu")
    void throwsWhenNotOwner() {
      Auction a = buildOpenAuction();
      Electronics item = new Electronics("Laptop", "desc", SELLER_ID, "Dell");
      item.setId(ITEM_ID);
      when(auctionDao.findById(AUCTION_ID)).thenReturn(Optional.of(a));
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.of(item));

      assertThrows(
          UnauthorizedException.class, () -> service.update(AUCTION_ID, buildRequest(), 99L));
      verify(auctionDao, never()).update(any());
    }

    @Test
    @DisplayName("ném NotFoundException khi auction không tồn tại")
    void throwsNotFoundWhenMissing() {
      when(auctionDao.findById(999L)).thenReturn(Optional.empty());

      assertThrows(NotFoundException.class, () -> service.update(999L, buildRequest(), SELLER_ID));
    }

    @Test
    @DisplayName("chỉ cập nhật các field không null trong request")
    void onlyUpdatesNonNullFields() {
      Auction a = buildOpenAuction();
      a.setStartingPrice(new BigDecimal("1000000"));
      Electronics item = new Electronics("Laptop", "desc", SELLER_ID, "Dell");
      item.setId(ITEM_ID);
      when(auctionDao.findById(AUCTION_ID)).thenReturn(Optional.of(a));
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.of(item));

      CreateAuctionRequest req = new CreateAuctionRequest();
      req.setStartingPrice(new BigDecimal("2000000")); // only price set

      service.update(AUCTION_ID, req, SELLER_ID);

      verify(auctionDao)
          .update(
              argThat(
                  updated -> updated.getStartingPrice().compareTo(new BigDecimal("2000000")) == 0));
    }
  }

  // ── getAllAuctions() (deprecated wrapper) ─────────────────

  @Nested
  @DisplayName("getAllAuctions() — alias cũ (deprecated)")
  class GetAllAuctions {

    @Test
    @DisplayName("trả về danh sách Auction model (không phải AuctionResponse)")
    void returnsAuctionList() {
      Auction a = buildOpenAuction();
      when(auctionDao.findAll(any(PageRequest.class))).thenReturn(List.of(a));
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.empty());

      var result = service.getAllAuctions(null);

      assertEquals(1, result.size());
      assertEquals(AUCTION_ID, result.get(0).getId());
    }

    @Test
    @DisplayName("lọc theo status khi được truyền")
    void filtersAuctionsByStatus() {
      Auction a = buildOpenAuction();
      when(auctionDao.findByStatus(eq("OPEN"), any(PageRequest.class))).thenReturn(List.of(a));
      when(itemDao.findById(ITEM_ID)).thenReturn(Optional.empty());

      var result = service.getAllAuctions("OPEN");

      assertEquals(1, result.size());
    }
  }
}
