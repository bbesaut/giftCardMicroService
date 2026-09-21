package com.finovago.p2p.integration;

import com.finovago.p2p.AbstractIntegrationTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the CORS policy is wired into the real Spring Security filter chain (allowed origin is
 * "http://localhost:4200" under the test profile). Rate limiting is re-enabled with a tiny login
 * quota to prove two things a browser front depends on: preflights don't consume quota, and a 429
 * still carries CORS headers (otherwise the front only sees an opaque network error).
 */
@DisplayName("CORS Integration Tests")
@TestPropertySource(properties = {
    "app.rate-limit.enabled=true",
    "app.rate-limit.login-capacity=2",
    "app.rate-limit.refill-period-seconds=60"
})
class CorsIntegrationTest extends AbstractIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:4200";
    private static final String OTHER_ORIGIN = "https://evil.example.com";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should answer a preflight from an allowed origin without any JWT")
    void shouldAcceptPreflight_fromAllowedOrigin_withoutAuthentication() throws Exception {
        mockMvc.perform(options("/api/v1/giftcards/redeem")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
                // Spring echoes the requested header names as the browser sent them (lowercase).
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("idempotency-key")))
                .andExpect(header().string("Access-Control-Max-Age", "3600"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    @DisplayName("Should reject a preflight from an origin that is not allowlisted")
    void shouldRejectPreflight_fromUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/giftcards/redeem")
                        .header("Origin", OTHER_ORIGIN)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("Should reject a preflight asking to send X-Api-Key from a browser")
    void shouldRejectPreflight_requestingApiKeyHeader() throws Exception {
        mockMvc.perform(options("/api/v1/giftcards/redeem")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "x-api-key"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("Should reject a preflight for a method the API does not expose")
    void shouldRejectPreflight_forUnsupportedMethod() throws Exception {
        mockMvc.perform(options("/api/v1/giftcards/redeem")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "DELETE"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should add CORS headers on a 401 so the front can read the failure")
    void shouldAddCorsHeaders_onUnauthorizedResponse() throws Exception {
        mockMvc.perform(get("/api/v1/giftcards/lookup/GC-1")
                        .header("Origin", ALLOWED_ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("X-Correlation-Id")))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("X-Response-Time")));
    }

    @Test
    @DisplayName("Should not add CORS headers when the request has no Origin (non-browser client)")
    void shouldNotAddCorsHeaders_whenNoOrigin() throws Exception {
        mockMvc.perform(get("/api/v1/giftcards/lookup/GC-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("Should not apply CORS to public endpoints outside /api")
    void shouldNotApplyCors_outsideApiPath() throws Exception {
        mockMvc.perform(get("/swagger-ui.html").header("Origin", OTHER_ORIGIN))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("Should not count preflights against the login quota, and keep CORS headers on the 429")
    void shouldNotThrottlePreflights_and_keepCorsHeadersOnTooManyRequests() throws Exception {
        String wrongCredentials = "{\"email\":\"cors-unknown@example.com\",\"password\":\"wrongPassword\"}";

        // Well over the quota of 2: preflights are answered by the CORS filter before rate limiting.
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(options("/api/v1/auth/login")
                            .header("Origin", ALLOWED_ORIGIN)
                            .header("Access-Control-Request-Method", "POST"))
                    .andExpect(status().isOk());
        }

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .header("Origin", ALLOWED_ORIGIN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(wrongCredentials))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("Origin", ALLOWED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongCredentials))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("Retry-After")));
    }
}
