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
- 47 unit tests, pure Mockito (no database at all)
- Fast (~30s), no external dependencies
- Best for: Local TDD, quick feedback loops
- No Docker required

**Integration Tests** - `mvn test -P integration-tests`
- 109 tests (47 unit + 62 integration) with real PostgreSQL 17
- Validates Flyway migrations
- Matches production database (Neon PostgreSQL 18.4)
- Requires: Docker installed and running
- Best for: CI/CD pipelines, pre-deployment verification

## 🗺️ Database Schema Diagram
An up-to-date ER diagram is generated automatically by `.github/workflows/schema-diagram.yml` whenever a push to `develop` or `main` touches `src/main/resources/db/migration/**`. It spins up Postgres, applies Flyway migrations via the `flyway-maven-plugin` (see `pom.xml`), runs SchemaSpy against it, and publishes the result to GitHub Pages: https://bbesaut.github.io/giftCardMicroService/schema/

**One-time setup required**: enable GitHub Pages for this repo (Settings → Pages → source: `gh-pages` branch) — the workflow creates/updates that branch but Pages must be turned on manually once.

## 📊 Code Coverage
`mvn test -P integration-tests` generates a JaCoCo line/branch coverage report at `target/site/jacoco/index.html` (requires Docker). `jacoco:check` fails the build if `com.finovago.p2p.service` (the business logic) drops below 80% line/branch coverage — scoped to the `integration-tests` profile only, so the fast unit-only feedback loop isn't penalized for logic that's only exercised by integration tests.

Two ways to see it without running Maven yourself:
- **Every PR**: `.github/workflows/ci.yml` posts a per-package coverage table to the job summary and uploads the full HTML report as a downloadable artifact.
- **Always current for `develop`/`main`**: `.github/workflows/coverage-report.yml` runs on every push to those branches and publishes the HTML report to GitHub Pages: https://bbesaut.github.io/giftCardMicroService/coverage/ (same one-time Pages setup as the schema diagram above; `keep_files: true` so the two publishers don't clobber each other's `gh-pages` content).

## 🏗️ Architecture Summary
- **Multi-tenancy**: every gift card belongs to exactly one `Merchant`. `ADMIN` is the platform owner (manages merchants, sees all cards via `/list`); `MERCHANT` is a merchant account, scoped to its own cards only. Tenant scoping is derived server-side from the JWT (`merchantId` claim), never from client input.
- **JWT auth**: JJWT-based, stateless, roles (ADMIN/MERCHANT), JWT carries a `merchantId` claim (null for ADMIN)
- **Service layer**: GiftCardService with async redemption (CompletableFuture)
- **Observability**: Correlation IDs in MDC, Loki logging in prod
- **Async**: Custom TaskExecutor with MdcTaskDecorator for MDC propagation
- **Exception handling**: GlobalExceptionHandler with custom exceptions
- **Response timing**: ResponseTimeFilter adds `X-Response-Time` header to all responses (in milliseconds)
- **Rate limiting**: `RateLimitFilter` caps `login` at 10 requests/minute per client IP (the only identity available pre-auth — protects against credential stuffing). `lookup`/`redeem`/`reserve` are capped at 300 requests/minute **per merchant** (from the JWT), not per IP — these are B2B endpoints called from a merchant's own backend, so all of a merchant's end users would otherwise share one IP and throttle each other. A merchant can get a custom quota via `merchants.rate_limit_capacity` (nullable override; `NULL` falls back to the `app.rate-limit.merchant-capacity` default). In-memory buckets, per-instance only (see `app.rate-limit.*` properties). Disabled under the `test` profile.
- **Idempotency**: `POST /giftcards/redeem` and `POST /giftcards/reserve` require an `Idempotency-Key` header — any mutating endpoint without a natural uniqueness guard is a candidate (`create`/`register` are already covered by their own unique constraints; `capture`/`release` are idempotent by target state — a retry that already reached the requested terminal state (e.g. re-capturing an already-CAPTURED hold) replays the same 200 response; a retry hitting a *different* terminal state (e.g. capturing an already-RELEASED hold) is a genuine conflict and returns 409). `IdempotencyKeyService` is endpoint-agnostic: it claims the key in its own transaction (REQUIRES_NEW) before the business logic runs, so concurrent duplicates are caught by a DB unique constraint (`merchant_id`, `idempotency_key`); a completed claim replays its cached response (serialized as JSON, endpoint-specific DTO type), a failed one is discarded so retries can proceed cleanly. `IdempotencyKeyCleanupScheduler` sweeps expired entries (see `app.idempotency.*` properties).
- **Ledger partitioning**: `gift_card_ledger` is RANGE-partitioned by year on `created_at` (see `V21__partition_gift_card_ledger_by_date.sql`) — it's an append-only audit trail that grows forever, so partitioning keeps per-partition indexes/vacuum small and makes future retention (`DETACH PARTITION` + export + drop) a metadata-only operation instead of a slow `DELETE`. Yearly (not monthly) because the retention policy this supports is expressed in years and current queries don't filter by date range, so finer granularity would only add catalog/index overhead. Partitions are pre-created through 2029, plus a `gift_card_ledger_default` catch-all so an insert past that window degrades (lands in the catch-all) instead of failing.
  - **Automated from 2030 onward** (see `V24__automate_ledger_partition_maintenance.sql`): `pg_partman` creates new yearly partitions on a `pg_cron` schedule (`partman-maintenance-ledger`, daily at 03:00 UTC), so a yearly manual migration is no longer needed. Neon-only: `pg_cron` requires `cron.database_name` set to `neondb` via the Neon API (`PATCH /endpoints/{id}`, `pg_settings.cron.database_name`) followed by a compute restart — not doable via SQL, must be done once out-of-band per environment. `p2p_app` still has no DDL rights (V17); instead, a dedicated `partman_admin` role (created out-of-band, same reasoning as `p2p_app`) executes the cron job, but only through the `run_ledger_partition_maintenance()` `SECURITY DEFINER` wrapper function — attaching a partition requires table ownership, which Postgres has no lesser grant for, so `partman_admin` itself can do nothing beyond calling that one function.
  - **Not exercised by tests**: `pg_cron`/`pg_partman` aren't bundled in the plain `postgres:17` image used by Testcontainers/`docker-compose`, so every step in V24 is guarded to no-op where the extensions aren't installed — this automation was validated by hand against a Neon branch, not by `mvn test -P integration-tests`.
  - `LedgerPartitionMonitorScheduler` runs weekly and only reads: it's now a safety net for the automation above (catches the `pg_cron` job silently failing) rather than the primary signal — it warns once less than a year of partitions remain, and errors if the catch-all partition ever receives a row (see `app.ledger.partition-monitor-interval-ms`). Actual archival/detachment of old partitions isn't implemented yet — this only lays the groundwork.

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
**Description**: Create a new merchant. Creates the `Merchant` record (business name) together with **two** MERCHANT-role users in one transaction: a human **owner** account (the submitted email/password — logs in, and will be the only account that can manage the merchant's other users) and an automated **service account** (server-generated credentials, for the merchant's own backend integration). Requires authentication (ADMIN role) — merchant onboarding is admin-gated, not public self-signup.

**Request** (RegisterRequest):
```json
{
  "email": "user@example.com",
  "password": "securePassword123",
  "merchantName": "Acme Corp"
}
```

**Response** (RegisterResponse - HTTP 200):
```json
{
  "owner": {
    "accessToken": "eyJhbGc...",
    "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
  },
  "serviceAccountEmail": "user+service-42@example.com",
  "serviceAccountPassword": "kQ2f...-generated-once"
}
```
`serviceAccountPassword` is shown **only in this response** — there is no retrieval endpoint, so hand it to the merchant immediately or have them rotate it via a future credential-rotation flow (not implemented yet).

**Error Responses**:
- `400 Bad Request`: Invalid email format or blank/missing fields (including blank `merchantName`)
- `401 Unauthorized`: Missing or invalid JWT token
- `403 Forbidden`: Insufficient permissions (ADMIN role required)
- `409 Conflict`: Email already registered
- `500 Internal Server Error`: Server error

**Logging**:
- `INFO`: "Registration attempt for email: u***@example.com"
- `INFO`: "Merchant registered successfully: merchantId: 1, owner: user@example.com, serviceAccount: user+service-1@example.com"
- `WARN`: "Registration failed - email already exists: u***@example.com"

**Field Validation**:
- `email`: Required, must be valid email format, must be unique in database — becomes the owner's login
- `password`: Required, non-blank — the owner's password
- `merchantName`: Required, non-blank — becomes the new Merchant's business name

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
- `429 Too Many Requests`: Rate limit exceeded (max 10 attempts/minute per IP)
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

All gift card endpoints below are scoped to the calling MERCHANT's own tenant — `merchantId` is derived from the JWT, never accepted from the client. A gift card code only needs to be unique **within a merchant** (`UNIQUE(merchant_id, card_code)`); two different merchants may use the same code without collision. Looking up or redeeming another merchant's card returns `404 Not Found` (not `403`), so tenant existence is never leaked.

### GET /api/v1/giftcards/lookup/{code}
**Description**: Retrieve detailed information about a specific gift card by its code, scoped to the caller's merchant. Returns the card's current balance, active status, and expiration date. Requires authentication (MERCHANT role).

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
- `404 Not Found`: Gift card with specified code does not exist for the caller's merchant
- `429 Too Many Requests`: Rate limit exceeded (max 10 attempts/minute per IP)
- `500 Internal Server Error`: Database or unexpected server error

### GET /api/v1/giftcards/{code}/ledger
**Description**: Retrieve the full append-only history of balance-affecting operations (creation, redemptions, holds) for a specific gift card, oldest first, scoped to the caller's merchant. Useful for customer support ("why did my balance change") without querying the database directly. Requires authentication (MERCHANT role).

**Path Parameters**:
- `code` (String): The gift card code

**Response** (List of LedgerEntryResponse - HTTP 200):
```json
[
  {
    "entryType": "CREATION",
    "amount": 100.00,
    "balanceAfter": 100.00,
    "holdId": null,
    "createdAt": "2026-07-23T15:30:00",
    "actor": "merchant@example.com"
  },
  {
    "entryType": "REDEMPTION",
    "amount": 30.00,
    "balanceAfter": 70.00,
    "holdId": null,
    "createdAt": "2026-07-24T09:12:00",
    "actor": "SYSTEM / integration@example.com"
  }
]
```

**Error Responses**:
- `401 Unauthorized`: Missing or invalid JWT token
- `404 Not Found`: Gift card with specified code does not exist for the caller's merchant
- `429 Too Many Requests`: Rate limit exceeded (max 10 attempts/minute per IP)
- `500 Internal Server Error`: Database or unexpected server error

### POST /api/v1/giftcards/redeem
**Description**: Redeem a specified amount from a gift card using its code, scoped to the caller's merchant. The request is processed asynchronously. Requires authentication (MERCHANT role).

**Idempotency**: Requires an `Idempotency-Key` header (client-generated, e.g. a UUID, one per redemption attempt — not per HTTP call). If the connection drops before the response arrives, retry the exact same request with the **same** key: a request that already completed replays its cached result instead of deducting the balance again. Reusing a key with a different `giftCardCode`/`amount` returns `409 Conflict`, as does retrying while the original request is still in flight. Keys are scoped per merchant and expire after `app.idempotency.ttl-hours` (default 24h; swept by `IdempotencyKeyCleanupScheduler`).

**Request** (RedemptionRequest):
```json
{
  "giftCardCode": "GC-12345",
  "amount": 50.0
}
```
Header: `Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000`

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
- `400 Bad Request`: Invalid request body (missing or invalid fields), or missing `Idempotency-Key` header
- `401 Unauthorized`: Missing or invalid JWT token
- `404 Not Found`: Gift card with specified code does not exist for the caller's merchant
- `409 Conflict`: `Idempotency-Key` reused with a different request payload, or a request with this key is still being processed
- `422 Unprocessable Entity`: Card is inactive or has expired (an amount exceeding the balance is NOT an error — the response returns `SUCCESS` with a non-zero `remainingToPay`)
- `429 Too Many Requests`: Rate limit exceeded (max 10 attempts/minute per IP)
- `500 Internal Server Error`: Server error

### POST /api/v1/giftcards/refund
**Description**: Reverses a specific prior `REDEMPTION` entry, scoped to the caller's merchant, exclusively through a new `REFUND` ledger entry (never a direct balance edit). Identified by `redemptionLedgerEntryId` (from `GET /{code}/ledger`). Capped at what's left to refund on that entry (its original amount minus any prior refunds against it). Callable by any authenticated merchant account — human or the merchant's own service/integration account (e.g. their checkout backend auto-triggering a refund when it registers a customer return) — since a refund is structurally bounded by a real prior transaction, unlike `/credit` below.

Deliberately allowed on an inactive/expired card — refunding exists specifically to fix a problem, so blocking on that same problem's status would defeat the purpose. Requires authentication (MERCHANT role).

**Idempotency**: Requires an `Idempotency-Key` header, same semantics as `redeem`/`reserve` — a retry with the same key replays the cached result instead of refunding twice. The hash covers `giftCardCode`/`amount`/`redemptionLedgerEntryId` (not `reason`, so rewording a justification on retry doesn't trigger a spurious `409`).

**Request** (RefundRequest):
```json
{
  "giftCardCode": "GC-12345",
  "amount": 30.0,
  "redemptionLedgerEntryId": 987,
  "reason": null
}
```
Header: `Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000`

**Response** (RefundResponse - HTTP 200 OK):
```json
{
  "status": "SUCCESS",
  "refundedAmount": 30.0,
  "newBalance": 130.0
}
```

**Error Responses**:
- `400 Bad Request`: Invalid request body (missing or invalid fields), or missing `Idempotency-Key` header
- `401 Unauthorized`: Missing or invalid JWT token
- `404 Not Found`: Gift card does not exist for the caller's merchant, or `redemptionLedgerEntryId` does not reference an entry on this card
- `409 Conflict`: `Idempotency-Key` reused with a different request payload, or a request with this key is still being processed
- `422 Unprocessable Entity`: `redemptionLedgerEntryId` does not reference a `REDEMPTION` entry, or the refund amount exceeds what's left to refund on it
- `429 Too Many Requests`: Rate limit exceeded (max 300 requests/minute per merchant)
- `500 Internal Server Error`: Server error

### POST /api/v1/giftcards/credit
**Description**: Adds a free-form manual credit onto a gift card, scoped to the caller's merchant, not tied to any prior redemption, exclusively through a new `ADJUSTMENT` ledger entry (never a direct balance edit). Requires a `reason` (audit trail for support/finance) — unlike `/refund`, there's no structural cap on this amount, so **only a human merchant account may call it**: rejected with `403` if the caller is the merchant's own service/integration account, since a free-form credit must be asserted by a person, not an automated script.

Deliberately allowed on an inactive/expired card — crediting exists specifically to fix a problem, so blocking on that same problem's status would defeat the purpose. Requires authentication (MERCHANT role).

**Idempotency**: Requires an `Idempotency-Key` header, same semantics as `redeem`/`reserve` — a retry with the same key replays the cached result instead of crediting twice. The hash covers `giftCardCode`/`amount` (not `reason`).

**Request** (CreditRequest):
```json
{
  "giftCardCode": "GC-12345",
  "amount": 20.0,
  "reason": "Goodwill gesture - support ticket #123"
}
```
Header: `Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000`

**Response** (CreditResponse - HTTP 200 OK):
```json
{
  "status": "SUCCESS",
  "creditedAmount": 20.0,
  "newBalance": 70.0
}
```

**Error Responses**:
- `400 Bad Request`: Invalid request body (missing or invalid fields, or missing `reason`), or missing `Idempotency-Key` header
- `401 Unauthorized`: Missing or invalid JWT token
- `403 Forbidden`: Caller is a service/integration account, not a human merchant account
- `404 Not Found`: Gift card does not exist for the caller's merchant
- `409 Conflict`: `Idempotency-Key` reused with a different request payload, or a request with this key is still being processed
- `429 Too Many Requests`: Rate limit exceeded (max 300 requests/minute per merchant)
- `500 Internal Server Error`: Server error

### POST /api/v1/giftcards/create
**Description**: Create a new gift card with the specified code and initial balance, under the caller's own merchant. Requires authentication (MERCHANT role). Gift card code must be unique within that merchant.

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
- `403 Forbidden`: Insufficient permissions (MERCHANT role required — ADMIN cannot create gift cards, it has no merchant of its own)
- `409 Conflict`: Gift card code already exists for this merchant
- `500 Internal Server Error`: Database or unexpected server error

### GET /api/v1/giftcards/list
**Description**: Retrieve a list of all available gift cards with their details. Requires authentication (ADMIN role) — this is the only gift card endpoint ADMIN can access, and it returns cards across **all** merchants (not scoped).

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
Used for merchant registration (POST /api/v1/auth/register) — describes the new merchant's **owner** account
- `email` (String): Owner's email, must be unique, validated with @Email
- `password` (String): Owner's password, non-blank
- `merchantName` (String): Business name for the new Merchant created alongside this user, non-blank

### RegisterResponse
Response for merchant registration
- `owner` (AuthResponse): Tokens for the newly created owner account
- `serviceAccountEmail` (String): Email of the auto-generated service account
- `serviceAccountPassword` (String): Plaintext password of the service account — shown only in this response, never retrievable again

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

### LedgerEntryResponse
Response for a single gift card ledger entry (GET /api/v1/giftcards/{code}/ledger)
- `entryType` (String): Kind of operation (CREATION, REDEMPTION, HOLD_PLACED, HOLD_CAPTURED, HOLD_RELEASED, REFUND, ADJUSTMENT)
- `amount` (BigDecimal): Amount involved in this operation
- `balanceAfter` (BigDecimal): Gift card balance immediately after this operation
- `holdId` (Long, nullable): Identifier of the related hold, if any
- `createdAt` (LocalDateTime): Timestamp at which this entry was recorded
- `actor` (String, nullable): Who triggered this operation — the user's email (`"SYSTEM / email"` for a service account), `"Deleted user"` if the account no longer exists, or `"Unknown"` for entries recorded before this field existed
- `reason` (String, nullable): Operator-supplied justification — set for ADJUSTMENT (mandatory when created) and optionally for REFUND, null otherwise

### RefundRequest
Used for refunding gift cards against a prior redemption (POST /api/v1/giftcards/refund)
- `giftCardCode` (String): Gift card code to refund
- `amount` (BigDecimal): Amount to refund (must be > 0)
- `redemptionLedgerEntryId` (Long): Id of the REDEMPTION ledger entry being refunded
- `reason` (String, nullable): Optional justification, max 500 chars — the link to the original redemption is usually enough on its own

### RefundResponse
Response for a successful refund operation
- `status` (String): Refund status (e.g., "SUCCESS")
- `refundedAmount` (BigDecimal): Amount refunded to the card
- `newBalance` (BigDecimal): Balance after this refund

### CreditRequest
Used for free-form manual credits, not tied to any redemption (POST /api/v1/giftcards/credit) — only callable by a human merchant account, not a service account
- `giftCardCode` (String): Gift card code to credit
- `amount` (BigDecimal): Amount to credit (must be > 0)
- `reason` (String): Mandatory justification, max 500 chars

### CreditResponse
Response for a successful credit operation
- `status` (String): Credit status (e.g., "SUCCESS")
- `creditedAmount` (BigDecimal): Amount credited to the card
- `newBalance` (BigDecimal): Balance after this credit

## ⚠️ Error Responses

All error responses follow this standard structure:
```json
{
  "error": "Error Type",
  "message": "Detailed description of what went wrong"
}
```

The correlation ID is **not** duplicated in the body — it is already returned on every response (success or error) via the `X-Correlation-Id` header, which is what you use to trace a request in Grafana/Loki.

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
  "message": "Email already registered"
}
```

## 📌 Conventions
- Use `@Valid` for DTO validation
- Async operations return CompletableFuture or HTTP 202 (Accepted)
- All endpoints require JWT (except /api/v1/auth/login, /api/v1/auth/refresh, /api/v1/auth/logout). `/api/v1/auth/register` requires JWT + ADMIN role.
- Profiles: dev (PostgreSQL via docker-compose, DEBUG), prod (PostgreSQL, INFO), test (PostgreSQL via Testcontainers, random port)
- **Response timing**: All responses include `X-Response-Time` header (milliseconds). This is HTTP metadata only—never add timing to DTOs.
- **Correlation id & response timing filters run before Spring Security** (`@Order(Ordered.HIGHEST_PRECEDENCE)` on `MdcFilter`/`ResponseTimeFilter`) so that even 401/403 responses rejected by Security itself carry `X-Correlation-Id`/`X-Response-Time` — don't remove that ordering.
- **Tenant scoping**: never trust a client-supplied `merchantId` for gift card operations — it always comes from the authenticated principal's JWT (`CurrentUserContext`).

## 👥 Admin User Setup

**Production**: Admin user is automatically created via V4 Flyway migration:
- Email: `admin@finovago.com`
- Password: `admin123` (⚠️ **Change immediately after first login**)
- See [docs/PRODUCTION_SETUP.md](docs/PRODUCTION_SETUP.md) for customization

**Development**: Admin + Merchant users created by DataInitializer on startup:
- `admin@finovago.com` / `admin123` (role: ADMIN, no merchant)
- `client@finovago.com` / `client123` (role: MERCHANT, attached to the seeded "Finovago Demo Merchant")

## 📝 Git Commits
- Write commit messages like a human, not a report: short sentences, no filler.
- Use clear, terse bullet points (dashes) for multi-line messages.
- No long prose, no restating the diff line by line.

## ⛔ DO NOT
- Modify files in `src/main/resources/db/migration/` directly
- Commit `.env` or JWT_SECRET_KEY
- Push to main without tests passing
- Use default admin password in production (change it!)