package com.auction.service;

import com.auction.dao.AuctionDao;
import com.auction.dao.BidTransactionDao;
import com.auction.dao.UserDao;
import com.auction.dto.BidRequest;
import com.auction.dto.BidUpdateMessage;
import com.auction.exception.InvalidBidException;
import com.auction.exception.NotFoundException;
import com.auction.model.Auction;
import com.auction.model.BidTransaction;
import com.auction.model.User;
import java.util.List;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BidService {

  private static final Logger LOGGER = LoggerFactory.getLogger(BidService.class);

  private final AuctionDao auctionDao;
  private final BidTransactionDao bidTransactionDao;
  private final UserDao userDao;
  private final Jdbi jdbi;

  // WebSocket handler — set sau khi khởi tạo (tránh circular dependency)
  private AuctionWebSocketBroadcaster broadcaster;

  public BidService(
      AuctionDao auctionDao, BidTransactionDao bidTransactionDao, UserDao userDao, Jdbi jdbi) {
    this.auctionDao = auctionDao;
    this.bidTransactionDao = bidTransactionDao;
    this.userDao = userDao;
    this.jdbi = jdbi;
  }

  public interface AuctionWebSocketBroadcaster {
    void broadcast(Long auctionId, BidUpdateMessage message);
  }

  public void setBroadcaster(AuctionWebSocketBroadcaster broadcaster) {
    this.broadcaster = broadcaster;
  }

  public BidTransaction placeBid(Long auctionId, BidRequest request, Long bidderId) {
    // Validate input
    if (request.getAmount() == null || request.getAmount().signum() <= 0) {
      throw new InvalidBidException("Bid amount must be greater than 0");
    }

    // Thực hiện trong transaction để đảm bảo consistency
    BidTransaction savedBid =
        jdbi.inTransaction(
            handle -> {
              // Lấy auction với FOR UPDATE lock (tránh race condition)
              Auction auction = auctionDao.findByIdForUpdate(handle, auctionId);

              // Kiểm tra trạng thái — chỉ RUNNING mới cho bid
              if ("OPEN".equals(auction.getStatus())) {
                throw new InvalidBidException("Phiên chưa bắt đầu");
              }
              if ("FINISHED".equals(auction.getStatus())) {
                throw new InvalidBidException("Phiên đã kết thúc");
              }
              if (!"RUNNING".equals(auction.getStatus())) {
                throw new InvalidBidException(
                    "Auction is not running. Current status: " + auction.getStatus());
              }

              // Kiểm tra hết hạn
              if (auction.isExpired()) {
                throw new InvalidBidException("Auction has expired");
              }

              // Kiểm tra giá phải cao hơn giá hiện tại
              if (request.getAmount().compareTo(auction.getCurrentPrice()) <= 0) {
                throw new InvalidBidException(
                    "Bid amount must be higher than current price: " + auction.getCurrentPrice());
              }

              // Cập nhật auction
              auction.setCurrentPrice(request.getAmount());
              auction.setLeadingBidderId(bidderId);
              auctionDao.updateInTransaction(handle, auction);

              // Ghi bid transaction
              BidTransaction transaction =
                  new BidTransaction(auctionId, bidderId, request.getAmount(), false);
              bidTransactionDao.insert(handle, transaction);

              LOGGER.info(
                  "Bid placed: auction={}, bidder={}, amount={}",
                  auctionId,
                  bidderId,
                  request.getAmount());

              return transaction;
            });

    // Broadcast qua WebSocket (ngoài transaction)
    if (broadcaster != null) {
      try {
        String username = userDao.findById(bidderId).map(User::getUsername).orElse("Unknown");

        Auction updatedAuction = auctionDao.findById(auctionId).orElse(null);
        if (updatedAuction != null) {
          BidUpdateMessage message =
              BidUpdateMessage.bidUpdate(
                  auctionId,
                  request.getAmount(),
                  bidderId,
                  username,
                  updatedAuction.getEndTime(),
                  false);
          broadcaster.broadcast(auctionId, message);
        }
      } catch (Exception e) {
        LOGGER.error("Failed to broadcast bid update: {}", e.getMessage());
      }
    }

    return savedBid;
  }

  public List<BidTransaction> getBidHistory(Long auctionId) {
    // Kiểm tra auction tồn tại
    if (!auctionDao.existsById(auctionId)) {
      throw new NotFoundException("Auction not found with id: " + auctionId);
    }
    return bidTransactionDao.findByAuctionId(auctionId);
  }
}
