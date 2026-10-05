package com.finovago.p2p.integration;

import java.io.IOException;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finovago.p2p.AbstractIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end verification flow: the token is read out of the email MailHog actually received. */
@Transactional
class EmailVerificationFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "securePassword123";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern TOKEN_PATTERN = Pattern.compile("Verification token: ([0-9a-fA-F-]{36})");

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        MailHogInbox.clear();
    }

    @Test
    void should_allowLogin_when_tokenFromEmailIsConfirmed() throws Exception {
        String email = "flow-verify@example.com";
        register("Flow Merchant", email);

        confirm(tokenFromLatestEmail());

        login(email).andExpect(status().isOk());
    }

    @Test
    void should_rejectReplay_when_tokenWasAlreadyConfirmed() throws Exception {
        String email = "flow-replay@example.com";
        register("Replay Merchant", email);
        String token = tokenFromLatestEmail();
        confirm(token);

        mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_invalidateEarlierToken_when_verificationIsResent() throws Exception {
        String email = "flow-resend@example.com";
        register("Resend Merchant", email);
        String firstToken = tokenFromLatestEmail();

        resend(email);
        String secondToken = tokenFromLatestEmail();
        assertNotEquals(firstToken, secondToken, "resend must issue a new token");

        mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + firstToken + "\"}"))
                .andExpect(status().isBadRequest());

        confirm(secondToken);
        login(email).andExpect(status().isOk());
    }

    @Test
    void should_sendNoEmail_when_resendIsRequestedForVerifiedAccount() throws Exception {
        String email = "flow-already-verified@example.com";
        register("Verified Merchant", email);
        confirm(tokenFromLatestEmail());
        MailHogInbox.clear();

        resend(email);

        assertEquals(0, MailHogInbox.messageCount());
    }

    @Test
    void should_letEmployeeLogIn_when_employeeVerifiesTheEmailTheOwnerTriggered() throws Exception {
        String ownerEmail = "flow-owner@example.com";
        String employeeEmail = "flow-employee@example.com";
        register("Team Merchant", ownerEmail);
        confirm(tokenFromLatestEmail());
        String ownerAccessToken = accessTokenFrom(login(ownerEmail).andExpect(status().isOk()).andReturn());
        MailHogInbox.clear();

        mockMvc.perform(post("/api/v1/auth/me/users")
                        .header(AUTHORIZATION, "Bearer " + ownerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + employeeEmail + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());

        login(employeeEmail).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Email Not Verified"));

        confirm(tokenFromLatestEmail());
        login(employeeEmail).andExpect(status().isOk());
    }

    private void register(String merchantName, String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"merchantName\":\"" + merchantName + "\"}"))
                .andExpect(status().isCreated());
    }

    private void resend(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-verification/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted());
    }

    private void confirm(String token) throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isNoContent());
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"));
    }

    private String tokenFromLatestEmail() throws IOException, InterruptedException {
        String token = MailHogInbox.tokenFromLatestEmail(TOKEN_PATTERN);
        assertNotNull(token);
        return token;
    }

    private String accessTokenFrom(MvcResult result) throws IOException {
        String body = result.getResponse().getContentAsString();
        return OBJECT_MAPPER.readTree(body).get("accessToken").asText();
    }
}
