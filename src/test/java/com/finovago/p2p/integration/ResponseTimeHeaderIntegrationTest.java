package com.finovago.p2p.integration;

import com.finovago.p2p.AbstractIntegrationTest;
import com.finovago.p2p.model.Role;
import com.finovago.p2p.model.User;
import com.finovago.p2p.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
@DisplayName("Response Time Header Integration Tests")
class ResponseTimeHeaderIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_EMAIL = "header-test@example.com";
    private static final String TEST_PASSWORD = "testPassword123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.save(new User(TEST_EMAIL, passwordEncoder.encode(TEST_PASSWORD), Role.CLIENT));
    }

    @Test
    @DisplayName("Should include X-Response-Time header in successful login response")
    void testResponseTimeHeaderInLoginSuccess() throws Exception {
        String loginJson = """
            {
                "email": "%s",
                "password": "%s"
            }
            """.formatted(TEST_EMAIL, TEST_PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        String responseTimeHeader = result.getResponse().getHeader("X-Response-Time");
        assertNotNull(responseTimeHeader, "X-Response-Time header should be present");
        assertTrue(responseTimeHeader.matches("\\d+"), "X-Response-Time should be numeric (milliseconds)");

        long duration = Long.parseLong(responseTimeHeader);
        assertTrue(duration >= 0, "Response time should be non-negative");
    }

    @Test
    @DisplayName("Should include X-Response-Time header in failed login response")
    void testResponseTimeHeaderInLoginFailure() throws Exception {
        String loginJson = """
            {
                "email": "%s",
                "password": "wrongPassword"
            }
            """.formatted(TEST_EMAIL);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String responseTimeHeader = result.getResponse().getHeader("X-Response-Time");
        assertNotNull(responseTimeHeader, "X-Response-Time header should be present even in error responses");
        assertTrue(responseTimeHeader.matches("\\d+"), "X-Response-Time should be numeric");
    }

    @Test
    @DisplayName("Should include X-Response-Time header in bad request response")
    void testResponseTimeHeaderInBadRequest() throws Exception {
        String invalidJson = """
            {
                "email": "invalid-email"
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andReturn();

        String responseTimeHeader = result.getResponse().getHeader("X-Response-Time");
        assertNotNull(responseTimeHeader, "X-Response-Time header should be present in validation error");
        assertTrue(responseTimeHeader.matches("\\d+"), "X-Response-Time should be numeric");
    }

}
