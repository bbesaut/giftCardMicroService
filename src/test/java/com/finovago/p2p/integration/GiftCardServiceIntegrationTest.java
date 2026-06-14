package com.finovago.p2p.integration;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
        double remainingToPay = giftCardService.redeemGiftCard(giftCardCode, amountToRedeem);

        assertEquals(0.0, remainingToPay);
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
        double remainingToPay = giftCardService.redeemGiftCard(giftCardCode, amountToRedeem);

        assertEquals(50.0, remainingToPay);
        GiftCard giftCard = giftCardRepository.findByCardCode(giftCardCode).orElseThrow(() -> new RuntimeException("Gift card not found"));
        assertEquals(0.0, giftCard.getBalance());
    }
}
