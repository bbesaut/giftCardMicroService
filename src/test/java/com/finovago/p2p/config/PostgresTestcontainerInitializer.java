package com.finovago.p2p.config;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration test initializer for PostgreSQL container.
 *
 * PRODUCTION READY: Requires Docker for integration tests.
 * This ensures tests run against the exact same database as production (Neon PostgreSQL 18).
 *
 * For developers without Docker:
 * - Use unit tests only
 * - CI/CD pipelines always have Docker available
 *
 * PostgreSQL version must match production (see application-test.properties)
 */
public class PostgresTestcontainerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final PostgreSQLContainer<?> postgres;

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

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        TestPropertyValues.of(
                "spring.datasource.url=" + postgres.getJdbcUrl(),
                "spring.datasource.username=" + postgres.getUsername(),
                "spring.datasource.password=" + postgres.getPassword(),
                "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
                "spring.datasource.driver-class-name=org.postgresql.Driver"
        ).applyTo(applicationContext.getEnvironment());
    }
}
