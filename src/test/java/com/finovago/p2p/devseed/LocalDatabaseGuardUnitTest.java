package com.finovago.p2p.devseed;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LocalDatabaseGuardUnitTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://localhost:5432/p2p_dev",
            "jdbc:postgresql://127.0.0.1:5432/p2p_dev",
            "jdbc:postgresql://[::1]:5432/p2p_dev",
            "jdbc:postgresql://LOCALHOST/p2p_dev",
            "jdbc:postgresql://localhost/p2p_dev?ssl=false"
    })
    void should_acceptUrl_when_hostIsLocal(String jdbcUrl) {
        assertTrue(LocalDatabaseGuard.isLocal(jdbcUrl));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://ep-cool-123.eu-central-1.aws.neon.tech/neondb",
            "jdbc:postgresql://10.0.0.5:5432/p2p_dev",
            // Look-alikes: the host that counts is the real one, not a substring or a query parameter.
            "jdbc:postgresql://localhost.evil.com:5432/p2p_dev",
            "jdbc:postgresql://localhost@evil.com/p2p_dev",
            "jdbc:postgresql://evil.com/p2p_dev?host=localhost",
            // Multi-host URLs can fail over to a remote host - fail closed rather than guess.
            "jdbc:postgresql://localhost:5432,evil.com:5432/p2p_dev",
            "jdbc:postgresql:///p2p_dev",
            "postgresql://localhost:5432/p2p_dev",
            "not a url"
    })
    void should_rejectUrl_when_hostIsNotLocalOrUrlIsUnusable(String jdbcUrl) {
        assertFalse(LocalDatabaseGuard.isLocal(jdbcUrl));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void should_rejectUrl_when_urlIsMissing(String jdbcUrl) {
        assertFalse(LocalDatabaseGuard.isLocal(jdbcUrl));
    }

    @Test
    void should_notThrow_when_requireLocalGetsALocalUrl() {
        assertDoesNotThrow(() -> LocalDatabaseGuard.requireLocal("jdbc:postgresql://localhost:5432/p2p_dev"));
    }

    @Test
    void should_throwAndNameTheUrl_when_requireLocalGetsARemoteUrl() {
        String remoteUrl = "jdbc:postgresql://ep-cool-123.eu-central-1.aws.neon.tech/neondb";

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> LocalDatabaseGuard.requireLocal(remoteUrl));

        assertTrue(thrown.getMessage().contains(remoteUrl));
    }
}
