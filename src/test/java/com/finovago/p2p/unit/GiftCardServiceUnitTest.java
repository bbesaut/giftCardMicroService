package com.finovago.p2p.unit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.finovago.p2p.dto.RedemptionResponse;
import com.finovago.p2p.exception.InactiveGiftCardException;
import com.finovago.p2p.exception.UnknownGiftCardException;
import com.finovago.p2p.model.GiftCard;
import com.finovago.p2p.repository.GiftCardRepository;
import com.finovago.p2p.service.GiftCardService;

@ExtendWith(MockitoExtension.class)
class GiftCardServiceUnitTest
{
    @Mock
    private GiftCardRepository giftCardRepository;

    @InjectMocks
    private GiftCardService giftCardService;

    @Test
    void should_throw_exception_when_card_code_is_invalid()
    {
        assertThrows(IllegalArgumentException.class, () -> giftCardService.executeRedemptionSync(null, 100.0));
        assertThrows(IllegalArgumentException.class, () -> giftCardService.executeRedemptionSync("", 100.0));
    } 

    @Test
    void should_throw_exception_when_amount_is_invalid()
    {
        assertThrows(IllegalArgumentException.class, () -> giftCardService.executeRedemptionSync("VALID_CODE", 0.0));
        assertThrows(IllegalArgumentException.class, () -> giftCardService.executeRedemptionSync("VALID_CODE", -50.0));
    }

    @Test
    void should_throw_exception_when_gift_card_not_found()
    {
        when(giftCardRepository.findByCardCode("INVALID_CODE")).thenReturn(Optional.empty());
        assertThrows(UnknownGiftCardException.class, () -> giftCardService.executeRedemptionSync("INVALID_CODE", 100.0));
    }

    @Test
    void should_throw_exception_when_gift_card_is_not_active()
    {
        String fakeGiftCardCode = "ABC123";
        GiftCard inactiveGiftCard = new GiftCard(fakeGiftCardCode, 100.0, false);
        when(giftCardRepository.findByCardCode(fakeGiftCardCode)).thenReturn(Optional.of(inactiveGiftCard));

        assertThrows(InactiveGiftCardException.class, () -> giftCardService.executeRedemptionSync(fakeGiftCardCode, 50.0));
    }

    @Test
    void should_deduct_balance_when_sufficient_funds()
    {
        String cardCode = "VALID123";
        GiftCard activeCard = new GiftCard(cardCode, 100.0, true);

        when(giftCardRepository.findByCardCode(cardCode)).thenReturn(Optional.of(activeCard));

        RedemptionResponse response = giftCardService.executeRedemptionSync(cardCode, 30.0);

        assertEquals("SUCCESS", response.status());
        assertEquals(30.0, response.deductedAmount());
        assertEquals(70.0, response.remainingBalance());
        assertEquals(0.0, response.remainingToPay());
    }

    @Test
    void should_drain_card_when_insufficient_funds()
    {
        String cardCode = "VALID456";
        GiftCard activeCard = new GiftCard(cardCode, 20.0, true);

        when(giftCardRepository.findByCardCode(cardCode)).thenReturn(Optional.of(activeCard));

        RedemptionResponse response = giftCardService.executeRedemptionSync(cardCode, 50.0);

        assertEquals("SUCCESS", response.status());
        assertEquals(20.0, response.deductedAmount());
        assertEquals(0.0, response.remainingBalance());
        assertEquals(30.0, response.remainingToPay());
    }
}
