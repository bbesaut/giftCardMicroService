# Gift Card Microservice

REST API for issuing, looking up and redeeming gift cards, with multi-tenant support (one `Merchant` per account) and JWT-based auth.

## Stack

- Java 21 / Spring Boot 3.4.2
- PostgreSQL (docker-compose in dev, Testcontainers in tests, Neon in prod)
- Flyway for migrations
- JWT (JJWT), Spring Security
- Swagger / OpenAPI

## Getting started

Requirements: JDK 21, Docker, Maven (or use `./mvnw`), [mkcert](https://github.com/FiloSottile/mkcert) (`scoop bucket add extras; scoop install mkcert`).

```bash
docker-compose up -d postgres-dev
.\scripts\generate-dev-cert.ps1       # one-time, generates the dev HTTPS keystore (see CLAUDE.md - Dev HTTPS)
./mvnw spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=dev"
```

The API starts on `https://localhost:8080`, Swagger UI at `/swagger-ui.html`.

### Dev data (seeding and reset)

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
- Code coverage report (auto-generated on push to `develop`/`main`): https://bbesaut.github.io/giftCardMicroService/coverage/

## Architecture notes

- Multi-tenant: every gift card belongs to a `Merchant`. Tenant scoping comes from the JWT (`merchantId` claim), never from client input.
- Idempotency required on `redeem`/`reserve` via `Idempotency-Key` header.
- Rate limiting: 10 req/min per IP on `login`/`lookup`/`redeem`.
- Correlation IDs (`X-Correlation-Id`) and response timing (`X-Response-Time`) on every response, including auth failures.
- CORS for browser front-ends: exact-origin allowlist via `app.cors.allowed-origins` (`CORS_ALLOWED_ORIGINS` in prod, required). `X-Correlation-Id`, `X-Response-Time` and `Retry-After` are exposed to browser JS.

See [CLAUDE.md](CLAUDE.md) for the detailed breakdown.
