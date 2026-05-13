-- Allow SETTLING as an intermediate lock state during auction settlement
ALTER TABLE auctions DROP CONSTRAINT IF EXISTS auctions_status_check;
ALTER TABLE auctions ADD CONSTRAINT auctions_status_check
    CHECK (status IN ('OPEN', 'RUNNING', 'SETTLING', 'FINISHED', 'PAID', 'CANCELED'));
