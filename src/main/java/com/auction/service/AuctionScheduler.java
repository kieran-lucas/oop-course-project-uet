package com.auction.service;

import com.auction.dao.AuctionDao;
import com.auction.model.Auction;
import com.auction.pattern.observer.AuctionEventManager;
import com.auction.dto.BidUpdateMessage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AuctionScheduler {

    private final AuctionDao auctionDao;
    private final AuctionEventManager eventManager;
    private final ScheduledExecutorService scheduler;

    // Khởi tạo Scheduler với các dependency cần thiết
    public AuctionScheduler(AuctionDao auctionDao, AuctionEventManager eventManager) {
        this.auctionDao = auctionDao;
        this.eventManager = eventManager;
        // Tạo một thread pool chỉ với 1 luồng chuyên chạy ngầm
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    // Hàm này sẽ được gọi ở file App.java khi khởi động server
    public void start() {
        // Lên lịch chạy hàm processAuctions mỗi 5 giây, bắt đầu chạy ngay lập tức (delay = 0)
        scheduler.scheduleAtFixedRate(this::processAuctions, 0, 5, TimeUnit.SECONDS);
        System.out.println("⏱️ AuctionScheduler đã khởi động, quét hệ thống mỗi 5 giây...");
    }

    // Dọn dẹp tài nguyên khi tắt server
    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }

    // Logic cốt lõi: Quét và cập nhật trạng thái
    private void processAuctions() {
        try {
            LocalDateTime now = LocalDateTime.now();

            // 1. Quét các phiên đang đợi (OPEN) -> Đến giờ thì cho chạy (RUNNING)
            List<Auction> openAuctions = auctionDao.findByStatus("OPEN");
            for (Auction auction : openAuctions) {
                if (!auction.getStartTime().isAfter(now)) { // Đã đến hoặc qua giờ bắt đầu
                    auction.setStatus("RUNNING");
                    auctionDao.update(auction);
                    System.out.println("🟢 Phiên " + auction.getId() + " đã chuyển sang RUNNING");
                }
            }

            // 2. Quét các phiên đang chạy (RUNNING) -> Hết giờ thì đóng lại (FINISHED)
            List<Auction> runningAuctions = auctionDao.findByStatus("RUNNING");
            for (Auction auction : runningAuctions) {
                if (!auction.getEndTime().isAfter(now)) { // Đã hết thời gian
                    auction.setStatus("FINISHED");
                    auctionDao.update(auction);
                    System.out.println("🔴 Phiên " + auction.getId() + " đã chuyển sang FINISHED");

                    // 3. Sử dụng Observer Pattern để bắn thông báo WebSocket cho Client
                    BidUpdateMessage msg = BidUpdateMessage.auctionEnded(
                            auction.getId(),
                            auction.getLeadingBidderId(),
                            auction.getCurrentPrice()
                    );
                    eventManager.notifyAuctionEnd(auction.getId(), msg);
                }
            }
        } catch (Exception e) {
            // Đảm bảo nếu có lỗi thì luồng chạy ngầm không bị "chết"
            System.err.println("❌ Lỗi trong lúc quét Scheduler: " + e.getMessage());
            e.printStackTrace();
        }
    }
}