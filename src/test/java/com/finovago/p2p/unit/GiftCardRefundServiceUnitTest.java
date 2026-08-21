package com.finovago.p2p.unit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.finovago.p2p.dto.RefundRequest;
import com.finovago.p2p.dto.RefundResponse;
import com.finovago.p2p.exception.InvalidRefundTargetException;
import com.finovago.p2p.exception.LedgerEntryNotFoundException;
import com.finovago.p2p.exception.RefundExceedsOriginalAmountException;
import com.finovago.p2p.exception.UnknownGiftCardException;
import com.finovago.p2p.model.GiftCard;
import com.finovago.p2p.model.LedgerEntry;
import com.finovago.p2p.model.LedgerEntryType;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.repository.GiftCardRepository;
import com.finovago.p2p.repository.LedgerEntryRepository;
import com.finovago.p2p.security.CurrentUserContext;
import com.finovago.p2p.service.GiftCardRefundService;
import com.finovago.p2p.service.IdempotencyKeyService;
import com.finovago.p2p.service.LedgerService;

@ExtendWith(MockitoExtension.class)
class GiftCardRefundServiceUnitTest {
    private static final Long MERCHANT_ID = 1L;
    private static final String IDEMPOTENCY_KEY = "client-refund-key";

    @Mock
    private GiftCardRepository giftCardRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private CurrentUserContext currentUserContext;

    @Mock
    private IdempotencyKeyService idempotencyKeyService;

    @Mock
    private LedgerService ledgerService;

    private GiftCardRefundService giftCardRefundService;

    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchant = new Merchant("Test Merchant", "merchant@example.com");
        giftCardRefundService = new GiftCardRefundService(giftCardRepository, ledgerEntryRepository, currentUserContext, idempotencyKeyService, ledgerService);
        lenient().when(currentUserContext.currentMerchantId()).thenReturn(MERCHANT_ID);
        lenient().when(currentUserContext.currentUserIdOrNull()).thenReturn(null);
        lenient().when(idempotencyKeyService.hashRequest(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn("hash");
        lenient().when(idempotencyKeyService.claim(any(), any(), any(), any(), eq(RefundResponse.class))).thenReturn(Optional.empty());
    }

    private static void assertMoneyEquals(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }

    private GiftCard activeCard(String code, double balance) {
        return new GiftCard(merchant, code, BigDecimal.valueOf(balance), true, LocalDate.now().plusDays(30));
    }

    @Test
    void should_refund_when_within_remaining_refundable_amount() {
        String cardCode = "GC-1";
        GiftCard card = activeCard(cardCode, 70.0);
        LedgerEntry redemption = new LedgerEntry(card, MERCHANT_ID, LedgerEntryType.REDEMPTION, new BigDecimal("30.00"), new BigDecimal("70.00"), null, null);

        when(giftCardRepository.findByMerchantIdAndCardCodeForUpdate(MERCHANT_ID, cardCode)).thenReturn(Optional.of(card));
        when(ledgerEntryRepository.findByIdAndGiftCardIdAndMerchantId(42L, card.getId(), MERCHANT_ID)).thenReturn(Optional.of(redemption));
        when(ledgerEntryRepository.sumRefundedAmountForEntry(42L)).thenReturn(BigDecimal.ZERO);

        RefundRequest request = new RefundRequest(cardCode, BigDecimal.valueOf(30.0), 42L, null);
        RefundResponse response = giftCardRefundService.refund(request, IDEMPOTENCY_KEY);

        assertEquals("SUCCESS", response.status());
        assertMoneyEquals(new BigDecimal("30.00"), response.refundedAmount());
        assertMoneyEquals(new BigDecimal("100.00"), response.newBalance());
        verify(ledgerService).record(eq(card), eq(MERCHANT_ID), eq(LedgerEntryType.REFUND), eq(new BigDecimal("30.00")), eq(new BigDecimal("100.00")), eq(null), eq(null), eq(false), eq(42L), eq(null));
    }

    @Test
    void should_throw_exception_when_refund_exceeds_remaining_refundable_amount() {
        String cardCode = "GC-1";
        GiftCard card = activeCard(cardCode, 70.0);
        LedgerEntry redemption = new LedgerEntry(card, MERCHANT_ID, LedgerEntryType.REDEMPTION, new BigDecimal("30.00"), new BigDecimal("70.00"), null, null);

        when(giftCardRepository.findByMerchantIdAndCardCodeForUpdate(MERCHANT_ID, cardCode)).thenReturn(Optional.of(card));
        when(ledgerEntryRepository.findByIdAndGiftCardIdAndMerchantId(42L, card.getId(), MERCHANT_ID)).thenReturn(Optional.of(redemption));
        when(ledgerEntryRepository.sumRefundedAmountForEntry(42L)).thenReturn(new BigDecimal("20.00"));

        RefundRequest request = new RefundRequest(cardCode, BigDecimal.valueOf(15.0), 42L, null);

        assertThrows(RefundExceedsOriginalAmountException.class, () -> giftCardRefundService.refund(request, IDEMPOTENCY_KEY));
        verify(ledgerService, never()).record(any(), any(), any(), any(BigDecimal.class), any(BigDecimal.class), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any(), any());
    }

    @Test
    void should_throw_exception_when_refund_target_not_found() {
        String cardCode = "GC-1";
        GiftCard card = activeCard(cardCode, 70.0);

        when(giftCardRepository.findByMerchantIdAndCardCodeForUpdate(MERCHANT_ID, cardCode)).thenReturn(Optional.of(card));
        when(ledgerEntryRepository.findByIdAndGiftCardIdAndMerchantId(999L, card.getId(), MERCHANT_ID)).thenReturn(Optional.empty());

        RefundRequest request = new RefundRequest(cardCode, BigDecimal.valueOf(10.0), 999L, null);

        assertThrows(LedgerEntryNotFoundException.class, () -> giftCardRefundService.refund(request, IDEMPOTENCY_KEY));
    }

    @Test
    void should_throw_exception_when_refund_target_is_not_a_redemption() {
        String cardCode = "GC-1";
        GiftCard card = activeCard(cardCode, 70.0);
        LedgerEntry creation = new LedgerEntry(card, MERCHANT_ID, LedgerEntryType.CREATION, new BigDecimal("100.00"), new BigDecimal("100.00"), null, null);

        when(giftCardRepository.findByMerchantIdAndCardCodeForUpdate(MERCHANT_ID, cardCode)).thenReturn(Optional.of(card));
        when(ledgerEntryRepository.findByIdAndGiftCardIdAndMerchantId(7L, card.getId(), MERCHANT_ID)).thenReturn(Optional.of(creation));

        RefundRequest request = new RefundRequest(cardCode, BigDecimal.valueOf(10.0), 7L, null);

        assertThrows(InvalidRefundTargetException.class, () -> giftCardRefundService.refund(request, IDEMPOTENCY_KEY));
    }

    @Test
    void should_throw_exception_when_card_not_found() {
        String unknownCode = "MISSING";
        when(giftCardRepository.findByMerchantIdAndCardCodeForUpdate(MERCHANT_ID, unknownCode)).thenReturn(Optional.empty());

        RefundRequest request = new RefundRequest(unknownCode, BigDecimal.valueOf(10.0), 1L, null);

        assertThrows(UnknownGiftCardException.class, () -> giftCardRefundService.refund(request, IDEMPOTENCY_KEY));
    }

    @Test
    void refund_returnsCachedResponse_withoutTouchingBusinessLogic_whenIdempotencyKeyAlreadyCompleted() {
        RefundResponse cached = new RefundResponse("SUCCESS", BigDecimal.valueOf(30.0), BigDecimal.valueOf(100.0));
        when(idempotencyKeyService.claim(eq(MERCHANT_ID), eq("refund"), eq(IDEMPOTENCY_KEY), eq("hash"), eq(RefundResponse.class))).thenReturn(Optional.of(cached));

        RefundRequest request = new RefundRequest("GC-1", BigDecimal.valueOf(30.0), 42L, null);
        RefundResponse response = giftCardRefundService.refund(request, IDEMPOTENCY_KEY);

        assertEquals(cached, response);
        verify(giftCardRepository, never()).findByMerchantIdAndCardCodeForUpdate(any(), any());
        verify(idempotencyKeyService, never()).complete(any(), any(), any(), any());
    }

    @Test
    void refund_discardsClaim_whenBusinessLogicFails() {
        String unknownCode = "MISSING";
        when(giftCardRepository.findByMerchantIdAndCardCodeForUpdate(MERCHANT_ID, unknownCode)).thenReturn(Optional.empty());

        RefundRequest request = new RefundRequest(unknownCode, BigDecimal.valueOf(10.0), 1L, null);

        assertThrows(UnknownGiftCardException.class, () -> giftCardRefundService.refund(request, IDEMPOTENCY_KEY));
        verify(idempotencyKeyService).discard(MERCHANT_ID, "refund", IDEMPOTENCY_KEY);
        verify(idempotencyKeyService, never()).complete(any(), any(), any(), any());
    }
}
