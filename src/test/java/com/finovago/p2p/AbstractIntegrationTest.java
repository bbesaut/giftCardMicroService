package com.finovago.p2p;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.servlet.http.Cookie;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MvcResult;

import com.finovago.p2p.config.MailHogTestcontainerInitializer;
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
@ContextConfiguration(initializers = {PostgresTestcontainerInitializer.class, MailHogTestcontainerInitializer.class})
public abstract class AbstractIntegrationTest {

    /** Extracts the raw refresh token from the refresh_token Set-Cookie header of a response. */
    protected String refreshTokenFromCookie(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        return setCookie.substring("refresh_token=".length(), setCookie.indexOf(';'));
    }

    protected Cookie refreshCookie(String refreshToken) {
        return new Cookie("refresh_token", refreshToken);
    }
}
