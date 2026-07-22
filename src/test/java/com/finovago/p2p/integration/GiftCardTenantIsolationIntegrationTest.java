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

/**
 * Proves that gift cards are isolated per merchant: two merchants can use the same card code
 * without colliding, and neither can see or redeem the other's cards through the public API.
 *
 * Not @Transactional: redemption runs on a separate thread pool (see
 * GiftCardService#redeemGiftCardAsync), so a test-managed transaction bound to the main thread
 * would be invisible to it. Rows are cleaned up manually in setUp() instead.
 */
class GiftCardTenantIsolationIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "securePassword123";
    private static final String SHARED_CODE = "SHARED-CODE-001";

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

    private String merchantAToken;
    private String merchantBToken;

    @BeforeEach
    void setUp() throws Exception {
        giftCardRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        merchantRepository.deleteAll();

        Merchant merchantA = merchantRepository.save(new Merchant("Merchant A", "a@example.com"));
        Merchant merchantB = merchantRepository.save(new Merchant("Merchant B", "b@example.com"));

        userRepository.save(new User("usera@example.com", passwordEncoder.encode(PASSWORD), Role.MERCHANT, merchantA));
        userRepository.save(new User("userb@example.com", passwordEncoder.encode(PASSWORD), Role.MERCHANT, merchantB));

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

    @Test
    void should_allowBothMerchants_to_useTheSameCardCode() throws Exception {
        mockMvc.perform(post("/api/v1/giftcards/create")
                        .header(AUTHORIZATION, "Bearer " + merchantAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"giftCardCode\":\"" + SHARED_CODE + "\",\"balance\":100.0,\"active\":true}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/giftcards/create")
                        .header(AUTHORIZATION, "Bearer " + merchantBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"giftCardCode\":\"" + SHARED_CODE + "\",\"balance\":200.0,\"active\":true}"))
                .andExpect(status().isCreated());
    }

    @Test
    void should_returnNotFound_when_merchantLooksUp_anotherMerchantsCard() throws Exception {
        mockMvc.perform(post("/api/v1/giftcards/create")
                        .header(AUTHORIZATION, "Bearer " + merchantAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"giftCardCode\":\"" + SHARED_CODE + "\",\"balance\":100.0,\"active\":true}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/giftcards/lookup/" + SHARED_CODE)
                        .header(AUTHORIZATION, "Bearer " + merchantBToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_returnNotFound_when_merchantRedeems_anotherMerchantsCard() throws Exception {
        mockMvc.perform(post("/api/v1/giftcards/create")
                        .header(AUTHORIZATION, "Bearer " + merchantAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"giftCardCode\":\"" + SHARED_CODE + "\",\"balance\":100.0,\"active\":true}"))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/api/v1/giftcards/redeem")
                        .header(AUTHORIZATION, "Bearer " + merchantBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"giftCardCode\":\"" + SHARED_CODE + "\",\"amount\":10.0}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Gift card not found"));
    }

    @Test
    void should_returnSuccessBody_when_merchantRedeemsOwnCard() throws Exception {
        mockMvc.perform(post("/api/v1/giftcards/create")
                        .header(AUTHORIZATION, "Bearer " + merchantAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"giftCardCode\":\"" + SHARED_CODE + "\",\"balance\":100.0,\"active\":true}"))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/api/v1/giftcards/redeem")
                        .header(AUTHORIZATION, "Bearer " + merchantAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"giftCardCode\":\"" + SHARED_CODE + "\",\"amount\":10.0}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.deductedAmount").value(10.0))
                .andExpect(jsonPath("$.remainingBalance").value(90.0));
    }
}
