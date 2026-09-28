package com.finovago.p2p.devseed;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.finovago.p2p.security.AuthenticatedUser;

/**
 * Runs a block as a given authenticated principal, outside any HTTP request. The business services
 * read tenant and actor from the security context (see CurrentUserContext), so seeding through them
 * - which keeps every invariant, ledger entries included - means impersonating the account that
 * would have made the call.
 */
final class SeedSecurityContext {

    private SeedSecurityContext() {
    }

    static void runAs(AuthenticatedUser principal, Runnable action) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext seedContext = SecurityContextHolder.createEmptyContext();
        seedContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + principal.role()))));
        SecurityContextHolder.setContext(seedContext);
        try {
            action.run();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }
}
