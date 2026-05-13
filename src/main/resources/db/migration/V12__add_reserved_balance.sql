-- Track money currently held by leading bids so a user cannot spend the same
-- balance across multiple auctions.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS reserved_balance DECIMAL(15,2) NOT NULL DEFAULT 0;
