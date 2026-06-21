package com.finovago.p2p.integration;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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

        giftCardService.createGiftCard(giftCardCode, balance, active);

        assertTrue(giftCardRepository.findByCardCode(giftCardCode).isPresent());
    }

    @Test
    void should_disable_gift_card_after_full_redeem()
    {
        String giftCardCode = "TEST789";
        double balance = 30.0;
        boolean active = true;

        giftCardService.createGiftCard(giftCardCode, balance, active);

        double amountToRedeem = 30.0;
        CompletableFuture<RedemptionResponse> future = giftCardService.redeemGiftCardAsync(giftCardCode, amountToRedeem);
        RedemptionResponse response = future.join();

        assertEquals(0.0, response.remainingToPay());
        GiftCard giftCard = giftCardRepository.findByCardCode(giftCardCode).orElseThrow(() -> new RuntimeException("Gift card not found"));
        assertEquals(0.0, giftCard.getBalance());
        assertFalse(giftCard.isActive());
    }

    @Test
    void should_reduce_the_original_balance_when_redeeming()
    {
        String giftCardCode = "TEST456";
        double balance = 50.0;
        boolean active = true;

        giftCardService.createGiftCard(giftCardCode, balance, active);

        double amountToRedeem = 100.0;
        CompletableFuture<RedemptionResponse> future = giftCardService.redeemGiftCardAsync(giftCardCode, amountToRedeem);
        RedemptionResponse response = future.join();

        assertEquals(50.0, response.remainingToPay());
        GiftCard giftCard = giftCardRepository.findByCardCode(giftCardCode).orElseThrow(() -> new RuntimeException("Gift card not found"));
        assertEquals(0.0, giftCard.getBalance());
    }
}
