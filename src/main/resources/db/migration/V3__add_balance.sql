-- Add account balance to users. Idempotent for existing local databases.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS balance DECIMAL(15,2) NOT NULL DEFAULT 0;
