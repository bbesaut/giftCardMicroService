package com.finovago.p2p.unit;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.finovago.p2p.dto.HoldResponse;
import com.finovago.p2p.dto.ReserveRequest;
import com.finovago.p2p.exception.ExpiredGiftCardException;
import com.finovago.p2p.exception.HoldAlreadyFinalizedException;
import com.finovago.p2p.exception.HoldNotFoundException;
import com.finovago.p2p.exception.InactiveGiftCardException;
import com.finovago.p2p.exception.InsufficientAvailableBalanceException;
import com.finovago.p2p.exception.UnknownGiftCardException;
import com.finovago.p2p.model.GiftCard;
import com.finovago.p2p.model.GiftCardHold;
import com.finovago.p2p.model.HoldStatus;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.repository.GiftCardHoldRepository;
import com.finovago.p2p.repository.GiftCardRepository;
import com.finovago.p2p.security.CurrentUserContext;
import com.finovago.p2p.service.GiftCardHoldService;

@ExtendWith(MockitoExtension.class)
class GiftCardHoldServiceUnitTest {
    private static final Long MERCHANT_ID = 1L;
    private static final long TTL_MINUTES = 15L;

    @Mock
    private GiftCardHoldRepository giftCardHoldRepository;

    @Mock
    private GiftCardRepository giftCardRepository;

    @Mock
    private CurrentUserContext currentUserContext;

    private GiftCardHoldService giftCardHoldService;

    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchant = new Merchant("Test Merchant", "merchant@example.com");
        giftCardHoldService = new GiftCardHoldService(giftCardHoldRepository, giftCardRepository, currentUserContext, TTL_MINUTES);
        lenient().when(currentUserContext.currentMerchantId()).thenReturn(MERCHANT_ID);
    }

    private GiftCard activeCard(String code, double balance) {
        GiftCard card = new GiftCard(merchant, code, balance, true, LocalDate.now().plusDays(30));
        return card;
    }

    @Test
    void should_reserve_hold_when_sufficient_available_balance() {
        String cardCode = "GC-1";
        GiftCard card = activeCard(cardCode, 100.0);

        when(giftCardRepository.findByMerchantIdAndCardCodeForUpdate(MERCHANT_ID, cardCode)).thenReturn(Optional.of(card));
        when(giftCardHoldRepository.sumPendingHoldAmounts(card.getId())).thenReturn(20.0);
        when(giftCardHoldRepository.save(org.mockito.ArgumentMatchers.any(GiftCardHold.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HoldResponse response = giftCardHoldService.reserve(new ReserveRequest(cardCode, 50.0));

        assertEquals("PENDING", response.status());
        assertEquals(30.0, response.remainingAvailableBalance());
    }

    @Test
    void should_throw_when_reserve_amount_exceeds_available_balance() {
        String cardCode = "GC-2";
        GiftCard card = activeCard(cardCode, 100.0);

        when(giftCardRepository.findByMerchantIdAndCardCodeForUpdate(MERCHANT_ID, cardCode)).thenReturn(Optional.of(card));
        when(giftCardHoldRepository.sumPendingHoldAmounts(card.getId())).thenReturn(60.0);

        assertThrows(InsufficientAvailableBalanceException.class,
                () -> giftCardHoldService.reserve(new ReserveRequest(cardCode, 50.0)));
    }

    @Test
    void should_throw_unknown_gift_card_exception_on_reserve_for_missing_card() {
        when(giftCardRepository.findByMerchantIdAndCardCodeForUpdate(MERCHANT_ID, "MISSING")).thenReturn(Optional.empty());

        assertThrows(UnknownGiftCardException.class,
                () -> giftCardHoldService.reserve(new ReserveRequest("MISSING", 10.0)));
    }

    @Test
    void should_throw_inactive_gift_card_exception_on_reserve() {
        String cardCode = "GC-3";
        GiftCard card = new GiftCard(merchant, cardCode, 100.0, false, LocalDate.now().plusDays(30));

        when(giftCardRepository.findByMerchantIdAndCardCodeForUpdate(MERCHANT_ID, cardCode)).thenReturn(Optional.of(card));

        assertThrows(InactiveGiftCardException.class,
                () -> giftCardHoldService.reserve(new ReserveRequest(cardCode, 10.0)));
    }

    @Test
    void should_throw_expired_gift_card_exception_on_reserve() {
        String cardCode = "GC-4";
        GiftCard card = new GiftCard(merchant, cardCode, 100.0, true, LocalDate.now().minusDays(1));

        when(giftCardRepository.findByMerchantIdAndCardCodeForUpdate(MERCHANT_ID, cardCode)).thenReturn(Optional.of(card));

        assertThrows(ExpiredGiftCardException.class,
                () -> giftCardHoldService.reserve(new ReserveRequest(cardCode, 10.0)));
    }

    @Test
    void should_capture_pending_hold_and_deduct_gift_card_balance() {
        String cardCode = "GC-5";
        GiftCard card = activeCard(cardCode, 100.0);
        GiftCardHold hold = new GiftCardHold(card, MERCHANT_ID, 30.0, LocalDateTime.now().plusMinutes(10));

        when(giftCardHoldRepository.findByIdAndMerchantIdForUpdate(1L, MERCHANT_ID)).thenReturn(Optional.of(hold));
        when(giftCardRepository.findByMerchantIdAndCardCodeForUpdate(MERCHANT_ID, cardCode)).thenReturn(Optional.of(card));

        HoldResponse response = giftCardHoldService.capture(1L);

        assertEquals("CAPTURED", response.status());
        assertEquals(70.0, card.getBalance());
    }

    @Test
    void should_throw_hold_already_finalized_on_double_capture() {
        String cardCode = "GC-6";
        GiftCard card = activeCard(cardCode, 100.0);
        GiftCardHold hold = new GiftCardHold(card, MERCHANT_ID, 30.0, LocalDateTime.now().plusMinutes(10));
        hold.capture();

        when(giftCardHoldRepository.findByIdAndMerchantIdForUpdate(1L, MERCHANT_ID)).thenReturn(Optional.of(hold));

        assertThrows(HoldAlreadyFinalizedException.class, () -> giftCardHoldService.capture(1L));
        verify(giftCardRepository, never()).findByMerchantIdAndCardCodeForUpdate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void should_throw_hold_already_finalized_on_capture_past_expiry() {
        String cardCode = "GC-7";
        GiftCard card = activeCard(cardCode, 100.0);
        GiftCardHold hold = new GiftCardHold(card, MERCHANT_ID, 30.0, LocalDateTime.now().minusMinutes(1));

        when(giftCardHoldRepository.findByIdAndMerchantIdForUpdate(1L, MERCHANT_ID)).thenReturn(Optional.of(hold));

        assertThrows(HoldAlreadyFinalizedException.class, () -> giftCardHoldService.capture(1L));
    }

    @Test
    void should_throw_hold_not_found_on_capture_for_missing_or_foreign_hold() {
        when(giftCardHoldRepository.findByIdAndMerchantIdForUpdate(1L, MERCHANT_ID)).thenReturn(Optional.empty());

        assertThrows(HoldNotFoundException.class, () -> giftCardHoldService.capture(1L));
    }

    @Test
    void should_release_pending_hold() {
        String cardCode = "GC-8";
        GiftCard card = activeCard(cardCode, 100.0);
        GiftCardHold hold = new GiftCardHold(card, MERCHANT_ID, 30.0, LocalDateTime.now().plusMinutes(10));

        when(giftCardHoldRepository.findByIdAndMerchantIdForUpdate(1L, MERCHANT_ID)).thenReturn(Optional.of(hold));

        HoldResponse response = giftCardHoldService.release(1L);

        assertEquals("RELEASED", response.status());
        assertEquals(100.0, card.getBalance());
    }

    @Test
    void should_throw_hold_already_finalized_on_double_release() {
        String cardCode = "GC-9";
        GiftCard card = activeCard(cardCode, 100.0);
        GiftCardHold hold = new GiftCardHold(card, MERCHANT_ID, 30.0, LocalDateTime.now().plusMinutes(10));
        hold.release();

        when(giftCardHoldRepository.findByIdAndMerchantIdForUpdate(1L, MERCHANT_ID)).thenReturn(Optional.of(hold));

        assertThrows(HoldAlreadyFinalizedException.class, () -> giftCardHoldService.release(1L));
    }

    @Test
    void should_throw_hold_not_found_on_release_for_missing_or_foreign_hold() {
        when(giftCardHoldRepository.findByIdAndMerchantIdForUpdate(1L, MERCHANT_ID)).thenReturn(Optional.empty());

        assertThrows(HoldNotFoundException.class, () -> giftCardHoldService.release(1L));
    }

    @Test
    void should_no_op_when_expiring_a_hold_that_is_no_longer_pending() {
        String cardCode = "GC-10";
        GiftCard card = activeCard(cardCode, 100.0);
        GiftCardHold hold = new GiftCardHold(card, MERCHANT_ID, 30.0, LocalDateTime.now().minusMinutes(1));
        hold.capture();

        when(giftCardHoldRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(hold));

        giftCardHoldService.expireIfStillPending(1L);

        assertEquals(HoldStatus.CAPTURED, hold.getStatus());
        verify(giftCardHoldRepository, never()).save(hold);
    }

    @Test
    void should_expire_pending_hold_past_ttl() {
        String cardCode = "GC-11";
        GiftCard card = activeCard(cardCode, 100.0);
        GiftCardHold hold = new GiftCardHold(card, MERCHANT_ID, 30.0, LocalDateTime.now().minusMinutes(1));

        when(giftCardHoldRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(hold));

        giftCardHoldService.expireIfStillPending(1L);

        assertEquals(HoldStatus.EXPIRED, hold.getStatus());
        verify(giftCardHoldRepository).save(hold);
    }
}
