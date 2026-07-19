# CLAUDE.md - Gift Card Service

## 📋 Stack
- Spring Boot 3.4.2 + Java 21
- PostgreSQL everywhere (dev via docker-compose, test via Testcontainers, prod on Neon)
- JWT authentication (JJWT)
- JPA with Lombok
- Swagger/OpenAPI

## ⚙️ Commands
```bash
docker-compose up -d postgres-dev    # Start local PostgreSQL for dev mode
mvn clean install                    # Full build with tests
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"  # Dev mode
mvn test                             # Run unit tests (Mockito-based, no DB, no Docker)
mvn test -P integration-tests        # Run all tests with real PostgreSQL 17 via Testcontainers (requires Docker)
```

## 🧪 Testing Strategy

Two Maven profiles for different workflows:

**Unit Tests (default)** - `mvn test`
- 39 unit tests, pure Mockito (no database at all)
- Fast (~30s), no external dependencies
- Best for: Local TDD, quick feedback loops
- No Docker required

**Integration Tests** - `mvn test -P integration-tests`
- 56 tests (39 unit + 17 integration) with real PostgreSQL 17
- Validates Flyway migrations
- Matches production database (Neon PostgreSQL 18.4)
- Requires: Docker installed and running
- Best for: CI/CD pipelines, pre-deployment verification

## 🏗️ Architecture Summary
- **JWT auth**: JJWT-based, stateless, roles (ADMIN/CLIENT)
- **Service layer**: GiftCardService with async redemption (CompletableFuture)
- **Observability**: Correlation IDs in MDC, Loki logging in prod
- **Async**: Custom TaskExecutor with MdcTaskDecorator for MDC propagation
- **Exception handling**: GlobalExceptionHandler with custom exceptions
- **Response timing**: ResponseTimeFilter adds `X-Response-Time` header to all responses (in milliseconds)

### Response Timing Header (X-Response-Time)
Every response includes an `X-Response-Time` header with the request processing time in milliseconds. This is a best practice for:
- **Monitoring**: Track endpoint performance and identify bottlenecks
- **Observability**: Integrate with APM tools and dashboards
- **Separation of concerns**: Timing is HTTP metadata (header), not business data (body)

**Implementation**: Automatically added by `ResponseTimeFilter` for all endpoints.

**Example**:
```
HTTP/1.1 200 OK
X-Response-Time: 125
Content-Type: application/json

{ "accessToken": "eyJ...", "refreshToken": "..." }
```

## 🔐 Authentication Endpoints

### POST /api/v1/auth/register
**Description**: Create a new user account. New users are automatically assigned CLIENT role. Requires authentication (ADMIN role).

**Request** (RegisterRequest):
```json
{
  "email": "user@example.com",
  "password": "securePassword123"
}
```

**Response** (AuthResponse - HTTP 200):
```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Error Responses**:
- `400 Bad Request`: Invalid email format or blank fields
- `401 Unauthorized`: Missing or invalid JWT token
- `403 Forbidden`: Insufficient permissions (ADMIN role required)
- `409 Conflict`: Email already registered
- `500 Internal Server Error`: Server error

**Logging**:
- `INFO`: "Registration attempt for email: u***@example.com"
- `INFO`: "User registered successfully: user@example.com (role: CLIENT)"
- `WARN`: "Registration failed - email already exists: u***@example.com"

**Field Validation**:
- `email`: Required, must be valid email format, must be unique in database
- `password`: Required, non-blank

### POST /api/v1/auth/login
**Description**: Authenticate user with credentials and obtain JWT tokens.

**Request** (LoginRequest):
```json
{
  "email": "user@example.com",
  "password": "securePassword123"
}
```

**Response** (AuthResponse - HTTP 200):
```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Error Responses**:
- `400 Bad Request`: Invalid email format or blank fields
- `401 Unauthorized`: Invalid email or password
- `500 Internal Server Error`: Server error

### POST /api/v1/auth/refresh
**Description**: Rotate refresh token and issue new access token. Old refresh token is automatically revoked.

**Request** (RefreshTokenRequest):
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response** (AuthResponse - HTTP 200):
```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6"
}
```

**Error Responses**:
- `400 Bad Request`: Missing or blank refresh token
- `401 Unauthorized`: Token expired, revoked, or invalid
- `500 Internal Server Error`: Server error

### POST /api/v1/auth/logout
**Description**: Revoke refresh token and invalidate future refresh attempts.

**Request** (RefreshTokenRequest):
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response**: HTTP 204 No Content

**Error Responses**:
- `400 Bad Request`: Missing or blank refresh token
- `401 Unauthorized`: Token not found or already revoked
- `500 Internal Server Error`: Server error

## 🎁 Gift Card Endpoints

### GET /api/v1/giftcards/lookup/{code}
**Description**: Retrieve detailed information about a specific gift card by its code. Returns the card's current balance, active status, and expiration date. Requires authentication (CLIENT role or higher).

**Path Parameters**:
- `code` (String): The gift card code to look up

**Response** (GiftCardResponse - HTTP 200):
```json
{
  "giftCardCode": "GC-12345",
  "balance": 150.0,
  "active": true,
  "expirationDate": "2025-12-31"
}
```

**Error Responses**:
- `401 Unauthorized`: Missing or invalid JWT token
- `404 Not Found`: Gift card with specified code does not exist
- `500 Internal Server Error`: Database or unexpected server error

### POST /api/v1/giftcards/redeem
**Description**: Redeem a specified amount from a gift card using its code. The request is processed asynchronously. Requires authentication (CLIENT role or higher).

**Request** (RedemptionRequest):
```json
{
  "giftCardCode": "GC-12345",
  "amount": 50.0
}
```

**Response** (RedemptionResponse - HTTP 202 Accepted):
```json
{
  "status": "SUCCESS",
  "deductedAmount": 50.0,
  "remainingBalance": 100.0,
  "remainingToPay": 0.0
}
```

**Error Responses**:
- `400 Bad Request`: Invalid request body (missing or invalid fields)
- `401 Unauthorized`: Missing or invalid JWT token
- `404 Not Found`: Gift card with specified code does not exist
- `422 Unprocessable Entity`: Card is inactive or has expired (an amount exceeding the balance is NOT an error — the response returns `SUCCESS` with a non-zero `remainingToPay`)
- `500 Internal Server Error`: Server error

### POST /api/v1/giftcards/create
**Description**: Create a new gift card with the specified code and initial balance. Requires authentication (ADMIN role). Gift card code must be unique.

**Request** (GiftCardCreateRequest):
```json
{
  "giftCardCode": "GC-12345",
  "balance": 1000.0,
  "active": true,
  "expirationDate": "2025-12-31"
}
```

**Response** (GiftCardResponse - HTTP 201 Created):
```json
{
  "giftCardCode": "GC-12345",
  "balance": 1000.0,
  "active": true,
  "expirationDate": "2025-12-31"
}
```

**Error Responses**:
- `400 Bad Request`: Invalid request body (missing or invalid fields)
- `401 Unauthorized`: Missing or invalid JWT token
- `403 Forbidden`: Insufficient permissions (ADMIN role required)
- `409 Conflict`: Gift card code already exists
- `500 Internal Server Error`: Database or unexpected server error

### GET /api/v1/giftcards/list
**Description**: Retrieve a list of all available gift cards with their details. Requires authentication (ADMIN role).

**Response** (List of GiftCardResponse - HTTP 200):
```json
[
  {
    "giftCardCode": "GC-12345",
    "balance": 150.0,
    "active": true,
    "expirationDate": "2025-12-31"
  },
  {
    "giftCardCode": "GC-67890",
    "balance": 500.0,
    "active": true,
    "expirationDate": "2025-11-30"
  }
]
```

**Error Responses**:
- `401 Unauthorized`: Missing or invalid JWT token
- `403 Forbidden`: User role not permitted to list gift cards
- `500 Internal Server Error`: Database or unexpected server error

## 📦 DTOs

### RegisterRequest
Used for user registration (POST /api/v1/auth/register)
- `email` (String): User's email, must be unique, validated with @Email
- `password` (String): User's password, non-blank

### LoginRequest
Used for authentication (POST /api/v1/auth/login)
- `email` (String): User's registered email
- `password` (String): User's password

### AuthResponse
Response containing JWT tokens
- `accessToken` (String): JWT access token (bearer token for API requests)
- `refreshToken` (String): UUID refresh token (used to obtain new access tokens)

### RefreshTokenRequest
Used for token refresh and logout operations
- `refreshToken` (String): The refresh token to process

### GiftCardResponse
Response containing gift card details
- `giftCardCode` (String): Unique gift card code
- `balance` (double): Current balance
- `active` (boolean): Indicates if the gift card is active
- `expirationDate` (LocalDate): Expiration date

### GiftCardCreateRequest
Used for creating new gift cards (POST /api/v1/giftcards/create)
- `giftCardCode` (String): Unique gift card code
- `balance` (double): Initial balance
- `active` (boolean): Is the card active at creation
- `expirationDate` (LocalDate): Expiration date (optional, defaults to 2 years from now)

### RedemptionRequest
Used for redeeming gift cards (POST /api/v1/giftcards/redeem)
- `giftCardCode` (String): Gift card code to redeem
- `amount` (double): Amount to redeem (must be > 0)

### RedemptionResponse
Response for redemption operations
- `status` (String): Redemption status (e.g., "SUCCESS")
- `deductedAmount` (double): Amount successfully deducted
- `remainingBalance` (double): Balance after deduction
- `remainingToPay` (double): Amount still owed if balance was insufficient

## ⚠️ Error Responses

All error responses follow this standard structure:
```json
{
  "error": "Error Type",
  "message": "Detailed description of what went wrong",
  "code": "correlation-id-for-tracking"
}
```

The `code` field contains the correlation ID from the request context, useful for tracing requests in logs and monitoring systems.

### Common HTTP Status Codes
- **400 Bad Request**: Invalid request body or validation failure
- **401 Unauthorized**: Missing or invalid JWT token
- **403 Forbidden**: Insufficient permissions (role-based access denied)
- **404 Not Found**: Resource doesn't exist
- **409 Conflict**: Resource already exists (e.g., duplicate email or gift card code)
- **422 Unprocessable Entity**: Business logic error (e.g., expired card, insufficient balance)
- **500 Internal Server Error**: Server-side error

### UserAlreadyExistsException
**HTTP Status**: 409 Conflict
**When**: Attempt to register with email that already exists in database
**Response Body**:
```json
{
  "error": "Conflict",
  "message": "Email already registered",
  "code": "correlation-id"
}
```

## 📌 Conventions
- Use `@Valid` for DTO validation
- Async operations return CompletableFuture or HTTP 202 (Accepted)
- All endpoints require JWT (except /api/v1/auth/login, /api/v1/auth/refresh, /api/v1/auth/logout). `/api/v1/auth/register` requires JWT + ADMIN role.
- Profiles: dev (PostgreSQL via docker-compose, DEBUG), prod (PostgreSQL, INFO), test (PostgreSQL via Testcontainers, random port)
- **Response timing**: All responses include `X-Response-Time` header (milliseconds). This is HTTP metadata only—never add timing to DTOs.

## 👥 Admin User Setup

**Production**: Admin user is automatically created via V4 Flyway migration:
- Email: `admin@finovago.com`
- Password: `admin123` (⚠️ **Change immediately after first login**)
- See [docs/PRODUCTION_SETUP.md](docs/PRODUCTION_SETUP.md) for customization

**Development**: Admin + Client users created by DataInitializer on startup:
- `admin@finovago.com` / `admin123`
- `client@finovago.com` / `client123`

## ⛔ DO NOT
- Modify files in `src/main/resources/db/migration/` directly
- Commit `.env` or JWT_SECRET_KEY
- Push to main without tests passing
- Use default admin password in production (change it!)