-- Money columns were DOUBLE PRECISION, which cannot represent currency amounts exactly.
-- NUMERIC(19,2) matches the BigDecimal domain type used in the application layer.
ALTER TABLE gift_card ALTER COLUMN balance TYPE NUMERIC(19,2);

ALTER TABLE gift_card_hold ALTER COLUMN amount TYPE NUMERIC(19,2);

ALTER TABLE gift_card_ledger ALTER COLUMN amount TYPE NUMERIC(19,2);
ALTER TABLE gift_card_ledger ALTER COLUMN balance_after TYPE NUMERIC(19,2);
