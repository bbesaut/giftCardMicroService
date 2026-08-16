-- Adds REFUND (crediting a card back for a specific prior redemption) and ADJUSTMENT (free-form
-- manual credit with a mandatory reason) as valid gift_card_ledger entry types, plus the two
-- columns those types need: related_entry_id links a REFUND back to the REDEMPTION entry it
-- reverses, reason carries the operator's justification for ADJUSTMENT (optional for REFUND).
--
-- No FK on related_entry_id -> gift_card_ledger(id): the table is partitioned by created_at (see
-- V21), and Postgres requires a FK on a partitioned table to include the full partition key, which
-- would force every caller to also carry the target row's created_at just to satisfy the
-- constraint. Not worth it here; validated at the application level instead (GiftCardCreditService).

ALTER TABLE gift_card_ledger ADD COLUMN related_entry_id BIGINT NULL;
ALTER TABLE gift_card_ledger ADD COLUMN reason VARCHAR(500) NULL;

CREATE INDEX idx_gift_card_ledger_related_entry_id ON gift_card_ledger (related_entry_id);

-- V21's create-copy-swap left the entry_type CHECK constraint auto-named by Postgres (never given
-- an explicit name), so look up its actual name instead of guessing it.
DO $$
DECLARE
    constraint_name text;
BEGIN
    SELECT con.conname INTO constraint_name
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    WHERE rel.relname = 'gift_card_ledger'
      AND con.contype = 'c'
      AND pg_get_constraintdef(con.oid) LIKE '%entry_type%';

    EXECUTE format('ALTER TABLE gift_card_ledger DROP CONSTRAINT %I', constraint_name);
END $$;

ALTER TABLE gift_card_ledger ADD CONSTRAINT gift_card_ledger_entry_type_check CHECK (
    entry_type IN ('CREATION', 'REDEMPTION', 'HOLD_PLACED', 'HOLD_CAPTURED', 'HOLD_RELEASED', 'REFUND', 'ADJUSTMENT')
);
