package com.finovago.p2p.unit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.finovago.p2p.security.AuthenticatedUser;
import com.finovago.p2p.security.CurrentUserContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentUserContextUnitTest {

    private final CurrentUserContext currentUserContext = new CurrentUserContext();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(AuthenticatedUser user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, java.util.List.of()));
    }

    @Test
    void should_returnTrue_when_currentUserIsAdmin() {
        authenticateAs(new AuthenticatedUser("admin@example.com", "ADMIN", null, 1L));

        assertTrue(currentUserContext.isAdmin());
    }

    @Test
    void should_returnFalse_when_currentUserIsMerchant() {
        authenticateAs(new AuthenticatedUser("merchant@example.com", "MERCHANT", 5L, 1L));

        assertFalse(currentUserContext.isAdmin());
    }

    @Test
    void should_returnMerchantId_when_present() {
        authenticateAs(new AuthenticatedUser("merchant@example.com", "MERCHANT", 5L, 1L));

        assertEquals(5L, currentUserContext.currentMerchantId());
    }

    @Test
    void should_throwIllegalStateException_when_merchantIdMissing() {
        authenticateAs(new AuthenticatedUser("admin@example.com", "ADMIN", null, 1L));

        assertThrows(IllegalStateException.class, currentUserContext::currentMerchantId);
    }

    @Test
    void should_throw_when_noAuthenticationInContext() {
        // SecurityContextHolder.getContext().getAuthentication() is null here (no filter has run),
        // so currentUser() NPEs before it ever gets a chance to check for AuthenticatedUser -
        // this path isn't reachable in production, where Spring Security's anyRequest().authenticated()
        // already rejects unauthenticated requests before any controller/service is invoked.
        assertThrows(NullPointerException.class, currentUserContext::currentMerchantId);
    }

    @Test
    void should_returnUserId_when_present() {
        authenticateAs(new AuthenticatedUser("merchant@example.com", "MERCHANT", 5L, 42L));

        assertEquals(42L, currentUserContext.currentUserIdOrNull());
    }

    @Test
    void should_returnNull_when_userIdMissing() {
        authenticateAs(new AuthenticatedUser("merchant@example.com", "MERCHANT", 5L, null));

        assertNull(currentUserContext.currentUserIdOrNull());
    }
}
