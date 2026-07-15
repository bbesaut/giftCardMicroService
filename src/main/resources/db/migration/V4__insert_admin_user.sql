-- Insert default admin user for production
-- Password is hashed with BCrypt
-- To generate your own hash, use the script in docs/generate-bcrypt-hash.sh
-- Or change the password and role as needed

INSERT INTO users (email, password, role)
VALUES ('admin@finovago.com', '$2a$10$slYQmyNdGzin7olVN3p5Be7DlH.PKZbv5H8KnzzVgXXbVxzy2.k/m', 'ADMIN')
ON CONFLICT (email) DO NOTHING;
