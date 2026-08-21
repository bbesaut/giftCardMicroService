-- Ledger entries written by an API-key-authenticated request have no User row to attribute to
-- (api_keys is decoupled from users - see V28), so this flag lets LedgerService render "SYSTEM"
-- instead of conflating them with genuinely unattributed legacy rows ("Unknown", null actor_user_id).
ALTER TABLE gift_card_ledger ADD COLUMN actor_via_api_key BOOLEAN NOT NULL DEFAULT FALSE;
