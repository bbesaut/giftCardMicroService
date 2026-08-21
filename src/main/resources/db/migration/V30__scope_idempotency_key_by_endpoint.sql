-- An idempotency key was only ever unique per (merchant, key), so the same key value reused
-- across two different mutating endpoints (e.g. reserve then redeem) with a payload that happened
-- to hash the same silently replayed the wrong endpoint's cached response instead of failing.
-- Scoping by endpoint too (like Stripe/PayPal/AWS do) makes that structurally impossible, the same
-- way real production idempotency systems work - not by trying to keep every endpoint's hash
-- inputs mutually exclusive by convention.
ALTER TABLE idempotency_key ADD COLUMN endpoint VARCHAR(50) NOT NULL DEFAULT 'UNKNOWN';

DROP INDEX idx_idempotency_key_merchant_key;
CREATE UNIQUE INDEX idx_idempotency_key_merchant_endpoint_key ON idempotency_key (merchant_id, endpoint, idempotency_key);
