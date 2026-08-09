package com.finovago.p2p.integration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.finovago.p2p.AbstractIntegrationTest;
import com.finovago.p2p.config.PostgresTestcontainerInitializer;
import com.finovago.p2p.dto.GiftCardCreateRequest;
import com.finovago.p2p.dto.RedemptionRequest;
import com.finovago.p2p.dto.RedemptionResponse;
import com.finovago.p2p.exception.ExpiredGiftCardException;
import com.finovago.p2p.model.GiftCard;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.repository.GiftCardRepository;
import com.finovago.p2p.repository.IdempotencyKeyRepository;
import com.finovago.p2p.repository.MerchantRepository;
import com.finovago.p2p.repository.RefreshTokenRepository;
import com.finovago.p2p.repository.UserRepository;
import com.finovago.p2p.security.AuthenticatedUser;
import com.finovago.p2p.service.GiftCardService;

class GiftCardServiceIntegrationTest extends AbstractIntegrationTest
{
    @Autowired
    private GiftCardRepository giftCardRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;


    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GiftCardService giftCardService;

    private Long merchantId;

    @BeforeEach
    void setUp() {
        // Redemption runs on a separate thread pool (see GiftCardService#redeemGiftCardAsync), so this
        // class deliberately does NOT use @Transactional — a test-managed transaction is bound to the
        // main thread only, and the async thread wouldn't see uncommitted data. Rows are cleaned up
        // manually instead, children before merchants (gift_card, idempotency_key and users all have
        // a FK to merchants).
        idempotencyKeyRepository.deleteAll();
        PostgresTestcontainerInitializer.executeAsMigrator("TRUNCATE TABLE gift_card_ledger RESTART IDENTITY");
        giftCardRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        merchantRepository.deleteAll();

        Merchant merchant = merchantRepository.save(new Merchant("Test Merchant", "merchant@example.com"));
        merchantId = merchant.getId();

        AuthenticatedUser authenticatedUser = new AuthenticatedUser("merchant@example.com", "MERCHANT", merchantId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(authenticatedUser, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void assertMoneyEquals(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }

    @Test
    void should_create_gift_card_successfully()
    {
        String giftCardCode = "TEST123";
        BigDecimal balance = BigDecimal.valueOf(100.0);
        boolean active = true;
        LocalDate expirationDate = LocalDate.now().plusYears(1);

        GiftCardCreateRequest request = new GiftCardCreateRequest(giftCardCode, balance, active, expirationDate);

        giftCardService.createGiftCard(request);

        assertTrue(giftCardRepository.findByMerchantIdAndCardCode(merchantId, giftCardCode).isPresent());
    }

    @Test
    void should_disable_gift_card_after_full_redeem()
    {
        String giftCardCode = "TEST789";
        BigDecimal balance = BigDecimal.valueOf(30.0);
        boolean active = true;
        LocalDate expirationDate = LocalDate.now().plusYears(1);

        GiftCardCreateRequest createRequest = new GiftCardCreateRequest(giftCardCode, balance, active, expirationDate);
        giftCardService.createGiftCard(createRequest);

        BigDecimal amountToRedeem = BigDecimal.valueOf(30.0);
        RedemptionRequest redemptionRequest = new RedemptionRequest(amountToRedeem, giftCardCode);
        CompletableFuture<RedemptionResponse> future = giftCardService.redeemGiftCardAsync(redemptionRequest, UUID.randomUUID().toString());
        RedemptionResponse response = future.join();

        assertMoneyEquals(BigDecimal.ZERO, response.remainingToPay());
        GiftCard giftCard = giftCardRepository.findByMerchantIdAndCardCode(merchantId, giftCardCode).orElseThrow(() -> new RuntimeException("Gift card not found"));
        assertMoneyEquals(BigDecimal.ZERO, giftCard.getBalance());
        assertFalse(giftCard.isActive());
    }

    @Test
    void should_throw_exception_when_creating_duplicate_gift_card_code() {
        String duplicateCode = "DUPLICATE_CODE";
        LocalDate expirationDate = LocalDate.now().plusYears(1);

        GiftCardCreateRequest createRequest = new GiftCardCreateRequest(duplicateCode, BigDecimal.valueOf(100.0), true, expirationDate);
        giftCardService.createGiftCard(createRequest);

        assertThrows(IllegalArgumentException.class, () -> {
            GiftCardCreateRequest duplicateRequest = new GiftCardCreateRequest(duplicateCode, BigDecimal.valueOf(50.0), true, expirationDate);
            giftCardService.createGiftCard(duplicateRequest);
        });
    }

    @Test
    void should_allow_same_card_code_for_different_merchants() {
        String sharedCode = "SHARED_CODE";
        LocalDate expirationDate = LocalDate.now().plusYears(1);

        giftCardService.createGiftCard(new GiftCardCreateRequest(sharedCode, BigDecimal.valueOf(100.0), true, expirationDate));

        Merchant otherMerchant = merchantRepository.save(new Merchant("Other Merchant", "other@example.com"));
        AuthenticatedUser otherUser = new AuthenticatedUser("other@example.com", "MERCHANT", otherMerchant.getId());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(otherUser, null, List.of()));

        giftCardService.createGiftCard(new GiftCardCreateRequest(sharedCode, BigDecimal.valueOf(200.0), true, expirationDate));

        assertTrue(giftCardRepository.findByMerchantIdAndCardCode(merchantId, sharedCode).isPresent());
        assertTrue(giftCardRepository.findByMerchantIdAndCardCode(otherMerchant.getId(), sharedCode).isPresent());
    }

    @Test
    void should_reduce_the_original_balance_when_redeeming()
    {
        String giftCardCode = "TEST456";
        BigDecimal balance = BigDecimal.valueOf(50.0);
        boolean active = true;
        LocalDate expirationDate = LocalDate.now().plusYears(1);

        GiftCardCreateRequest createRequest = new GiftCardCreateRequest(giftCardCode, balance, active, expirationDate);
        giftCardService.createGiftCard(createRequest);

        BigDecimal amountToRedeem = BigDecimal.valueOf(100.0);
        RedemptionRequest request = new RedemptionRequest(amountToRedeem, giftCardCode);
        CompletableFuture<RedemptionResponse> future = giftCardService.redeemGiftCardAsync(request, UUID.randomUUID().toString());
        RedemptionResponse response = future.join();

        assertMoneyEquals(BigDecimal.valueOf(50.0), response.remainingToPay());
        GiftCard giftCard = giftCardRepository.findByMerchantIdAndCardCode(merchantId, giftCardCode).orElseThrow(() -> new RuntimeException("Gift card not found"));
        assertMoneyEquals(BigDecimal.ZERO, giftCard.getBalance());
    }

    @Test
    void should_throw_exception_when_redeeming_expired_gift_card() {
        String expiredCardCode = "EXPIRED_CARD";
        BigDecimal balance = BigDecimal.valueOf(100.0);
        boolean active = true;
        LocalDate expirationDate = LocalDate.now().minusDays(1);

        GiftCardCreateRequest createRequest = new GiftCardCreateRequest(expiredCardCode, balance, active, expirationDate);
        giftCardService.createGiftCard(createRequest);

        BigDecimal amountToRedeem = BigDecimal.valueOf(50.0);
        RedemptionRequest request = new RedemptionRequest(amountToRedeem, expiredCardCode);
        CompletableFuture<RedemptionResponse> future = giftCardService.redeemGiftCardAsync(request, UUID.randomUUID().toString());

        try {
            future.join();
            throw new AssertionError("Expected ExpiredGiftCardException to be thrown");
        } catch (Exception e) {
            if (e.getCause() instanceof ExpiredGiftCardException) {
                return;
            }
            throw e;
        }
    }

    @Test
    void should_set_default_expiration_date_when_not_provided() {
        String cardCode = "DEFAULT_EXPIRATION";
        BigDecimal balance = BigDecimal.valueOf(100.0);
        boolean active = true;

        GiftCardCreateRequest createRequest = new GiftCardCreateRequest(cardCode, balance, active, null);
        giftCardService.createGiftCard(createRequest);

        var savedCard = giftCardRepository.findByMerchantIdAndCardCode(merchantId, cardCode).orElseThrow();
        LocalDate expectedExpiration = LocalDate.now().plusYears(2);

        assertEquals(expectedExpiration, savedCard.getExpirationDate());
    }
}
