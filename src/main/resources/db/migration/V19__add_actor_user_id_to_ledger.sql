-- ON DELETE SET NULL: this column is audit metadata, not a data-integrity relationship - deleting a
-- user must never be blocked by (or cascade into destroying) historical ledger entries. If the user
-- is gone, the entry just loses its "who" attribution and falls back to "unknown actor".
ALTER TABLE gift_card_ledger ADD COLUMN actor_user_id BIGINT NULL REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX idx_gift_card_ledger_actor_user_id ON gift_card_ledger(actor_user_id);
