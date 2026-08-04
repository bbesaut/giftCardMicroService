package com.finovago.p2p.integration;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finovago.p2p.AbstractIntegrationTest;
import com.finovago.p2p.dto.AuthResponse;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.model.Role;
import com.finovago.p2p.model.User;
import com.finovago.p2p.repository.GiftCardRepository;
import com.finovago.p2p.repository.IdempotencyKeyRepository;
import com.finovago.p2p.repository.LedgerEntryRepository;
import com.finovago.p2p.repository.MerchantRepository;
import com.finovago.p2p.repository.RefreshTokenRepository;
import com.finovago.p2p.repository.UserRepository;

/**
 * Proves that monetary amounts with more than 2 decimal places are rejected at the API boundary
 * rather than silently rounded - the DB column is NUMERIC(19,2), so any precision beyond that
 * would otherwise be dropped between what the client sent and what actually gets persisted.
 */
class GiftCardMoneyValidationIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "securePassword123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private GiftCardRepository giftCardRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private String merchantToken;

    @BeforeEach
    void setUp() throws Exception {
        idempotencyKeyRepository.deleteAll();
        ledgerEntryRepository.deleteAll();
        giftCardRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        merchantRepository.deleteAll();

        Merchant merchant = merchantRepository.save(new Merchant("Test Merchant", "merchant@example.com"));
        userRepository.save(new User("merchant@example.com", passwordEncoder.encode(PASSWORD), Role.MERCHANT, merchant));

        merchantToken = loginAndGetAccessToken("merchant@example.com");
    }

    private String loginAndGetAccessToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        return objectMapper.readValue(body, AuthResponse.class).accessToken();
    }

    @Test
    void should_rejectCreate_when_balanceHasMoreThanTwoDecimalPlaces() throws Exception {
        mockMvc.perform(post("/api/v1/giftcards/create")
                        .header(AUTHORIZATION, "Bearer " + merchantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"giftCardCode\":\"PRECISE-1\",\"balance\":99.999,\"active\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_acceptCreate_when_balanceHasExactlyTwoDecimalPlaces() throws Exception {
        mockMvc.perform(post("/api/v1/giftcards/create")
                        .header(AUTHORIZATION, "Bearer " + merchantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"giftCardCode\":\"PRECISE-2\",\"balance\":99.99,\"active\":true}"))
                .andExpect(status().isCreated());
    }

    @Test
    void should_rejectRedeem_when_amountHasMoreThanTwoDecimalPlaces() throws Exception {
        mockMvc.perform(post("/api/v1/giftcards/create")
                        .header(AUTHORIZATION, "Bearer " + merchantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"giftCardCode\":\"PRECISE-3\",\"balance\":100.0,\"active\":true}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/giftcards/redeem")
                        .header(AUTHORIZATION, "Bearer " + merchantToken)
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"giftCardCode\":\"PRECISE-3\",\"amount\":10.001}"))
                .andExpect(status().isBadRequest());
    }
}
