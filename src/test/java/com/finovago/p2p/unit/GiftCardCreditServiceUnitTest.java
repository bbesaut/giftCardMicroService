package com.finovago.p2p.unit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.finovago.p2p.dto.CreditRequest;
import com.finovago.p2p.dto.CreditResponse;
import com.finovago.p2p.exception.ServiceAccountNotAllowedException;
import com.finovago.p2p.exception.UnknownGiftCardException;
import com.finovago.p2p.model.GiftCard;
import com.finovago.p2p.model.LedgerEntryType;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.model.Role;
import com.finovago.p2p.model.User;
import com.finovago.p2p.repository.GiftCardRepository;
import com.finovago.p2p.repository.UserRepository;
import com.finovago.p2p.security.CurrentUserContext;
import com.finovago.p2p.service.GiftCardCreditService;
import com.finovago.p2p.service.IdempotencyKeyService;
import com.finovago.p2p.service.LedgerService;

@ExtendWith(MockitoExtension.class)
class GiftCardCreditServiceUnitTest {
    private static final Long MERCHANT_ID = 1L;
    private static final Long USER_ID = 5L;
    private static final String IDEMPOTENCY_KEY = "client-credit-key";

    @Mock
    private GiftCardRepository giftCardRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrentUserContext currentUserContext;

    @Mock
    private IdempotencyKeyService idempotencyKeyService;

    @Mock
    private LedgerService ledgerService;

    private GiftCardCreditService giftCardCreditService;

    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchant = new Merchant("Test Merchant", "merchant@example.com");
        giftCardCreditService = new GiftCardCreditService(giftCardRepository, userRepository, currentUserContext, idempotencyKeyService, ledgerService);
        lenient().when(currentUserContext.currentMerchantId()).thenReturn(MERCHANT_ID);
        lenient().when(currentUserContext.currentUserIdOrNull()).thenReturn(USER_ID);
        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(humanUser()));
        lenient().when(idempotencyKeyService.hashRequest(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("hash");
        lenient().when(idempotencyKeyService.claim(any(), any(), any(), eq(CreditResponse.class))).thenReturn(Optional.empty());
    }

    private static void assertMoneyEquals(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }

    private GiftCard activeCard(String code, double balance) {
        return new GiftCard(merchant, code, BigDecimal.valueOf(balance), true, LocalDate.now().plusDays(30));
    }

    private User humanUser() {
        return new User("employee@example.com", "hashed", Role.MERCHANT, merchant, false);
    }

    @Test
    void should_credit_when_adjustment_has_a_reason() {
        String cardCode = "GC-2";
        GiftCard card = activeCard(cardCode, 50.0);

        when(giftCardRepository.findByMerchantIdAndCardCodeForUpdate(MERCHANT_ID, cardCode)).thenReturn(Optional.of(card));

        CreditRequest request = new CreditRequest(cardCode, BigDecimal.valueOf(20.0), "Goodwill gesture - support ticket #123");
        CreditResponse response = giftCardCreditService.credit(request, IDEMPOTENCY_KEY);

        assertEquals("SUCCESS", response.status());
        assertMoneyEquals(new BigDecimal("70.00"), response.newBalance());
        verify(ledgerService).record(eq(card), eq(MERCHANT_ID), eq(LedgerEntryType.ADJUSTMENT), eq(new BigDecimal("20.00")), eq(new BigDecimal("70.00")), eq(null), eq(USER_ID), eq(false), eq(null), eq("Goodwill gesture - support ticket #123"));
    }

    @Test
    void should_throw_exception_when_caller_has_no_resolvable_user() {
        // An API-key-authenticated call has no backing User at all - currentUserIdOrNull() is null.
        when(currentUserContext.currentUserIdOrNull()).thenReturn(null);

        CreditRequest request = new CreditRequest("GC-2", BigDecimal.valueOf(20.0), "Some reason");

        assertThrows(ServiceAccountNotAllowedException.class, () -> giftCardCreditService.credit(request, IDEMPOTENCY_KEY));
        verify(giftCardRepository, never()).findByMerchantIdAndCardCodeForUpdate(any(), any());
        verify(idempotencyKeyService, never()).claim(any(), any(), any(), any());
    }

    @Test
    void should_throw_exception_when_card_not_found() {
        String unknownCode = "MISSING";
        when(giftCardRepository.findByMerchantIdAndCardCodeForUpdate(MERCHANT_ID, unknownCode)).thenReturn(Optional.empty());

        CreditRequest request = new CreditRequest(unknownCode, BigDecimal.valueOf(10.0), "reason");

        assertThrows(UnknownGiftCardException.class, () -> giftCardCreditService.credit(request, IDEMPOTENCY_KEY));
    }

    @Test
    void credit_returnsCachedResponse_withoutTouchingBusinessLogic_whenIdempotencyKeyAlreadyCompleted() {
        CreditResponse cached = new CreditResponse("SUCCESS", BigDecimal.valueOf(20.0), BigDecimal.valueOf(70.0));
        when(idempotencyKeyService.claim(eq(MERCHANT_ID), eq(IDEMPOTENCY_KEY), eq("hash"), eq(CreditResponse.class))).thenReturn(Optional.of(cached));

        CreditRequest request = new CreditRequest("GC-2", BigDecimal.valueOf(20.0), "reason");
        CreditResponse response = giftCardCreditService.credit(request, IDEMPOTENCY_KEY);

        assertEquals(cached, response);
        verify(giftCardRepository, never()).findByMerchantIdAndCardCodeForUpdate(any(), any());
        verify(idempotencyKeyService, never()).complete(any(), any(), any());
    }

    @Test
    void credit_discardsClaim_whenBusinessLogicFails() {
        String unknownCode = "MISSING";
        when(giftCardRepository.findByMerchantIdAndCardCodeForUpdate(MERCHANT_ID, unknownCode)).thenReturn(Optional.empty());

        CreditRequest request = new CreditRequest(unknownCode, BigDecimal.valueOf(10.0), "reason");

        assertThrows(UnknownGiftCardException.class, () -> giftCardCreditService.credit(request, IDEMPOTENCY_KEY));
        verify(idempotencyKeyService).discard(MERCHANT_ID, IDEMPOTENCY_KEY);
        verify(idempotencyKeyService, never()).complete(any(), any(), any());
    }
}
