package com.auction.service;

import com.auction.dao.AuctionDao;
import com.auction.dao.ItemDao;
import com.auction.dao.UserDao;
import com.auction.dto.AuctionResponse;
import com.auction.dto.BidUpdateMessage;
import com.auction.dto.CreateAuctionRequest;
import com.auction.dto.PageRequest;
import com.auction.exception.DuplicateException;
import com.auction.exception.NotFoundException;
import com.auction.exception.UnauthorizedException;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.Item;
import com.auction.pattern.observer.AuctionEventManager;
import com.auction.pattern.state.AuctionState;
import com.auction.pattern.state.AuctionStates;
import java.math.BigDecimal;
import java.util.List;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Service xử lý logic nghiệp vụ cho Auction (phiên đấu giá). */
public class AuctionService {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuctionService.class);

  private final AuctionDao auctionDao;
  private final ItemDao itemDao;
  private final UserDao userDao;
  private final AuctionEventManager eventManager;
  private final Jdbi jdbi;

  public AuctionService(
      AuctionDao auctionDao,
      ItemDao itemDao,
      UserDao userDao,
      AuctionEventManager eventManager,
      Jdbi jdbi) {
    this.auctionDao = auctionDao;
    this.itemDao = itemDao;
    this.userDao = userDao;
    this.eventManager = eventManager;
    this.jdbi = jdbi;
  }

  /** For unit tests that don't need notification. */
  AuctionService(AuctionDao auctionDao, ItemDao itemDao, UserDao userDao) {
    this(auctionDao, itemDao, userDao, null, null);
  }

  /**
   * Lấy tất cả auctions, có thể filter theo status.
   *
   * <p>GET /api/auctions?status=RUNNING → chỉ lấy auctions đang chạy. GET /api/auctions → lấy tất
   * cả.
   */
  @Deprecated(since = "1.1", forRemoval = true)
  public List<Auction> getAllAuctions(String status) {
    return getAll(status, PageRequest.of(0, 100)).stream().map(this::toAuction).toList();
  }

  /**
   * Alias cho getAllAuctions — dùng bởi AuctionController.
   *
   * @param status filter theo trạng thái (có thể null để lấy tất cả)
   * @return danh sách AuctionResponse đã được enrich
   */
  public List<AuctionResponse> getAll(String status) {
    return getAll(status, null);
  }

  public List<AuctionResponse> getAll(String status, PageRequest pageRequest) {
    PageRequest page = pageRequest != null ? pageRequest : PageRequest.of(0, 20);
    List<Auction> auctions;
    if (status != null && !status.isBlank()) {
      auctions = auctionDao.findByStatus(status.toUpperCase(), page);
    } else {
      auctions = auctionDao.findAll(page);
    }
    return auctions.stream().map(this::enrichAuctionResponse).toList();
  }

  /**
   * Alias cho getAuctionById — dùng bởi AuctionController.
   *
   * @param id ID của auction
   * @return AuctionResponse đầy đủ thông tin
   * @throws NotFoundException nếu auction không tồn tại
   */
  public AuctionResponse getById(Long id) {
    return getAuctionById(id);
  }

  /**
   * Lấy chi tiết 1 auction, trả về AuctionResponse (bao gồm itemName, leadingBidderUsername).
   *
   * <p>AuctionResponse chứa nhiều thông tin hơn Auction model: - itemName, itemCategory: lấy từ
   * bảng items - leadingBidderUsername: lấy từ bảng users
   *
   * @param id ID của auction
   * @return AuctionResponse đầy đủ thông tin
   * @throws NotFoundException nếu auction không tồn tại
   */
  public AuctionResponse getAuctionById(Long id) {
    // Tìm auction
    Auction auction =
        auctionDao
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Auction not found with id: " + id));

    // Chuyển Auction → AuctionResponse (thêm itemName, leadingBidderUsername)
    return enrichAuctionResponse(auction);
  }

  /**
   * Tạo phiên đấu giá mới — seller đã được xác thực bởi AuctionController.
   *
   * @param req dữ liệu từ client
   * @param sellerId ID seller từ JWT
   */
  public Auction create(CreateAuctionRequest req, Long sellerId) {
    if (req.getItemId() == null) {
      throw new IllegalArgumentException("Item ID is required");
    }
    if (req.getStartingPrice() == null || req.getStartingPrice().signum() <= 0) {
      throw new IllegalArgumentException("Starting price must be greater than 0");
    }
    if (req.getStartTime() == null || req.getEndTime() == null) {
      throw new IllegalArgumentException("Start time and end time are required");
    }
    if (!req.getEndTime().isAfter(req.getStartTime())) {
      throw new IllegalArgumentException("End time must be after start time");
    }

    if (jdbi != null) {
      return createInTransaction(req, sellerId);
    }

    Item item =
        itemDao
            .findById(req.getItemId())
            .orElseThrow(() -> new NotFoundException("Item not found with id: " + req.getItemId()));

    validateItemCanBeAuctioned(req.getItemId(), sellerId, item);

    Auction auction =
        new Auction(req.getItemId(), req.getStartingPrice(), req.getStartTime(), req.getEndTime());
    auction.setSellerId(sellerId);
    auctionDao.insert(auction);
    item.setStatus("IN_AUCTION");
    itemDao.update(item);
    LOGGER.info("Auction created (via create): itemId={}, seller={}", req.getItemId(), sellerId);
    return auction;
  }

  private Auction createInTransaction(CreateAuctionRequest req, Long sellerId) {
    return jdbi.inTransaction(
        handle -> {
          Item item =
              itemDao
                  .findByIdForUpdate(handle, req.getItemId())
                  .orElseThrow(
                      () -> new NotFoundException("Item not found with id: " + req.getItemId()));

          validateItemCanBeAuctionedInTransaction(handle, req.getItemId(), sellerId, item);

          Auction auction =
              new Auction(
                  req.getItemId(), req.getStartingPrice(), req.getStartTime(), req.getEndTime());
          auction.setSellerId(sellerId);
          auctionDao.insertInTransaction(handle, auction);
          itemDao.updateStatusInTransaction(handle, req.getItemId(), "IN_AUCTION");

          LOGGER.info(
              "Auction created (via create): itemId={}, seller={}", req.getItemId(), sellerId);
          return auction;
        });
  }

  private void validateItemCanBeAuctioned(Long itemId, Long sellerId, Item item) {
    if (!item.getSellerId().equals(sellerId)) {
      throw new UnauthorizedException("Bạn chỉ có thể tạo phiên đấu giá cho sản phẩm của mình");
    }

    if (!"AVAILABLE".equals(item.getStatus())) {
      throw new DuplicateException("Sản phẩm #" + itemId + " không còn ở trạng thái AVAILABLE");
    }

    if (auctionDao.existsActiveAuctionForItem(itemId)) {
      throw new DuplicateException("Sản phẩm #" + itemId + " đã có phiên đấu giá đang hoạt động");
    }

    if (auctionDao.existsPaidAuctionForItem(itemId)) {
      throw new DuplicateException("Sản phẩm #" + itemId + " đã được bán");
    }
  }

  private void validateItemCanBeAuctionedInTransaction(
      org.jdbi.v3.core.Handle handle, Long itemId, Long sellerId, Item item) {
    if (!item.getSellerId().equals(sellerId)) {
      throw new UnauthorizedException("Bạn chỉ có thể tạo phiên đấu giá cho sản phẩm của mình");
    }

    if (!"AVAILABLE".equals(item.getStatus())) {
      throw new DuplicateException("Sản phẩm #" + itemId + " không còn ở trạng thái AVAILABLE");
    }

    if (auctionDao.existsActiveAuctionForItem(handle, itemId)) {
      throw new DuplicateException("Sản phẩm #" + itemId + " đã có phiên đấu giá đang hoạt động");
    }

    if (auctionDao.existsPaidAuctionForItem(handle, itemId)) {
      throw new DuplicateException("Sản phẩm #" + itemId + " đã được bán");
    }
  }

  /**
   * Cập nhật giá khởi điểm của phiên (simplified — chỉ nhận BigDecimal). Kiểm tra trạng thái qua
   * State pattern trước khi sửa.
   */
  public Auction update(Long auctionId, BigDecimal newPrice, Long sellerId) {
    Auction auction =
        auctionDao
            .findById(auctionId)
            .orElseThrow(() -> new NotFoundException("Auction not found: " + auctionId));

    // Dùng State pattern để validate — OpenState cho phép, RunningState/FinishedState từ chối
    getState(auction).edit(auction);

    auction.setStartingPrice(newPrice);
    auction.setCurrentPrice(newPrice);
    auctionDao.update(auction);
    return auction;
  }

  /**
   * Cập nhật phiên đấu giá và trả về AuctionResponse — dùng bởi AuctionController.
   *
   * <p>Chỉ cho phép sửa khi status = OPEN. Seller chỉ được sửa phiên của item mình.
   *
   * @param auctionId ID phiên cần sửa
   * @param request dữ liệu mới (startingPrice, startTime, endTime)
   * @param userId ID seller từ JWT
   * @return AuctionResponse đã cập nhật
   */
  public AuctionResponse update(Long auctionId, CreateAuctionRequest request, Long userId) {
    Auction auction =
        auctionDao
            .findById(auctionId)
            .orElseThrow(() -> new NotFoundException("Auction not found with id: " + auctionId));

    if (auction.getStatus() != AuctionStatus.OPEN) {
      throw new IllegalStateException(
          "Can only edit auctions with OPEN status. Current status: " + auction.getStatus());
    }

    Item item =
        itemDao
            .findById(auction.getItemId())
            .orElseThrow(() -> new NotFoundException("Item not found"));

    if (!item.getSellerId().equals(userId)) {
      throw new UnauthorizedException("You can only edit your own auctions");
    }

    if (request.getStartingPrice() != null && request.getStartingPrice().signum() > 0) {
      auction.setStartingPrice(request.getStartingPrice());
      auction.setCurrentPrice(request.getStartingPrice());
    }
    if (request.getStartTime() != null) {
      auction.setStartTime(request.getStartTime());
    }
    if (request.getEndTime() != null) {
      auction.setEndTime(request.getEndTime());
    }

    auctionDao.update(auction);
    LOGGER.info("Auction updated: id={}", auctionId);
    return enrichAuctionResponse(auction);
  }

  /**
   * Hủy phiên đấu giá (soft delete — chuyển status sang CANCELED).
   *
   * <p>Phân quyền:
   *
   * <ul>
   *   <li>ADMIN → hủy được bất kỳ phiên nào ở mọi trạng thái.
   *   <li>SELLER → chỉ hủy phiên của chính mình, khi status là OPEN hoặc RUNNING (trường hợp bất
   *       khả kháng).
   *   <li>Các role khác → {@link UnauthorizedException}.
   * </ul>
   *
   * @param auctionId ID phiên cần hủy
   * @param userId ID người thực hiện (từ JWT)
   * @param role role của người thực hiện ("ADMIN" hoặc "SELLER")
   * @throws NotFoundException nếu auction không tồn tại
   * @throws UnauthorizedException nếu SELLER cố hủy phiên của người khác, hoặc role không hợp lệ
   * @throws IllegalStateException nếu SELLER cố hủy phiên đã FINISHED / PAID / CANCELED
   */
  public void delete(Long auctionId, Long userId, String role) {
    Auction auction =
        auctionDao
            .findById(auctionId)
            .orElseThrow(() -> new NotFoundException("Auction not found: " + auctionId));

    // ADMIN: được hủy bất chấp mọi trạng thái
    if ("ADMIN".equals(role)) {
      AuctionStatus previousStatus = auction.getStatus();
      auction.setStatus(AuctionStatus.CANCELED);
      persistCanceledAuction(auction, previousStatus);
      notifyLeadingBidderIfCanceled(auction);
      LOGGER.info("ADMIN (userId={}) đã cưỡng chế hủy phiên đấu giá #{}", userId, auctionId);
      return;
    }

    // SELLER: chỉ hủy phiên của chính mình, khi status = OPEN hoặc RUNNING
    if ("SELLER".equals(role)) {
      Long actualSellerId = auction.getSellerId();
      if (actualSellerId == null) {
        Item item =
            itemDao
                .findById(auction.getItemId())
                .orElseThrow(() -> new NotFoundException("Item not found"));
        actualSellerId = item.getSellerId();
      }

      if (!actualSellerId.equals(userId)) {
        throw new UnauthorizedException("Bạn không có quyền hủy phiên đấu giá của người khác!");
      }

      if (AuctionStatus.RUNNING == auction.getStatus() && !"ADMIN".equals(role)) {
        throw new IllegalStateException("Cannot cancel a running auction. Only ADMIN is allowed.");
      }

      AuctionStatus status = auction.getStatus();
      if (status != AuctionStatus.OPEN && status != AuctionStatus.RUNNING) {
        throw new IllegalStateException(
            "Chỉ có thể hủy phiên đấu giá khi đang ở trạng thái OPEN hoặc RUNNING."
                + " Trạng thái hiện tại: "
                + status);
      }

      AuctionStatus previousStatus = status;
      auction.setStatus(AuctionStatus.CANCELED);
      persistCanceledAuction(auction, previousStatus);
      notifyLeadingBidderIfCanceled(auction);
      LOGGER.info(
          "SELLER (userId={}) đã hủy phiên đấu giá #{} (trạng thái trước: {})",
          userId,
          auctionId,
          status);
      return;
    }

    // Các role khác (BIDDER, ...) không được phép
    throw new UnauthorizedException("Bạn không có quyền thực hiện thao tác này.");
  }

  /**
   * Xóa cứng phiên đấu giá khỏi DB (chỉ ADMIN). Xóa toàn bộ bid_transactions và auto_bid_configs
   * liên quan trong cùng một transaction.
   */
  public void hardDelete(Long auctionId) {
    auctionDao
        .findById(auctionId)
        .orElseThrow(() -> new NotFoundException("Auction not found: " + auctionId));
    auctionDao.hardDelete(auctionId);
    LOGGER.info("Admin hard-deleted auction #{}", auctionId);
  }

  /**
   * Đặt giá thông qua State pattern (dùng trong test và AuctionScheduler). Không persist xuống DB —
   * chỉ validate state và update in-memory.
   */
  public void placeBidViaState(Long auctionId, Long bidderId, BigDecimal amount) {
    Auction auction =
        auctionDao
            .findById(auctionId)
            .orElseThrow(() -> new NotFoundException("Auction not found: " + auctionId));
    getState(auction).placeBid(auction, amount, bidderId);
  }

  /**
   * Trả về AuctionState tương ứng với trạng thái hiện tại của auction. Dùng để delegate behavior
   * theo State pattern.
   *
   * @param auction auction cần resolve state
   * @return AuctionState implementation tương ứng
   */
  public AuctionState getState(Auction auction) {
    return switch (auction.getStatus()) {
      case OPEN -> AuctionStates.OPEN;
      case RUNNING, SETTLING -> AuctionStates.RUNNING;
      case FINISHED -> AuctionStates.FINISHED;
      case PAID -> AuctionStates.PAID;
      case CANCELED -> AuctionStates.CANCELED;
    };
  }

  private void notifyLeadingBidderIfCanceled(Auction auction) {
    if (eventManager != null && jdbi != null && auction.getLeadingBidderId() != null) {
      BidUpdateMessage msg =
          BidUpdateMessage.auctionEnded(auction.getId(), auction.getCurrentPrice(), null, null);
      eventManager.notifyAuctionEnd(auction.getId(), msg);
      jdbi.useHandle(
          h ->
              h.execute(
                  "INSERT INTO notifications (user_id, message, notification_type) "
                      + "VALUES (?, ?, 'AUCTION_CANCELED')",
                  auction.getLeadingBidderId(),
                  "Phiên đấu giá #" + auction.getId() + " đã bị hủy bởi người bán."));
    }
  }

  private void persistCanceledAuction(Auction auction, AuctionStatus previousStatus) {
    if (jdbi == null) {
      auctionDao.update(auction);
      return;
    }

    jdbi.useTransaction(
        handle -> {
          if (previousStatus == AuctionStatus.RUNNING && auction.getLeadingBidderId() != null) {
            userDao.releaseReservedBalanceInTransaction(
                handle, auction.getLeadingBidderId(), auction.getCurrentPrice());
          }
          auctionDao.updateInTransaction(handle, auction);
        });
  }

  private Auction toAuction(AuctionResponse response) {
    Auction auction = new Auction();
    auction.setId(response.getId());
    auction.setItemId(response.getItemId());
    auction.setSellerId(response.getSellerId());
    auction.setStartingPrice(response.getStartingPrice());
    auction.setCurrentPrice(response.getCurrentPrice());
    auction.setLeadingBidderId(response.getLeadingBidderId());
    auction.setStartTime(response.getStartTime());
    auction.setEndTime(response.getEndTime());
    if (response.getStatus() != null) {
      auction.setStatus(AuctionStatus.from(response.getStatus()));
    }
    return auction;
  }

  /**
   * Bổ sung thông tin itemName, leadingBidderUsername và các field riêng theo category (itemBrand,
   * itemArtist, itemYear) vào AuctionResponse.
   *
   * <p>AuctionResponse.fromAuction() chỉ copy dữ liệu từ Auction model. Method này bổ sung thêm
   * thông tin từ bảng items và users, bao gồm các field đặc thù theo loại sản phẩm:
   *
   * <ul>
   *   <li>ELECTRONICS → itemBrand (hãng sản xuất)
   *   <li>ART → itemArtist (nghệ sĩ)
   *   <li>VEHICLE → itemYear (năm sản xuất)
   * </ul>
   */
  private AuctionResponse enrichAuctionResponse(Auction auction) {
    AuctionResponse response = AuctionResponse.fromAuction(auction);

    // Bổ sung itemName, itemCategory, itemDescription + category-specific fields
    itemDao
        .findById(auction.getItemId())
        .ifPresent(
            item -> {
              response.setItemName(item.getName());
              response.setItemCategory(item.getCategory());
              response.setItemDescription(item.getDescription());

              // Bổ sung field đặc thù theo loại sản phẩm (từ flat model)
              response.setItemBrand(item.getBrand());
              response.setItemArtist(item.getArtist());
              response.setItemYear(item.getYear());
            });

    // Bổ sung leadingBidderUsername
    if (auction.getLeadingBidderId() != null) {
      userDao
          .findById(auction.getLeadingBidderId())
          .ifPresent(user -> response.setLeadingBidderUsername(user.getUsername()));
    }

    return response;
  }
}
