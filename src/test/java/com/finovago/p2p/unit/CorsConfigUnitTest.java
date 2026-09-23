package com.finovago.p2p.unit;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import com.finovago.p2p.config.CorsConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsConfigUnitTest {

    private static final String FRONT_ORIGIN = "https://front.example.com";
    private static final long MAX_AGE_SECONDS = 3600;

    private CorsConfig corsConfig;

    @BeforeEach
    void setUp() {
        corsConfig = new CorsConfig();
    }

    private CorsConfiguration configurationForApiPath(List<String> origins, boolean requireHttps) {
        CorsConfigurationSource source = corsConfig.corsConfigurationSource(origins, requireHttps, MAX_AGE_SECONDS);
        return source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v1/giftcards/lookup/GC-1"));
    }

    @Test
    void should_allowOnlyConfiguredOrigins_when_originsAreValid() {
        List<String> origins = List.of(FRONT_ORIGIN, "https://admin.example.com:8443");

        CorsConfiguration configuration = configurationForApiPath(origins, true);

        assertNotNull(configuration);
        assertEquals(origins, configuration.getAllowedOrigins());
    }

    @Test
    void should_trimAndIgnoreBlankEntries_when_originsHaveWhitespace() {
        CorsConfiguration configuration = configurationForApiPath(List.of("  " + FRONT_ORIGIN + " ", ""), true);

        assertEquals(List.of(FRONT_ORIGIN), configuration.getAllowedOrigins());
    }

    @Test
    void should_notAllowCredentials() {
        CorsConfiguration configuration = configurationForApiPath(List.of(FRONT_ORIGIN), true);

        assertFalse(Boolean.TRUE.equals(configuration.getAllowCredentials()));
    }

    @Test
    void should_restrictMethodsToWhatTheApiUses() {
        CorsConfiguration configuration = configurationForApiPath(List.of(FRONT_ORIGIN), true);

        assertEquals(List.of("GET", "POST", "OPTIONS"), configuration.getAllowedMethods());
    }

    @Test
    void should_allowAuthAndIdempotencyHeaders_but_notApiKeyHeader() {
        CorsConfiguration configuration = configurationForApiPath(List.of(FRONT_ORIGIN), true);

        List<String> allowedHeaders = configuration.getAllowedHeaders();
        assertTrue(allowedHeaders.containsAll(List.of("Authorization", "Content-Type", "Idempotency-Key")));
        assertFalse(allowedHeaders.stream().anyMatch("X-Api-Key"::equalsIgnoreCase));
    }

    @Test
    void should_exposeObservabilityHeadersAndRetryAfter() {
        CorsConfiguration configuration = configurationForApiPath(List.of(FRONT_ORIGIN), true);

        assertTrue(configuration.getExposedHeaders()
                .containsAll(List.of("X-Correlation-Id", "X-Response-Time", "Retry-After")));
    }

    @Test
    void should_setPreflightMaxAge_fromConfiguration() {
        CorsConfiguration configuration = configurationForApiPath(List.of(FRONT_ORIGIN), true);

        assertEquals(MAX_AGE_SECONDS, configuration.getMaxAge());
    }

    @Test
    void should_notApplyCors_when_pathIsOutsideApi() {
        CorsConfigurationSource source = corsConfig.corsConfigurationSource(List.of(FRONT_ORIGIN), true, MAX_AGE_SECONDS);

        assertNull(source.getCorsConfiguration(new MockHttpServletRequest("GET", "/swagger-ui.html")));
    }

    @Test
    void should_acceptHttpOrigin_when_httpsNotRequired() {
        CorsConfiguration configuration = configurationForApiPath(List.of("http://localhost:4200"), false);

        assertEquals(List.of("http://localhost:4200"), configuration.getAllowedOrigins());
    }

    @Test
    void should_rejectHttpOrigin_when_httpsRequired() {
        assertThrows(IllegalStateException.class,
                () -> corsConfig.corsConfigurationSource(List.of("http://front.example.com"), true, MAX_AGE_SECONDS));
    }

    @Test
    void should_failFast_when_originsAreMissingOrBlank() {
        assertThrows(IllegalStateException.class,
                () -> corsConfig.corsConfigurationSource(List.of(), true, MAX_AGE_SECONDS));
        assertThrows(IllegalStateException.class,
                () -> corsConfig.corsConfigurationSource(List.of(" ", ""), true, MAX_AGE_SECONDS));
        assertThrows(IllegalStateException.class,
                () -> corsConfig.corsConfigurationSource(null, true, MAX_AGE_SECONDS));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "*",
            "https://*.example.com",
            "front.example.com",
            "ftp://front.example.com",
            "https://front.example.com/",
            "https://front.example.com/app",
            "https://front.example.com?x=1",
            "https://user@front.example.com",
            "https://"
    })
    void should_rejectMalformedOrigin(String origin) {
        assertThrows(IllegalStateException.class,
                () -> corsConfig.corsConfigurationSource(List.of(origin), false, MAX_AGE_SECONDS));
    }

    @Test
    void should_rejectWholeList_when_oneOriginIsInvalid() {
        assertThrows(IllegalStateException.class,
                () -> corsConfig.corsConfigurationSource(List.of(FRONT_ORIGIN, "*"), true, MAX_AGE_SECONDS));
    }
}
