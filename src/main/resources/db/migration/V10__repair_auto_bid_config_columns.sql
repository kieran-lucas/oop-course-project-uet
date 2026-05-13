-- Repair older local databases whose V1 auto_bid_configs table was created
-- before active and registered_at existed.
ALTER TABLE auto_bid_configs
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE auto_bid_configs
    ADD COLUMN IF NOT EXISTS registered_at TIMESTAMP NOT NULL DEFAULT NOW();
