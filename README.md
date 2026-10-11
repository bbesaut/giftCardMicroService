# Gift Card Microservice

[![CI](https://github.com/bbesaut/giftCardMicroService/actions/workflows/ci.yml/badge.svg)](https://github.com/bbesaut/giftCardMicroService/actions/workflows/ci.yml)
[![Coverage report](https://img.shields.io/badge/coverage-report-blue)](https://bbesaut.github.io/giftCardMicroService/coverage/)
[![Schema](https://img.shields.io/badge/db-schema-blue)](https://bbesaut.github.io/giftCardMicroService/schema/)

Multi-tenant REST API for issuing, redeeming, holding and refunding gift cards. Each merchant gets its own isolated space, its own team of users and an API key for backend-to-backend integration.

Built as a production-style service: money-safe operations (idempotency, append-only ledger), layered security, observability and a real-PostgreSQL test suite.

## Highlights

- **Multi-tenancy**: every card belongs to one merchant; the tenant always comes from the JWT or API key, never from client input. Cross-tenant lookups return `404`, so existence is never leaked.
- **Money-safe operations**: `redeem`, `reserve`, `refund` and `credit` require an `Idempotency-Key`, so a retry after a network failure can never deduct twice. Holds follow a `reserve → capture / release` lifecycle.
- **Append-only ledger**: every balance change is a ledger entry (who, when, why, balance after), RANGE-partitioned by year in PostgreSQL. Balances are never edited directly.
- **Two authentication modes**: short-lived JWT access tokens with a rotating `HttpOnly` refresh cookie (with an origin guard against CSRF) for humans, and hashed API keys for merchants' backends.
- **Account lifecycle**: email verification, password reset, owner/employee roles, user and merchant deactivation with immediate refresh-token revocation.
- **Rate limiting**: per IP on public auth endpoints, per merchant (with an admin-set override) on business endpoints.
- **Observability**: correlation ID on every response and log line (MDC, propagated to async threads), `X-Response-Time` header, Loki logging in prod.
- **Schema as code**: Flyway migrations, a dedicated least-privilege DB role for the app, and an ER diagram regenerated on every push.

## Stack

- Java 21 / Spring Boot 4.1.0
- PostgreSQL (docker-compose in dev, Testcontainers in tests, Neon in prod)
- Flyway for migrations
- JWT (JJWT), Spring Security
- Swagger / OpenAPI
- Brevo for transactional email (SMTP in dev, HTTP API in prod)

## Getting started

Requirements: JDK 21, Docker, Maven (or use `./mvnw`), [mkcert](https://github.com/FiloSottile/mkcert) (`scoop bucket add extras; scoop install mkcert`).

```bash
docker-compose up -d postgres-dev
.\scripts\generate-dev-cert.ps1       # one-time, generates the dev HTTPS keystore (see CLAUDE.md - Dev HTTPS)
./mvnw spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=dev"
```

The API starts on `https://localhost:8080`, Swagger UI at `/swagger-ui.html`. To get demo data to play with, see [Dev data](#dev-data-seeding-and-reset) below.

## Tests

```bash
./mvnw test                       # unit tests, Mockito only, no DB, no Docker
./mvnw test -P integration-tests  # full suite against a real PostgreSQL via Testcontainers
```

- Unit tests are fast and need nothing; integration tests run the real Flyway migrations on PostgreSQL 17 and exercise the API end to end (real SMTP capture with MailHog for the email flows).
- The build fails if line or branch coverage of the business-logic package (`service`) drops below 80%.
- CI runs the integration profile on every PR and publishes the coverage report.

## Documentation

- Full API reference (endpoints, DTOs, error codes): [CLAUDE.md](CLAUDE.md), also browsable live in Swagger UI
- Production setup (admin bootstrap, secrets): [docs/PRODUCTION_SETUP.md](docs/PRODUCTION_SETUP.md)
- Database schema (auto-generated on push to `develop`/`main`): https://bbesaut.github.io/giftCardMicroService/schema/
- Code coverage report (auto-generated on push to `develop`/`main`): https://bbesaut.github.io/giftCardMicroService/coverage/

## Architecture notes

- Multi-tenant: every gift card belongs to a `Merchant`. Tenant scoping comes from the JWT (`merchantId` claim) or the API key, never from client input.
- Idempotency required on `redeem`/`reserve`/`refund`/`credit` via the `Idempotency-Key` header, scoped per merchant and per endpoint.
- Rate limiting: 10 req/min per IP on the public auth endpoints (`login`, `register`, password reset, email verification); 300 req/min per merchant on `lookup`/`redeem`/`reserve`/`refund`/`credit`.
- Correlation IDs (`X-Correlation-Id`) and response timing (`X-Response-Time`) on every response, including auth failures.
- CORS for browser front-ends: exact-origin allowlist via `app.cors.allowed-origins` (`CORS_ALLOWED_ORIGINS` in prod, required). `X-Correlation-Id`, `X-Response-Time` and `Retry-After` are exposed to browser JS.

See [CLAUDE.md](CLAUDE.md) for the detailed breakdown.

## Branching (Gitflow)

- `main` — production, always deployable. Tags cut releases.
- `develop` — integration branch, base for all feature work.
- `feature/*` — branched from `develop`, merged back via PR.
- `hotfix/*` — branched from `main` for urgent prod fixes, merged into both `main` and `develop`.

Never push directly to `main`. PRs into `develop` or `main` require tests passing.

## Dev data (seeding and reset)

A fresh dev database only contains the admin created by the migrations. To load a full demo dataset, or to wipe everything and get back to it at any time:

```powershell
.\scripts\reset-dev.ps1
```

It takes about 20 seconds and ends with the app exiting by itself. It never runs as part of a normal startup.

**What you get**

| Merchant | Cards | Particularity |
|---|---|---|
| Finovago Demo Merchant | 36 | owner + 3 employees (one deactivated), an API key |
| Librairie du Coin | 60 | big catalogue, good for pagination and sorting |
| Boulangerie Petit Pain | 8 | owner only |
| Vintage Vinyl Shop | 12 | deactivated merchant |
| MegaMart Online | 25 | custom rate limit (1000/min) |

Every merchant has cards in each state (active, high balance, expiring soon, deactivated, expired) and 60 days of history made through the real services: redemptions, holds (captured, released, expired, a few left pending), refunds and manual credits, by the merchant's users and by its API key. The data is the same on every reset, apart from dates, which are relative to today.

**Accounts**

| Email | Password | Role |
|---|---|---|
| admin@finovago.com | admin123 | ADMIN |
| client@finovago.com | client123 | MERCHANT (owner of the demo merchant) |
| every other seeded account, e.g. owner@librairie-du-coin.example.com | Passw0rd! | MERCHANT |

The demo merchant's API key is printed in the job's output (`API key for Finovago Demo Merchant (dev only)`). The full list of accounts and cards lives in [`SeedCatalog`](src/main/java/com/finovago/p2p/devseed/SeedCatalog.java).

**Good to know**

- The reset only exists under the `seed` profile (the script activates `dev,seed`); it can run while your dev instance is up, since the job listens on a random port. Restart the app afterwards, as anything cached in memory (e.g. API keys) predates the reset.
- It refuses to run unless both database URLs point at `localhost`.
- It connects with the schema-owner role (`spring.flyway.*` in `application-dev.properties`): the runtime role `p2p_app` can neither `TRUNCATE` nor update the append-only ledger, which the seeding needs to write a history. Nothing about that role changes.
- It wipes every table but keeps the schema and Flyway's history, so no migration is replayed.
