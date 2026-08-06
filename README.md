# Gift Card Microservice

REST API for issuing, looking up and redeeming gift cards, with multi-tenant support (one `Merchant` per account) and JWT-based auth.

## Stack

- Java 21 / Spring Boot 3.4.2
- PostgreSQL (docker-compose in dev, Testcontainers in tests, Neon in prod)
- Flyway for migrations
- JWT (JJWT), Spring Security
- Swagger / OpenAPI

## Getting started

Requirements: JDK 21, Docker, Maven (or use `./mvnw`).

```bash
docker-compose up -d postgres-dev
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"
```

The API starts on `http://localhost:8080`, Swagger UI at `/swagger-ui.html`.

Seeded accounts in dev (see `DataInitializer`):

| Email | Password | Role |
|---|---|---|
| admin@finovago.com | admin123 | ADMIN |
| client@finovago.com | client123 | MERCHANT |

## Tests

```bash
./mvnw test                       # unit tests, Mockito only, no DB
./mvnw test -P integration-tests  # full suite with Testcontainers (needs Docker)
```

CI runs the integration profile on every PR.

## Branching (Gitflow)

- `main` — production, always deployable. Tags cut releases.
- `develop` — integration branch, base for all feature work.
- `feature/*` — branched from `develop`, merged back via PR.
- `hotfix/*` — branched from `main` for urgent prod fixes, merged into both `main` and `develop`.

Never push directly to `main`. PRs into `develop` or `main` require tests passing.

## Documentation

- Full API reference (endpoints, DTOs, error codes): [CLAUDE.md](CLAUDE.md)
- Production setup (admin bootstrap, secrets): [docs/PRODUCTION_SETUP.md](docs/PRODUCTION_SETUP.md)
- Database schema (auto-generated on push to `develop`/`main`): https://bbesaut.github.io/giftCardMicroService/schema/

## Architecture notes

- Multi-tenant: every gift card belongs to a `Merchant`. Tenant scoping comes from the JWT (`merchantId` claim), never from client input.
- Idempotency required on `redeem`/`reserve` via `Idempotency-Key` header.
- Rate limiting: 10 req/min per IP on `login`/`lookup`/`redeem`.
- Correlation IDs (`X-Correlation-Id`) and response timing (`X-Response-Time`) on every response, including auth failures.

See [CLAUDE.md](CLAUDE.md) for the detailed breakdown.
