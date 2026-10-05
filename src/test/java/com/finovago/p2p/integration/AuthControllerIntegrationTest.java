package com.finovago.p2p.integration;

import com.finovago.p2p.AbstractIntegrationTest;
import com.finovago.p2p.config.PostgresTestcontainerInitializer;
import com.finovago.p2p.dto.ApiKeyResponse;
import com.finovago.p2p.dto.AuthResponse;
import com.finovago.p2p.dto.MerchantUserResponse;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.SET_COOKIE;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.json.JsonMapper;

@Transactional
class AuthControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "controller-test@example.com";
    private static final String PASSWORD = "securePassword123";
    private static final String VALID_EMAIL = "valid@example.com";
    private static final String OWNER_EMAIL = "owner-test@example.com";

    private Long merchantId;
    private Long ownerId;

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

    @Autowired
    private JsonMapper objectMapper;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        // gift_card and idempotency_key both have a FK to merchants; other integration test classes
        // (e.g. GiftCardServiceIntegrationTest) are non-transactional and commit rows that outlive
        // this class, so merchants must not be deleted while leftover rows still reference them.
        idempotencyKeyRepository.deleteAll();
        PostgresTestcontainerInitializer.executeAsMigrator("TRUNCATE TABLE gift_card_ledger RESTART IDENTITY");
        giftCardRepository.deleteAll();
        merchantRepository.deleteAll();
        Merchant merchant = merchantRepository.save(new Merchant("Test Merchant", "merchant@example.com"));
        merchantId = merchant.getId();
        userRepository.save(new User(EMAIL, passwordEncoder.encode(PASSWORD), Role.MERCHANT, merchant));
        userRepository.save(new User(VALID_EMAIL, passwordEncoder.encode(PASSWORD), Role.ADMIN, null));
        ownerId = userRepository.save(new User(OWNER_EMAIL, passwordEncoder.encode(PASSWORD), Role.MERCHANT, merchant, true)).getId();
    }

    @Test
    void should_loginSuccessfully_and_returnAccessTokenOnly_withRefreshTokenInCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string(SET_COOKIE, containsString("refresh_token=")));
    }

    @Test
    void should_returnUnauthorized_when_emailNotExists() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nonexistent@example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_returnUnauthorized_when_passwordIsWrong() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"wrongPassword\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_returnBadRequest_when_emailIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_returnBadRequest_when_passwordIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_returnBadRequest_when_emailIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_returnBadRequest_when_requestBodyIsMalformed() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"pass\""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_refreshFromCookie_and_returnNewAccessTokenOnly() throws Exception {
        String refreshToken = loginAndGetRefreshToken(EMAIL, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void should_returnBadRequest_when_refreshCookieIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_returnBadRequest_when_refreshCookieIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie("")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_returnBadRequest_when_refreshTokenIsOnlyInBody() throws Exception {
        String refreshToken = loginAndGetRefreshToken(EMAIL, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_returnUnauthorized_when_refreshCookieIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie("invalid-refresh-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_logoutFromCookie_and_returnNoContent() throws Exception {
        String refreshToken = loginAndGetRefreshToken(EMAIL, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(refreshCookie(refreshToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    void should_rejectRefreshCookie_after_logout() throws Exception {
        String refreshToken = loginAndGetRefreshToken(EMAIL, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(refreshCookie(refreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_returnUnauthorized_when_logoutCookieIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(refreshCookie("invalid-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_returnBadRequest_when_logoutCookieIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_returnBadRequest_when_logoutTokenIsOnlyInBody() throws Exception {
        String refreshToken = loginAndGetRefreshToken(EMAIL, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_setRefreshTokenCookie_notBody_when_loginSucceeds() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = loginResult.getResponse().getHeader(SET_COOKIE);

        assertNotNull(setCookie);
        assertTrue(setCookie.startsWith("refresh_token="));
        assertRefreshCookieAttributes(setCookie, 604_800);
        assertFalse(loginResult.getResponse().getContentAsString().contains("refreshToken"));
    }

    @Test
    void should_rotateRefreshCookie_when_refreshSucceeds() throws Exception {
        String oldRefreshToken = loginAndGetRefreshToken(EMAIL, PASSWORD);

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie(oldRefreshToken)))
                .andExpect(status().isOk())
                .andReturn();

        String newRefreshToken = refreshTokenFromCookie(refreshResult);
        String setCookie = refreshResult.getResponse().getHeader(SET_COOKIE);

        assertNotEquals(oldRefreshToken, newRefreshToken);
        assertRefreshCookieAttributes(setCookie, 604_800);
    }

    @Test
    void should_clearRefreshCookie_when_logoutSucceeds() throws Exception {
        String refreshToken = loginAndGetRefreshToken(EMAIL, PASSWORD);

        MvcResult logoutResult = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(refreshCookie(refreshToken)))
                .andExpect(status().isNoContent())
                .andReturn();

        String setCookie = logoutResult.getResponse().getHeader(SET_COOKIE);
        assertNotNull(setCookie);
        assertTrue(setCookie.startsWith("refresh_token=;"));
        assertTrue(setCookie.contains("Max-Age=0"));
        assertTrue(setCookie.contains("Path=/api/v1/auth"));
    }

    @Test
    void should_notSetRefreshCookie_when_loginFails() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"wrongPassword\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertNull(result.getResponse().getHeader(SET_COOKIE));
    }

    @Test
    void should_notTouchRefreshCookie_when_logoutFails() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(refreshCookie("invalid-refresh-token")))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertNull(result.getResponse().getHeader(SET_COOKIE));
    }

    private String loginAndGetRefreshToken(String email, String password) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return refreshTokenFromCookie(loginResult);
    }

    private void assertRefreshCookieAttributes(String setCookie, long maxAgeSeconds) {
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("Secure"));
        assertTrue(setCookie.contains("SameSite=None"));
        assertTrue(setCookie.contains("Path=/api/v1/auth"));
        assertTrue(setCookie.contains("Max-Age=" + maxAgeSeconds));
    }

    @Test
    void should_handleMultipleLoginAttempts() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken", notNullValue()))
                    .andExpect(jsonPath("$.refreshToken").doesNotExist());
        }
    }

    @Test
    void should_loginWith_differentUsersAndDifferentRoles() throws Exception {
        MvcResult adminLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + VALID_EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String adminResponse = adminLogin.getResponse().getContentAsString();
        AuthResponse adminAuthResponse = objectMapper.readValue(adminResponse, AuthResponse.class);

        assertNotNull(adminAuthResponse.accessToken());
        assertNotNull(refreshTokenFromCookie(adminLogin));

        MvcResult clientLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String clientResponse = clientLogin.getResponse().getContentAsString();
        AuthResponse clientAuthResponse = objectMapper.readValue(clientResponse, AuthResponse.class);

        assertNotNull(clientAuthResponse.accessToken());
        assertNotNull(refreshTokenFromCookie(clientLogin));
    }

    @Test
    void should_generateValidTokensOnEachLogin() throws Exception {
        MvcResult result1 = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Thread.sleep(100);

        MvcResult result2 = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String response1 = result1.getResponse().getContentAsString();
        String response2 = result2.getResponse().getContentAsString();

        AuthResponse auth1 = objectMapper.readValue(response1, AuthResponse.class);
        AuthResponse auth2 = objectMapper.readValue(response2, AuthResponse.class);

        // Each login should generate a valid access token
        assertNotNull(auth1.accessToken());
        assertNotNull(auth2.accessToken());

        // Refresh tokens travel in the cookie and should be different (they are UUIDs)
        assertNotEquals(refreshTokenFromCookie(result1), refreshTokenFromCookie(result2));
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String loginResponse = loginResult.getResponse().getContentAsString();
        return objectMapper.readValue(loginResponse, AuthResponse.class).accessToken();
    }

    @Test
    void should_registerAsPublicSignup_and_logOwnerInWithCookie() throws Exception {
        String newEmail = "newuser@example.com";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + newEmail + "\",\"password\":\"" + PASSWORD + "\",\"merchantName\":\"New Merchant\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        assertRefreshCookieAttributes(result.getResponse().getHeader(SET_COOKIE), 604_800);

        // Verify user was created
        assertNotNull(userRepository.findByEmail(newEmail));
    }

    @Test
    void should_returnBadRequest_when_publicRegisterPasswordIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"anonymous@example.com\",\"password\":\"\",\"merchantName\":\"New Merchant\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_returnConflict_when_emailAlreadyExists() throws Exception {
        String adminAccessToken = loginAndGetAccessToken(VALID_EMAIL, PASSWORD);
        mockMvc.perform(post("/api/v1/auth/register")
                        .header(AUTHORIZATION, "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\",\"merchantName\":\"New Merchant\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void should_returnBadRequest_when_registrationEmailIsBlank() throws Exception {
        String adminAccessToken = loginAndGetAccessToken(VALID_EMAIL, PASSWORD);
        mockMvc.perform(post("/api/v1/auth/register")
                        .header(AUTHORIZATION, "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"" + PASSWORD + "\",\"merchantName\":\"New Merchant\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_returnBadRequest_when_registrationPasswordIsBlank() throws Exception {
        String adminAccessToken = loginAndGetAccessToken(VALID_EMAIL, PASSWORD);
        mockMvc.perform(post("/api/v1/auth/register")
                        .header(AUTHORIZATION, "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"newuser@example.com\",\"password\":\"\",\"merchantName\":\"New Merchant\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_returnBadRequest_when_registrationEmailIsInvalid() throws Exception {
        String adminAccessToken = loginAndGetAccessToken(VALID_EMAIL, PASSWORD);
        mockMvc.perform(post("/api/v1/auth/register")
                        .header(AUTHORIZATION, "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"" + PASSWORD + "\",\"merchantName\":\"New Merchant\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_returnBadRequest_when_registrationMerchantNameIsBlank() throws Exception {
        String adminAccessToken = loginAndGetAccessToken(VALID_EMAIL, PASSWORD);
        mockMvc.perform(post("/api/v1/auth/register")
                        .header(AUTHORIZATION, "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"newuser@example.com\",\"password\":\"" + PASSWORD + "\",\"merchantName\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_createNewMerchantAndUserWithMerchantRoleOnRegistration() throws Exception {
        String adminAccessToken = loginAndGetAccessToken(VALID_EMAIL, PASSWORD);
        String newEmail = "merchantuser@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .header(AUTHORIZATION, "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + newEmail + "\",\"password\":\"" + PASSWORD + "\",\"merchantName\":\"New Merchant\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken").doesNotExist());

        User createdOwner = userRepository.findByEmail(newEmail).orElseThrow();
        assertEquals(Role.MERCHANT, createdOwner.getRole());
        assertNotNull(createdOwner.getMerchant());
        assertEquals("New Merchant", createdOwner.getMerchant().getName());
        assertEquals(true, createdOwner.isOwner());

        // No other user is created upfront - only the owner.
        long usersForMerchant = userRepository.findAll().stream()
                .filter(u -> createdOwner.getMerchant().getId().equals(u.getMerchant() != null ? u.getMerchant().getId() : null))
                .count();
        assertEquals(1L, usersForMerchant);
    }

    @Test
    void should_generateApiKeyForMerchant_withoutCreatingAnyUser_when_ownerRequestsOne() throws Exception {
        String ownerAccessToken = loginAndGetAccessToken(OWNER_EMAIL, PASSWORD);
        long usersBefore = userRepository.count();

        mockMvc.perform(post("/api/v1/auth/me/api-key")
                        .header(AUTHORIZATION, "Bearer " + ownerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyPrefix", notNullValue()))
                .andExpect(jsonPath("$.apiKeySecret", notNullValue()));

        assertEquals(usersBefore, userRepository.count());
    }

    @Test
    void should_authenticateWithApiKey_when_usedOnAMerchantEndpoint() throws Exception {
        String ownerAccessToken = loginAndGetAccessToken(OWNER_EMAIL, PASSWORD);

        MvcResult keyResult = mockMvc.perform(post("/api/v1/auth/me/api-key")
                        .header(AUTHORIZATION, "Bearer " + ownerAccessToken))
                .andExpect(status().isOk())
                .andReturn();

        String apiKeySecret = objectMapper.readValue(keyResult.getResponse().getContentAsString(), ApiKeyResponse.class)
                .apiKeySecret();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/giftcards/lookup/NON-EXISTENT-CODE")
                        .header("X-Api-Key", apiKeySecret))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_rotateApiKey_invalidatingThePreviousSecret_when_ownerGeneratesAgain() throws Exception {
        String ownerAccessToken = loginAndGetAccessToken(OWNER_EMAIL, PASSWORD);

        MvcResult firstResult = mockMvc.perform(post("/api/v1/auth/me/api-key")
                        .header(AUTHORIZATION, "Bearer " + ownerAccessToken))
                .andExpect(status().isOk())
                .andReturn();
        String firstSecret = objectMapper.readValue(firstResult.getResponse().getContentAsString(), ApiKeyResponse.class)
                .apiKeySecret();

        mockMvc.perform(post("/api/v1/auth/me/api-key")
                        .header(AUTHORIZATION, "Bearer " + ownerAccessToken))
                .andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/giftcards/lookup/NON-EXISTENT-CODE")
                        .header("X-Api-Key", firstSecret))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_revokeApiKey_when_ownerCallsRevoke() throws Exception {
        String ownerAccessToken = loginAndGetAccessToken(OWNER_EMAIL, PASSWORD);

        MvcResult keyResult = mockMvc.perform(post("/api/v1/auth/me/api-key")
                        .header(AUTHORIZATION, "Bearer " + ownerAccessToken))
                .andExpect(status().isOk())
                .andReturn();
        String apiKeySecret = objectMapper.readValue(keyResult.getResponse().getContentAsString(), ApiKeyResponse.class)
                .apiKeySecret();

        mockMvc.perform(post("/api/v1/auth/me/api-key/revoke")
                        .header(AUTHORIZATION, "Bearer " + ownerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/giftcards/lookup/NON-EXISTENT-CODE")
                        .header("X-Api-Key", apiKeySecret))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_returnInactiveStatus_when_revokingApiKeyThatDoesNotExist() throws Exception {
        String ownerAccessToken = loginAndGetAccessToken(OWNER_EMAIL, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/me/api-key/revoke")
                        .header(AUTHORIZATION, "Bearer " + ownerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void should_returnForbidden_when_nonOwnerRequestsApiKey() throws Exception {
        String clientAccessToken = loginAndGetAccessToken(EMAIL, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/me/api-key")
                        .header(AUTHORIZATION, "Bearer " + clientAccessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_returnInactiveNullFields_when_gettingApiKeyStatusWithNoKeyGenerated() throws Exception {
        String ownerAccessToken = loginAndGetAccessToken(OWNER_EMAIL, PASSWORD);

        mockMvc.perform(get("/api/v1/auth/me/api-key")
                        .header(AUTHORIZATION, "Bearer " + ownerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyPrefix", nullValue()))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.createdAt", nullValue()));
    }

    @Test
    void should_returnPrefixActiveAndCreatedAt_butNeverTheSecret_when_gettingApiKeyStatusAfterGenerating() throws Exception {
        String ownerAccessToken = loginAndGetAccessToken(OWNER_EMAIL, PASSWORD);

        MvcResult keyResult = mockMvc.perform(post("/api/v1/auth/me/api-key")
                        .header(AUTHORIZATION, "Bearer " + ownerAccessToken))
                        .andExpect(status().isOk())
                        .andReturn();
        String keyPrefix = objectMapper.readValue(keyResult.getResponse().getContentAsString(), ApiKeyResponse.class)
                .keyPrefix();

        MvcResult statusResult = mockMvc.perform(get("/api/v1/auth/me/api-key")
                        .header(AUTHORIZATION, "Bearer " + ownerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyPrefix").value(keyPrefix))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andReturn();

        // Regression guard: the secret must never resurface outside its one-time POST /me/api-key response.
        assertTrue(!statusResult.getResponse().getContentAsString().contains("apiKeySecret"));
    }

    @Test
    void should_returnInactiveStatus_when_gettingApiKeyStatusAfterRevoking() throws Exception {
        String ownerAccessToken = loginAndGetAccessToken(OWNER_EMAIL, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/me/api-key")
                        .header(AUTHORIZATION, "Bearer " + ownerAccessToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/me/api-key/revoke")
                        .header(AUTHORIZATION, "Bearer " + ownerAccessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me/api-key")
                        .header(AUTHORIZATION, "Bearer " + ownerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyPrefix", notNullValue()))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void should_returnForbidden_when_nonOwnerGetsApiKeyStatus() throws Exception {
        String clientAccessToken = loginAndGetAccessToken(EMAIL, PASSWORD);

        mockMvc.perform(get("/api/v1/auth/me/api-key")
                        .header(AUTHORIZATION, "Bearer " + clientAccessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_returnUnauthorized_when_gettingApiKeyStatusWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me/api-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_selfServiceAddUser_return201WithoutTokensOrCookie_when_callerIsOwner() throws Exception {
        String ownerAccessToken = loginAndGetAccessToken(OWNER_EMAIL, PASSWORD);
        String newEmail = "self-service-employee@example.com";

        MvcResult result = mockMvc.perform(post("/api/v1/auth/me/users")
                        .header(AUTHORIZATION, "Bearer " + ownerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + newEmail + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        assertEquals(0, result.getResponse().getContentAsString().length());
        // The owner's own browser session must not be touched by creating an employee
        assertNull(result.getResponse().getHeader(SET_COOKIE));

        User createdUser = userRepository.findByEmail(newEmail).orElseThrow();
        assertEquals(merchantId, createdUser.getMerchant().getId());
        assertEquals(false, createdUser.isOwner());
    }

    @Test
    void should_returnForbidden_when_selfServiceAddUser_callerIsNotOwner() throws Exception {
        String clientAccessToken = loginAndGetAccessToken(EMAIL, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/me/users")
                        .header(AUTHORIZATION, "Bearer " + clientAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"blocked@example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_returnUnauthorized_when_selfServiceAddUserWithoutToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/me/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"anonymous@example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_returnAllUsersOfOwnMerchant_when_callerIsOwner() throws Exception {
        String ownerAccessToken = loginAndGetAccessToken(OWNER_EMAIL, PASSWORD);

        MvcResult result = mockMvc.perform(get("/api/v1/auth/me/users")
                        .header(AUTHORIZATION, "Bearer " + ownerAccessToken))
                .andExpect(status().isOk())
                .andReturn();

        MerchantUserResponse[] users = objectMapper.readValue(result.getResponse().getContentAsString(), MerchantUserResponse[].class);

        assertEquals(2, users.length);
        assertTrue(Arrays.stream(users).anyMatch(u -> u.email().equals(OWNER_EMAIL) && u.owner() && u.active()));
        assertTrue(Arrays.stream(users).anyMatch(u -> u.email().equals(EMAIL) && !u.owner() && u.active()));
    }

    @Test
    void should_notIncludeOtherMerchantsUsers_when_listingMyUsers() throws Exception {
        String adminAccessToken = loginAndGetAccessToken(VALID_EMAIL, PASSWORD);
        String otherMerchantOwnerEmail = "other-merchant-owner@example.com";

        mockMvc.perform(post("/api/v1/auth/register")
                        .header(AUTHORIZATION, "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + otherMerchantOwnerEmail + "\",\"password\":\"" + PASSWORD + "\",\"merchantName\":\"Other Merchant\"}"))
                .andExpect(status().isCreated());

        String ownerAccessToken = loginAndGetAccessToken(OWNER_EMAIL, PASSWORD);
        MvcResult result = mockMvc.perform(get("/api/v1/auth/me/users")
                        .header(AUTHORIZATION, "Bearer " + ownerAccessToken))
                .andExpect(status().isOk())
                .andReturn();

        MerchantUserResponse[] users = objectMapper.readValue(result.getResponse().getContentAsString(), MerchantUserResponse[].class);

        assertEquals(2, users.length);
        assertTrue(Arrays.stream(users).noneMatch(u -> u.email().equals(otherMerchantOwnerEmail)));
    }

    @Test
    void should_returnForbidden_when_listingUsers_callerIsNotOwner() throws Exception {
        String clientAccessToken = loginAndGetAccessToken(EMAIL, PASSWORD);

        mockMvc.perform(get("/api/v1/auth/me/users")
                        .header(AUTHORIZATION, "Bearer " + clientAccessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_returnUnauthorized_when_listingUsersWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_deactivateUserAndRevokeTokens_when_callerIsOwner() throws Exception {
        String ownerAccessToken = loginAndGetAccessToken(OWNER_EMAIL, PASSWORD);
        Long targetUserId = userRepository.findByEmail(EMAIL).orElseThrow().getId();

        mockMvc.perform(post("/api/v1/auth/me/users/" + targetUserId + "/deactivate")
                        .header(AUTHORIZATION, "Bearer " + ownerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        User deactivated = userRepository.findByEmail(EMAIL).orElseThrow();
        assertEquals(false, deactivated.isActive());

        // Deactivated user can no longer log in.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_reactivateUser_when_callerIsOwner() throws Exception {
        String ownerAccessToken = loginAndGetAccessToken(OWNER_EMAIL, PASSWORD);
        Long targetUserId = userRepository.findByEmail(EMAIL).orElseThrow().getId();

        mockMvc.perform(post("/api/v1/auth/me/users/" + targetUserId + "/deactivate")
                        .header(AUTHORIZATION, "Bearer " + ownerAccessToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/me/users/" + targetUserId + "/activate")
                        .header(AUTHORIZATION, "Bearer " + ownerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void should_returnConflict_when_ownerDeactivatesSelf() throws Exception {
        String ownerAccessToken = loginAndGetAccessToken(OWNER_EMAIL, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/me/users/" + ownerId + "/deactivate")
                        .header(AUTHORIZATION, "Bearer " + ownerAccessToken))
                .andExpect(status().isConflict());
    }

    @Test
    void should_revokeApiKey_when_ownerNeedsToCutLeakedCredentials() throws Exception {
        String adminAccessToken = loginAndGetAccessToken(VALID_EMAIL, PASSWORD);
        String newOwnerEmail = "svc-deactivate-owner@example.com";

        mockMvc.perform(post("/api/v1/auth/register")
                        .header(AUTHORIZATION, "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + newOwnerEmail + "\",\"password\":\"" + PASSWORD + "\",\"merchantName\":\"Svc Deactivate Merchant\"}"))
                .andExpect(status().isCreated());

        String newOwnerAccessToken = loginAndGetAccessToken(newOwnerEmail, PASSWORD);
        MvcResult keyResult = mockMvc.perform(post("/api/v1/auth/me/api-key")
                        .header(AUTHORIZATION, "Bearer " + newOwnerAccessToken))
                .andExpect(status().isOk())
                .andReturn();
        String apiKeySecret = objectMapper.readValue(keyResult.getResponse().getContentAsString(), ApiKeyResponse.class)
                .apiKeySecret();

        mockMvc.perform(post("/api/v1/auth/me/api-key/revoke")
                        .header(AUTHORIZATION, "Bearer " + newOwnerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/giftcards/lookup/NON-EXISTENT-CODE")
                        .header("X-Api-Key", apiKeySecret))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_returnNotFound_when_deactivatingUserNotInCallersMerchant() throws Exception {
        String ownerAccessToken = loginAndGetAccessToken(OWNER_EMAIL, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/me/users/999999/deactivate")
                        .header(AUTHORIZATION, "Bearer " + ownerAccessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_returnForbidden_when_deactivatingUser_callerIsNotOwner() throws Exception {
        String clientAccessToken = loginAndGetAccessToken(EMAIL, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/me/users/" + ownerId + "/deactivate")
                        .header(AUTHORIZATION, "Bearer " + clientAccessToken))
                .andExpect(status().isForbidden());
    }

    private static final String NEW_PASSWORD = "NewStrongP@ssw0rd";

    @Test
    void should_changePasswordAndRevokeOtherSessions_when_currentPasswordCorrect() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        AuthResponse loginResponse = objectMapper.readValue(loginResult.getResponse().getContentAsString(), AuthResponse.class);
        String refreshTokenBeforeChange = refreshTokenFromCookie(loginResult);

        mockMvc.perform(post("/api/v1/auth/me/password")
                        .header(AUTHORIZATION, "Bearer " + loginResponse.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        // Old password no longer works.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());

        // New password works.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk());

        // The refresh token issued before the change is revoked (other sessions logged out).
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie(refreshTokenBeforeChange)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_returnUnauthorized_when_currentPasswordIsWrong() throws Exception {
        String accessToken = loginAndGetAccessToken(EMAIL, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/me/password")
                        .header(AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrongPassword\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_returnUnprocessableEntity_when_newPasswordSameAsCurrentPassword() throws Exception {
        // PASSWORD itself doesn't satisfy the new-password complexity rule, so this needs a user
        // whose current password already complies, otherwise validation would 400 before the
        // same-password check ever runs.
        String ownerAccessToken = loginAndGetAccessToken(OWNER_EMAIL, PASSWORD);
        String compliantEmployeeEmail = "compliant-password-employee@example.com";
        mockMvc.perform(post("/api/v1/auth/me/users")
                        .header(AUTHORIZATION, "Bearer " + ownerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + compliantEmployeeEmail + "\",\"password\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isCreated());
        String employeeAccessToken = loginAndGetAccessToken(compliantEmployeeEmail, NEW_PASSWORD);

        mockMvc.perform(post("/api/v1/auth/me/password")
                        .header(AUTHORIZATION, "Bearer " + employeeAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + NEW_PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void should_returnBadRequest_when_newPasswordFailsComplexityRules() throws Exception {
        String accessToken = loginAndGetAccessToken(EMAIL, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/me/password")
                        .header(AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"weak\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_returnForbidden_when_changePasswordCalledViaApiKey() throws Exception {
        String ownerAccessToken = loginAndGetAccessToken(OWNER_EMAIL, PASSWORD);
        MvcResult keyResult = mockMvc.perform(post("/api/v1/auth/me/api-key")
                        .header(AUTHORIZATION, "Bearer " + ownerAccessToken))
                .andExpect(status().isOk())
                .andReturn();
        String apiKeySecret = objectMapper.readValue(keyResult.getResponse().getContentAsString(), ApiKeyResponse.class)
                .apiKeySecret();

        mockMvc.perform(post("/api/v1/auth/me/password")
                        .header("X-Api-Key", apiKeySecret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_returnUnauthorized_when_changePasswordWithoutToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_returnOwnerProfileWithMerchant_when_getMeCalledByOwner() throws Exception {
        String accessToken = loginAndGetAccessToken(OWNER_EMAIL, PASSWORD);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(ownerId))
                .andExpect(jsonPath("$.email").value(OWNER_EMAIL))
                .andExpect(jsonPath("$.role").value("MERCHANT"))
                .andExpect(jsonPath("$.owner").value(true))
                .andExpect(jsonPath("$.merchant.id").value(merchantId))
                .andExpect(jsonPath("$.merchant.name").value("Test Merchant"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void should_returnNonOwnerProfile_when_getMeCalledByEmployee() throws Exception {
        String accessToken = loginAndGetAccessToken(EMAIL, PASSWORD);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("MERCHANT"))
                .andExpect(jsonPath("$.owner").value(false))
                .andExpect(jsonPath("$.merchant.id").value(merchantId));
    }

    @Test
    void should_returnProfileWithNullMerchant_when_getMeCalledByAdmin() throws Exception {
        String accessToken = loginAndGetAccessToken(VALID_EMAIL, PASSWORD);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(VALID_EMAIL))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.owner").value(false))
                .andExpect(jsonPath("$.merchant").value(nullValue()));
    }

    @Test
    void should_returnUnauthorized_when_getMeCalledWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_returnForbidden_when_getMeCalledViaApiKey() throws Exception {
        String ownerAccessToken = loginAndGetAccessToken(OWNER_EMAIL, PASSWORD);
        MvcResult keyResult = mockMvc.perform(post("/api/v1/auth/me/api-key")
                        .header(AUTHORIZATION, "Bearer " + ownerAccessToken))
                .andExpect(status().isOk())
                .andReturn();
        String apiKeySecret = objectMapper.readValue(keyResult.getResponse().getContentAsString(), ApiKeyResponse.class)
                .apiKeySecret();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("X-Api-Key", apiKeySecret))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_returnUnauthorized_when_getMeCalledByUserDeactivatedAfterTokenIssued() throws Exception {
        String employeeAccessToken = loginAndGetAccessToken(EMAIL, PASSWORD);
        String ownerAccessToken = loginAndGetAccessToken(OWNER_EMAIL, PASSWORD);
        Long employeeId = userRepository.findByEmail(EMAIL).orElseThrow().getId();

        mockMvc.perform(post("/api/v1/auth/me/users/" + employeeId + "/deactivate")
                        .header(AUTHORIZATION, "Bearer " + ownerAccessToken))
                .andExpect(status().isOk());

        // The access token is still unexpired, but /me must not honour it for a deactivated account.
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(AUTHORIZATION, "Bearer " + employeeAccessToken))
                .andExpect(status().isUnauthorized());
    }
}
