-- reference_id only ever holds a GiftCardHold id (HOLD_PLACED/HOLD_CAPTURED/HOLD_RELEASED entries);
-- CREATION/REDEMPTION always leave it null. Renamed for clarity - it was never actually generic.
ALTER TABLE gift_card_ledger RENAME COLUMN reference_id TO hold_id;
