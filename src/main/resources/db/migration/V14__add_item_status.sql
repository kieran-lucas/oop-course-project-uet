-- Thêm vòng đời cho hàng đơn chiếc: còn rảnh, đang đấu giá, đã bán, hoặc đã gỡ.
ALTER TABLE items
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE';

ALTER TABLE items
    ALTER COLUMN status SET DEFAULT 'AVAILABLE';

UPDATE items
SET status = 'AVAILABLE'
WHERE status IS NULL;

ALTER TABLE items
    ALTER COLUMN status SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'items_status_check'
    ) THEN
        ALTER TABLE items
            ADD CONSTRAINT items_status_check
            CHECK (status IN ('AVAILABLE', 'IN_AUCTION', 'SOLD', 'REMOVED'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_items_status ON items(status);
