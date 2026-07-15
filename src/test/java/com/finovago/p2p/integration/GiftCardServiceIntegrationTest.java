package com.finovago.p2p.integration;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.finovago.p2p.dto.GiftCardCreateRequest;
import com.finovago.p2p.dto.RedemptionRequest;
import com.finovago.p2p.dto.RedemptionResponse;
import com.finovago.p2p.exception.ExpiredGiftCardException;
import com.finovago.p2p.model.GiftCard;
import com.finovago.p2p.repository.GiftCardRepository;
import com.finovago.p2p.service.GiftCardService;

@SpringBootTest
class GiftCardServiceIntegrationTest
{
    @Autowired
    private GiftCardRepository giftCardRepository;

    @Autowired
    private GiftCardService giftCardService;

    @Test
    void should_create_gift_card_successfully()
    {
        String giftCardCode = "TEST123";
        double balance = 100.0;
        boolean active = true;
        LocalDate expirationDate = LocalDate.now().plusYears(1);

        GiftCardCreateRequest request = new GiftCardCreateRequest(giftCardCode, balance, active, expirationDate);

        giftCardService.createGiftCard(request);

        assertTrue(giftCardRepository.findByCardCode(giftCardCode).isPresent());
    }

    @Test
    void should_disable_gift_card_after_full_redeem()
    {
        String giftCardCode = "TEST789";
        double balance = 30.0;
        boolean active = true;
        LocalDate expirationDate = LocalDate.now().plusYears(1);

        GiftCardCreateRequest createRequest = new GiftCardCreateRequest(giftCardCode, balance, active, expirationDate);
        giftCardService.createGiftCard(createRequest);

        double amountToRedeem = 30.0;
        RedemptionRequest redemptionRequest = new RedemptionRequest(amountToRedeem, giftCardCode);
        CompletableFuture<RedemptionResponse> future = giftCardService.redeemGiftCardAsync(redemptionRequest);
        RedemptionResponse response = future.join();

        assertEquals(0.0, response.remainingToPay());
        GiftCard giftCard = giftCardRepository.findByCardCode(giftCardCode).orElseThrow(() -> new RuntimeException("Gift card not found"));
        assertEquals(0.0, giftCard.getBalance());
        assertFalse(giftCard.isActive());
    }

    @Test
    void should_throw_exception_when_creating_duplicate_gift_card_code() {
        String duplicateCode = "DUPLICATE_CODE";
        LocalDate expirationDate = LocalDate.now().plusYears(1);

        GiftCardCreateRequest createRequest = new GiftCardCreateRequest(duplicateCode, 100.0, true, expirationDate);
        giftCardService.createGiftCard(createRequest);

        assertThrows(IllegalArgumentException.class, () -> {
            GiftCardCreateRequest duplicateRequest = new GiftCardCreateRequest(duplicateCode, 50.0, true, expirationDate);
            giftCardService.createGiftCard(duplicateRequest);
        });
    }

    @Test
    void should_reduce_the_original_balance_when_redeeming()
    {
        String giftCardCode = "TEST456";
        double balance = 50.0;
        boolean active = true;
        LocalDate expirationDate = LocalDate.now().plusYears(1);

        GiftCardCreateRequest createRequest = new GiftCardCreateRequest(giftCardCode, balance, active, expirationDate);
        giftCardService.createGiftCard(createRequest);

        double amountToRedeem = 100.0;
        RedemptionRequest request = new RedemptionRequest(amountToRedeem, giftCardCode);
        CompletableFuture<RedemptionResponse> future = giftCardService.redeemGiftCardAsync(request);
        RedemptionResponse response = future.join();

        assertEquals(50.0, response.remainingToPay());
        GiftCard giftCard = giftCardRepository.findByCardCode(giftCardCode).orElseThrow(() -> new RuntimeException("Gift card not found"));
        assertEquals(0.0, giftCard.getBalance());
    }

    @Test
    void should_throw_exception_when_redeeming_expired_gift_card() {
        String expiredCardCode = "EXPIRED_CARD";
        double balance = 100.0;
        boolean active = true;
        LocalDate expirationDate = LocalDate.now().minusDays(1);

        GiftCardCreateRequest createRequest = new GiftCardCreateRequest(expiredCardCode, balance, active, expirationDate);
        giftCardService.createGiftCard(createRequest);

        double amountToRedeem = 50.0;
        RedemptionRequest request = new RedemptionRequest(amountToRedeem, expiredCardCode);
        CompletableFuture<RedemptionResponse> future = giftCardService.redeemGiftCardAsync(request);

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
        double balance = 100.0;
        boolean active = true;

        GiftCardCreateRequest createRequest = new GiftCardCreateRequest(cardCode, balance, active, null);
        giftCardService.createGiftCard(createRequest);

        var savedCard = giftCardRepository.findByCardCode(cardCode).orElseThrow();
        LocalDate expectedExpiration = LocalDate.now().plusYears(2);

        assertEquals(expectedExpiration, savedCard.getExpirationDate());
    }
}
