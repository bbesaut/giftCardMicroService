package com.finovago.p2p.unit;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.finovago.p2p.dto.GiftCardCreateRequest;
import com.finovago.p2p.dto.GiftCardResponse;
import com.finovago.p2p.dto.RedemptionResponse;
import com.finovago.p2p.exception.ExpiredGiftCardException;
import com.finovago.p2p.exception.InactiveGiftCardException;
import com.finovago.p2p.exception.UnknownGiftCardException;
import com.finovago.p2p.model.GiftCard;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.repository.GiftCardRepository;
import com.finovago.p2p.repository.MerchantRepository;
import com.finovago.p2p.security.CurrentUserContext;
import com.finovago.p2p.service.GiftCardService;

@ExtendWith(MockitoExtension.class)
class GiftCardServiceUnitTest
{
    private static final Long MERCHANT_ID = 1L;

    @Mock
    private GiftCardRepository giftCardRepository;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private CurrentUserContext currentUserContext;

    @InjectMocks
    private GiftCardService giftCardService;

    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchant = new Merchant("Test Merchant", "merchant@example.com");
        lenient().when(currentUserContext.currentMerchantId()).thenReturn(MERCHANT_ID);
        lenient().when(merchantRepository.getReferenceById(MERCHANT_ID)).thenReturn(merchant);
    }

    @Test
    void should_throw_exception_when_card_code_is_invalid()
    {
        assertThrows(IllegalArgumentException.class, () -> giftCardService.executeRedemptionSync(MERCHANT_ID, null, 100.0));
        assertThrows(IllegalArgumentException.class, () -> giftCardService.executeRedemptionSync(MERCHANT_ID, "", 100.0));
    }

    @Test
    void should_throw_exception_when_amount_is_invalid()
    {
        assertThrows(IllegalArgumentException.class, () -> giftCardService.executeRedemptionSync(MERCHANT_ID, "VALID_CODE", 0.0));
        assertThrows(IllegalArgumentException.class, () -> giftCardService.executeRedemptionSync(MERCHANT_ID, "VALID_CODE", -50.0));
    }

    @Test
    void should_throw_exception_when_gift_card_not_found()
    {
        when(giftCardRepository.findByMerchantIdAndCardCode(MERCHANT_ID, "INVALID_CODE")).thenReturn(Optional.empty());
        assertThrows(UnknownGiftCardException.class, () -> giftCardService.executeRedemptionSync(MERCHANT_ID, "INVALID_CODE", 100.0));
    }

    @Test
    void should_throw_exception_when_gift_card_is_not_active()
    {
        String fakeGiftCardCode = "ABC123";
        GiftCard inactiveGiftCard = new GiftCard(merchant, fakeGiftCardCode, 100.0, false, LocalDate.now().plusDays(30));
        when(giftCardRepository.findByMerchantIdAndCardCode(MERCHANT_ID, fakeGiftCardCode)).thenReturn(Optional.of(inactiveGiftCard));

        assertThrows(InactiveGiftCardException.class, () -> giftCardService.executeRedemptionSync(MERCHANT_ID, fakeGiftCardCode, 50.0));
    }

    @Test
    void should_throw_exception_when_gift_card_is_expired()
    {
        String expiredCardCode = "EXPIRED123";
        GiftCard expiredCard = new GiftCard(merchant, expiredCardCode, 100.0, true, LocalDate.now().minusDays(1));
        when(giftCardRepository.findByMerchantIdAndCardCode(MERCHANT_ID, expiredCardCode)).thenReturn(Optional.of(expiredCard));

        assertThrows(ExpiredGiftCardException.class, () -> giftCardService.executeRedemptionSync(MERCHANT_ID, expiredCardCode, 50.0));
    }

    @Test
    void should_deduct_balance_when_sufficient_funds()
    {
        String cardCode = "VALID123";
        GiftCard activeCard = new GiftCard(merchant, cardCode, 100.0, true, LocalDate.now().plusDays(30));

        when(giftCardRepository.findByMerchantIdAndCardCode(MERCHANT_ID, cardCode)).thenReturn(Optional.of(activeCard));

        RedemptionResponse response = giftCardService.executeRedemptionSync(MERCHANT_ID, cardCode, 30.0);

        assertEquals("SUCCESS", response.status());
        assertEquals(30.0, response.deductedAmount());
        assertEquals(70.0, response.remainingBalance());
        assertEquals(0.0, response.remainingToPay());
    }

    @Test
    void should_throw_exception_when_code_already_exists() {
        String existingCode = "ALREADY_EXISTS";
        GiftCard fakeExistingCard = new GiftCard(merchant, existingCode, 100.0, true, LocalDate.now().plusDays(30));

        when(giftCardRepository.findByMerchantIdAndCardCode(MERCHANT_ID, existingCode)).thenReturn(Optional.of(fakeExistingCard));

        assertThrows(IllegalArgumentException.class, () ->
            giftCardService.createGiftCard(new GiftCardCreateRequest(existingCode, 0, false, LocalDate.now().plusDays(30)))
        );
    }

    @Test
    void should_use_default_expiration_date_when_not_provided() {
        String cardCode = "DEFAULT_DATE";
        LocalDate expectedDate = LocalDate.now().plusYears(2);
        GiftCard savedCard = new GiftCard(merchant, cardCode, 100.0, true, expectedDate);

        when(giftCardRepository.findByMerchantIdAndCardCode(MERCHANT_ID, cardCode)).thenReturn(Optional.empty());
        when(giftCardRepository.save(ArgumentMatchers.any(GiftCard.class))).thenReturn(savedCard);

        var response = giftCardService.createGiftCard(new GiftCardCreateRequest(cardCode, 100.0, true, null));

        assertEquals(expectedDate, response.expirationDate());
    }

    @Test
    void should_drain_card_when_insufficient_funds()
    {
        String cardCode = "VALID456";
        GiftCard activeCard = new GiftCard(merchant, cardCode, 20.0, true, LocalDate.now().plusDays(30));

        when(giftCardRepository.findByMerchantIdAndCardCode(MERCHANT_ID, cardCode)).thenReturn(Optional.of(activeCard));

        RedemptionResponse response = giftCardService.executeRedemptionSync(MERCHANT_ID, cardCode, 50.0);

        assertEquals("SUCCESS", response.status());
        assertEquals(20.0, response.deductedAmount());
        assertEquals(0.0, response.remainingBalance());
        assertEquals(30.0, response.remainingToPay());
    }

    @Test
    void should_return_gift_card_details_on_lookup()
    {
        String cardCode = "LOOKUP123";
        LocalDate expirationDate = LocalDate.now().plusDays(30);
        GiftCard giftCard = new GiftCard(merchant, cardCode, 150.0, true, expirationDate);

        when(giftCardRepository.findByMerchantIdAndCardCode(MERCHANT_ID, cardCode)).thenReturn(Optional.of(giftCard));

        GiftCardResponse response = giftCardService.lookupGiftCard(cardCode);

        assertEquals(cardCode, response.giftCardCode());
        assertEquals(150.0, response.balance());
        assertEquals(true, response.active());
        assertEquals(expirationDate, response.expirationDate());
    }

    @Test
    void should_throw_exception_when_looking_up_non_existent_card()
    {
        String nonExistentCode = "NONEXISTENT";
        when(giftCardRepository.findByMerchantIdAndCardCode(MERCHANT_ID, nonExistentCode)).thenReturn(Optional.empty());

        assertThrows(UnknownGiftCardException.class, () -> giftCardService.lookupGiftCard(nonExistentCode));
    }
}
