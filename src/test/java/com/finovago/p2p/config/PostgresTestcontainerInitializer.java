package com.finovago.p2p.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration test initializer using PostgreSQL TestContainer.
 *
 * Part of the unified testing strategy:
 * ✅ Unit tests: Mockito mocks (fast, no Docker)
 * ✅ Integration tests: Real PostgreSQL 17 via TestContainers (Docker required)
 *
 * Runs ONLY with: mvn test -P integration-tests
 *
 * PRODUCTION PARITY:
 * - Matches Neon PostgreSQL 18.4 in production
 * - Validates Flyway migrations exactly as they run in prod
 * - Catches SQL dialect issues before deployment
 *
 * DOCKER REQUIREMENT:
 * - CI/CD pipelines always have Docker available
 * - Local dev: use `mvn test` for unit tests (no Docker needed)
 * - Or: install Docker Desktop for full integration test suite
 */
public class PostgresTestcontainerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final PostgreSQLContainer<?> postgres;

    // Least-privilege runtime role, mirroring p2p_app in dev/prod (see V17__restrict_app_role_privileges.sql).
    // postgres.getUsername()/getPassword() (p2p_user) stays the migration/owner role, used only for Flyway.
    private static final String APP_ROLE = "p2p_app";
    private static final String APP_ROLE_PASSWORD = "p2p_app_password";

    // Match the version used on Neon in production
    // Production: PostgreSQL 18.4 (verify in: Neon console > Connection details)
    // For Docker images, use closest stable version available
    private static final String POSTGRES_VERSION = System.getenv()
            .getOrDefault("TEST_POSTGRES_VERSION", "17");

    static {
        try {
            DockerImageName imageName = DockerImageName.parse("postgres:" + POSTGRES_VERSION);
            postgres = new PostgreSQLContainer<>(imageName)
                    .withDatabaseName("p2p_test")
                    .withUsername("p2p_user")
                    .withPassword("p2p_password");

            postgres.start();
            createAppRole();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    postgres.stop();
                } catch (Exception e) {
                    System.err.println("Failed to stop PostgreSQL container: " + e.getMessage());
                }
            }));

            System.out.println("✅ PostgreSQL " + POSTGRES_VERSION + " TestContainer started");
            System.out.println("   JDBC URL: " + postgres.getJdbcUrl());

        } catch (Exception e) {
            System.err.println("❌ CRITICAL: Failed to start PostgreSQL TestContainer");
            System.err.println("   Reason: " + e.getMessage());
            System.err.println("   ");
            System.err.println("   SOLUTION: Integration tests require Docker");
            System.err.println("   ");
            System.err.println("   Option 1: Install Docker Desktop");
            System.err.println("     - https://www.docker.com/products/docker-desktop");
            System.err.println("   ");
            System.err.println("   Option 2: Run only unit tests");
            System.err.println("     - mvn test -Dtest=**/*UnitTest");
            System.err.println("   ");
            System.err.println("   Option 3: Override with TEST_POSTGRES_VERSION env var");
            System.err.println("     - export TEST_POSTGRES_VERSION=16-alpine");
            System.err.println("   ");
            throw new RuntimeException("Docker required for integration tests", e);
        }
    }

    // Creates the restricted role before Flyway runs, so V17__restrict_app_role_privileges.sql
    // (which assumes the role already exists - see that migration for why) has something to grant to.
    private static void createAppRole() throws Exception {
        executeAsMigrator("CREATE ROLE " + APP_ROLE + " LOGIN PASSWORD '" + APP_ROLE_PASSWORD + "'");
    }

    // Test-only escape hatch for state reset between test methods (e.g. TRUNCATE gift_card_ledger).
    // p2p_app deliberately can't do this at runtime (see V17) - only the migration/owner role can,
    // same as a DBA would reset a sandbox DB, never the application itself.
    public static void executeAsMigrator(String sql) {
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute as migrator role: " + sql, e);
        }
    }

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        TestPropertyValues.of(
                // Migration role: owns the schema, runs Flyway.
                "spring.flyway.url=" + postgres.getJdbcUrl(),
                "spring.flyway.user=" + postgres.getUsername(),
                "spring.flyway.password=" + postgres.getPassword(),

                // App role: what the running application actually connects as at runtime.
                "spring.datasource.url=" + postgres.getJdbcUrl(),
                "spring.datasource.username=" + APP_ROLE,
                "spring.datasource.password=" + APP_ROLE_PASSWORD,
                "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
                "spring.datasource.driver-class-name=org.postgresql.Driver"
        ).applyTo(applicationContext.getEnvironment());
    }
}
