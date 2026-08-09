ALTER TABLE users ADD COLUMN is_service_account BOOLEAN NOT NULL DEFAULT FALSE;

-- Under the previous one-user-per-merchant model, each MERCHANT user was the merchant's
-- only account and already served as its de facto automated-integration account.
UPDATE users SET is_service_account = TRUE WHERE role = 'MERCHANT';
