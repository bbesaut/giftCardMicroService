ALTER TABLE gift_card
    ALTER COLUMN merchant_id SET NOT NULL;

ALTER TABLE gift_card
    DROP CONSTRAINT gift_card_card_code_key;

ALTER TABLE gift_card
    ADD CONSTRAINT uq_giftcard_merchant_cardcode UNIQUE (merchant_id, card_code);

ALTER TABLE users
    ADD CONSTRAINT chk_users_role_merchant CHECK (
        (role = 'ADMIN' AND merchant_id IS NULL)
        OR (role = 'MERCHANT' AND merchant_id IS NOT NULL)
    );
