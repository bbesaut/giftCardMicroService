package com.finovago.p2p.integration;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.finovago.p2p.AbstractIntegrationTest;
import com.finovago.p2p.config.PostgresTestcontainerInitializer;
import com.finovago.p2p.dto.AuthResponse;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.model.Role;
import com.finovago.p2p.model.User;
import com.finovago.p2p.repository.GiftCardRepository;
import com.finovago.p2p.repository.IdempotencyKeyRepository;
import com.finovago.p2p.repository.MerchantRepository;
import com.finovago.p2p.repository.RefreshTokenRepository;
import com.finovago.p2p.repository.UserRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * Not @Transactional: same reasoning as GiftCardTenantIsolationIntegrationTest - one test here
 * (should_blockRedeem_after_deactivation) exercises redeem, which runs on a separate thread pool
 * and wouldn't see an uncommitted transaction. Rows are cleaned up manually in setUp() instead.
 */
class GiftCardActivationIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "securePassword123";
    private static final String CARD_CODE = "ACTV-CODE-001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private GiftCardRepository giftCardRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JsonMapper objectMapper;

    private String merchantAToken;
    private String merchantBToken;

    @BeforeEach
    void setUp() throws Exception {
        idempotencyKeyRepository.deleteAll();
        PostgresTestcontainerInitializer.executeAsMigrator("TRUNCATE TABLE gift_card_ledger RESTART IDENTITY");
        giftCardRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        merchantRepository.deleteAll();

        Merchant merchantA = merchantRepository.save(new Merchant("Merchant A", "a@example.com"));
        Merchant merchantB = merchantRepository.save(new Merchant("Merchant B", "b@example.com"));

        userRepository.save(TestUsers.verified("usera@example.com", passwordEncoder.encode(PASSWORD), Role.MERCHANT, merchantA));
        userRepository.save(TestUsers.verified("userb@example.com", passwordEncoder.encode(PASSWORD), Role.MERCHANT, merchantB));

        merchantAToken = loginAndGetAccessToken("usera@example.com");
        merchantBToken = loginAndGetAccessToken("userb@example.com");
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

    private void createCard(String token, String code, boolean active) throws Exception {
        mockMvc.perform(post("/api/v1/giftcards/create")
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"giftCardCode\":\"" + code + "\",\"balance\":100.0,\"active\":" + active + "}"))
                .andExpect(status().isCreated());
    }

    @Test
    void should_deactivateOwnCard_and_returnInactiveStatus() throws Exception {
        createCard(merchantAToken, CARD_CODE, true);

        mockMvc.perform(post("/api/v1/giftcards/" + CARD_CODE + "/deactivate")
                        .header(AUTHORIZATION, "Bearer " + merchantAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.giftCardCode").value(CARD_CODE))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/v1/giftcards/lookup/" + CARD_CODE)
                        .header(AUTHORIZATION, "Bearer " + merchantAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void should_reactivateOwnCard_and_returnActiveStatus() throws Exception {
        createCard(merchantAToken, CARD_CODE, false);

        mockMvc.perform(post("/api/v1/giftcards/" + CARD_CODE + "/activate")
                        .header(AUTHORIZATION, "Bearer " + merchantAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.giftCardCode").value(CARD_CODE))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get("/api/v1/giftcards/lookup/" + CARD_CODE)
                        .header(AUTHORIZATION, "Bearer " + merchantAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void should_replaySameStatus_when_deactivatingAlreadyInactiveCard() throws Exception {
        createCard(merchantAToken, CARD_CODE, true);

        mockMvc.perform(post("/api/v1/giftcards/" + CARD_CODE + "/deactivate")
                        .header(AUTHORIZATION, "Bearer " + merchantAToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/giftcards/" + CARD_CODE + "/deactivate")
                        .header(AUTHORIZATION, "Bearer " + merchantAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void should_returnNotFound_when_deactivatingNonExistentCard() throws Exception {
        mockMvc.perform(post("/api/v1/giftcards/NONEXISTENT/deactivate")
                        .header(AUTHORIZATION, "Bearer " + merchantAToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Gift card not found"));
    }

    @Test
    void should_returnNotFound_when_merchantDeactivates_anotherMerchantsCard() throws Exception {
        createCard(merchantAToken, CARD_CODE, true);

        mockMvc.perform(post("/api/v1/giftcards/" + CARD_CODE + "/deactivate")
                        .header(AUTHORIZATION, "Bearer " + merchantBToken))
                .andExpect(status().isNotFound());

        // Confirms merchant B's failed attempt left merchant A's card untouched.
        mockMvc.perform(get("/api/v1/giftcards/lookup/" + CARD_CODE)
                        .header(AUTHORIZATION, "Bearer " + merchantAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void should_blockRedeem_after_deactivation() throws Exception {
        createCard(merchantAToken, CARD_CODE, true);

        mockMvc.perform(post("/api/v1/giftcards/" + CARD_CODE + "/deactivate")
                        .header(AUTHORIZATION, "Bearer " + merchantAToken))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(post("/api/v1/giftcards/redeem")
                        .header(AUTHORIZATION, "Bearer " + merchantAToken)
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"giftCardCode\":\"" + CARD_CODE + "\",\"amount\":10.0}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Card is already inactive"));
    }

    @Test
    void should_notWriteLedgerEntry_when_deactivating() throws Exception {
        createCard(merchantAToken, CARD_CODE, true);

        mockMvc.perform(post("/api/v1/giftcards/" + CARD_CODE + "/deactivate")
                        .header(AUTHORIZATION, "Bearer " + merchantAToken))
                .andExpect(status().isOk());

        // Only the CREATION entry from createCard() should exist - deactivate is not ledgered.
        mockMvc.perform(get("/api/v1/giftcards/" + CARD_CODE + "/ledger")
                        .header(AUTHORIZATION, "Bearer " + merchantAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].entryType").value("CREATION"));
    }
}
