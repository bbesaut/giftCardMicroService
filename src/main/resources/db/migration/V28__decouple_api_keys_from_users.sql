-- API keys are their own identity, not a stand-in for a "service account" User (no such user has a
-- real email/password to speak of). Rekey api_keys directly onto the merchant it belongs to.
ALTER TABLE api_keys ADD COLUMN merchant_id BIGINT REFERENCES merchants(id);

UPDATE api_keys ak
    SET merchant_id = u.merchant_id
    FROM users u
    WHERE u.id = ak.user_id;

ALTER TABLE api_keys ALTER COLUMN merchant_id SET NOT NULL;
ALTER TABLE api_keys ADD CONSTRAINT uq_api_keys_merchant_id UNIQUE (merchant_id);
ALTER TABLE api_keys DROP COLUMN user_id;

-- Pre-API-key service-account Users (email + password, created by /register or the old
-- is_service_account=TRUE backfill in V18) can no longer log in (AuthService#login rejects service
-- accounts outright) and are no longer referenced by the api_keys lookup above. Deactivate rather
-- than delete: preserves gift_card_ledger.actor_user_id history and FK integrity for past entries.
UPDATE users SET active = FALSE WHERE is_service_account = TRUE;
