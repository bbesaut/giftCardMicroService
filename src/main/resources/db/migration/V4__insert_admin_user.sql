-- Insert default admin user for production
-- Password: admin123 (hashed with BCrypt cost 10)
-- To change the password, use the BcryptHashGenerator utility or online BCrypt tool
-- DO NOT hardcode plaintext passwords

INSERT INTO users (email, password, role)
VALUES ('admin@finovago.com', '$2a$10$mH6zYdBAzEw5Fy1RZJRBkO6rRT5MziKkna/Pl6IvhG/SZfrJmRUpG', 'ADMIN')
ON CONFLICT (email) DO NOTHING;
