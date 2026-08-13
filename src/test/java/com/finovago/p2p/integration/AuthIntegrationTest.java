package com.finovago.p2p.integration;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.finovago.p2p.AbstractIntegrationTest;
import com.finovago.p2p.config.PostgresTestcontainerInitializer;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.model.RefreshToken;
import com.finovago.p2p.model.Role;
import com.finovago.p2p.model.User;
import com.finovago.p2p.repository.GiftCardHoldRepository;
import com.finovago.p2p.repository.GiftCardRepository;
import com.finovago.p2p.repository.IdempotencyKeyRepository;
import com.finovago.p2p.repository.MerchantRepository;
import com.finovago.p2p.repository.RefreshTokenRepository;
import com.finovago.p2p.repository.UserRepository;
import com.finovago.p2p.scheduler.RefreshTokenCleanupScheduler;

@Transactional
class AuthIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "merchant@example.com";
    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenCleanupScheduler refreshTokenCleanupScheduler;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private GiftCardHoldRepository giftCardHoldRepository;

    @Autowired
    private GiftCardRepository giftCardRepository;

    private User user;

    @BeforeEach
    void setUp() {
        // Defensive cleanup, matching every other integration test class: some sibling classes
        // (GiftCardServiceIntegrationTest, LedgerIntegrationTest, etc.) can't use @Transactional
        // because they exercise code running on a separate thread, so they commit real rows and
        // never see a rollback. Without this, this class's insert of the same fixed email can
        // collide with whatever they left behind, depending on execution order - and deleting a
        // leftover merchant/gift card without clearing its dependents first (ledger, holds) would
        // just trade one FK violation for another.
        idempotencyKeyRepository.deleteAll();
        giftCardHoldRepository.deleteAll();
        PostgresTestcontainerInitializer.executeAsMigrator("TRUNCATE TABLE gift_card_ledger RESTART IDENTITY");
        giftCardRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        merchantRepository.deleteAll();

        Merchant merchant = merchantRepository.save(new Merchant("Test Merchant", "merchant@example.com"));
        user = userRepository.save(new User(EMAIL, passwordEncoder.encode(PASSWORD), Role.MERCHANT, merchant));
    }

    @Test
    void should_rotateRefreshTokenAndRejectOldToken_when_fullAuthFlowIsExercised() throws Exception {
        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        String firstRefreshToken = extractField(loginBody, "refreshToken");

        String refreshBody = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + firstRefreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        String secondRefreshToken = extractField(refreshBody, "refreshToken");
        assert !firstRefreshToken.equals(secondRefreshToken) : "refresh token should rotate";

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + firstRefreshToken + "\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + secondRefreshToken + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + secondRefreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_returnUnauthorized_when_loginWithWrongPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_deleteExpiredRefreshToken_when_cleanupSweepRuns() {
        RefreshToken expired = refreshTokenRepository.save(
                new RefreshToken("expired-hash", user, Instant.now().minusSeconds(60)));
        RefreshToken stillValid = refreshTokenRepository.save(
                new RefreshToken("valid-hash", user, Instant.now().plusSeconds(3600)));

        refreshTokenCleanupScheduler.sweepExpiredTokens();

        assertTrue(refreshTokenRepository.findById(expired.getId()).isEmpty());
        assertTrue(refreshTokenRepository.findById(stillValid.getId()).isPresent());
    }

    private String extractField(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker) + marker.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}
