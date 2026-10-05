-- Existing accounts predate email verification: they are grandfathered in as verified so that
-- current owners (and the dev seed accounts) are not locked out at the next login. New rows get
-- FALSE from the column default, which matches the User constructor.
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE users SET email_verified = TRUE;
