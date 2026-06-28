package com.finovago.p2p.integration;
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

        GiftCardCreateRequest request = new GiftCardCreateRequest(giftCardCode, balance, active);

        giftCardService.createGiftCard(request);

        assertTrue(giftCardRepository.findByCardCode(giftCardCode).isPresent());
    }

    @Test
    void should_disable_gift_card_after_full_redeem()
    {
        String giftCardCode = "TEST789";
        double balance = 30.0;
        boolean active = true;

        GiftCardCreateRequest createRequest = new GiftCardCreateRequest(giftCardCode, balance, active);
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
        
        GiftCardCreateRequest createRequest = new GiftCardCreateRequest(duplicateCode, 100.0, true);
        giftCardService.createGiftCard(createRequest);

        assertThrows(IllegalArgumentException.class, () -> {
            GiftCardCreateRequest duplicateRequest = new GiftCardCreateRequest(duplicateCode, 50.0, true);
            giftCardService.createGiftCard(duplicateRequest);
        });
    }

    @Test
    void should_reduce_the_original_balance_when_redeeming()
    {
        String giftCardCode = "TEST456";
        double balance = 50.0;
        boolean active = true;

        GiftCardCreateRequest createRequest = new GiftCardCreateRequest(giftCardCode, balance, active);
        giftCardService.createGiftCard(createRequest);

        double amountToRedeem = 100.0;
        RedemptionRequest request = new RedemptionRequest(amountToRedeem, giftCardCode);
        CompletableFuture<RedemptionResponse> future = giftCardService.redeemGiftCardAsync(request);
        RedemptionResponse response = future.join();

        assertEquals(50.0, response.remainingToPay());
        GiftCard giftCard = giftCardRepository.findByCardCode(giftCardCode).orElseThrow(() -> new RuntimeException("Gift card not found"));
        assertEquals(0.0, giftCard.getBalance());
    }
}
