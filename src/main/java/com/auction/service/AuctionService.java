package com.auction.service;

import com.auction.dao.AuctionDao;
import com.auction.dao.ItemDao;
import com.auction.dao.UserDao;
import com.auction.dto.AuctionResponse;
import com.auction.dto.CreateAuctionRequest;
import com.auction.exception.NotFoundException;
import com.auction.exception.UnauthorizedException;
import com.auction.model.Auction;
import com.auction.model.Item;
import java.util.List;
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
