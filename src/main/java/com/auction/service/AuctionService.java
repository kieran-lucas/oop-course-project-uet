package com.auction.service;

import com.auction.dao.AuctionDao;
import com.auction.dao.ItemDao;
import com.auction.dao.UserDao;
import com.auction.dto.AuctionResponse;
import com.auction.dto.CreateAuctionRequest;
import com.auction.exception.AuctionClosedException;
import com.auction.exception.NotFoundException;
import com.auction.exception.UnauthorizedException;
import com.auction.model.Auction;
import com.auction.model.Item;
import com.auction.pattern.state.AuctionState;
import com.auction.pattern.state.CanceledState;
import com.auction.pattern.state.FinishedState;
import com.auction.pattern.state.OpenState;
import com.auction.pattern.state.PaidState;
import com.auction.pattern.state.RunningState;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Service xử lý logic nghiệp vụ cho Auction (phiên đấu giá). */
public class AuctionService {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuctionService.class);

  private final AuctionDao auctionDao;
  private final ItemDao itemDao;
  private final UserDao userDao;

  public AuctionService(AuctionDao auctionDao, ItemDao itemDao, UserDao userDao) {
    this.auctionDao = auctionDao;
    this.itemDao = itemDao;
    this.userDao = userDao;
  }

  /**
   * Lấy tất cả auctions, có thể filter theo status.
   *
   * <p>GET /api/auctions?status=RUNNING → chỉ lấy auctions đang chạy. GET /api/auctions → lấy tất
   * cả.
   */
  public List<Auction> getAllAuctions(String status) {
    if (status != null && !status.isBlank()) {
      return auctionDao.findByStatus(status.toUpperCase());
    }
    return auctionDao.findAll();
  }

  /**
   * Alias cho getAllAuctions — dùng bởi AuctionController.
   *
   * @param status filter theo trạng thái (có thể null để lấy tất cả)
   * @return danh sách AuctionResponse đã được enrich
   */
  public List<AuctionResponse> getAll(String status) {
    return getAllAuctions(status).stream()
        .map(this::enrichAuctionResponse)
        .collect(Collectors.toList());
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
   * Seller tạo phiên đấu giá mới.
   *
   * <p>Kiểm tra: 1. Item có tồn tại không? 2. Item có thuộc seller này không? 3. startTime <
   * endTime? 4. startingPrice > 0?
   *
   * @param request dữ liệu từ client
   * @param userId ID seller từ JWT
   * @param role role từ JWT (phải là SELLER)
   * @throws NotFoundException nếu item không tồn tại
   * @throws UnauthorizedException nếu item không thuộc seller
   * @throws IllegalArgumentException nếu input không hợp lệ
   */
  public Auction createAuction(CreateAuctionRequest request, Long userId, String role) {
    // Kiểm tra role
    if (!"SELLER".equals(role)) {
      throw new UnauthorizedException("Only sellers can create auctions");
    }

    // Validate input
    if (request.getItemId() == null) {
      throw new IllegalArgumentException("Item ID is required");
    }
    if (request.getStartingPrice() == null || request.getStartingPrice().signum() <= 0) {
      throw new IllegalArgumentException("Starting price must be greater than 0");
    }
    if (request.getStartTime() == null || request.getEndTime() == null) {
      throw new IllegalArgumentException("Start time and end time are required");
    }
    if (!request.getEndTime().isAfter(request.getStartTime())) {
      throw new IllegalArgumentException("End time must be after start time");
    }

    // Kiểm tra item tồn tại
    Item item =
        itemDao
            .findById(request.getItemId())
            .orElseThrow(
                () -> new NotFoundException("Item not found with id: " + request.getItemId()));

    // Kiểm tra item thuộc seller này
    if (!item.getSellerId().equals(userId)) {
      throw new UnauthorizedException("You can only create auctions for your own items");
    }

    // Tạo Auction mới
    Auction auction =
        new Auction(
            request.getItemId(),
            request.getStartingPrice(),
            request.getStartTime(),
            request.getEndTime());

    Auction saved = auctionDao.insert(auction);
    LOGGER.info(
        "Auction created: id={}, itemId={}, seller={}", saved.getId(), saved.getItemId(), userId);
    return saved;
  }

  /**
   * Seller cập nhật phiên đấu giá.
   *
   * <p>Chỉ cho phép sửa khi status = OPEN (chưa bắt đầu). Khi đã RUNNING hoặc FINISHED thì không
   * sửa được.
   *
   * @param auctionId ID auction cần sửa
   * @param request dữ liệu mới
   * @param userId ID user từ JWT
   * @throws NotFoundException nếu auction không tồn tại
   * @throws UnauthorizedException nếu không có quyền hoặc auction đã bắt đầu
   */
  public Auction updateAuction(Long auctionId, CreateAuctionRequest request, Long userId) {
    // Tìm auction
    Auction auction =
        auctionDao
            .findById(auctionId)
            .orElseThrow(() -> new NotFoundException("Auction not found with id: " + auctionId));

    // Chỉ sửa được khi status = OPEN
    if (!"OPEN".equals(auction.getStatus())) {
      throw new IllegalStateException(
          "Can only edit auctions with OPEN status. Current status: " + auction.getStatus());
    }

    // Kiểm tra ownership thông qua item
    Item item =
        itemDao
            .findById(auction.getItemId())
            .orElseThrow(() -> new NotFoundException("Item not found"));

    if (!item.getSellerId().equals(userId)) {
      throw new UnauthorizedException("You can only edit your own auctions");
    }

    // Cập nhật
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
    return auction;
  }

  /**
   * Seller hoặc Admin xóa phiên đấu giá.
   *
   * @param auctionId ID auction cần xóa
   * @param userId ID user từ JWT
   * @param role role từ JWT
   * @throws NotFoundException nếu auction không tồn tại
   * @throws UnauthorizedException nếu không có quyền
   */
  public void deleteAuction(Long auctionId, Long userId, String role) {
    Auction auction =
        auctionDao
            .findById(auctionId)
            .orElseThrow(() -> new NotFoundException("Auction not found with id: " + auctionId));

    // Admin xóa tất cả
    if ("ADMIN".equals(role)) {
      auctionDao.delete(auctionId);
      LOGGER.info("Auction deleted by admin: id={}", auctionId);
      return;
    }

    // Seller chỉ xóa auction của item mình
    Item item =
        itemDao
            .findById(auction.getItemId())
            .orElseThrow(() -> new NotFoundException("Item not found"));

    if (!item.getSellerId().equals(userId)) {
      throw new UnauthorizedException("You can only delete your own auctions");
    }

    auctionDao.delete(auctionId);
    LOGGER.info("Auction deleted: id={}, by userId={}", auctionId, userId);
  }

  // ========== Methods required by AuctionServiceTest ==========

  /**
   * Tạo phiên đấu giá (simplified — không check role, dùng cho test).
   * Delegate sang createAuction() với role="SELLER".
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

    Item item = itemDao.findById(req.getItemId())
        .orElseThrow(() -> new NotFoundException("Item not found with id: " + req.getItemId()));

    if (!item.getSellerId().equals(sellerId)) {
      throw new UnauthorizedException("You can only create auctions for your own items");
    }

    Auction auction = new Auction(
        req.getItemId(), req.getStartingPrice(), req.getStartTime(), req.getEndTime());
    auction.setSellerId(sellerId);
    auctionDao.insert(auction);
    LOGGER.info("Auction created (via create): itemId={}, seller={}", req.getItemId(), sellerId);
    return auction;
  }

  /**
   * Cập nhật giá khởi điểm của phiên (simplified — chỉ nhận BigDecimal).
   * Kiểm tra trạng thái qua State pattern trước khi sửa.
   */
  public Auction update(Long auctionId, BigDecimal newPrice, Long sellerId) {
    Auction auction = auctionDao.findById(auctionId)
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
   * <p>Delegate sang {@link #updateAuction} rồi enrich kết quả.
   *
   * @param auctionId ID phiên cần sửa
   * @param request   dữ liệu mới (startingPrice, startTime, endTime)
   * @param userId    ID seller từ JWT
   * @return AuctionResponse đã cập nhật
   */
  public AuctionResponse update(Long auctionId, CreateAuctionRequest request, Long userId) {
    Auction auction = updateAuction(auctionId, request, userId);
    return enrichAuctionResponse(auction);
  }

  /**
   * Xóa phiên đấu giá (delegate sang deleteAuction với tên ngắn hơn).
   * Không cho phép xóa phiên đang RUNNING.
   */
  public void delete(Long auctionId, Long userId, String role) {
    Auction auction = auctionDao.findById(auctionId)
        .orElseThrow(() -> new NotFoundException("Auction not found: " + auctionId));

    // Không xóa được phiên đang RUNNING (trừ ADMIN)
    if ("RUNNING".equals(auction.getStatus()) && !"ADMIN".equals(role)) {
      throw new AuctionClosedException("Không thể xóa phiên đang diễn ra.");
    }

    if ("ADMIN".equals(role)) {
      auctionDao.delete(auctionId);
      return;
    }

    // Kiểm tra ownership qua sellerId trên auction
    Long auctionSellerId = auction.getSellerId();
    if (auctionSellerId == null) {
      // Fallback: kiểm tra qua item
      Item item = itemDao.findById(auction.getItemId())
          .orElseThrow(() -> new NotFoundException("Item not found"));
      auctionSellerId = item.getSellerId();
    }
    if (!auctionSellerId.equals(userId)) {
      throw new UnauthorizedException("You can only delete your own auctions");
    }

    auctionDao.delete(auctionId);
  }

  /**
   * Đặt giá thông qua State pattern (dùng trong test và AuctionScheduler).
   * Không persist xuống DB — chỉ validate state và update in-memory.
   */
  public void placeBidViaState(Long auctionId, Long bidderId, BigDecimal amount) {
    Auction auction = auctionDao.findById(auctionId)
        .orElseThrow(() -> new NotFoundException("Auction not found: " + auctionId));
    getState(auction).placeBid(auction, amount, bidderId);
  }

  /**
   * Chuyển trạng thái OPEN → RUNNING.
   * Chỉ hợp lệ khi auction đang ở OPEN. Gọi bởi AuctionScheduler.
   *
   * @throws AuctionClosedException nếu auction không ở trạng thái OPEN
   */
  public void transitionToRunning(Long auctionId) {
    Auction auction = auctionDao.findById(auctionId)
        .orElseThrow(() -> new NotFoundException("Auction not found: " + auctionId));

    if (!"OPEN".equals(auction.getStatus())) {
      throw new AuctionClosedException(
          "Không thể chuyển sang RUNNING từ trạng thái: " + auction.getStatus());
    }

    auction.setStatus("RUNNING");
    auctionDao.update(auction);
    LOGGER.info("Auction #{} → RUNNING", auctionId);
  }

  /**
   * Chuyển trạng thái RUNNING → FINISHED.
   * Chỉ hợp lệ khi auction đang ở RUNNING. Gọi bởi AuctionScheduler.
   */
  public void transitionToFinished(Long auctionId) {
    Auction auction = auctionDao.findById(auctionId)
        .orElseThrow(() -> new NotFoundException("Auction not found: " + auctionId));

    if (!"RUNNING".equals(auction.getStatus())) {
      throw new AuctionClosedException(
          "Không thể chuyển sang FINISHED từ trạng thái: " + auction.getStatus());
    }

    auction.setStatus("FINISHED");
    auctionDao.update(auction);
    LOGGER.info("Auction #{} → FINISHED", auctionId);
  }

  /**
   * Trả về AuctionState tương ứng với trạng thái hiện tại của auction.
   * Dùng để delegate behavior theo State pattern.
   *
   * @param auction auction cần resolve state
   * @return AuctionState implementation tương ứng
   */
  public AuctionState getState(Auction auction) {
    return switch (auction.getStatus()) {
      case "OPEN"     -> new OpenState();
      case "RUNNING"  -> new RunningState();
      case "FINISHED" -> new FinishedState();
      case "PAID"     -> new PaidState();
      case "CANCELED" -> new CanceledState();
      default -> throw new IllegalStateException("Unknown auction status: " + auction.getStatus());
    };
  }

  /**
   * Bổ sung thông tin itemName và leadingBidderUsername vào AuctionResponse.
   *
   * <p>AuctionResponse.fromAuction() chỉ copy dữ liệu từ Auction model. Method này bổ sung thêm
   * thông tin từ bảng items và users.
   */
  private AuctionResponse enrichAuctionResponse(Auction auction) {
    AuctionResponse response = AuctionResponse.fromAuction(auction);

    // Bổ sung itemName và itemCategory
    itemDao
        .findById(auction.getItemId())
        .ifPresent(
            item -> {
              response.setItemName(item.getName());
              response.setItemCategory(item.getCategory());
            });

    // Bổ sung leadingBidderUsername
    if (auction.getLeadingBidderId() != null) {
      userDao
          .findById(auction.getLeadingBidderId())
          .ifPresent(
              user -> {
                response.setLeadingBidderUsername(user.getUsername());
              });
    }

    return response;
  }
}
