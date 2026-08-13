package com.finovago.p2p.unit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.finovago.p2p.dto.GiftCardCreateRequest;
import com.finovago.p2p.dto.GiftCardResponse;
import com.finovago.p2p.dto.LedgerEntryResponse;
import com.finovago.p2p.dto.RedemptionRequest;
import com.finovago.p2p.dto.RedemptionResponse;
import com.finovago.p2p.exception.ExpiredGiftCardException;
import com.finovago.p2p.exception.InactiveGiftCardException;
import com.finovago.p2p.exception.UnknownGiftCardException;
import com.finovago.p2p.model.GiftCard;
import com.finovago.p2p.model.LedgerEntry;
import com.finovago.p2p.model.LedgerEntryType;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.repository.GiftCardRepository;
import com.finovago.p2p.repository.MerchantRepository;
import com.finovago.p2p.security.CurrentUserContext;
import com.finovago.p2p.service.GiftCardService;
import com.finovago.p2p.service.IdempotencyKeyService;
import com.finovago.p2p.service.LedgerService;

@ExtendWith(MockitoExtension.class)
class GiftCardServiceUnitTest
{
    private static final Long MERCHANT_ID = 1L;
    private static final String IDEMPOTENCY_KEY = "client-key-abc";

    @Mock
    private GiftCardRepository giftCardRepository;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private CurrentUserContext currentUserContext;

    @Mock
    private IdempotencyKeyService idempotencyKeyService;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private Executor taskExecutor;

    @InjectMocks
    private GiftCardService giftCardService;

    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchant = new Merchant("Test Merchant", "merchant@example.com");
        lenient().when(currentUserContext.currentMerchantId()).thenReturn(MERCHANT_ID);
        lenient().when(currentUserContext.currentUserIdOrNull()).thenReturn(null);
        lenient().when(merchantRepository.getReferenceById(MERCHANT_ID)).thenReturn(merchant);
        // Runs the supplyAsync task synchronously on the calling thread so tests don't need to wait on a real pool.
        lenient().doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(taskExecutor).execute(any());
    }

    private static void assertMoneyEquals(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }

    @Test
    void should_throw_exception_when_card_code_is_invalid()
    {
        assertThrows(IllegalArgumentException.class, () -> giftCardService.executeRedemptionSync(MERCHANT_ID, null, BigDecimal.valueOf(100.0)));
        assertThrows(IllegalArgumentException.class, () -> giftCardService.executeRedemptionSync(MERCHANT_ID, "", BigDecimal.valueOf(100.0)));
    }

    @Test
    void should_throw_exception_when_amount_is_invalid()
    {
        assertThrows(IllegalArgumentException.class, () -> giftCardService.executeRedemptionSync(MERCHANT_ID, "VALID_CODE", BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> giftCardService.executeRedemptionSync(MERCHANT_ID, "VALID_CODE", BigDecimal.valueOf(-50.0)));
    }

    @Test
    void should_throw_exception_when_gift_card_not_found()
    {
        when(giftCardRepository.findByMerchantIdAndCardCode(MERCHANT_ID, "INVALID_CODE")).thenReturn(Optional.empty());
        assertThrows(UnknownGiftCardException.class, () -> giftCardService.executeRedemptionSync(MERCHANT_ID, "INVALID_CODE", BigDecimal.valueOf(100.0)));
    }

    @Test
    void should_throw_exception_when_gift_card_is_not_active()
    {
        String fakeGiftCardCode = "ABC123";
        GiftCard inactiveGiftCard = new GiftCard(merchant, fakeGiftCardCode, BigDecimal.valueOf(100.0), false, LocalDate.now().plusDays(30));
        when(giftCardRepository.findByMerchantIdAndCardCode(MERCHANT_ID, fakeGiftCardCode)).thenReturn(Optional.of(inactiveGiftCard));

        assertThrows(InactiveGiftCardException.class, () -> giftCardService.executeRedemptionSync(MERCHANT_ID, fakeGiftCardCode, BigDecimal.valueOf(50.0)));
        verify(ledgerService, never()).record(any(), any(), any(), any(BigDecimal.class), any(BigDecimal.class), any(), any());
    }

    @Test
    void should_throw_exception_when_gift_card_is_expired()
    {
        String expiredCardCode = "EXPIRED123";
        GiftCard expiredCard = new GiftCard(merchant, expiredCardCode, BigDecimal.valueOf(100.0), true, LocalDate.now().minusDays(1));
        when(giftCardRepository.findByMerchantIdAndCardCode(MERCHANT_ID, expiredCardCode)).thenReturn(Optional.of(expiredCard));

        assertThrows(ExpiredGiftCardException.class, () -> giftCardService.executeRedemptionSync(MERCHANT_ID, expiredCardCode, BigDecimal.valueOf(50.0)));
    }

    @Test
    void should_deduct_balance_when_sufficient_funds()
    {
        String cardCode = "VALID123";
        GiftCard activeCard = new GiftCard(merchant, cardCode, BigDecimal.valueOf(100.0), true, LocalDate.now().plusDays(30));

        when(giftCardRepository.findByMerchantIdAndCardCode(MERCHANT_ID, cardCode)).thenReturn(Optional.of(activeCard));

        RedemptionResponse response = giftCardService.executeRedemptionSync(MERCHANT_ID, cardCode, BigDecimal.valueOf(30.0));

        assertEquals("SUCCESS", response.status());
        assertMoneyEquals(BigDecimal.valueOf(30.0), response.deductedAmount());
        assertMoneyEquals(BigDecimal.valueOf(70.0), response.remainingBalance());
        assertMoneyEquals(BigDecimal.ZERO, response.remainingToPay());
        // executeRedemptionSync normalizes the incoming amount to scale 2, so the ledger record
        // reflects "30.00"/"70.00", not the scale-1 literals the request/entity were built with.
        verify(ledgerService).record(eq(activeCard), eq(MERCHANT_ID), eq(LedgerEntryType.REDEMPTION), eq(new BigDecimal("30.00")), eq(new BigDecimal("70.00")), eq(null), eq(null));
    }

    @Test
    void redeemGiftCardAsync_returnsCachedResponse_withoutTouchingBusinessLogic_whenIdempotencyKeyAlreadyCompleted() {
        RedemptionResponse cached = new RedemptionResponse("SUCCESS", BigDecimal.valueOf(30.0), BigDecimal.valueOf(70.0), BigDecimal.ZERO);
        when(idempotencyKeyService.hashRequest("VALID123", "30.00")).thenReturn("hash");
        when(idempotencyKeyService.claim(MERCHANT_ID, IDEMPOTENCY_KEY, "hash", RedemptionResponse.class)).thenReturn(Optional.of(cached));

        CompletableFuture<RedemptionResponse> future = giftCardService.redeemGiftCardAsync(
                new RedemptionRequest(BigDecimal.valueOf(30.0), "VALID123"), IDEMPOTENCY_KEY);

        assertEquals(cached, future.join());
        verify(giftCardRepository, never()).findByMerchantIdAndCardCode(any(), any());
        verify(idempotencyKeyService, never()).complete(any(), any(), any());
    }

    @Test
    void redeemGiftCardAsync_executesAndRecordsCompletion_whenNoCachedResponse() {
        String cardCode = "VALID123";
        GiftCard activeCard = new GiftCard(merchant, cardCode, BigDecimal.valueOf(100.0), true, LocalDate.now().plusDays(30));
        when(idempotencyKeyService.hashRequest(cardCode, "30.00")).thenReturn("hash");
        when(idempotencyKeyService.claim(MERCHANT_ID, IDEMPOTENCY_KEY, "hash", RedemptionResponse.class)).thenReturn(Optional.empty());
        when(giftCardRepository.findByMerchantIdAndCardCode(MERCHANT_ID, cardCode)).thenReturn(Optional.of(activeCard));

        CompletableFuture<RedemptionResponse> future = giftCardService.redeemGiftCardAsync(
                new RedemptionRequest(BigDecimal.valueOf(30.0), cardCode), IDEMPOTENCY_KEY);
        RedemptionResponse response = future.join();

        assertMoneyEquals(BigDecimal.valueOf(30.0), response.deductedAmount());
        verify(idempotencyKeyService).complete(eq(MERCHANT_ID), eq(IDEMPOTENCY_KEY), eq(response));
    }

    @Test
    void redeemGiftCardAsync_discardsClaim_whenBusinessLogicFails() {
        String unknownCode = "MISSING";
        when(idempotencyKeyService.hashRequest(unknownCode, "30.00")).thenReturn("hash");
        when(idempotencyKeyService.claim(MERCHANT_ID, IDEMPOTENCY_KEY, "hash", RedemptionResponse.class)).thenReturn(Optional.empty());
        when(giftCardRepository.findByMerchantIdAndCardCode(MERCHANT_ID, unknownCode)).thenReturn(Optional.empty());

        CompletableFuture<RedemptionResponse> future = giftCardService.redeemGiftCardAsync(
                new RedemptionRequest(BigDecimal.valueOf(30.0), unknownCode), IDEMPOTENCY_KEY);

        CompletionException thrown = assertThrows(CompletionException.class, future::join);
        assertEquals(UnknownGiftCardException.class, thrown.getCause().getClass());
        verify(idempotencyKeyService).discard(MERCHANT_ID, IDEMPOTENCY_KEY);
        verify(idempotencyKeyService, never()).complete(any(), any(), any());
    }

    @Test
    void should_throw_exception_when_code_already_exists() {
        String existingCode = "ALREADY_EXISTS";
        GiftCard fakeExistingCard = new GiftCard(merchant, existingCode, BigDecimal.valueOf(100.0), true, LocalDate.now().plusDays(30));

        when(giftCardRepository.findByMerchantIdAndCardCode(MERCHANT_ID, existingCode)).thenReturn(Optional.of(fakeExistingCard));

        assertThrows(IllegalArgumentException.class, () ->
            giftCardService.createGiftCard(new GiftCardCreateRequest(existingCode, BigDecimal.ZERO, false, LocalDate.now().plusDays(30)))
        );
        verify(ledgerService, never()).record(any(), any(), any(), any(BigDecimal.class), any(BigDecimal.class), any(), any());
    }

    @Test
    void should_use_default_expiration_date_when_not_provided() {
        String cardCode = "DEFAULT_DATE";
        LocalDate expectedDate = LocalDate.now().plusYears(2);
        GiftCard savedCard = new GiftCard(merchant, cardCode, BigDecimal.valueOf(100.0), true, expectedDate);

        when(giftCardRepository.findByMerchantIdAndCardCode(MERCHANT_ID, cardCode)).thenReturn(Optional.empty());
        when(giftCardRepository.save(ArgumentMatchers.any(GiftCard.class))).thenReturn(savedCard);

        var response = giftCardService.createGiftCard(new GiftCardCreateRequest(cardCode, BigDecimal.valueOf(100.0), true, null));

        assertEquals(expectedDate, response.expirationDate());
        verify(ledgerService).record(eq(savedCard), eq(MERCHANT_ID), eq(LedgerEntryType.CREATION), eq(BigDecimal.valueOf(100.0)), eq(BigDecimal.valueOf(100.0)), eq(null), eq(null));
    }

    @Test
    void should_drain_card_when_insufficient_funds()
    {
        String cardCode = "VALID456";
        GiftCard activeCard = new GiftCard(merchant, cardCode, BigDecimal.valueOf(20.0), true, LocalDate.now().plusDays(30));

        when(giftCardRepository.findByMerchantIdAndCardCode(MERCHANT_ID, cardCode)).thenReturn(Optional.of(activeCard));

        RedemptionResponse response = giftCardService.executeRedemptionSync(MERCHANT_ID, cardCode, BigDecimal.valueOf(50.0));

        assertEquals("SUCCESS", response.status());
        assertMoneyEquals(BigDecimal.valueOf(20.0), response.deductedAmount());
        assertMoneyEquals(BigDecimal.ZERO, response.remainingBalance());
        assertMoneyEquals(BigDecimal.valueOf(30.0), response.remainingToPay());
        // drainCard() zeroes the balance as "0.00" (scale 2), not the bare BigDecimal.ZERO (scale 0).
        verify(ledgerService).record(eq(activeCard), eq(MERCHANT_ID), eq(LedgerEntryType.REDEMPTION), eq(BigDecimal.valueOf(20.0)), eq(BigDecimal.ZERO.setScale(2)), eq(null), eq(null));
    }

    @Test
    void should_return_gift_card_details_on_lookup()
    {
        String cardCode = "LOOKUP123";
        LocalDate expirationDate = LocalDate.now().plusDays(30);
        GiftCard giftCard = new GiftCard(merchant, cardCode, BigDecimal.valueOf(150.0), true, expirationDate);

        when(giftCardRepository.findByMerchantIdAndCardCode(MERCHANT_ID, cardCode)).thenReturn(Optional.of(giftCard));

        GiftCardResponse response = giftCardService.lookupGiftCard(cardCode);

        assertEquals(cardCode, response.giftCardCode());
        assertMoneyEquals(BigDecimal.valueOf(150.0), response.balance());
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

    @Test
    void should_return_ledger_entries_oldest_first_on_getLedger()
    {
        String cardCode = "LEDGER123";
        GiftCard giftCard = new GiftCard(merchant, cardCode, BigDecimal.valueOf(70.0), true, LocalDate.now().plusDays(30));
        LedgerEntry creation = new LedgerEntry(giftCard, MERCHANT_ID, LedgerEntryType.CREATION, new BigDecimal("100.00"), new BigDecimal("100.00"), null, 7L);
        LedgerEntry redemption = new LedgerEntry(giftCard, MERCHANT_ID, LedgerEntryType.REDEMPTION, new BigDecimal("30.00"), new BigDecimal("70.00"), null, null);

        when(giftCardRepository.findByMerchantIdAndCardCode(MERCHANT_ID, cardCode)).thenReturn(Optional.of(giftCard));
        List<LedgerEntry> entries = List.of(creation, redemption);
        when(ledgerService.getEntriesForCard(giftCard.getId())).thenReturn(entries);
        when(ledgerService.resolveActors(entries)).thenReturn(java.util.Map.of(7L, "merchant@example.com"));

        List<LedgerEntryResponse> response = giftCardService.getLedger(cardCode);

        assertEquals(2, response.size());
        assertEquals("CREATION", response.get(0).entryType());
        assertMoneyEquals(new BigDecimal("100.00"), response.get(0).amount());
        assertEquals("merchant@example.com", response.get(0).actor());
        assertEquals("REDEMPTION", response.get(1).entryType());
        assertMoneyEquals(new BigDecimal("30.00"), response.get(1).amount());
        assertMoneyEquals(new BigDecimal("70.00"), response.get(1).balanceAfter());
        assertEquals("Unknown", response.get(1).actor());
    }

    @Test
    void should_throw_exception_when_getting_ledger_for_non_existent_card()
    {
        String nonExistentCode = "NONEXISTENT";
        when(giftCardRepository.findByMerchantIdAndCardCode(MERCHANT_ID, nonExistentCode)).thenReturn(Optional.empty());

        assertThrows(UnknownGiftCardException.class, () -> giftCardService.getLedger(nonExistentCode));
        verify(ledgerService, never()).getEntriesForCard(any());
    }

    @Test
    void should_list_cards_across_all_merchants_when_caller_is_admin()
    {
        when(currentUserContext.isAdmin()).thenReturn(true);
        GiftCard cardA = new GiftCard(merchant, "GC-A", BigDecimal.valueOf(100.0), true, LocalDate.now().plusDays(30));
        GiftCard cardB = new GiftCard(merchant, "GC-B", BigDecimal.valueOf(200.0), true, LocalDate.now().plusDays(30));
        when(giftCardRepository.findAll()).thenReturn(List.of(cardA, cardB));

        List<GiftCardResponse> response = giftCardService.getAllGiftCards();

        assertEquals(2, response.size());
        assertEquals("GC-A", response.get(0).giftCardCode());
        assertEquals("GC-B", response.get(1).giftCardCode());
        verify(giftCardRepository, never()).findAllByMerchantId(any());
    }

    @Test
    void should_list_only_own_cards_when_caller_is_merchant()
    {
        when(currentUserContext.isAdmin()).thenReturn(false);
        GiftCard card = new GiftCard(merchant, "GC-OWN", BigDecimal.valueOf(50.0), true, LocalDate.now().plusDays(30));
        when(giftCardRepository.findAllByMerchantId(MERCHANT_ID)).thenReturn(List.of(card));

        List<GiftCardResponse> response = giftCardService.getAllGiftCards();

        assertEquals(1, response.size());
        assertEquals("GC-OWN", response.get(0).giftCardCode());
        verify(giftCardRepository, never()).findAll();
    }
}
