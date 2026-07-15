# CLAUDE.md - Gift Card Service

## 📋 Stack
- Spring Boot 3.4.2 + Java 21
- PostgreSQL (prod) / H2 (dev/test)
- JWT authentication (JJWT)
- JPA with Lombok
- Swagger/OpenAPI

## ⚙️ Commands
```bash
mvn clean install                    # Full build with tests
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"  # Dev mode
mvn test                             # Run unit tests (H2, fast, no Docker)
mvn test -P integration-tests        # Run all tests with real PostgreSQL 17 (requires Docker)
```

## 🧪 Testing Strategy

Two Maven profiles for different workflows:

**Unit Tests (default)** - `mvn test`
- 34 unit tests with H2 in-memory database
- Fast (~30s), no external dependencies
- Best for: Local TDD, quick feedback loops
- No Docker required

**Integration Tests** - `mvn test -P integration-tests`
- 51 tests (34 unit + 17 integration) with real PostgreSQL 17
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

## 📌 Conventions
- Use `@Valid` for DTO validation
- Async operations return CompletableFuture or HTTP 202 (Accepted)
- All endpoints require JWT (except /api/v1/auth/**)
- Profiles: dev (H2, DEBUG), prod (PostgreSQL, INFO), test (H2, random port)

## ⛔ DO NOT
- Modify files in `src/main/resources/db/migration/` directly
- Commit `.env` or JWT_SECRET_KEY
- Push to main without tests passing