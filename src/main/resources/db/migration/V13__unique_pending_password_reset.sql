-- Enforce the service rule at the database level: one pending password reset
-- request per user. Older duplicate pending rows are rejected before the index
-- is created so existing local databases can migrate cleanly.
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY created_at DESC, id DESC) AS rn
    FROM password_reset_requests
    WHERE status = 'PENDING'
)
UPDATE password_reset_requests
SET status = 'REJECTED',
    reviewed_at = NOW()
WHERE id IN (SELECT id FROM ranked WHERE rn > 1);

CREATE UNIQUE INDEX IF NOT EXISTS ux_password_reset_one_pending_per_user
    ON password_reset_requests(user_id)
    WHERE status = 'PENDING';
