INSERT INTO merchants (name, contact_email, active, created_at)
VALUES ('Finovago Legacy Merchant', 'client@finovago.com', TRUE, now());

UPDATE users
SET merchant_id = (SELECT id FROM merchants WHERE contact_email = 'client@finovago.com')
WHERE role = 'CLIENT';

UPDATE gift_card
SET merchant_id = (SELECT id FROM merchants WHERE contact_email = 'client@finovago.com')
WHERE merchant_id IS NULL;
