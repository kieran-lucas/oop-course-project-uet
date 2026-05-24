-- ============================================================
-- Migration V17: Bảng wallet_transactions — Sổ cái ví điện tử
-- Mô tả : Lưu lịch sử giao dịch tài chính của từng user trong
--          các luồng nghiệp vụ bình thường dưới dạng append-only
--          ledger (sổ cái chỉ thêm, không sửa).
--
--          Ngoại lệ có chủ đích: admin hard-delete auction là
--          destructive cleanup. Luồng đó có thể purge các ledger
--          rows gắn với auction bị xóa để dọn khóa ngoại trước khi
--          xóa auction row. Không dùng hard-delete như nghiệp vụ
--          tài chính thông thường.
--
--         Mô hình balance hiện tại:
--           balance là tổng số dư ví đã nạp, chưa trừ tiền giữ chỗ.
--           reserved_balance là phần balance đang bị khóa bởi bid đang dẫn đầu.
--           available_balance = balance - reserved_balance.
--
--         Các loại giao dịch (kind):
--           DEPOSIT       — User nạp tiền vào ví khi admin duyệt deposit request
--                           (balance tăng).
--           FREEZE        — Tạm giữ tiền khi user dẫn đầu phiên đấu giá
--                           (reserved_balance tăng; balance chưa giảm ở bước này).
--           RELEASE       — Giải phóng tiền giữ chỗ khi user bị vượt qua hoặc
--                           settlement không thu được tiền thắng
--                           (reserved_balance giảm; balance không tăng ở bước này).
--           WIN_CONSUME   — Xác nhận tiêu dùng khi user thắng đấu giá
--                           (balance giảm và reserved_balance giảm).
--           SELLER_PAYOUT — Chuyển tiền cho seller khi phiên kết thúc
--                           (balance của seller tăng).
--           CANCEL_RELEASE— Giải phóng tiền đang giữ khi phiên bị hủy
--                           (reserved_balance giảm; balance không tăng ở bước này).
--
--         Liên kết:
--           user_id            — chủ tài khoản của giao dịch
--           auction_id         — phiên đấu giá liên quan (NULL cho DEPOSIT)
--           bid_transaction_id — lượt đặt giá kích hoạt giao dịch này (NULL nếu không có)
--           reference_info     — text tùy ý để debug / trace (ví dụ: "auction_cancel:42")
--
--         Indexes:
--           (user_id, created_at DESC) — lịch sử ví của một user theo thứ tự mới nhất
--           (auction_id)               — tất cả giao dịch liên quan đến một phiên
--           (bid_transaction_id)       — giao dịch kích hoạt bởi một lượt bid cụ thể
-- ============================================================

CREATE TABLE wallet_transactions (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    auction_id          BIGINT REFERENCES auctions(id),        -- NULL cho DEPOSIT
    bid_transaction_id  BIGINT REFERENCES bid_transactions(id),-- NULL nếu không liên quan đến bid
    kind                VARCHAR(32) NOT NULL CHECK (
                            kind IN (
                                'DEPOSIT',
                                'FREEZE',
                                'RELEASE',
                                'WIN_CONSUME',
                                'SELLER_PAYOUT',
                                'CANCEL_RELEASE'
                            )
                        ),
    amount              DECIMAL(15,2) NOT NULL CHECK (amount > 0), -- Luôn dương — chiều tiền xác định bởi kind
    reference_info      TEXT,                                  -- Thông tin tra cứu / debug tùy ý
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Lịch sử ví: lấy giao dịch mới nhất của một user (ORDER BY created_at DESC)
CREATE INDEX idx_wallet_transactions_user_created
    ON wallet_transactions(user_id, created_at DESC);

-- Tìm tất cả giao dịch liên quan đến một phiên đấu giá
CREATE INDEX idx_wallet_transactions_auction
    ON wallet_transactions(auction_id);

-- Truy ngược từ bid_transaction sang wallet_transaction tương ứng
CREATE INDEX idx_wallet_transactions_bid
    ON wallet_transactions(bid_transaction_id);