package com.finovago.p2p.config;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Unit test initializer using H2 in-memory database.
 *
 * Fast, no external dependencies, no Docker required.
 * Used by: mvn test (default unit-tests profile)
 *
 * ⚠️ H2 ≠ PostgreSQL - some edge cases may differ.
 * For production parity, use integration-tests profile with Docker.
 */
public class H2TestcontainerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        System.out.println("✅ H2 Test Configuration");
        System.out.println("   - Fast unit tests, no Docker required");
        System.out.println("   - For integration tests with real PostgreSQL, run:");
        System.out.println("     mvn test -P integration-tests");

        TestPropertyValues.of(
                "spring.datasource.url=jdbc:h2:mem:testdb",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
        ).applyTo(applicationContext.getEnvironment());
    }
}
