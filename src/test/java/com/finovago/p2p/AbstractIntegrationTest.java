package com.finovago.p2p;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import com.finovago.p2p.config.PostgresTestcontainerInitializer;

/**
 * Base class for integration tests using real PostgreSQL container.
 *
 * REQUIRES: Docker installed and running
 *
 * Run with: mvn test -P integration-tests
 *
 * Benefits over unit tests:
 * - Tests against real PostgreSQL 17 (matches production PostgreSQL 18)
 * - Validates Flyway migrations
 * - Tests actual database constraints and behavior
 * - Detects SQL/transaction issues before production
 *
 * For developers without Docker, use unit tests only:
 *   mvn test  (default, runs unit tests with H2)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresTestcontainerInitializer.class)
public abstract class AbstractIntegrationTest {
}
