-- Migration V8: Add seller_id to auctions table
-- Description: Auctions table was missing seller_id, which led to security issues (sellers bidding on their own auctions)
-- and extra queries. This migration adds seller_id and populates it from items table.

ALTER TABLE auctions ADD COLUMN IF NOT EXISTS seller_id BIGINT REFERENCES users(id);

-- Populate seller_id from items table
UPDATE auctions SET seller_id = (SELECT seller_id FROM items WHERE items.id = auctions.item_id);

-- Make it NOT NULL after population
ALTER TABLE auctions ALTER COLUMN seller_id SET NOT NULL;

-- Add index for performance
CREATE INDEX IF NOT EXISTS idx_auctions_seller ON auctions(seller_id);
