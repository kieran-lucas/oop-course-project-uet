package com.auction.dao;

import com.auction.model.AutoBidConfig;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * DAO (Data Access Object) cho bảng auto_bid_configs.
 * 
 * <p>Class này chịu trách nhiệm quản lý cấu hình đấu giá tự động (auto-bid).
 * Auto-bid cho phép người dùng đặt trước giá tối đa và bước giá, hệ thống sẽ
 * tự động trả giá thay họ khi có người khác bid.
 * 
 * <h3>Vai trò trong hệ thống</h3>
 * <p>AutoBidConfig là một phần quan trọng của chức năng nâng cao auto-bidding.
 * Mỗi khi có bid mới, AutoBidStrategy sẽ:
 * <ol>
 *   <li>Lấy tất cả auto-bid configs active của phiên đó</li>
 *   <li>Sắp xếp theo registered_at (ai đăng ký trước được ưu tiên)</li>
 *   <li>Duyệt từng config, nếu còn budget thì tự động bid</li>
 *   <li>Lặp lại cho đến khi không còn ai đủ budget</li>
 * </ol>
 * 
 * <h3>Ràng buộc UNIQUE</h3>
 * <p>Bảng auto_bid_configs có constraint UNIQUE(auction_id, bidder_id).
 * Điều này đảm bảo mỗi user chỉ có 1 auto-bid config active cho mỗi phiên.
 * Khi user thay đổi maxBid, ta UPDATE thay vì INSERT mới.
 * 
 * <h3>Trạng thái active</h3>
 * <p>Auto-bid config có thể bị vô hiệu hóa (active = false) trong các trường hợp:
 * <ul>
 *   <li>User chủ động tắt auto-bid</li>
 *   <li>Max bid đã bị vượt quá (hết budget)</li>
 *   <li>User đã thắng phiên (không cần auto-bid nữa)</li>
 * </ul>
 * 
 * <h3>Liên kết với các file khác</h3>
 * <ul>
 *   <li><b>AutoBidConfig.java</b> — model class, chứa cấu hình auto-bid</li>
 *   <li><b>BidService.java</b> — gọi DAO để lấy configs khi có bid mới</li>
 *   <li><b>AutoBidStrategy.java</b> — dùng PriorityQueue và DAO để xử lý</li>
 *   <li><b>V1__initial_schema.sql</b> — định nghĩa bảng auto_bid_configs</li>
 * </ul>
 */
public class AutoBidConfigDao {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoBidConfigDao.class);
    private final Jdbi jdbi;
    
    public AutoBidConfigDao(Jdbi jdbi) {
        this.jdbi = jdbi;
    }
    
    /**
     * RowMapper chuyển ResultSet thành AutoBidConfig object.
     * 
     * <p>Map các cột trong bảng auto_bid_configs:
     * <pre>
     * | id | auction_id | bidder_id | max_bid | increment_amount | active | registered_at |
     * </pre>
     * 
     * <p>Lưu ý: Bảng không có cột created_at, dùng registered_at cho cả createdAt.
     */
    private static class AutoBidConfigMapper implements RowMapper<AutoBidConfig> {
        @Override
        public AutoBidConfig map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new AutoBidConfig(
                rs.getLong("id"),
                rs.getLong("auction_id"),
                rs.getLong("bidder_id"),
                rs.getBigDecimal("max_bid"),
                rs.getBigDecimal("increment_amount"),
                rs.getBoolean("active"),
                rs.getTimestamp("registered_at").toLocalDateTime(),
                rs.getTimestamp("registered_at").toLocalDateTime() // created_at không có, dùng registered_at
            );
        }
    }
    
    // ============================================================
    // CREATE (INSERT)
    // ============================================================
    
    /**
     * Tạo cấu hình auto-bid mới.
     * 
     * <p>Được gọi khi người dùng bật auto-bid cho một phiên.
     * 
     * <p><b>Lưu ý:</b> Nếu user đã có config cho phiên này (do UNIQUE constraint),
     * INSERT sẽ thất bại. Trong trường hợp đó, nên gọi update() để cập nhật.
     * 
     * @param config AutoBidConfig cần tạo (chưa có id)
     * @return AutoBidConfig đã được gán id từ database
     * @throws org.jdbi.v3.core.statement.UnableToExecuteStatementException 
     *         nếu vi phạm UNIQUE constraint (user đã có config cho auction này)
     */
    public AutoBidConfig insert(AutoBidConfig config) {
        String sql = """
            INSERT INTO auto_bid_configs 
                (auction_id, bidder_id, max_bid, increment_amount, active, registered_at)
            VALUES 
                (:auctionId, :bidderId, :maxBid, :increment, :active, :registeredAt)
            RETURNING id
            """;
        
        return jdbi.withHandle(handle -> {
            long id = handle.createQuery(sql)
                    .bind("auctionId", config.getAuctionId())
                    .bind("bidderId", config.getBidderId())
                    .bind("maxBid", config.getMaxBid())
                    .bind("increment", config.getIncrement())
                    .bind("active", config.isActive())
                    .bind("registeredAt", config.getRegisteredAt())
                    .mapTo(Long.class)
                    .one();
            
            config.setId(id);
            LOGGER.debug("Inserted auto-bid config: auction={}, bidder={}, max={}", 
                    config.getAuctionId(), config.getBidderId(), config.getMaxBid());
            return config;
        });
    }
    
    // ============================================================
    // READ (SELECT)
    // ============================================================
    
    /**
     * Tìm cấu hình auto-bid theo ID.
     * 
     * @param id ID của cấu hình
     * @return Optional chứa AutoBidConfig nếu tìm thấy
     */
    public Optional<AutoBidConfig> findById(Long id) {
        String sql = """
            SELECT id, auction_id, bidder_id, max_bid, increment_amount, 
                   active, registered_at
            FROM auto_bid_configs
            WHERE id = :id
            """;
        
        return jdbi.withHandle(handle ->
            handle.createQuery(sql)
                    .bind("id", id)
                    .map(new AutoBidConfigMapper())
                    .findOne()
        );
    }
    
    /**
     * Tìm cấu hình auto-bid của một user trong một phiên.
     * 
     * <p>Method này quan trọng để:
     * <ul>
     *   <li>Kiểm tra user đã có auto-bid cho phiên này chưa</li>
     *   <li>Lấy config để cập nhật khi user thay đổi maxBid</li>
     *   <li>Kiểm tra trạng thái active của auto-bid user</li>
     * </ul>
     * 
     * @param auctionId ID phiên đấu giá
     * @param bidderId ID người đấu giá
     * @return Optional chứa AutoBidConfig nếu tồn tại
     */
    public Optional<AutoBidConfig> findByAuctionAndBidder(Long auctionId, Long bidderId) {
        String sql = """
            SELECT id, auction_id, bidder_id, max_bid, increment_amount, 
                   active, registered_at
            FROM auto_bid_configs
            WHERE auction_id = :auctionId AND bidder_id = :bidderId
            """;
        
        return jdbi.withHandle(handle ->
            handle.createQuery(sql)
                    .bind("auctionId", auctionId)
                    .bind("bidderId", bidderId)
                    .map(new AutoBidConfigMapper())
                    .findOne()
        );
    }
    
    /**
     * Lấy tất cả auto-bid configs active của một phiên.
     * 
     * <p><b>QUAN TRỌNG:</b> Method này được AutoBidStrategy gọi mỗi khi có bid mới.
     * Kết quả trả về cần được sắp xếp theo registered_at (ai đăng ký trước được ưu tiên).
     * 
     * @param auctionId ID phiên đấu giá
     * @return List các AutoBidConfig active, sắp xếp theo thời gian đăng ký
     */
    public List<AutoBidConfig> findActiveByAuctionId(Long auctionId) {
        String sql = """
            SELECT id, auction_id, bidder_id, max_bid, increment_amount, 
                   active, registered_at
            FROM auto_bid_configs
            WHERE auction_id = :auctionId AND active = true
            ORDER BY registered_at ASC
            """;
        
        return jdbi.withHandle(handle ->
            handle.createQuery(sql)
                    .bind("auctionId", auctionId)
                    .map(new AutoBidConfigMapper())
                    .list()
        );
    }
    
    /**
     * Lấy tất cả auto-bid configs của một user (xuyên suốt các phiên).
     * 
     * <p>Dùng để hiển thị trong User Profile: "Các phiên bạn đang auto-bid".
     * 
     * @param bidderId ID người đấu giá
     * @return List các AutoBidConfig của user đó
     */
    public List<AutoBidConfig> findByBidderId(Long bidderId) {
        String sql = """
            SELECT id, auction_id, bidder_id, max_bid, increment_amount, 
                   active, registered_at
            FROM auto_bid_configs
            WHERE bidder_id = :bidderId
            ORDER BY registered_at DESC
            """;
        
        return jdbi.withHandle(handle ->
            handle.createQuery(sql)
                    .bind("bidderId", bidderId)
                    .map(new AutoBidConfigMapper())
                    .list()
        );
    }
    
    /**
     * Lấy tất cả auto-bid configs của một phiên (kể cả inactive).
     * 
     * <p>Dùng cho Admin để xem lịch sử auto-bid của phiên.
     * 
     * @param auctionId ID phiên đấu giá
     * @return List tất cả AutoBidConfig của phiên
     */
    public List<AutoBidConfig> findAllByAuctionId(Long auctionId) {
        String sql = """
            SELECT id, auction_id, bidder_id, max_bid, increment_amount, 
                   active, registered_at
            FROM auto_bid_configs
            WHERE auction_id = :auctionId
            ORDER BY registered_at ASC
            """;
        
        return jdbi.withHandle(handle ->
            handle.createQuery(sql)
                    .bind("auctionId", auctionId)
                    .map(new AutoBidConfigMapper())
                    .list()
        );
    }
    
    // ============================================================
    // UPDATE
    // ============================================================
    
    /**
     * Cập nhật cấu hình auto-bid.
     * 
     * <p>Dùng khi:
     * <ul>
     *   <li>User thay đổi maxBid hoặc increment</li>
     *   <li>User tắt auto-bid (set active = false)</li>
     *   <li>Hệ thống vô hiệu hóa auto-bid khi hết budget</li>
     * </ul>
     * 
     * @param config AutoBidConfig đã cập nhật (phải có id)
     * @return true nếu cập nhật thành công, false nếu không tìm thấy
     */
    public boolean update(AutoBidConfig config) {
        String sql = """
            UPDATE auto_bid_configs
            SET max_bid = :maxBid,
                increment_amount = :increment,
                active = :active
            WHERE id = :id
            """;
        
        int rowsAffected = jdbi.withHandle(handle ->
            handle.createUpdate(sql)
                    .bind("maxBid", config.getMaxBid())
                    .bind("increment", config.getIncrement())
                    .bind("active", config.isActive())
                    .bind("id", config.getId())
                    .execute()
        );
        
        if (rowsAffected > 0) {
            LOGGER.debug("Updated auto-bid config: id={}, active={}, max={}", 
                    config.getId(), config.isActive(), config.getMaxBid());
            return true;
        }
        
        LOGGER.warn("Auto-bid config not found for update: id={}", config.getId());
        return false;
    }
    
    /**
     * Cập nhật cấu hình auto-bid theo auctionId và bidderId.
     * 
     * <p>Method này hữu ích khi không có id config (chỉ biết auction và user).
     * 
     * @param auctionId ID phiên đấu giá
     * @param bidderId ID người đấu giá
     * @param maxBid giá tối đa mới
     * @param increment bước giá mới
     * @param active trạng thái active mới
     * @return true nếu cập nhật thành công, false nếu không tìm thấy config
     */
    public boolean updateByAuctionAndBidder(Long auctionId, Long bidderId, 
                                             BigDecimal maxBid, BigDecimal increment, 
                                             boolean active) {
        String sql = """
            UPDATE auto_bid_configs
            SET max_bid = :maxBid,
                increment_amount = :increment,
                active = :active
            WHERE auction_id = :auctionId AND bidder_id = :bidderId
            """;
        
        int rowsAffected = jdbi.withHandle(handle ->
            handle.createUpdate(sql)
                    .bind("maxBid", maxBid)
                    .bind("increment", increment)
                    .bind("active", active)
                    .bind("auctionId", auctionId)
                    .bind("bidderId", bidderId)
                    .execute()
        );
        
        if (rowsAffected > 0) {
            LOGGER.debug("Updated auto-bid config: auction={}, bidder={}, active={}, max={}", 
                    auctionId, bidderId, active, maxBid);
            return true;
        }
        
        return false;
    }
    
    /**
     * Vô hiệu hóa auto-bid của một user trong một phiên.
     * 
     * <p>Được gọi khi:
     * <ul>
     *   <li>User tắt auto-bid</li>
     *   <li>Auto-bid hết budget (maxBid đã bị vượt)</li>
     *   <li>User thắng phiên (không cần auto-bid nữa)</li>
     *   <li>Phiên đấu giá kết thúc</li>
     * </ul>
     * 
     * @param auctionId ID phiên đấu giá
     * @param bidderId ID người đấu giá
     * @return true nếu vô hiệu hóa thành công, false nếu không tìm thấy config
     */
    public boolean deactivate(Long auctionId, Long bidderId) {
        String sql = """
            UPDATE auto_bid_configs
            SET active = false
            WHERE auction_id = :auctionId AND bidder_id = :bidderId AND active = true
            """;
        
        int rowsAffected = jdbi.withHandle(handle ->
            handle.createUpdate(sql)
                    .bind("auctionId", auctionId)
                    .bind("bidderId", bidderId)
                    .execute()
        );
        
        if (rowsAffected > 0) {
            LOGGER.info("Deactivated auto-bid: auction={}, bidder={}", auctionId, bidderId);
            return true;
        }
        
        return false;
    }
    
    /**
     * Vô hiệu hóa tất cả auto-bid configs của một phiên.
     * 
     * <p>Được gọi khi phiên đấu giá kết thúc (FINISHED hoặc CANCELED).
     * 
     * @param auctionId ID phiên đấu giá
     * @return số lượng configs bị vô hiệu hóa
     */
    public int deactivateAllByAuctionId(Long auctionId) {
        String sql = """
            UPDATE auto_bid_configs
            SET active = false
            WHERE auction_id = :auctionId AND active = true
            """;
        
        int rowsAffected = jdbi.withHandle(handle ->
            handle.createUpdate(sql)
                    .bind("auctionId", auctionId)
                    .execute()
        );
        
        if (rowsAffected > 0) {
            LOGGER.info("Deactivated {} auto-bid configs for auction: {}", 
                    rowsAffected, auctionId);
        }
        
        return rowsAffected;
    }
    
    // ============================================================
    // DELETE (Chỉ dùng cho test)
    // ============================================================
    
    /**
     * Xóa cấu hình auto-bid theo ID.
     * 
     * <p><b>CHỈ DÙNG CHO TEST.</b> Không gọi method này trong production.
     * Trong production, chỉ cập nhật active = false, không xóa.
     * 
     * @param id ID của cấu hình cần xóa
     * @return true nếu xóa thành công
     */
    public boolean deleteById(Long id) {
        String sql = "DELETE FROM auto_bid_configs WHERE id = :id";
        
        int rowsAffected = jdbi.withHandle(handle ->
            handle.createUpdate(sql)
                    .bind("id", id)
                    .execute()
        );
        
        return rowsAffected > 0;
    }
    
    /**
     * Xóa tất cả auto-bid configs của một phiên.
     * 
     * <p><b>CHỈ DÙNG CHO TEST.</b>
     * 
     * @param auctionId ID phiên đấu giá
     * @return số lượng configs bị xóa
     */
    public int deleteByAuctionId(Long auctionId) {
        String sql = "DELETE FROM auto_bid_configs WHERE auction_id = :auctionId";
        
        return jdbi.withHandle(handle ->
            handle.createUpdate(sql)
                    .bind("auctionId", auctionId)
                    .execute()
        );
    }
    
    // ============================================================
    // HELPER METHODS
    // ============================================================
    
    /**
     * Kiểm tra user đã có auto-bid config active cho phiên này chưa.
     * 
     * @param auctionId ID phiên đấu giá
     * @param bidderId ID người đấu giá
     * @return true nếu đã có config active
     */
    public boolean hasActiveConfig(Long auctionId, Long bidderId) {
        String sql = """
            SELECT COUNT(*) FROM auto_bid_configs 
            WHERE auction_id = :auctionId AND bidder_id = :bidderId AND active = true
            """;
        
        long count = jdbi.withHandle(handle ->
            handle.createQuery(sql)
                    .bind("auctionId", auctionId)
                    .bind("bidderId", bidderId)
                    .mapTo(Long.class)
                    .one()
        );
        
        return count > 0;
    }
    
    /**
     * Lấy số lượng auto-bid configs active của một phiên.
     * 
     * @param auctionId ID phiên đấu giá
     * @return số lượng configs active
     */
    public int countActiveByAuctionId(Long auctionId) {
        String sql = """
            SELECT COUNT(*) FROM auto_bid_configs 
            WHERE auction_id = :auctionId AND active = true
            """;
        
        return jdbi.withHandle(handle ->
            handle.createQuery(sql)
                    .bind("auctionId", auctionId)
                    .mapTo(Integer.class)
                    .one()
        );
    }
}