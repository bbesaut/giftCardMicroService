package com.finovago.p2p.integration;

import com.finovago.p2p.model.Role;
import com.finovago.p2p.model.User;
import com.finovago.p2p.repository.RefreshTokenRepository;
import com.finovago.p2p.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthValidationIntegrationTest {

    private static final String VALID_EMAIL = "valid@example.com";
    private static final String VALID_PASSWORD = "securePassword123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(new User(VALID_EMAIL, passwordEncoder.encode(VALID_PASSWORD), Role.CLIENT));
    }

    // ==================== Login Validation Tests ====================

    @Test
    @DisplayName("Login fails when email is missing from request")
    void should_returnBadRequest_when_emailIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + VALID_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Login fails when password is missing from request")
    void should_returnBadRequest_when_passwordIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + VALID_EMAIL + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Login fails when both fields are missing")
    void should_returnBadRequest_when_bothFieldsAreMissing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Login fails when email contains leading/trailing spaces (invalid format)")
    void should_returnBadRequest_when_emailHasLeadingTrailingSpaces() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\" " + VALID_EMAIL + " \",\"password\":\"" + VALID_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Login fails when email format is invalid")
    void should_returnBadRequest_when_emailFormatIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"" + VALID_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Login fails when email is just @")
    void should_returnBadRequest_when_emailIsJustAt() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"@\",\"password\":\"" + VALID_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Login fails with very long email")
    void should_returnBadRequest_when_emailIsTooLong() throws Exception {
        String longEmail = "a".repeat(300) + "@example.com";
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + longEmail + "\",\"password\":\"" + VALID_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Login fails with very long password")
    void should_returnUnauthorized_when_passwordIsTooLong() throws Exception {
        String longPassword = "a".repeat(10000);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + VALID_EMAIL + "\",\"password\":\"" + longPassword + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Login fails when content-type is not JSON")
    void should_returnUnsupportedMediaType_when_contentTypeIsNotJson() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<email>" + VALID_EMAIL + "</email>"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("Login fails with null values in JSON")
    void should_returnBadRequest_when_emailIsNull() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":null,\"password\":\"" + VALID_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Login fails when password is null")
    void should_returnBadRequest_when_passwordIsNull() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + VALID_EMAIL + "\",\"password\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Login fails with case-sensitive email mismatch")
    void should_returnUnauthorized_when_emailCaseIsDifferent() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + VALID_EMAIL.toUpperCase() + "\",\"password\":\"" + VALID_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Login fails when password has leading/trailing spaces")
    void should_returnUnauthorized_when_passwordHasSpaces() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + VALID_EMAIL + "\",\"password\":\" " + VALID_PASSWORD + " \"}"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== Refresh Token Validation Tests ====================

    @Test
    @DisplayName("Refresh fails when refreshToken field is missing")
    void should_returnBadRequest_when_refreshTokenFieldIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Refresh fails when refreshToken is null")
    void should_returnBadRequest_when_refreshTokenIsNull() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Refresh fails with invalid token format")
    void should_returnUnauthorized_when_refreshTokenFormatIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"@@@@invalid@@@@\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Refresh fails with random UUID string")
    void should_returnUnauthorized_when_refreshTokenIsRandomUuid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"12345678-1234-1234-1234-123456789012\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Refresh fails when refreshToken has leading/trailing spaces")
    void should_returnUnauthorized_when_refreshTokenHasSpaces() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\" valid-token-with-spaces \"}"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== Logout Validation Tests ====================

    @Test
    @DisplayName("Logout fails when refreshToken field is missing")
    void should_returnBadRequest_when_logoutRefreshTokenFieldIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Logout fails when refreshToken is null")
    void should_returnBadRequest_when_logoutRefreshTokenIsNull() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Logout fails with invalid token")
    void should_returnUnauthorized_when_logoutWithInvalidRefreshToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"invalid-refresh-token\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== Malformed Request Tests ====================

    @Test
    @DisplayName("Login fails with incomplete JSON")
    void should_returnBadRequest_when_jsonIsIncomplete() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + VALID_EMAIL + "\",\"password\":\"pass\""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Login fails with empty JSON body")
    void should_returnBadRequest_when_bodyIsEmpty() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Login fails with extra fields in request")
    void should_ignoreExtraFieldsInLogin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + VALID_EMAIL + "\",\"password\":\"" + VALID_PASSWORD + "\",\"extra\":\"field\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Refresh fails with malformed JSON array instead of object")
    void should_returnBadRequest_when_refreshPayloadIsArray() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"refreshToken\":\"token\"}]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Logout fails when payload is not JSON")
    void should_returnBadRequest_when_logoutPayloadIsNotJson() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not a json"))
                .andExpect(status().isBadRequest());
    }
}
