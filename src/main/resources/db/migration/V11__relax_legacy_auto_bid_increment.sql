-- Older local databases may still have a legacy auto_bid_configs.increment
-- column. The application uses increment_amount; keep the legacy column from
-- rejecting inserts that correctly populate increment_amount only.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'auto_bid_configs'
          AND column_name = 'increment'
    ) THEN
        ALTER TABLE auto_bid_configs ALTER COLUMN increment DROP NOT NULL;
    END IF;
END $$;
