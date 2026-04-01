package model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Bidder extends User {

    private double walletBalance;
    private final List<Long> watchList;
    private final List<Long> bidHistory;
    private double rating;

    public Bidder() {
        super();
        this.watchList     = new ArrayList<>();
        this.bidHistory    = new ArrayList<>();
        this.walletBalance = 0.0;
        this.rating        = 5.0; // rating mặc định khi mới tạo
    }

    public class BidTransaction {
        private static long counter = 1; // Tạo ID tự động tạm thời
        private Long id;
        private Long bidderId;
        private Long auctionId;
        private double amount;
        private LocalDateTime timestamp;

        public BidTransaction(Long bidderId, Long auctionId, double amount) {
            this.id = counter++;
            this.bidderId = bidderId;
            this.auctionId = auctionId;
            this.amount = amount;
            this.timestamp = LocalDateTime.now();
        }

        public Long getId() {
            return id;
        }
    }

    public Bidder(String username, String password, String email) {
        // Gọi constructor của User, truyền role = BIDDER
        super(username, password, Role.BIDDER, email);
        this.watchList     = new ArrayList<>();
        this.bidHistory    = new ArrayList<>();
        this.walletBalance = 0.0;
        this.rating        = 5.0;
    }

    public BidTransaction placeBid(Long auctionId, double amount) {
        // Validation 1: amount phải dương
        if (amount <= 0) {
            throw new IllegalArgumentException(
                "Số tiền đặt giá phải lớn hơn 0. Giá trị nhận được: " + amount);
        }

        // Validation 2: số dư ví phải đủ
        if (walletBalance < amount) {
            throw new IllegalStateException(
                String.format("Số dư không đủ. Cần: %.0f, Hiện có: %.0f", amount, walletBalance));
        }

        // Sửa lỗi 2: TRỪ TIỀN TRONG VÍ SAU KHI ĐẶT GIÁ HỢP LỆ
        this.walletBalance -= amount;

        // Tạo BidTransaction
        BidTransaction transaction = new BidTransaction(this.getId(), auctionId, amount);

        // Lưu vào lịch sử
        bidHistory.add(transaction.getId());

        System.out.printf("[Bidder %s] Đã đặt giá %.0f VND cho phiên #%d. Số dư còn lại: %.0f VND%n",
            getUsername(), amount, auctionId, walletBalance);

        return transaction;
    }

    public void watchAuction(Long auctionId) {
        if (auctionId == null) {
            throw new IllegalArgumentException("AuctionId không được null.");
        }
        if (!watchList.contains(auctionId)) {
            watchList.add(auctionId);
            System.out.printf("[Bidder %s] Đang theo dõi phiên đấu giá #%d%n",
                getUsername(), auctionId);
        } else {
            System.out.println("Bạn đã theo dõi phiên này rồi.");
        }
    }

    public void unwatchAuction(Long auctionId) {
        if (watchList.remove(auctionId)) {
            System.out.printf("[Bidder %s] Đã bỏ theo dõi phiên #%d%n",
                getUsername(), auctionId);
        }
    }

    public void topUpWallet(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Số tiền nạp phải > 0.");
        }
        walletBalance += amount;
        System.out.printf("[Bidder %s] Nạp tiền thành công. Số dư hiện tại: %.0f VND%n",
            getUsername(), walletBalance);
    }

    @Override
    public void printInfo() {
        System.out.println("=== BIDDER INFO ===");
        System.out.println("ID       : " + getId());
        System.out.println("Username : " + getUsername());
        System.out.println("Email    : " + getEmail());
        System.out.printf ("Balance  : %.0f VND%n", walletBalance);
        System.out.println("Bids     : " + bidHistory.size() + " lần");
        System.out.println("Watching : " + watchList.size() + " phiên");
        System.out.printf ("Rating   : %.1f/5.0%n", rating);
    }

    @Override
    public String getRoleDescription() {
        return "Người tham gia đấu giá (Bidder)";
    }

    public double getWalletBalance() { return walletBalance; }
    public void setWalletBalance(double walletBalance) {
        if (walletBalance < 0) throw new IllegalArgumentException("Số dư không thể âm.");
        this.walletBalance = walletBalance;
    }

    public List<Long> getWatchList() {
        return Collections.unmodifiableList(watchList);
    }

    public List<Long> getBidHistory() {
        return Collections.unmodifiableList(bidHistory);
    }

    public double getRating() { return rating; }
    public void setRating(double rating) {
        if (rating < 0 || rating > 5) throw new IllegalArgumentException("Rating phải từ 0–5.");
        this.rating = rating;
    }
}
