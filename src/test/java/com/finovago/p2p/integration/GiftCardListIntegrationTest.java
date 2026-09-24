package com.finovago.p2p.integration;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

// Exercises GET /api/v1/giftcards end to end against real Postgres: this is what actually proves
// the Specification-built WHERE, the Pageable-driven LIMIT/OFFSET/ORDER BY and the tenant scoping
// produce correct SQL, not just that the right Java objects get passed around (already covered by
// GiftCardServiceUnitTest/GiftCardSpecificationsUnitTest with mocks).
class GiftCardListIntegrationTest extends AbstractIntegrationTest {

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
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JsonMapper objectMapper;

    private String merchantAToken;

    @BeforeEach
    void setUp() throws Exception {
        // Children before merchants (gift_card, idempotency_key and users all have a FK to merchants) -
        // see GiftCardServiceIntegrationTest for the same pattern.
        idempotencyKeyRepository.deleteAll();
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
        String merchantBToken = loginAndGetAccessToken("userb@example.com");

        createCard(merchantAToken, "GC-APPLE", 100.0, true);
        createCard(merchantAToken, "GC-BANANA", 50.0, true);
        createCard(merchantAToken, "GC-CHERRY", 200.0, false);
        createCard(merchantBToken, "GC-OTHER", 999.0, true);
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

    @Test
    void should_returnOnlyCallersOwnCards_sortedByCodeByDefault() throws Exception {
        mockMvc.perform(get("/api/v1/giftcards").header(AUTHORIZATION, "Bearer " + merchantAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].giftCardCode").value("GC-APPLE"))
                .andExpect(jsonPath("$.content[1].giftCardCode").value("GC-BANANA"))
                .andExpect(jsonPath("$.content[2].giftCardCode").value("GC-CHERRY"));
    }

    @Test
    void should_filterByActiveStatus() throws Exception {
        mockMvc.perform(get("/api/v1/giftcards").param("active", "true").header(AUTHORIZATION, "Bearer " + merchantAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].giftCardCode").value("GC-APPLE"))
                .andExpect(jsonPath("$.content[1].giftCardCode").value("GC-BANANA"));
    }

    @Test
    void should_filterByCodeSubstring_caseInsensitive() throws Exception {
        mockMvc.perform(get("/api/v1/giftcards").param("code", "an").header(AUTHORIZATION, "Bearer " + merchantAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].giftCardCode").value("GC-BANANA"));
    }

    @Test
    void should_paginateResults_withoutOverlapBetweenPages() throws Exception {
        mockMvc.perform(get("/api/v1/giftcards").param("page", "0").param("size", "2").header(AUTHORIZATION, "Bearer " + merchantAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].giftCardCode").value("GC-APPLE"))
                .andExpect(jsonPath("$.content[1].giftCardCode").value("GC-BANANA"));

        mockMvc.perform(get("/api/v1/giftcards").param("page", "1").param("size", "2").header(AUTHORIZATION, "Bearer " + merchantAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].giftCardCode").value("GC-CHERRY"));
    }

    @Test
    void should_sortByRequestedFieldAndDirection() throws Exception {
        mockMvc.perform(get("/api/v1/giftcards")
                        .param("sortBy", "BALANCE")
                        .param("sortDirection", "DESC")
                        .header(AUTHORIZATION, "Bearer " + merchantAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].giftCardCode").value("GC-CHERRY"))
                .andExpect(jsonPath("$.content[1].giftCardCode").value("GC-APPLE"))
                .andExpect(jsonPath("$.content[2].giftCardCode").value("GC-BANANA"));
    }

    @Test
    void should_returnBadRequest_whenSizeExceedsMax() throws Exception {
        // Regression: the message must always be in English (not the host JVM's default locale)
        // and name the parameter itself ("size"), not the internal "listMyGiftCards.size" path.
        mockMvc.perform(get("/api/v1/giftcards").param("size", "101").header(AUTHORIZATION, "Bearer " + merchantAToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("size: must be less than or equal to 100"));
    }

    @Test
    void should_returnBadRequest_whenPageIsNegative() throws Exception {
        mockMvc.perform(get("/api/v1/giftcards").param("page", "-1").header(AUTHORIZATION, "Bearer " + merchantAToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("page: must be greater than or equal to 0"));
    }

    @Test
    void should_returnBadRequest_whenSortByIsNotARecognizedField() throws Exception {
        mockMvc.perform(get("/api/v1/giftcards").param("sortBy", "NOPE").header(AUTHORIZATION, "Bearer " + merchantAToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void should_returnForbidden_whenCallerIsAdmin() throws Exception {
        userRepository.save(new User("admin@example.com", passwordEncoder.encode(PASSWORD), Role.ADMIN, null));
        String adminToken = loginAndGetAccessToken("admin@example.com");

        mockMvc.perform(get("/api/v1/giftcards").header(AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }
}
