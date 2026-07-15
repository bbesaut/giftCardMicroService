package com.finovago.p2p;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import com.finovago.p2p.config.H2TestcontainerInitializer;

/**
 * Base class for unit tests using H2 in-memory database.
 *
 * Fast tests, no Docker required.
 * Run with: mvn test (default, unit-tests profile)
 */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = H2TestcontainerInitializer.class)
public abstract class AbstractUnitTest {
}
