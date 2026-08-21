-- One API key per merchant service-account User, mirroring the previous one-service-account-per-merchant
-- rule. The User row itself is unchanged (still the ledger actor / /credit-block identity); this table
-- only replaces how that identity is authenticated (a rotatable key instead of a login password).
CREATE TABLE api_keys (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
    key_prefix VARCHAR(32) NOT NULL UNIQUE,
    hashed_secret VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    revoked_at TIMESTAMP,
    last_used_at TIMESTAMP
);
