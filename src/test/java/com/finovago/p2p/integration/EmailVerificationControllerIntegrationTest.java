package com.finovago.p2p.integration;

import com.finovago.p2p.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the public routing, validation and error mapping of the email verification endpoints. The
 * full happy path (reading the token from the email) lives in the MailHog-backed integration test.
 */
class EmailVerificationControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void should_returnAccepted_when_resendIsRequestedForUnknownEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-verification/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"unknown@example.com\"}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void should_returnBadRequest_when_resendEmailIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-verification/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_returnBadRequestWithErrorBody_when_confirmTokenIsUnknown() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"not-a-real-token\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid or expired verification token"));
    }

    @Test
    void should_returnBadRequest_when_confirmTokenIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
