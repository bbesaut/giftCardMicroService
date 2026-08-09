-- Idempotency is being reused by a second endpoint (hold reserve) whose response shape differs
-- from redeem's. Replace the redeem-specific response columns with a single JSON blob so the
-- table stays endpoint-agnostic.
ALTER TABLE idempotency_key
    DROP COLUMN response_status,
    DROP COLUMN deducted_amount,
    DROP COLUMN remaining_balance,
    DROP COLUMN remaining_to_pay;

ALTER TABLE idempotency_key
    ADD COLUMN response_body TEXT;
