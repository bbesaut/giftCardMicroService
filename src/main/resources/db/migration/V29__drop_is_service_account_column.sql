-- Service accounts (email + password User rows) no longer exist as a concept - V28 already
-- deactivated every remaining one and decoupled api_keys from users entirely. No environment has
-- real merchants depending on this data, so this drops it outright rather than keeping a vestigial
-- column around.
DELETE FROM users WHERE is_service_account = TRUE;
ALTER TABLE users DROP COLUMN is_service_account;
