-- Migration V15: Thêm trạng thái chi tiết cho auto-bid.
ALTER TABLE auto_bid_configs
    ADD COLUMN IF NOT EXISTS status VARCHAR(20);

UPDATE auto_bid_configs
SET status = CASE WHEN active THEN 'ACTIVE' ELSE 'STOPPED' END
WHERE status IS NULL;

ALTER TABLE auto_bid_configs
    ALTER COLUMN status SET DEFAULT 'ACTIVE';

ALTER TABLE auto_bid_configs
    ALTER COLUMN status SET NOT NULL;

ALTER TABLE auto_bid_configs
    ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(50);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'auto_bid_configs_status_check'
    ) THEN
        ALTER TABLE auto_bid_configs
            ADD CONSTRAINT auto_bid_configs_status_check
            CHECK (status IN ('ACTIVE', 'STOPPED', 'EXHAUSTED', 'FAILED'));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'auto_bid_configs_failure_reason_check'
    ) THEN
        ALTER TABLE auto_bid_configs
            ADD CONSTRAINT auto_bid_configs_failure_reason_check
            CHECK (
                failure_reason IS NULL
                OR failure_reason IN (
                    'MAX_PRICE_TOO_LOW',
                    'INSUFFICIENT_BALANCE',
                    'AUCTION_NOT_RUNNING',
                    'BIDDER_ALREADY_HIGHEST',
                    'ACTIVE_AUTOBID_EXISTS'
                )
            );
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_auto_bid_configs_status ON auto_bid_configs(status);
