package com.auction.service;

import com.auction.controller.AuctionWebSocketHandler;
import com.auction.dao.AuctionDao;
import com.auction.dao.BidTransactionDao;
import com.auction.dao.ItemDao;
import com.auction.dao.UserDao;
import com.auction.dao.WalletTransactionDao;
import com.auction.dto.AuctionResponse;
import com.auction.dto.BidUpdateMessage;
import com.auction.dto.CreateAuctionRequest;
import com.auction.dto.PageRequest;
import com.auction.exception.DuplicateException;
import com.auction.exception.NotFoundException;
import com.auction.exception.UnauthorizedException;
import com.auction.model.Art;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.Electronics;
import com.auction.model.Item;
import com.auction.model.Vehicle;
import com.auction.pattern.observer.AuctionEventManager;
import com.auction.pattern.state.AuctionState;
import com.auction.pattern.state.AuctionStates;
import com.auction.util.MoneyValidator;
import com.auction.util.NotificationFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service xử lý toàn bộ logic nghiệp vụ cho phiên đấu giá (Auction).
 *
 * <p>Lớp này là tầng trung gian chính giữa Controller và các DAO, kết hợp nhiều pattern:
 *
 * <ul>
 *   <li><b>State Pattern</b> — ủy quyền kiểm tra tính hợp lệ của hành động cho {@code AuctionState}
 *       (đặt giá, đóng, chỉnh sửa, gia hạn) thay vì dùng if/switch trên trường status.
 *   <li><b>Observer Pattern</b> — phát sự kiện real-time qua {@code AuctionEventManager} đến
 *       WebSocket clients khi có bid mới, gia hạn, hoặc kết thúc phiên.
 *   <li><b>Transaction</b> — dùng {@code jdbi.inTransaction()} khi cần tính nhất quán nhiều bảng
 *       (ví dụ: tạo phiên + khóa item, hủy phiên + hoàn tiền + ghi notification).
 * </ul>
 */
public class AuctionService {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuctionService.class);

  private final AuctionDao auctionDao;
  private final ItemDao itemDao;
  private final UserDao userDao;
  private final AuctionEventManager eventManager;
  private final Jdbi jdbi;
  private final BidTransactionDao bidTransactionDao;
  private final AuctionWebSocketHandler wsHandler;

  public AuctionService(
      AuctionDao auctionDao,
      ItemDao itemDao,
      UserDao userDao,
      AuctionEventManager eventManager,
      Jdbi jdbi,
      BidTransactionDao bidTransactionDao) {
    this(auctionDao, itemDao, userDao, eventManager, jdbi, bidTransactionDao, null);
  }

  public AuctionService(
      AuctionDao auctionDao,
      ItemDao itemDao,
      UserDao userDao,
      AuctionEventManager eventManager,
      Jdbi jdbi,
      BidTransactionDao bidTransactionDao,
      AuctionWebSocketHandler wsHandler) {
    this.auctionDao = auctionDao;
    this.itemDao = itemDao;
    this.userDao = userDao;
    this.eventManager = eventManager;
    this.jdbi = jdbi;
    this.bidTransactionDao = bidTransactionDao;
    this.wsHandler = wsHandler;
  }

  /** Constructor tối giản cho unit test không cần thông báo hay transaction. */
  AuctionService(AuctionDao auctionDao, ItemDao itemDao, UserDao userDao) {
    this(auctionDao, itemDao, userDao, null, null, null, null);
  }

  @Deprecated(since = "1.1", forRemoval = true)
  public List<Auction> getAllAuctions(String status) {
    return getAll(status, PageRequest.of(0, 100)).stream().map(this::toAuction).toList();
  }

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

  public AuctionResponse getById(Long id) {
    return getAuctionById(id);
  }

  public AuctionResponse getAuctionById(Long id) {
    Auction auction =
        auctionDao
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Auction not found with id: " + id));
    return enrichAuctionResponse(auction);
  }

  public Auction create(CreateAuctionRequest req, Long sellerId) {
    if (req.getItemId() == null) {
      throw new IllegalArgumentException("Item ID is required");
    }
    MoneyValidator.requirePositiveIntegerVnd(req.getStartingPrice(), "Starting price");
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
      throw new UnauthorizedException("You can only create auctions for your own items");
    }

    if (!"AVAILABLE".equals(item.getStatus())) {
      throw new DuplicateException("Item #" + itemId + " is no longer in AVAILABLE status");
    }

    if (auctionDao.existsActiveAuctionForItem(itemId)) {
      throw new DuplicateException("Item #" + itemId + " already has an active auction");
    }

    if (auctionDao.existsPaidAuctionForItem(itemId)) {
      throw new DuplicateException("Item #" + itemId + " has already been sold");
    }
  }

  private void validateItemCanBeAuctionedInTransaction(
      Handle handle, Long itemId, Long sellerId, Item item) {
    if (!item.getSellerId().equals(sellerId)) {
      throw new UnauthorizedException("You can only create auctions for your own items");
    }

    if (!"AVAILABLE".equals(item.getStatus())) {
      throw new DuplicateException("Item #" + itemId + " is no longer in AVAILABLE status");
    }

    if (auctionDao.existsActiveAuctionForItem(handle, itemId)) {
      throw new DuplicateException("Item #" + itemId + " already has an active auction");
    }

    if (auctionDao.existsPaidAuctionForItem(handle, itemId)) {
      throw new DuplicateException("Item #" + itemId + " has already been sold");
    }
  }

  public Auction update(Long auctionId, BigDecimal newPrice, Long sellerId) {
    MoneyValidator.requirePositiveIntegerVnd(newPrice, "Starting price");

    Auction auction =
        auctionDao
            .findById(auctionId)
            .orElseThrow(() -> new NotFoundException("Auction not found: " + auctionId));

    getState(auction).edit(auction);

    auction.setStartingPrice(newPrice);
    auction.setCurrentPrice(newPrice);
    auctionDao.update(auction);
    return auction;
  }

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

    java.time.LocalDateTime candidateStartTime =
        request.getStartTime() != null ? request.getStartTime() : auction.getStartTime();
    java.time.LocalDateTime candidateEndTime =
        request.getEndTime() != null ? request.getEndTime() : auction.getEndTime();
    validateAuctionTimeRange(candidateStartTime, candidateEndTime);

    if (request.getStartingPrice() != null) {
      MoneyValidator.requirePositiveIntegerVnd(request.getStartingPrice(), "Starting price");
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

  private void validateAuctionTimeRange(
      java.time.LocalDateTime startTime, java.time.LocalDateTime endTime) {
    if (startTime == null || endTime == null) {
      throw new IllegalArgumentException("Start time and end time are required");
    }
    if (!endTime.isAfter(startTime)) {
      throw new IllegalArgumentException("End time must be after start time");
    }
  }

  public void delete(Long auctionId, Long userId, String role) {
    Auction auction =
        auctionDao
            .findById(auctionId)
            .orElseThrow(() -> new NotFoundException("Auction not found: " + auctionId));

    if ("ADMIN".equals(role)) {
      AuctionStatus status = auction.getStatus();
      if (status != AuctionStatus.OPEN && status != AuctionStatus.RUNNING) {
        throw new IllegalStateException(
            "Only auctions in OPEN or RUNNING status can be cancelled."
                + " Current status: "
                + status
                + ". Use the Delete button to remove completed auctions.");
      }
      AuctionStatus previousStatus = status;
      auction.setStatus(AuctionStatus.CANCELED);
      persistCanceledAuction(auction, previousStatus, "ADMIN");
      emitCancellationIfNeeded(auction, previousStatus);
      LOGGER.info("ADMIN (userId={}) cancelled auction #{}", userId, auctionId);
      return;
    }

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
        throw new UnauthorizedException(
            "You do not have permission to cancel another seller's auction!");
      }

      if (AuctionStatus.RUNNING == auction.getStatus() && hasBids(auction)) {
        throw new IllegalStateException("Cannot cancel a running auction after the first bid.");
      }
      if (auction.getEndTime() != null && !LocalDateTime.now().isBefore(auction.getEndTime())) {
        throw new IllegalStateException("Cannot cancel an auction after its end time.");
      }

      AuctionStatus status = auction.getStatus();
      if (status != AuctionStatus.OPEN && status != AuctionStatus.RUNNING) {
        throw new IllegalStateException(
            "Only auctions in OPEN or RUNNING status can be cancelled."
                + " Current status: "
                + status);
      }

      AuctionStatus previousStatus = status;
      auction.setStatus(AuctionStatus.CANCELED);
      persistCanceledAuction(auction, previousStatus, "SELLER");
      emitCancellationIfNeeded(auction, previousStatus);
      LOGGER.info(
          "SELLER (userId={}) cancelled auction #{} (previous status: {})",
          userId,
          auctionId,
          status);
      return;
    }

    throw new UnauthorizedException("You do not have permission to perform this action.");
  }

  /** Ensures an item can still be edited. A listed item remains editable until its first bid. */
  public void ensureItemCanBeModified(Long itemId) {
    for (Auction auction : auctionDao.findByItemId(itemId)) {
      if (auction.getStatus() == AuctionStatus.SETTLING) {
        throw new IllegalStateException(
            "Cannot modify item #" + itemId + " because its auction is settling.");
      }
      if (isActive(auction) && hasBids(auction)) {
        throw new IllegalStateException(
            "Cannot modify item #" + itemId + " because its auction already has bids.");
      }
    }
  }

  /** Updates an item while holding the same auction-row lock used by bid placement. */
  public void updateItemWithoutBids(Item updatedItem) {
    if (jdbi == null) {
      ensureItemCanBeModified(updatedItem.getId());
      itemDao.update(updatedItem);
      return;
    }
    jdbi.useTransaction(
        handle -> {
          auctionDao.findActiveByItemIdForUpdate(handle, updatedItem.getId());
          Item lockedItem =
              itemDao
                  .findByIdForUpdate(handle, updatedItem.getId())
                  .orElseThrow(
                      () -> new NotFoundException("Item #" + updatedItem.getId() + " not found"));
          ensureMutableStatus(lockedItem);
          ensureNoBids(handle, auctionDao.findActiveByItemIdForUpdate(handle, updatedItem.getId()));
          updatedItem.setStatus(lockedItem.getStatus());
          itemDao.updateInTransaction(handle, updatedItem);
        });
  }

  /** Cancels active no-bid auctions before an item is removed. */
  public void cancelActiveAuctionsWithoutBids(Long itemId, Long userId, String role) {
    for (Auction auction : auctionDao.findByItemId(itemId)) {
      if (!isActive(auction)) {
        continue;
      }
      if (hasBids(auction)) {
        throw new IllegalStateException(
            "Cannot delete item #" + itemId + " because its auction already has bids.");
      }
      delete(auction.getId(), userId, role);
    }
  }

  /** Cancels active no-bid auctions and soft-deletes the item in one transaction. */
  public void removeItemWithoutBids(Long itemId, Long userId, String role) {
    if (jdbi == null) {
      cancelActiveAuctionsWithoutBids(itemId, userId, role);
      itemDao.delete(itemId);
      return;
    }

    List<CanceledAuction> canceledAuctions = new java.util.ArrayList<>();
    List<NotificationBatch> notificationBatches = new java.util.ArrayList<>();
    jdbi.useTransaction(
        handle -> {
          auctionDao.findActiveByItemIdForUpdate(handle, itemId);
          Item lockedItem =
              itemDao
                  .findByIdForUpdate(handle, itemId)
                  .orElseThrow(() -> new NotFoundException("Item #" + itemId + " not found"));
          ensureMutableStatus(lockedItem);
          List<Auction> activeAuctions = auctionDao.findActiveByItemIdForUpdate(handle, itemId);
          ensureNoBids(handle, activeAuctions);
          for (Auction auction : activeAuctions) {
            AuctionStatus previousStatus = auction.getStatus();
            auction.setStatus(AuctionStatus.CANCELED);
            deactivateActiveAutoBidsInTransaction(handle, auction.getId());
            auctionDao.updateInTransaction(handle, auction);
            List<Long> recipients = new java.util.ArrayList<>();
            String message =
                insertCancellationNotificationInTransaction(
                    handle, auction, previousStatus, role, recipients);
            notificationBatches.add(new NotificationBatch(recipients, message));
            canceledAuctions.add(new CanceledAuction(auction, previousStatus));
          }
          itemDao.deleteInTransaction(handle, itemId);
        });

    for (CanceledAuction canceled : canceledAuctions) {
      emitCancellationIfNeeded(canceled.auction(), canceled.previousStatus());
    }
    for (NotificationBatch batch : notificationBatches) {
      pushLiveNotifications(batch.recipients(), batch.message());
    }
  }

  private record CanceledAuction(Auction auction, AuctionStatus previousStatus) {}

  private record NotificationBatch(List<Long> recipients, String message) {}

  private void ensureMutableStatus(Item item) {
    if (!"AVAILABLE".equals(item.getStatus()) && !"IN_AUCTION".equals(item.getStatus())) {
      throw new IllegalStateException(
          "Cannot modify item #" + item.getId() + " because its status is " + item.getStatus());
    }
  }

  private void ensureNoBids(Handle handle, List<Auction> auctions) {
    for (Auction auction : auctions) {
      if (auction.getStatus() == AuctionStatus.SETTLING) {
        throw new IllegalStateException(
            "Cannot modify item #" + auction.getItemId() + " because its auction is settling.");
      }
      if (bidTransactionDao.countByAuctionId(handle, auction.getId()) > 0) {
        throw new IllegalStateException(
            "Cannot modify item #"
                + auction.getItemId()
                + " because its auction already has bids.");
      }
    }
  }

  private boolean isActive(Auction auction) {
    return auction.getStatus() == AuctionStatus.OPEN
        || auction.getStatus() == AuctionStatus.RUNNING
        || auction.getStatus() == AuctionStatus.SETTLING;
  }

  private boolean hasBids(Auction auction) {
    if (bidTransactionDao != null) {
      return bidTransactionDao.countByAuctionId(auction.getId()) > 0;
    }
    return auction.getLeadingBidderId() != null;
  }

  public void hardDelete(Long auctionId) {
    Auction auction =
        auctionDao
            .findById(auctionId)
            .orElseThrow(() -> new NotFoundException("Auction not found: " + auctionId));

    if (eventManager != null) {
      try {
        BidUpdateMessage msg =
            BidUpdateMessage.auctionEnded(auction.getId(), auction.getCurrentPrice(), null, null);
        eventManager.notifyAuctionEnd(auction.getId(), msg);
      } catch (Exception e) {
        LOGGER.warn("Cannot broadcast AUCTION_ENDED before hard-deleting auction #{}", auctionId);
      }
    }

    java.util.List<Long> recipients = new java.util.ArrayList<>();
    String message = "Auction " + formatAuctionDisplayName(auction) + " was deleted by Admin";

    if (jdbi != null) {
      jdbi.useTransaction(
          handle -> {
            releaseReservationBeforeHardDelete(handle, auction);
            syncItemStatusBeforeHardDelete(handle, auction);
            insertHardDeleteNotificationsInTransaction(handle, auction, message, recipients);
            auctionDao.hardDeleteInTransaction(handle, auctionId);
          });
    } else {
      restoreItemAvailabilityWithoutTransaction(auction);
      auctionDao.hardDelete(auctionId);
    }

    pushLiveNotifications(recipients, message);
    LOGGER.info("Admin hard-deleted auction #{}", auctionId);
  }

  private void insertHardDeleteNotificationsInTransaction(
      Handle handle, Auction auction, String message, java.util.List<Long> recipientsOut) {
    java.util.Set<Long> recipients = new java.util.LinkedHashSet<>();

    java.util.List<Long> allBidders =
        handle
            .createQuery(
                "SELECT DISTINCT bidder_id FROM bid_transactions WHERE auction_id = :auctionId")
            .bind("auctionId", auction.getId())
            .mapTo(Long.class)
            .list();
    recipients.addAll(allBidders);

    if (auction.getSellerId() != null) {
      recipients.add(auction.getSellerId());
    }
    if (auction.getLeadingBidderId() != null) {
      recipients.add(auction.getLeadingBidderId());
    }

    for (Long recipientId : recipients) {
      handle.execute(
          "INSERT INTO notifications (user_id, message, notification_type) "
              + "VALUES (?, ?, 'AUCTION_DELETED')",
          recipientId,
          message);
    }

    recipientsOut.addAll(recipients);
  }

  public void placeBidViaState(Long auctionId, Long bidderId, BigDecimal amount) {
    Auction auction =
        auctionDao
            .findById(auctionId)
            .orElseThrow(() -> new NotFoundException("Auction not found: " + auctionId));
    getState(auction).placeBid(auction, amount, bidderId);
  }

  public AuctionState getState(Auction auction) {
    return switch (auction.getStatus()) {
      case OPEN -> AuctionStates.OPEN;
      case RUNNING -> AuctionStates.RUNNING;
      case SETTLING -> AuctionStates.SETTLING;
      case FINISHED -> AuctionStates.FINISHED;
      case PAID -> AuctionStates.PAID;
      case CANCELED -> AuctionStates.CANCELED;
    };
  }

  private void emitCancellationIfNeeded(Auction auction, AuctionStatus previousStatus) {
    if (eventManager != null && previousStatus != AuctionStatus.CANCELED) {
      BidUpdateMessage msg =
          BidUpdateMessage.auctionEnded(auction.getId(), auction.getCurrentPrice(), null, null);
      eventManager.notifyAuctionEnd(auction.getId(), msg);
    }
  }

  private void persistCanceledAuction(
      Auction auction, AuctionStatus previousStatus, String actorRole) {
    if (jdbi == null) {
      auctionDao.update(auction);
      restoreItemAvailabilityWithoutTransaction(auction);
      return;
    }

    java.util.List<Long> recipients = new java.util.ArrayList<>();
    String[] msgHolder = new String[1];

    jdbi.useTransaction(
        handle -> {
          Auction lockedAuction = auctionDao.findByIdForUpdate(handle, auction.getId());
          AuctionStatus lockedPreviousStatus = lockedAuction.getStatus();
          if (lockedPreviousStatus != AuctionStatus.OPEN
              && lockedPreviousStatus != AuctionStatus.RUNNING) {
            throw new IllegalStateException(
                "Only auctions in OPEN or RUNNING status can be cancelled. Current status: "
                    + lockedPreviousStatus);
          }
          if ("SELLER".equals(actorRole)
              && bidTransactionDao.countByAuctionId(handle, lockedAuction.getId()) > 0) {
            throw new IllegalStateException("Cannot cancel a running auction after the first bid.");
          }
          if ("SELLER".equals(actorRole)
              && lockedAuction.getEndTime() != null
              && !LocalDateTime.now().isBefore(lockedAuction.getEndTime())) {
            throw new IllegalStateException("Cannot cancel an auction after its end time.");
          }

          lockedAuction.setStatus(AuctionStatus.CANCELED);
          if (lockedPreviousStatus == AuctionStatus.RUNNING
              && lockedAuction.getLeadingBidderId() != null) {
            userDao.releaseReservedBalanceInTransaction(
                handle, lockedAuction.getLeadingBidderId(), lockedAuction.getCurrentPrice());
            WalletTransactionDao.insert(
                handle,
                lockedAuction.getLeadingBidderId(),
                lockedAuction.getId(),
                null,
                "CANCEL_RELEASE",
                lockedAuction.getCurrentPrice(),
                "auction_cancel:" + lockedAuction.getId());
          }
          deactivateActiveAutoBidsInTransaction(handle, lockedAuction.getId());
          itemDao.updateStatusInTransaction(handle, lockedAuction.getItemId(), "AVAILABLE");
          auctionDao.updateInTransaction(handle, lockedAuction);
          msgHolder[0] =
              insertCancellationNotificationInTransaction(
                  handle, lockedAuction, lockedPreviousStatus, actorRole, recipients);
        });

    auction.setStatus(AuctionStatus.CANCELED);
    pushLiveNotifications(recipients, msgHolder[0]);
  }

  private void deactivateActiveAutoBidsInTransaction(Handle handle, Long auctionId) {
    handle
        .createUpdate(
            """
            UPDATE auto_bid_configs
            SET active = false,
                status = 'STOPPED',
                failure_reason = NULL
            WHERE auction_id = :auctionId AND status = 'ACTIVE'
            """)
        .bind("auctionId", auctionId)
        .execute();
  }

  private String insertCancellationNotificationInTransaction(
      Handle handle,
      Auction auction,
      AuctionStatus previousStatus,
      String actorRole,
      java.util.List<Long> recipientsOut) {
    if (previousStatus == AuctionStatus.CANCELED) {
      return null;
    }
    String byWho = "ADMIN".equals(actorRole) ? "Admin" : "seller";
    String message = "Auction #" + auction.getId() + " was canceled by " + byWho;

    java.util.Set<Long> recipients = new java.util.LinkedHashSet<>();

    java.util.List<Long> allBidders =
        handle
            .createQuery(
                "SELECT DISTINCT bidder_id FROM bid_transactions WHERE auction_id = :auctionId")
            .bind("auctionId", auction.getId())
            .mapTo(Long.class)
            .list();
    recipients.addAll(allBidders);

    if (auction.getSellerId() != null) {
      recipients.add(auction.getSellerId());
    }
    if (auction.getLeadingBidderId() != null) {
      recipients.add(auction.getLeadingBidderId());
    }

    for (Long recipientId : recipients) {
      handle.execute(
          "INSERT INTO notifications (user_id, message, notification_type) "
              + "VALUES (?, ?, 'AUCTION_CANCELED')",
          recipientId,
          message);
    }

    recipientsOut.addAll(recipients);
    return message;
  }

  private void releaseReservationBeforeHardDelete(Handle handle, Auction auction) {
    AuctionStatus status = auction.getStatus();
    if ((status == AuctionStatus.RUNNING || status == AuctionStatus.SETTLING)
        && auction.getLeadingBidderId() != null) {
      userDao.releaseReservedBalanceInTransaction(
          handle, auction.getLeadingBidderId(), auction.getCurrentPrice());
    }
  }

  private void syncItemStatusBeforeHardDelete(Handle handle, Auction auction) {
    if (auction.getItemId() == null) {
      return;
    }
    String itemStatus = auction.getStatus() == AuctionStatus.PAID ? "SOLD" : "AVAILABLE";
    itemDao.updateStatusInTransaction(handle, auction.getItemId(), itemStatus);
  }

  private void restoreItemAvailabilityWithoutTransaction(Auction auction) {
    if (auction.getItemId() == null) {
      return;
    }
    itemDao
        .findById(auction.getItemId())
        .ifPresent(
            item -> {
              item.setStatus("AVAILABLE");
              itemDao.update(item);
            });
  }

  private String formatAuctionDisplayName(Auction auction) {
    String itemName = null;
    try {
      if (auction.getItemId() != null) {
        itemName = itemDao.findById(auction.getItemId()).map(Item::getName).orElse(null);
      }
    } catch (Exception e) {
      LOGGER.warn("Cannot resolve item name for auction #{}", auction.getId(), e);
    }
    return NotificationFormat.auctionName(auction.getId(), itemName);
  }

  private void pushLiveNotifications(java.util.List<Long> recipients, String message) {
    if (wsHandler == null || message == null || recipients == null || recipients.isEmpty()) {
      return;
    }
    for (Long uid : recipients) {
      try {
        wsHandler.pushUserNotification(uid, message);
      } catch (Exception e) {
        LOGGER.warn("Cannot push USER_NOTIFICATION to user #{}: {}", uid, e.getMessage());
      }
    }
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
    auction.setCreatedAt(response.getCreatedAt());
    if (response.getStatus() != null) {
      auction.setStatus(AuctionStatus.from(response.getStatus()));
    }
    return auction;
  }

  private AuctionResponse enrichAuctionResponse(Auction auction) {
    AuctionResponse response = AuctionResponse.fromAuction(auction);

    itemDao
        .findById(auction.getItemId())
        .ifPresent(
            item -> {
              response.setItemName(item.getName());
              response.setItemCategory(item.getCategory());
              response.setItemDescription(item.getDescription());

              if (item instanceof Electronics electronics) {
                response.setItemBrand(electronics.getBrand());
              } else if (item instanceof Art art) {
                response.setItemArtist(art.getArtist());
              } else if (item instanceof Vehicle vehicle) {
                response.setItemYear(vehicle.getYear());
              }
            });

    if (auction.getLeadingBidderId() != null) {
      userDao
          .findById(auction.getLeadingBidderId())
          .ifPresent(user -> response.setLeadingBidderUsername(user.getUsername()));
    }

    return response;
  }
}
