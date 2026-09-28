package com.finovago.p2p.integration;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.finovago.p2p.AbstractIntegrationTest;
import com.finovago.p2p.config.PostgresTestcontainerInitializer;
import com.finovago.p2p.dto.ApiKeyResponse;
import com.finovago.p2p.dto.AuthResponse;
import com.finovago.p2p.model.GiftCard;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.model.Role;
import com.finovago.p2p.model.User;
import com.finovago.p2p.repository.GiftCardRepository;
import com.finovago.p2p.repository.IdempotencyKeyRepository;
import com.finovago.p2p.repository.MerchantRepository;
import com.finovago.p2p.repository.RefreshTokenRepository;
import com.finovago.p2p.repository.UserRepository;

import tools.jackson.databind.json.JsonMapper;

// Exercises the ADMIN merchant-management endpoints end to end: not just that the right HTTP status
// comes back, but that deactivating a merchant actually cuts off login, refresh and API-key auth for
// its users - the part that can't be verified by GiftCardListIntegrationTest's or the unit tests' mocks.
@Transactional
class AdminMerchantControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "securePassword123";
    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String OWNER_EMAIL = "ownerA@example.com";
    private static final String EMPLOYEE_EMAIL = "employeeA@example.com";

    private Merchant merchantA;
    private Merchant merchantB;
    private Long merchantAId;
    private Long merchantBId;

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

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        PostgresTestcontainerInitializer.executeAsMigrator("TRUNCATE TABLE gift_card_ledger RESTART IDENTITY");
        giftCardRepository.deleteAll();
        merchantRepository.deleteAll();

        merchantA = merchantRepository.save(new Merchant("Merchant A", "a@example.com"));
        merchantB = merchantRepository.save(new Merchant("Merchant B", "b@example.com"));
        merchantAId = merchantA.getId();
        merchantBId = merchantB.getId();

        userRepository.save(new User(ADMIN_EMAIL, passwordEncoder.encode(PASSWORD), Role.ADMIN, null));
        userRepository.save(new User(OWNER_EMAIL, passwordEncoder.encode(PASSWORD), Role.MERCHANT, merchantA, true));
        userRepository.save(new User(EMPLOYEE_EMAIL, passwordEncoder.encode(PASSWORD), Role.MERCHANT, merchantA, false));
    }

    private String loginAndGetAccessToken(String email) throws Exception {
        return login(email).accessToken();
    }

    private AuthResponse login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }

    @Test
    void should_listAllMerchants_when_callerIsAdmin() throws Exception {
        String adminToken = loginAndGetAccessToken(ADMIN_EMAIL);

        mockMvc.perform(get("/api/v1/admin/merchants").header(AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].merchantId").value(merchantAId))
                .andExpect(jsonPath("$[0].name").value("Merchant A"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].rateLimitCapacity").doesNotExist())
                .andExpect(jsonPath("$[1].merchantId").value(merchantBId));
    }

    @Test
    void should_returnForbidden_when_nonAdminListsMerchants() throws Exception {
        String ownerToken = loginAndGetAccessToken(OWNER_EMAIL);

        mockMvc.perform(get("/api/v1/admin/merchants").header(AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_returnUnauthorized_when_listingMerchantsWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/admin/merchants"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_listMerchantUsers_when_callerIsAdmin() throws Exception {
        String adminToken = loginAndGetAccessToken(ADMIN_EMAIL);

        mockMvc.perform(get("/api/v1/admin/merchants/" + merchantAId + "/users").header(AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].email").value(OWNER_EMAIL))
                .andExpect(jsonPath("$[0].owner").value(true))
                .andExpect(jsonPath("$[1].email").value(EMPLOYEE_EMAIL))
                .andExpect(jsonPath("$[1].owner").value(false));
    }

    @Test
    void should_returnNotFound_when_listingUsersOfUnknownMerchant() throws Exception {
        String adminToken = loginAndGetAccessToken(ADMIN_EMAIL);

        mockMvc.perform(get("/api/v1/admin/merchants/999999/users").header(AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_returnForbidden_when_nonAdminListsMerchantUsers() throws Exception {
        String ownerToken = loginAndGetAccessToken(OWNER_EMAIL);

        mockMvc.perform(get("/api/v1/admin/merchants/" + merchantAId + "/users").header(AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_listMerchantGiftCards_scopedToThatMerchantOnly_when_callerIsAdmin() throws Exception {
        giftCardRepository.save(new GiftCard(merchantA, "GC-A1", BigDecimal.valueOf(100.0), true, LocalDate.now().plusYears(1)));
        giftCardRepository.save(new GiftCard(merchantA, "GC-A2", BigDecimal.valueOf(50.0), true, LocalDate.now().plusYears(1)));
        giftCardRepository.save(new GiftCard(merchantB, "GC-B1", BigDecimal.valueOf(999.0), true, LocalDate.now().plusYears(1)));
        String adminToken = loginAndGetAccessToken(ADMIN_EMAIL);

        mockMvc.perform(get("/api/v1/admin/merchants/" + merchantAId + "/giftcards").header(AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].giftCardCode").value("GC-A1"))
                .andExpect(jsonPath("$.content[0].merchantId").value(merchantAId))
                .andExpect(jsonPath("$.content[1].giftCardCode").value("GC-A2"));
    }

    @Test
    void should_filterMerchantGiftCardsByActiveStatus_when_callerIsAdmin() throws Exception {
        giftCardRepository.save(new GiftCard(merchantA, "GC-ACTIVE", BigDecimal.valueOf(100.0), true, LocalDate.now().plusYears(1)));
        giftCardRepository.save(new GiftCard(merchantA, "GC-INACTIVE", BigDecimal.valueOf(50.0), false, LocalDate.now().plusYears(1)));
        String adminToken = loginAndGetAccessToken(ADMIN_EMAIL);

        mockMvc.perform(get("/api/v1/admin/merchants/" + merchantAId + "/giftcards")
                        .param("active", "true")
                        .header(AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].giftCardCode").value("GC-ACTIVE"));
    }

    @Test
    void should_returnNotFound_when_listingGiftCardsOfUnknownMerchant() throws Exception {
        String adminToken = loginAndGetAccessToken(ADMIN_EMAIL);

        mockMvc.perform(get("/api/v1/admin/merchants/999999/giftcards").header(AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_returnForbidden_when_nonAdminListsMerchantGiftCards() throws Exception {
        String ownerToken = loginAndGetAccessToken(OWNER_EMAIL);

        mockMvc.perform(get("/api/v1/admin/merchants/" + merchantAId + "/giftcards").header(AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_returnUnauthorized_when_listingMerchantGiftCardsWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/admin/merchants/" + merchantAId + "/giftcards"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_deactivateMerchant_and_blockLoginForItsUsers() throws Exception {
        String adminToken = loginAndGetAccessToken(ADMIN_EMAIL);

        mockMvc.perform(post("/api/v1/admin/merchants/" + merchantAId + "/deactivate").header(AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + OWNER_EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_deactivateMerchant_and_revokeRefreshTokensForAllItsUsers() throws Exception {
        AuthResponse ownerAuth = login(OWNER_EMAIL);
        AuthResponse employeeAuth = login(EMPLOYEE_EMAIL);
        String adminToken = loginAndGetAccessToken(ADMIN_EMAIL);

        mockMvc.perform(post("/api/v1/admin/merchants/" + merchantAId + "/deactivate").header(AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + ownerAuth.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + employeeAuth.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_deactivateMerchant_and_blockItsApiKey() throws Exception {
        String ownerToken = loginAndGetAccessToken(OWNER_EMAIL);
        MvcResult keyResult = mockMvc.perform(post("/api/v1/auth/me/api-key").header(AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        String apiKeySecret = objectMapper.readValue(keyResult.getResponse().getContentAsString(), ApiKeyResponse.class).apiKeySecret();

        String adminToken = loginAndGetAccessToken(ADMIN_EMAIL);
        mockMvc.perform(post("/api/v1/admin/merchants/" + merchantAId + "/deactivate").header(AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/giftcards/lookup/NON-EXISTENT-CODE").header("X-Api-Key", apiKeySecret))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_reactivateMerchant_and_allowLoginAgain() throws Exception {
        String adminToken = loginAndGetAccessToken(ADMIN_EMAIL);

        mockMvc.perform(post("/api/v1/admin/merchants/" + merchantAId + "/deactivate").header(AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/merchants/" + merchantAId + "/activate").header(AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + OWNER_EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void should_returnNotFound_when_deactivatingUnknownMerchant() throws Exception {
        String adminToken = loginAndGetAccessToken(ADMIN_EMAIL);

        mockMvc.perform(post("/api/v1/admin/merchants/999999/deactivate").header(AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_returnForbidden_when_nonAdminDeactivatesMerchant() throws Exception {
        String ownerToken = loginAndGetAccessToken(OWNER_EMAIL);

        mockMvc.perform(post("/api/v1/admin/merchants/" + merchantAId + "/deactivate").header(AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_setRateLimitCapacity_when_callerIsAdmin() throws Exception {
        String adminToken = loginAndGetAccessToken(ADMIN_EMAIL);

        mockMvc.perform(post("/api/v1/admin/merchants/" + merchantAId + "/rate-limit-capacity")
                        .header(AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rateLimitCapacity\":750}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateLimitCapacity").value(750));

        mockMvc.perform(get("/api/v1/admin/merchants").header(AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rateLimitCapacity").value(750));
    }

    @Test
    void should_clearRateLimitCapacityOverride_when_settingNull() throws Exception {
        String adminToken = loginAndGetAccessToken(ADMIN_EMAIL);

        mockMvc.perform(post("/api/v1/admin/merchants/" + merchantAId + "/rate-limit-capacity")
                        .header(AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rateLimitCapacity\":750}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/merchants/" + merchantAId + "/rate-limit-capacity")
                        .header(AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rateLimitCapacity\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateLimitCapacity").doesNotExist());
    }

    @Test
    void should_returnBadRequest_when_rateLimitCapacityIsZeroOrLess() throws Exception {
        String adminToken = loginAndGetAccessToken(ADMIN_EMAIL);

        mockMvc.perform(post("/api/v1/admin/merchants/" + merchantAId + "/rate-limit-capacity")
                        .header(AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rateLimitCapacity\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_returnNotFound_when_settingRateLimitOfUnknownMerchant() throws Exception {
        String adminToken = loginAndGetAccessToken(ADMIN_EMAIL);

        mockMvc.perform(post("/api/v1/admin/merchants/999999/rate-limit-capacity")
                        .header(AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rateLimitCapacity\":100}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_returnForbidden_when_nonAdminSetsRateLimitCapacity() throws Exception {
        String ownerToken = loginAndGetAccessToken(OWNER_EMAIL);

        mockMvc.perform(post("/api/v1/admin/merchants/" + merchantAId + "/rate-limit-capacity")
                        .header(AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rateLimitCapacity\":100}"))
                .andExpect(status().isForbidden());
    }
}
