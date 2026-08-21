package com.finovago.p2p.security;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.finovago.p2p.model.ApiKey;
import com.finovago.p2p.service.ApiKeyService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Alternative to JwtAuthenticationFilter for a merchant's automated/backend integration: a
 * rotatable "X-Api-Key: {prefix}.{secret}" header instead of a Bearer JWT. An API key is its own
 * identity (no backing User row - see ApiKeyService), so the resulting principal has no userId:
 * ledger writes attribute it to "SYSTEM" (CurrentUserContext#isApiKeyAuthenticated) instead of an
 * email, and owner-only self-service routes reject it the same way they'd reject any anonymous
 * caller (requireOwner needs a real userId).
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-Api-Key";

    private final ApiKeyService apiKeyService;
    private final RoleHierarchy roleHierarchy;

    public ApiKeyAuthenticationFilter(@Lazy ApiKeyService apiKeyService, @Lazy RoleHierarchy roleHierarchy) {
        this.apiKeyService = apiKeyService;
        this.roleHierarchy = roleHierarchy;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String presentedKey = request.getHeader(HEADER_NAME);

        if (presentedKey == null || presentedKey.isBlank()
                || SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<ApiKey> apiKey = apiKeyService.resolve(presentedKey);

        if (apiKey.isPresent()) {
            ApiKey key = apiKey.get();
            List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_MERCHANT"));
            Collection<? extends GrantedAuthority> reachableRoles = roleHierarchy.getReachableGrantedAuthorities(authorities);

            AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                    "api-key:" + key.getKeyPrefix(), "MERCHANT", key.getMerchant().getId(), null, true);
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(authenticatedUser, null, reachableRoles);

            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        filterChain.doFilter(request, response);
    }
}
