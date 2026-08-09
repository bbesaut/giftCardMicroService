-- Least-privilege grants for the runtime application role (p2p_app).
-- This role is NOT the table owner (that's the migration role running this script), so REVOKE/GRANT
-- actually constrains it - unlike the owner, which always bypasses privilege checks.
--
-- Prerequisite: the p2p_app role must already exist before this migration runs (created out-of-band per
-- environment - docker-entrypoint-initdb.d for local dev, TestContainers bootstrap for tests, Neon console
-- for prod). Role creation is deliberately NOT done here: it would require committing a password to this
-- file, which is checked into git and applied identically to every environment including prod.
GRANT SELECT, INSERT, UPDATE, DELETE ON
    users, merchants, gift_card, gift_card_hold, idempotency_key, refresh_tokens
    TO p2p_app;

-- gift_card_ledger is an append-only audit trail: the app may only ever add rows, never change or
-- remove existing history. Deliberately no UPDATE/DELETE here.
GRANT SELECT, INSERT ON gift_card_ledger TO p2p_app;

-- IDENTITY columns need USAGE on their backing sequence for INSERT ... RETURNING to work for a non-owner role.
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO p2p_app;

-- Keep future tables covered automatically without a follow-up migration.
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO p2p_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO p2p_app;
