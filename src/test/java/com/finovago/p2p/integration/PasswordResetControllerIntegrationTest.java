package com.finovago.p2p.integration;

import java.io.IOException;
import java.util.regex.Pattern;

import com.finovago.p2p.AbstractIntegrationTest;
import com.finovago.p2p.config.PostgresTestcontainerInitializer;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.model.Role;
import com.finovago.p2p.model.User;
import com.finovago.p2p.repository.GiftCardRepository;
import com.finovago.p2p.repository.IdempotencyKeyRepository;
import com.finovago.p2p.repository.MerchantRepository;
import com.finovago.p2p.repository.RefreshTokenRepository;
import com.finovago.p2p.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class PasswordResetControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "reset-test@example.com";
    private static final String PASSWORD = "securePassword123";
    private static final String NEW_PASSWORD = "NewStrongP@ssw0rd";
    private static final Pattern TOKEN_PATTERN = Pattern.compile("Reset token: ([0-9a-fA-F-]{36})");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private GiftCardRepository giftCardRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        // gift_card and idempotency_key both have a FK to merchants; other non-transactional
        // integration test classes commit rows that outlive this class, so merchants must not be
        // deleted while leftover rows still reference them - same reasoning as
        // AuthControllerIntegrationTest#setUp.
        idempotencyKeyRepository.deleteAll();
        PostgresTestcontainerInitializer.executeAsMigrator("TRUNCATE TABLE gift_card_ledger RESTART IDENTITY");
        giftCardRepository.deleteAll();
        merchantRepository.deleteAll();
        Merchant merchant = merchantRepository.save(new Merchant("Test Merchant", "merchant@example.com"));
        userRepository.save(TestUsers.verified(EMAIL, passwordEncoder.encode(PASSWORD), Role.MERCHANT, merchant));

        MailHogInbox.clear();
    }

    @Test
    void should_resetPasswordAndRevokeOtherSessions_when_tokenIsValid() throws Exception {
        MvcResult loginResult = performLogin(EMAIL, PASSWORD, status().isOk());
        String oldRefreshToken = refreshTokenFromCookie(loginResult);

        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isAccepted());

        String rawToken = MailHogInbox.tokenFromLatestEmail(TOKEN_PATTERN);

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawToken + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        // Old password no longer works, new password does.
        performLogin(EMAIL, PASSWORD, status().isUnauthorized());
        performLogin(EMAIL, NEW_PASSWORD, status().isOk());

        // The refresh token issued before the reset is revoked (other sessions logged out).
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie(oldRefreshToken)))
                .andExpect(status().isUnauthorized());

        // The token is single-use: replaying it fails.
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawToken + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_returnAccepted_when_emailIsUnknown() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"unknown@example.com\"}"))
                .andExpect(status().isAccepted());

        assertEquals(0, MailHogInbox.messageCount());
    }

    @Test
    void should_returnBadRequest_when_confirmTokenIsUnknown() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"not-a-real-token\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_returnBadRequest_when_newPasswordFailsComplexityRules() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isAccepted());

        String rawToken = MailHogInbox.tokenFromLatestEmail(TOKEN_PATTERN);

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawToken + "\",\"newPassword\":\"weak\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_returnBadRequest_when_requestEmailIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
    }

    private MvcResult performLogin(String email, String password, ResultMatcher expectedStatus) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(expectedStatus)
                .andReturn();
    }
}
