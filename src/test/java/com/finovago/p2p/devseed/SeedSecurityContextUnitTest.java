package com.finovago.p2p.devseed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.finovago.p2p.security.AuthenticatedUser;
import com.finovago.p2p.security.CurrentUserContext;

class SeedSecurityContextUnitTest {

    private static final AuthenticatedUser MERCHANT_USER = new AuthenticatedUser("owner@example.com", "MERCHANT", 7L, 42L);
    private static final AuthenticatedUser API_KEY_CALLER = new AuthenticatedUser("api-key", "MERCHANT", 7L, null, true);

    private final CurrentUserContext currentUserContext = new CurrentUserContext();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_exposeThePrincipalToServices_when_insideRunAs() {
        AtomicReference<Long> merchantId = new AtomicReference<>();
        AtomicReference<Long> userId = new AtomicReference<>();

        SeedSecurityContext.runAs(MERCHANT_USER, () -> {
            merchantId.set(currentUserContext.currentMerchantId());
            userId.set(currentUserContext.currentUserIdOrNull());
        });

        assertEquals(7L, merchantId.get());
        assertEquals(42L, userId.get());
    }

    @Test
    void should_exposeApiKeyCaller_when_principalIsApiKey() {
        AtomicReference<Boolean> viaApiKey = new AtomicReference<>();
        AtomicReference<Long> userId = new AtomicReference<>(-1L);

        SeedSecurityContext.runAs(API_KEY_CALLER, () -> {
            viaApiKey.set(currentUserContext.isApiKeyAuthenticated());
            userId.set(currentUserContext.currentUserIdOrNull());
        });

        assertTrue(viaApiKey.get());
        assertNull(userId.get());
    }

    @Test
    void should_returnTheActionResult_when_usingCall() {
        String result = SeedSecurityContext.call(MERCHANT_USER, () -> "result for " + currentUserContext.currentMerchantId());

        assertEquals("result for 7", result);
    }

    @Test
    void should_restoreThePreviousContext_when_actionCompletes() {
        Authentication previous = new UsernamePasswordAuthenticationToken("someone-else", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(previous);

        SeedSecurityContext.runAs(MERCHANT_USER, () -> { });

        assertSame(previous, SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void should_restoreThePreviousContext_when_actionThrows() {
        Authentication previous = new UsernamePasswordAuthenticationToken("someone-else", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(previous);

        assertThrows(IllegalStateException.class, () -> SeedSecurityContext.runAs(MERCHANT_USER, () -> {
            throw new IllegalStateException("boom");
        }));

        assertSame(previous, SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void should_leaveNoAuthentication_when_therewasNoneBefore() {
        SeedSecurityContext.runAs(MERCHANT_USER, () -> { });

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
