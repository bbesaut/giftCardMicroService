package com.finovago.p2p.integration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finovago.p2p.AbstractIntegrationTest;
import com.finovago.p2p.config.MailHogTestcontainerInitializer;
import com.finovago.p2p.config.PostgresTestcontainerInitializer;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.model.Role;
import com.finovago.p2p.model.User;
import com.finovago.p2p.repository.GiftCardRepository;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class PasswordResetControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "reset-test@example.com";
    private static final String PASSWORD = "securePassword123";
    private static final String NEW_PASSWORD = "NewStrongP@ssw0rd";
    private static final Pattern TOKEN_PATTERN = Pattern.compile("Reset token: ([0-9a-fA-F-]{36})");

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

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
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        // gift_card has a FK to merchants; other non-transactional integration test classes commit
        // rows that outlive this class, so merchants must not be deleted while leftover gift cards
        // still reference them - same reasoning as AuthControllerIntegrationTest#setUp.
        PostgresTestcontainerInitializer.executeAsMigrator("TRUNCATE TABLE gift_card_ledger RESTART IDENTITY");
        giftCardRepository.deleteAll();
        merchantRepository.deleteAll();
        Merchant merchant = merchantRepository.save(new Merchant("Test Merchant", "merchant@example.com"));
        userRepository.save(new User(EMAIL, passwordEncoder.encode(PASSWORD), Role.MERCHANT, merchant));

        clearMailhogInbox();
    }

    @Test
    void should_resetPasswordAndRevokeOtherSessions_when_tokenIsValid() throws Exception {
        MvcResult loginResult = performLogin(EMAIL, PASSWORD, status().isOk());
        String oldRefreshToken = extractField(loginResult, "refreshToken");

        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isAccepted());

        String rawToken = extractTokenFromLatestEmail();

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawToken + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        // Old password no longer works, new password does.
        performLogin(EMAIL, PASSWORD, status().isUnauthorized());
        performLogin(EMAIL, NEW_PASSWORD, status().isOk());

        // The refresh token issued before the reset is revoked (other sessions logged out).
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefreshToken + "\"}"))
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

        assertEquals(0, mailhogMessageCount());
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

        String rawToken = extractTokenFromLatestEmail();

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

    private void clearMailhogInbox() throws IOException, InterruptedException {
        httpClient.send(HttpRequest.newBuilder(URI.create(MailHogTestcontainerInitializer.httpApiBaseUrl() + "/api/v1/messages"))
                .DELETE()
                .build(), HttpResponse.BodyHandlers.discarding());
    }

    private int mailhogMessageCount() throws IOException, InterruptedException {
        return latestMessagesJson().get("total").asInt();
    }

    private String extractTokenFromLatestEmail() throws IOException, InterruptedException {
        JsonNode root = latestMessagesJson();
        String body = root.get("items").get(0).get("Content").get("Body").asText();
        assertNotNull(body);
        Matcher matcher = TOKEN_PATTERN.matcher(body);
        assertTrue(matcher.find(), "No reset token found in email body: " + body);
        return matcher.group(1);
    }

    private JsonNode latestMessagesJson() throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(MailHogTestcontainerInitializer.httpApiBaseUrl() + "/api/v2/messages")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(response.body());
    }

    private String extractField(MvcResult result, String fieldName) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.get(fieldName).asText();
    }
}
