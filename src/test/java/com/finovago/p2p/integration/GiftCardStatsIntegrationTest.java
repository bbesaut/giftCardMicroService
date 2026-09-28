package com.finovago.p2p.integration;

import java.time.LocalDate;
import java.util.UUID;

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
import com.finovago.p2p.dto.HoldResponse;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.model.Role;
import com.finovago.p2p.model.User;
import com.finovago.p2p.repository.GiftCardHoldRepository;
import com.finovago.p2p.repository.GiftCardRepository;
import com.finovago.p2p.repository.IdempotencyKeyRepository;
import com.finovago.p2p.repository.MerchantRepository;
import com.finovago.p2p.repository.RefreshTokenRepository;
import com.finovago.p2p.repository.UserRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * Not @Transactional: redemption runs on a separate thread pool (see
 * GiftCardService#redeemGiftCardAsync), so a test-managed transaction bound to the main thread
 * would be invisible to it. Rows are cleaned up manually in setUp() instead.
 */
class GiftCardStatsIntegrationTest extends AbstractIntegrationTest {

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
    private GiftCardHoldRepository giftCardHoldRepository;

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
        giftCardHoldRepository.deleteAll();
        PostgresTestcontainerInitializer.executeAsMigrator("TRUNCATE TABLE gift_card_ledger RESTART IDENTITY");
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

    private void createCard(String token, String code, double balance, boolean active) throws Exception {
        mockMvc.perform(post("/api/v1/giftcards/create")
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"giftCardCode\":\"" + code + "\",\"balance\":" + balance + ",\"active\":" + active + "}"))
                .andExpect(status().isCreated());
    }

    private void redeem(String token, String code, double amount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/giftcards/redeem")
                        .header(AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"giftCardCode\":\"" + code + "\",\"amount\":" + amount + "}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isAccepted());
    }

    private void reserveAndCapture(String token, String code, double amount) throws Exception {
        MvcResult reserveResult = mockMvc.perform(post("/api/v1/giftcards/reserve")
                        .header(AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"giftCardCode\":\"" + code + "\",\"amount\":" + amount + "}"))
                .andExpect(status().isCreated())
                .andReturn();

        Long holdId = objectMapper.readValue(reserveResult.getResponse().getContentAsString(), HoldResponse.class).holdId();

        mockMvc.perform(post("/api/v1/giftcards/holds/" + holdId + "/capture")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void should_returnActiveBalanceAndCardCounts_scopedToCallerMerchant() throws Exception {
        createCard(merchantAToken, "STATS-A1", 100.0, true);
        createCard(merchantAToken, "STATS-A2", 50.0, true);
        createCard(merchantAToken, "STATS-A3", 30.0, false);

        mockMvc.perform(get("/api/v1/giftcards/stats")
                        .header(AUTHORIZATION, "Bearer " + merchantAToken)
                        .param("days", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalActiveBalance").value(150.0))
                .andExpect(jsonPath("$.activeCards").value(2))
                .andExpect(jsonPath("$.inactiveCards").value(1));
    }

    @Test
    void should_aggregateDirectRedemptionAndCapturedHold_intoTodaysStats() throws Exception {
        createCard(merchantAToken, "STATS-B1", 200.0, true);
        redeem(merchantAToken, "STATS-B1", 30.0);
        reserveAndCapture(merchantAToken, "STATS-B1", 20.0);

        mockMvc.perform(get("/api/v1/giftcards/stats")
                        .header(AUTHORIZATION, "Bearer " + merchantAToken)
                        .param("days", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRedeemedInPeriod").value(50.0))
                .andExpect(jsonPath("$.redemptionsPerDay.length()").value(1))
                .andExpect(jsonPath("$.redemptionsPerDay[0].date").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.redemptionsPerDay[0].count").value(2))
                .andExpect(jsonPath("$.redemptionsPerDay[0].totalAmount").value(50.0));
    }

    @Test
    void should_zeroFillDaysWithNoActivity_overTheRequestedWindow() throws Exception {
        createCard(merchantAToken, "STATS-C1", 100.0, true);

        mockMvc.perform(get("/api/v1/giftcards/stats")
                        .header(AUTHORIZATION, "Bearer " + merchantAToken)
                        .param("days", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redemptionsPerDay.length()").value(3))
                .andExpect(jsonPath("$.redemptionsPerDay[0].date").value(LocalDate.now().minusDays(2).toString()))
                .andExpect(jsonPath("$.redemptionsPerDay[0].count").value(0))
                .andExpect(jsonPath("$.redemptionsPerDay[0].totalAmount").value(0.0))
                .andExpect(jsonPath("$.redemptionsPerDay[2].date").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.redemptionsPerDay[2].count").value(0));
    }

    @Test
    void should_notLeakOtherMerchantsActivity_intoCallersStats() throws Exception {
        createCard(merchantAToken, "STATS-D1", 100.0, true);
        redeem(merchantAToken, "STATS-D1", 10.0);

        mockMvc.perform(get("/api/v1/giftcards/stats")
                        .header(AUTHORIZATION, "Bearer " + merchantBToken)
                        .param("days", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalActiveBalance").value(0.0))
                .andExpect(jsonPath("$.activeCards").value(0))
                .andExpect(jsonPath("$.inactiveCards").value(0))
                .andExpect(jsonPath("$.totalRedeemedInPeriod").value(0.0))
                .andExpect(jsonPath("$.redemptionsPerDay[0].count").value(0));
    }

    @Test
    void should_rejectRequest_when_daysParameterIsBelowMinimum() throws Exception {
        mockMvc.perform(get("/api/v1/giftcards/stats")
                        .header(AUTHORIZATION, "Bearer " + merchantAToken)
                        .param("days", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_rejectRequest_when_daysParameterExceedsMaximum() throws Exception {
        mockMvc.perform(get("/api/v1/giftcards/stats")
                        .header(AUTHORIZATION, "Bearer " + merchantAToken)
                        .param("days", "91"))
                .andExpect(status().isBadRequest());
    }
}
