package com.finovago.p2p.integration;

import com.finovago.p2p.AbstractIntegrationTest;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.model.Role;
import com.finovago.p2p.model.User;
import com.finovago.p2p.repository.MerchantRepository;
import com.finovago.p2p.repository.RefreshTokenRepository;
import com.finovago.p2p.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rate limiting is disabled under the "test" profile (see application-test.properties) so the
 * rest of the integration suite isn't spuriously throttled. This class re-enables it with small
 * capacities to verify the filter is actually wired into the real HTTP filter chain.
 */
@Transactional
@DisplayName("Rate Limit Filter Integration Tests")
@TestPropertySource(properties = {
    "app.rate-limit.enabled=true",
    "app.rate-limit.login-capacity=3",
    "app.rate-limit.merchant-capacity=3",
    "app.rate-limit.refill-period-seconds=60"
})
class RateLimitFilterIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "rate-limit-test@example.com";
    private static final String PASSWORD = "securePassword123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        Merchant merchant = merchantRepository.save(new Merchant("Test Merchant", "merchant@example.com"));
        userRepository.save(new User(EMAIL, passwordEncoder.encode(PASSWORD), Role.MERCHANT, merchant));
    }

    @Test
    @DisplayName("Should return 429 once the login quota (3/min) is exceeded for the same IP")
    void shouldReturnTooManyRequests_afterExceedingLoginQuota() throws Exception {
        String wrongCredentials = "{\"email\":\"" + EMAIL + "\",\"password\":\"wrongPassword\"}";

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(wrongCredentials))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongCredentials))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", notNullValue()))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    @DisplayName("Should return 429 once the lookup quota (3/min) is exceeded for the same merchant, even across different client IPs")
    void shouldReturnTooManyRequests_afterExceedingMerchantQuota_regardlessOfClientIp() throws Exception {
        String loginBody = "{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}";
        // Own IP for this login call - the login bucket is keyed by IP and this Spring context (and
        // its RateLimitFilter bucket cache) is shared with shouldReturnTooManyRequests_afterExceedingLoginQuota,
        // which exercises that same login endpoint at the default MockMvc remote address.
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody)
                        .with(req -> { req.setRemoteAddr("10.1.1.1"); return req; }))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = loginResponse.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");

        for (int i = 0; i < 3; i++) {
            String clientIp = "10.0.0." + i;
            mockMvc.perform(get("/api/v1/giftcards/lookup/does-not-exist")
                            .header("Authorization", "Bearer " + token)
                            .with(req -> { req.setRemoteAddr(clientIp); return req; }))
                    .andExpect(status().isNotFound());
        }

        mockMvc.perform(get("/api/v1/giftcards/lookup/does-not-exist")
                        .header("Authorization", "Bearer " + token)
                        .with(req -> { req.setRemoteAddr("10.0.0.99"); return req; }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", notNullValue()));
    }

    @Test
    @DisplayName("Should not rate-limit endpoints outside the protected list")
    void shouldNotRateLimit_unprotectedEndpoint() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"non-existent-token\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
