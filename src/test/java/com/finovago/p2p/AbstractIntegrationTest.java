package com.finovago.p2p;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import com.finovago.p2p.config.PostgresTestcontainerInitializer;

/**
 * Base class for integration tests using real PostgreSQL container via TestContainers.
 *
 * REQUIRES: Docker installed and running
 *
 * Run with: mvn test -P integration-tests
 *
 * Benefits:
 * - Tests against real PostgreSQL 17 (matches production PostgreSQL 18.4)
 * - Validates Flyway migrations and constraints
 * - Tests actual database behavior and edge cases
 * - Detects SQL/transaction issues before production
 *
 * All tests now use TestContainers:
 * - Unit tests: Mockito mocks (no Spring Boot, fastest)
 * - Integration tests: Real PostgreSQL via Docker
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresTestcontainerInitializer.class)
public abstract class AbstractIntegrationTest {
}
