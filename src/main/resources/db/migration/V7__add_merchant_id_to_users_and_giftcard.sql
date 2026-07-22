ALTER TABLE users
    ADD COLUMN merchant_id BIGINT REFERENCES merchants(id);

ALTER TABLE gift_card
    ADD COLUMN merchant_id BIGINT REFERENCES merchants(id);

CREATE INDEX idx_gift_card_merchant_id ON gift_card(merchant_id);
CREATE INDEX idx_users_merchant_id ON users(merchant_id);
