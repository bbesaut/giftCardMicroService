ALTER TABLE gift_card_ledger ADD COLUMN actor_user_id BIGINT NULL REFERENCES users(id);
CREATE INDEX idx_gift_card_ledger_actor_user_id ON gift_card_ledger(actor_user_id);
