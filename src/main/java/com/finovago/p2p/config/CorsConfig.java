package com.finovago.p2p.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS policy for browser clients (e.g. the Angular front, served from a different origin).
 * <p>
 * Authentication is a Bearer JWT in the {@code Authorization} header - no cookies, no session - so
 * credentials are deliberately NOT allowed: that keeps a compromised or over-broad origin from
 * riding on ambient browser credentials, and avoids the browser rule that forbids combining
 * credentials with wildcard origins. Origins come from configuration as an exact allowlist (never
 * {@code *}); the app refuses to start on a missing, blank or malformed list rather than silently
 * falling open or closed.
 * <p>
 * {@code X-Api-Key} is intentionally absent from the allowed headers: API keys are for
 * backend-to-backend integration and must never be sent from a browser, where they'd be exposed.
 * <p>
 * CORS is enforced by browsers only - it is not an access control. Authorization stays with the
 * JWT/role checks in {@link SecurityConfig}.
 */
@Configuration
public class CorsConfig {

    static final String API_PATH_PATTERN = "/api/**";

    private static final List<String> ALLOWED_METHODS = List.of("GET", "POST", "OPTIONS");
    private static final List<String> ALLOWED_HEADERS = List.of("Authorization", "Content-Type", "Idempotency-Key");

    // Response headers are hidden from browser JS unless listed here. Correlation id / timing are
    // what a front needs to report an issue or surface latency; Retry-After lets it honor a 429.
    private static final List<String> EXPOSED_HEADERS = List.of("X-Correlation-Id", "X-Response-Time", "Retry-After");

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins,
            @Value("${app.cors.require-https:false}") boolean requireHttps,
            @Value("${app.cors.max-age-seconds:3600}") long maxAgeSeconds) {

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(validateOrigins(allowedOrigins, requireHttps));
        configuration.setAllowedMethods(ALLOWED_METHODS);
        configuration.setAllowedHeaders(ALLOWED_HEADERS);
        configuration.setExposedHeaders(EXPOSED_HEADERS);
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(Duration.ofSeconds(maxAgeSeconds));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(API_PATH_PATTERN, configuration);
        return source;
    }

    /**
     * Fails fast on anything that isn't a bare {@code scheme://host[:port]} origin. Browsers send
     * the {@code Origin} header in exactly that form, so a trailing slash or a path would silently
     * never match and the front would just see an opaque CORS error.
     */
    static List<String> validateOrigins(List<String> origins, boolean requireHttps) {
        List<String> cleaned = origins == null ? List.of()
                : origins.stream().map(String::trim).filter(o -> !o.isEmpty()).toList();

        if (cleaned.isEmpty()) {
            throw new IllegalStateException(
                    "app.cors.allowed-origins must list at least one origin (e.g. https://app.example.com)");
        }
        cleaned.forEach(origin -> validateOrigin(origin, requireHttps));
        return cleaned;
    }

    private static void validateOrigin(String origin, boolean requireHttps) {
        if (origin.contains("*")) {
            throw new IllegalStateException("Wildcard CORS origins are not allowed: '" + origin + "'");
        }

        URI uri;
        try {
            uri = new URI(origin);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Invalid CORS origin: '" + origin + "'", e);
        }

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new IllegalStateException("CORS origin must start with http:// or https://: '" + origin + "'");
        }
        if (requireHttps && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalStateException("CORS origin must use https in this environment: '" + origin + "'");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalStateException("CORS origin must be scheme://host[:port]: '" + origin + "'");
        }
        boolean hasPathQueryOrFragment = (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null;
        if (hasPathQueryOrFragment) {
            throw new IllegalStateException(
                    "CORS origin must not have a path, query or trailing slash: '" + origin + "'");
        }
    }
}
