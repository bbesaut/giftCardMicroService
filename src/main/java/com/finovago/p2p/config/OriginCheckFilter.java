package com.finovago.p2p.config;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import tools.jackson.databind.json.JsonMapper;

/**
 * Rejects cross-site browser requests to the endpoints that will read the refresh token cookie.
 * <p>
 * The refresh cookie is SameSite=None, so browsers attach it to cross-site requests. Once
 * {@code /refresh} and {@code /logout} read that cookie, a page on another site could trigger them
 * with the victim's cookie. Browsers always send an {@code Origin} header on cross-site requests, so
 * an Origin outside the CORS allowlist is refused. Spring's {@code CorsFilter} already does this for
 * {@code /api/**} and runs first, so this filter is defense in depth: the rule stays explicit on these
 * two routes even if the CORS mapping changes. Requests without an Origin (curl, mobile apps, backend
 * callers) are not browser cross-site requests and pass through.
 * <p>
 * The allowlist is the same {@code app.cors.allowed-origins} that {@link CorsConfig} validates at
 * startup, so the two cannot drift apart.
 */
@Component
public class OriginCheckFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(OriginCheckFilter.class);

    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout"
    );

    private final Set<String> allowedOrigins;
    private final JsonMapper jsonMapper;

    public OriginCheckFilter(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins,
            JsonMapper jsonMapper) {
        this.allowedOrigins = Set.copyOf(allowedOrigins.stream().map(String::trim).toList());
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PROTECTED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String origin = request.getHeader("Origin");
        if (origin == null || allowedOrigins.contains(origin)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Rejected {} from untrusted origin {}", request.getRequestURI(), origin);
        response.setStatus(403);
        response.setContentType("application/json");
        Map<String, Object> errorResponse = Map.of(
                "error", "Forbidden",
                "message", "Request origin is not allowed."
        );
        response.getWriter().write(jsonMapper.writeValueAsString(errorResponse));
    }
}
