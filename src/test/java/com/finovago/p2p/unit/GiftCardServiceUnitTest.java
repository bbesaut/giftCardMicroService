package com.finovago.p2p.unit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

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
        assertThrows(IllegalArgumentException.class, () -> giftCardService.redeemGiftCard(null, 100.0));
        assertThrows(IllegalArgumentException.class, () -> giftCardService.redeemGiftCard("", 100.0));
    } 

    @Test
    void should_throw_exception_when_amount_is_invalid()
    {
        assertThrows(IllegalArgumentException.class, () -> giftCardService.redeemGiftCard("VALID_CODE", 0.0));
        assertThrows(IllegalArgumentException.class, () -> giftCardService.redeemGiftCard("VALID_CODE", -50.0));
    }

    @Test
    void should_throw_exception_when_gift_card_not_found()
    {
        when(giftCardRepository.findByCardCode("INVALID_CODE")).thenReturn(Optional.empty());
        assertThrows(UnknownGiftCardException.class, () -> giftCardService.redeemGiftCard("INVALID_CODE", 100.0));
    }

    @Test
    void should_throw_exception_when_gift_card_is_not_active()
    {
        String fakeGiftCardCode = "ABC123";
        GiftCard inactiveGiftCard = new GiftCard(fakeGiftCardCode, 100.0, false);
        when(giftCardRepository.findByCardCode(fakeGiftCardCode)).thenReturn(Optional.of(inactiveGiftCard));

        assertThrows(InactiveGiftCardException.class, () -> giftCardService.redeemGiftCard(fakeGiftCardCode, 50.0));
    } 
}
