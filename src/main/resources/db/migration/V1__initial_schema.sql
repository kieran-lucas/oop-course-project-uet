<<<<<<< HEAD
-- V1: Initial schema for auction system (UPDATED - khớp với DAO hiện tại)

=======
-- V1: Initial schema for auction system
>>>>>>> c3f76507c55bc1e193cd958fb01c50c4ea92d4dd
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50) UNIQUE NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    email           VARCHAR(100) UNIQUE NOT NULL,
    role            VARCHAR(20) NOT NULL CHECK (role IN ('BIDDER', 'SELLER', 'ADMIN')),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE items (
    id              BIGSERIAL PRIMARY KEY,
    seller_id       BIGINT NOT NULL REFERENCES users(id),
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    category        VARCHAR(20) NOT NULL CHECK (category IN ('ELECTRONICS', 'ART', 'VEHICLE')),
    brand           VARCHAR(100),
    artist          VARCHAR(100),
    year            INTEGER,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE auctions (
    id                  BIGSERIAL PRIMARY KEY,
    item_id             BIGINT NOT NULL REFERENCES items(id),
    starting_price      DECIMAL(15,2) NOT NULL,
    current_price       DECIMAL(15,2) NOT NULL,
    leading_bidder_id   BIGINT REFERENCES users(id),
    start_time          TIMESTAMP NOT NULL,
    end_time            TIMESTAMP NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN'
                        CHECK (status IN ('OPEN', 'RUNNING', 'FINISHED', 'PAID', 'CANCELED')),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ==================== SỬA Ở ĐÂY ====================
CREATE TABLE bid_transactions (
    id              BIGSERIAL PRIMARY KEY,
    auction_id      BIGINT NOT NULL REFERENCES auctions(id),
    bidder_id       BIGINT NOT NULL REFERENCES users(id),
    amount          DECIMAL(15,2) NOT NULL,
    auto_bid        BOOLEAN NOT NULL DEFAULT FALSE,   -- ← sửa từ is_auto_bid
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE auto_bid_configs (
    id              BIGSERIAL PRIMARY KEY,
    auction_id      BIGINT NOT NULL REFERENCES auctions(id),
    bidder_id       BIGINT NOT NULL REFERENCES users(id),
    max_bid         DECIMAL(15,2) NOT NULL,
    increment_amount DECIMAL(15,2) NOT NULL,         -- ← sửa từ increment
    active          BOOLEAN NOT NULL DEFAULT TRUE,    -- ← sửa từ is_active
    registered_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (auction_id, bidder_id)
);
-- =================================================

-- Indexes for common queries
CREATE INDEX idx_auctions_status ON auctions(status);
CREATE INDEX idx_bid_transactions_auction ON bid_transactions(auction_id);
CREATE INDEX idx_items_seller ON items(seller_id);