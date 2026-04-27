package com.auction.service;

import com.auction.dao.AuctionDao;
import com.auction.model.Auction;
// ĐÃ ẨN DO DEV CHƯA PUSH CODE OBSERVER
// import com.auction.pattern.observer.AuctionEventManager;
// import com.auction.dto.BidUpdateMessage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AuctionScheduler {

    private final AuctionDao auctionDao;
    // ĐÃ ẨN
    // private final AuctionEventManager eventManager;
    private final ScheduledExecutorService scheduler;

    public AuctionScheduler(AuctionDao auctionDao) {
        this.auctionDao = auctionDao;
        // ĐÃ ẨN
        // this.eventManager = eventManager;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::processAuctions, 0, 5, TimeUnit.SECONDS);
        System.out.println("⏱️ AuctionScheduler đã khởi động, quét hệ thống mỗi 5 giây...");
    }

    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }

    private void processAuctions() {
        try {
            LocalDateTime now = LocalDateTime.now();

            List<Auction> openAuctions = auctionDao.findByStatus("OPEN");
            for (Auction auction : openAuctions) {
                if (!auction.getStartTime().isAfter(now)) {
                    auction.setStatus("RUNNING");
                    auctionDao.update(auction);
                }
            }

            List<Auction> runningAuctions = auctionDao.findByStatus("RUNNING");
            for (Auction auction : runningAuctions) {
                if (!auction.getEndTime().isAfter(now)) {
                    auction.setStatus("FINISHED");
                    auctionDao.update(auction);

                    // ĐÃ ẨN DO DEV CHƯA PUSH CODE
                    /*
                    BidUpdateMessage msg = BidUpdateMessage.auctionEnded(
                            auction.getId(),
                            auction.getCurrentPrice(),
                            auction.getLeadingBidderId(),
                            ""
                    );
                    eventManager.notifyAuctionEnd(auction.getId(), msg);
                    */
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}