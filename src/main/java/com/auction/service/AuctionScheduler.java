package com.auction.service;

import com.auction.dao.AuctionDao;
import com.auction.model.Auction;
// 1. TẠM ẨN IMPORT DO DEV CHƯA PUSH CODE
// import com.auction.pattern.observer.AuctionEventManager;
// import com.auction.dto.BidUpdateMessage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AuctionScheduler {

    private final AuctionDao auctionDao;
    // 2. TẠM ẨN BIẾN NÀY
    // private final AuctionEventManager eventManager;
    private final ScheduledExecutorService scheduler;

    // 3. SỬA HÀM KHỞI TẠO (Bỏ eventManager đi)
    public AuctionScheduler(AuctionDao auctionDao) {
        this.auctionDao = auctionDao;
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
                    System.out.println("🟢 Phiên " + auction.getId() + " đã chuyển sang RUNNING");
                }
            }

            List<Auction> runningAuctions = auctionDao.findByStatus("RUNNING");
            for (Auction auction : runningAuctions) {
                if (!auction.getEndTime().isAfter(now)) {
                    auction.setStatus("FINISHED");
                    auctionDao.update(auction);
                    System.out.println("🔴 Phiên " + auction.getId() + " đã chuyển sang FINISHED");

                    // 4. TẠM ẨN ĐOẠN GỌI WEBSOCKET CHỜ DEV PUSH CODE OBSERVER
                    /*
                    BidUpdateMessage msg = BidUpdateMessage.auctionEnded(
                            auction.getId(),
                            auction.getCurrentPrice(),
                            auction.getLeadingBidderId(),
                            "" // Thêm tham số String rỗng để khớp với sửa đổi của Dev
                    );
                    eventManager.notifyAuctionEnd(auction.getId(), msg);
                    */
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi trong lúc quét Scheduler: " + e.getMessage());
            e.printStackTrace();
        }
    }
}