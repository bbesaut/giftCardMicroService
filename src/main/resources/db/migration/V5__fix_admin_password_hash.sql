-- Fix admin password hash to match "admin123"
-- Previous migration V4 had incorrect hash
-- This migration updates it to the correct BCrypt hash

UPDATE users
SET password = '$2a$10$mH6zYdBAzEw5Fy1RZJRBkO6rRT5MziKkna/Pl6IvhG/SZfrJmRUpG'
WHERE email = 'admin@finovago.com'
AND password = '$2a$10$slYQmyNdGzin7olVN3p5Be7DlH.PKZbv5H8KnzzVgXXbVxzy2.k/m';
