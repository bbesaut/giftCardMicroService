#!/bin/bash
# Generate BCrypt hash for a password to use in SQL migrations
# Usage: ./generate-bcrypt-hash.sh "your-password"

if [ -z "$1" ]; then
    echo "Usage: $0 <password>"
    echo "Example: $0 'MySecurePassword123!'"
    exit 1
fi

PASSWORD=$1

# Use Java to generate BCrypt hash
java -version 2>&1 | grep -q "version" || {
    echo "Java is not installed or not in PATH"
    exit 1
}

java -jar - "$PASSWORD" 2>/dev/null << 'EOF'
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
public class HashGenerator {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java HashGenerator <password>");
            System.exit(1);
        }
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode(args[0]);
        System.out.println("Hashed password: " + hash);
    }
}
EOF

# Alternative: Use Maven to run a simple BCrypt generator
echo "Generating BCrypt hash for password..."
echo ""
echo "If the above didn't work, use this Maven command instead:"
echo "mvn spring-boot:run -Dspring-boot.run.arguments=\"--hash=$PASSWORD\""
echo ""
echo "Then update V4__insert_admin_user.sql with the generated hash"
