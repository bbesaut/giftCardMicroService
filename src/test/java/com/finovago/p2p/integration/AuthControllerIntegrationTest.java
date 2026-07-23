package com.finovago.p2p.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finovago.p2p.AbstractIntegrationTest;
import com.finovago.p2p.dto.AuthResponse;
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
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class AuthControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "controller-test@example.com";
    private static final String PASSWORD = "securePassword123";
    private static final String VALID_EMAIL = "valid@example.com";

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

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        // gift_card has a FK to merchants; other integration test classes (e.g.
        // GiftCardServiceIntegrationTest) are non-transactional and commit rows that outlive this
        // class, so merchants must not be deleted while leftover gift cards still reference them.
        giftCardRepository.deleteAll();
        merchantRepository.deleteAll();
        Merchant merchant = merchantRepository.save(new Merchant("Test Merchant", "merchant@example.com"));
        userRepository.save(new User(EMAIL, passwordEncoder.encode(PASSWORD), Role.MERCHANT, merchant));
        userRepository.save(new User(VALID_EMAIL, passwordEncoder.encode(PASSWORD), Role.ADMIN, null));
    }

    @Test
    void should_loginSuccessfully_and_returnAccessAndRefreshTokens() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()));
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
    void should_refreshTokenSuccessfully_and_returnNewTokens() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String loginResponse = loginResult.getResponse().getContentAsString();
        AuthResponse authResponse = objectMapper.readValue(loginResponse, AuthResponse.class);
        String refreshToken = authResponse.refreshToken();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()));
    }

    @Test
    void should_returnUnauthorized_when_refreshTokenIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"invalid-refresh-token\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_returnBadRequest_when_refreshTokenIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_logoutSuccessfully_and_returnNoContent() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String loginResponse = loginResult.getResponse().getContentAsString();
        AuthResponse authResponse = objectMapper.readValue(loginResponse, AuthResponse.class);
        String refreshToken = authResponse.refreshToken();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void should_rejectRefreshToken_after_logout() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String loginResponse = loginResult.getResponse().getContentAsString();
        AuthResponse authResponse = objectMapper.readValue(loginResponse, AuthResponse.class);
        String refreshToken = authResponse.refreshToken();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_returnUnauthorized_when_logoutWithInvalidToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"invalid-token\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_returnBadRequest_when_logoutWithBlankToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_handleMultipleLoginAttempts() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken", notNullValue()))
                    .andExpect(jsonPath("$.refreshToken", notNullValue()));
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
        assertNotNull(adminAuthResponse.refreshToken());

        MvcResult clientLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String clientResponse = clientLogin.getResponse().getContentAsString();
        AuthResponse clientAuthResponse = objectMapper.readValue(clientResponse, AuthResponse.class);

        assertNotNull(clientAuthResponse.accessToken());
        assertNotNull(clientAuthResponse.refreshToken());
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

        // Each login should generate valid tokens
        assertNotNull(auth1.accessToken());
        assertNotNull(auth1.refreshToken());
        assertNotNull(auth2.accessToken());
        assertNotNull(auth2.refreshToken());

        // Refresh tokens should be different (they are UUIDs)
        assertEquals(false, auth1.refreshToken().equals(auth2.refreshToken()));
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
    void should_registerSuccessfully_and_returnAccessAndRefreshTokens() throws Exception {
        String adminAccessToken = loginAndGetAccessToken(VALID_EMAIL, PASSWORD);
        String newEmail = "newuser@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .header(AUTHORIZATION, "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + newEmail + "\",\"password\":\"" + PASSWORD + "\",\"merchantName\":\"New Merchant\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()));

        // Verify user was created
        assertNotNull(userRepository.findByEmail(newEmail));
    }

    @Test
    void should_returnUnauthorized_when_registeringWithoutToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"anonymous@example.com\",\"password\":\"" + PASSWORD + "\",\"merchantName\":\"New Merchant\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_returnForbidden_when_registeringAsClient() throws Exception {
        String clientAccessToken = loginAndGetAccessToken(EMAIL, PASSWORD);
        mockMvc.perform(post("/api/v1/auth/register")
                        .header(AUTHORIZATION, "Bearer " + clientAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"blockedclient@example.com\",\"password\":\"" + PASSWORD + "\",\"merchantName\":\"New Merchant\"}"))
                .andExpect(status().isForbidden());
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
                .andExpect(status().isOk());

        User createdUser = userRepository.findByEmail(newEmail).orElseThrow();
        assertEquals(Role.MERCHANT, createdUser.getRole());
        assertNotNull(createdUser.getMerchant());
        assertEquals("New Merchant", createdUser.getMerchant().getName());
    }
}
