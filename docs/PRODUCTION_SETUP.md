# Production Setup Guide

## Admin User Creation

### Default Admin (V4 Migration)

When the application starts in production, Flyway automatically runs the migration `V4__insert_admin_user.sql` which creates a default admin user:

- **Email**: `admin@finovago.com`
- **Password**: `admin123` (BCrypt hash: `$2a$10$slYQmyNdGzin7olVN3p5Be7DlH.PKZbv5H8KnzzVgXXbVxzy2.k/m`)
- **Role**: `ADMIN`

### Customizing Admin Password

**IMPORTANT**: Change the default password immediately after first login!

To create an admin with a custom password:

1. **Generate BCrypt hash** of your desired password:
   ```bash
   # Using the provided script
   chmod +x docs/generate-bcrypt-hash.sh
   ./docs/generate-bcrypt-hash.sh "YourSecurePassword123!"
   ```

   Or use this Spring Boot one-liner:
   ```bash
   mvn spring-boot:run -Dspring-boot.run.arguments="--bcrypt=YourPassword"
   ```

2. **Update the migration** `V4__insert_admin_user.sql`:
   ```sql
   INSERT INTO users (email, password, role)
   VALUES ('admin@finovago.com', '$2a$10$YOUR_GENERATED_HASH_HERE', 'ADMIN')
   ON CONFLICT (email) DO NOTHING;
   ```

3. **Commit and deploy** - Flyway will automatically run the migration on startup

### Creating Additional Admins

Once you have the first admin, you can create more admins via:

1. **Direct SQL** (safest):
   ```sql
   INSERT INTO users (email, password, role)
   VALUES ('another-admin@finovago.com', '<bcrypt-hash>', 'ADMIN');
   ```

2. **Future endpoint** (when implemented):
   ```bash
   POST /api/v1/admin/users
   Authorization: Bearer <admin-token>
   Content-Type: application/json

   {
     "email": "admin2@finovago.com",
     "password": "SecurePassword123!",
     "role": "ADMIN"
   }
   ```

### User Registration (Self-Service)

Regular users can register themselves via the public endpoint:

```bash
POST /api/v1/auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password123"
}
```

New users are automatically assigned the **CLIENT** role and cannot elevate to ADMIN.

### Database Constraints

- **Email uniqueness**: `email` column has UNIQUE constraint - prevents duplicate registrations
- **Role constraint**: Only ADMIN users can be created directly in database
- **Password hashing**: All passwords are BCrypt-hashed (cost factor 10)

## Security Checklist

- [ ] Change default admin password before going live
- [ ] Configure JWT_SECRET_KEY environment variable (never hardcode)
- [ ] Enable HTTPS in production
- [ ] Set appropriate log levels (INFO or WARNING, not DEBUG)
- [ ] Review database backups and recovery procedures
- [ ] Monitor authentication attempts in logs (MDC correlation IDs)
