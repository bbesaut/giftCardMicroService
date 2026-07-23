package com.finovago.p2p.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final RoleHierarchy roleHierarchy;

    public JwtAuthenticationFilter(JwtService jwtService, @Lazy RoleHierarchy roleHierarchy) {
        this.jwtService = jwtService;
        this.roleHierarchy = roleHierarchy;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        // Async controller methods (e.g. redeem, backed by CompletableFuture) resume on the container's
        // async dispatch, which runs SecurityContextHolder-empty by default. Spring Security's own
        // authorization filter still runs on that dispatch, so this filter must re-authenticate too —
        // otherwise the final response for any async endpoint is always 401, regardless of outcome.
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            String username = jwtService.extractUsername(token);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                if (jwtService.isTokenValid(token)) {
                    List<String> roles = jwtService.extractRoles(token);
                    Long merchantId = jwtService.extractMerchantId(token);
                    String role = roles.isEmpty() ? null : roles.get(0);

                    // Fail closed: a MERCHANT token minted before merchantId was introduced (or otherwise
                    // missing it) must not be treated as authenticated, since it has no tenant to scope to.
                    boolean missingRequiredMerchant = "MERCHANT".equals(role) && merchantId == null;

                    if (!missingRequiredMerchant) {
                        List<SimpleGrantedAuthority> authorities = roles.stream()
                                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                                .toList();

                        Collection<? extends GrantedAuthority> reachableRoles =
                                roleHierarchy.getReachableGrantedAuthorities(authorities);

                        AuthenticatedUser authenticatedUser = new AuthenticatedUser(username, role, merchantId);
                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(authenticatedUser, null, reachableRoles);

                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            }
            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException | MalformedJwtException | SignatureException e) {
            request.setAttribute("authException", e);
            throw e;
        }
    }
}