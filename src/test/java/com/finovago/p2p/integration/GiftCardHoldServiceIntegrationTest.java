package com.finovago.p2p.integration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.finovago.p2p.AbstractIntegrationTest;
import com.finovago.p2p.dto.GiftCardCreateRequest;
import com.finovago.p2p.dto.HoldResponse;
import com.finovago.p2p.dto.ReserveRequest;
import com.finovago.p2p.exception.HoldAlreadyFinalizedException;
import com.finovago.p2p.exception.HoldNotFoundException;
import com.finovago.p2p.exception.InsufficientAvailableBalanceException;
import com.finovago.p2p.model.GiftCard;
import com.finovago.p2p.model.GiftCardHold;
import com.finovago.p2p.model.HoldStatus;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.repository.GiftCardHoldRepository;
import com.finovago.p2p.repository.GiftCardRepository;
import com.finovago.p2p.repository.IdempotencyKeyRepository;
import com.finovago.p2p.repository.LedgerEntryRepository;
import com.finovago.p2p.repository.MerchantRepository;
import com.finovago.p2p.repository.RefreshTokenRepository;
import com.finovago.p2p.repository.UserRepository;
import com.finovago.p2p.scheduler.HoldExpirationScheduler;
import com.finovago.p2p.security.AuthenticatedUser;
import com.finovago.p2p.service.GiftCardHoldService;
import com.finovago.p2p.service.GiftCardService;

class GiftCardHoldServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private GiftCardRepository giftCardRepository;

    @Autowired
    private GiftCardHoldRepository giftCardHoldRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private GiftCardService giftCardService;

    @Autowired
    private GiftCardHoldService giftCardHoldService;

    @Autowired
    private HoldExpirationScheduler holdExpirationScheduler;

    private Long merchantId;
    private static final String CARD_CODE = "HOLD-CARD";

    @BeforeEach
    void setUp() {
        idempotencyKeyRepository.deleteAll();
        giftCardHoldRepository.deleteAll();
        ledgerEntryRepository.deleteAll();
        giftCardRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        merchantRepository.deleteAll();

        Merchant merchant = merchantRepository.save(new Merchant("Test Merchant", "merchant@example.com"));
        merchantId = merchant.getId();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser("merchant@example.com", "MERCHANT", merchantId), null, List.of()));

        giftCardService.createGiftCard(new GiftCardCreateRequest(CARD_CODE, BigDecimal.valueOf(100.0), true, LocalDate.now().plusYears(1)));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private GiftCard reloadCard() {
        return giftCardRepository.findByMerchantIdAndCardCode(merchantId, CARD_CODE).orElseThrow();
    }

    private static void assertMoneyEquals(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }

    @Test
    void should_reserve_hold_without_touching_real_balance() {
        HoldResponse response = giftCardHoldService.reserve(new ReserveRequest(CARD_CODE, BigDecimal.valueOf(40.0)), UUID.randomUUID().toString());

        assertEquals("PENDING", response.status());
        assertMoneyEquals(BigDecimal.valueOf(60.0), response.remainingAvailableBalance());
        assertMoneyEquals(BigDecimal.valueOf(100.0), reloadCard().getBalance());
    }

    @Test
    void should_fail_reserve_when_amount_exceeds_available_balance() {
        giftCardHoldService.reserve(new ReserveRequest(CARD_CODE, BigDecimal.valueOf(70.0)), UUID.randomUUID().toString());

        assertThrows(InsufficientAvailableBalanceException.class,
                () -> giftCardHoldService.reserve(new ReserveRequest(CARD_CODE, BigDecimal.valueOf(40.0)), UUID.randomUUID().toString()));
    }

    @Test
    void should_capture_hold_and_deduct_real_balance() {
        HoldResponse reserved = giftCardHoldService.reserve(new ReserveRequest(CARD_CODE, BigDecimal.valueOf(40.0)), UUID.randomUUID().toString());

        HoldResponse captured = giftCardHoldService.capture(reserved.holdId());

        assertEquals("CAPTURED", captured.status());
        assertMoneyEquals(BigDecimal.valueOf(60.0), reloadCard().getBalance());
    }

    @Test
    void should_replay_response_on_double_capture_and_not_double_deduct() {
        HoldResponse reserved = giftCardHoldService.reserve(new ReserveRequest(CARD_CODE, BigDecimal.valueOf(40.0)), UUID.randomUUID().toString());
        giftCardHoldService.capture(reserved.holdId());

        HoldResponse replayed = giftCardHoldService.capture(reserved.holdId());

        assertEquals("CAPTURED", replayed.status());
        assertMoneyEquals(BigDecimal.valueOf(60.0), reloadCard().getBalance());
    }

    @Test
    void should_fail_capturing_a_released_hold() {
        HoldResponse reserved = giftCardHoldService.reserve(new ReserveRequest(CARD_CODE, BigDecimal.valueOf(40.0)), UUID.randomUUID().toString());
        giftCardHoldService.release(reserved.holdId());

        assertThrows(HoldAlreadyFinalizedException.class, () -> giftCardHoldService.capture(reserved.holdId()));
    }

    @Test
    void should_release_hold_and_free_up_available_balance_for_new_reserve() {
        HoldResponse reserved = giftCardHoldService.reserve(new ReserveRequest(CARD_CODE, BigDecimal.valueOf(70.0)), UUID.randomUUID().toString());

        HoldResponse released = giftCardHoldService.release(reserved.holdId());
        assertEquals("RELEASED", released.status());
        assertMoneyEquals(BigDecimal.valueOf(100.0), reloadCard().getBalance());

        HoldResponse secondReserve = giftCardHoldService.reserve(new ReserveRequest(CARD_CODE, BigDecimal.valueOf(70.0)), UUID.randomUUID().toString());
        assertEquals("PENDING", secondReserve.status());
    }

    @Test
    void should_replay_response_on_double_release() {
        HoldResponse reserved = giftCardHoldService.reserve(new ReserveRequest(CARD_CODE, BigDecimal.valueOf(40.0)), UUID.randomUUID().toString());
        giftCardHoldService.release(reserved.holdId());

        HoldResponse replayed = giftCardHoldService.release(reserved.holdId());

        assertEquals("RELEASED", replayed.status());
    }

    @Test
    void should_fail_releasing_a_captured_hold() {
        HoldResponse reserved = giftCardHoldService.reserve(new ReserveRequest(CARD_CODE, BigDecimal.valueOf(40.0)), UUID.randomUUID().toString());
        giftCardHoldService.capture(reserved.holdId());

        assertThrows(HoldAlreadyFinalizedException.class, () -> giftCardHoldService.release(reserved.holdId()));
    }

    @Test
    void should_return_not_found_for_capture_and_release_on_nonexistent_hold() {
        assertThrows(HoldNotFoundException.class, () -> giftCardHoldService.capture(999_999L));
        assertThrows(HoldNotFoundException.class, () -> giftCardHoldService.release(999_999L));
    }

    @Test
    void should_return_not_found_when_another_merchant_tries_to_capture_or_release() {
        HoldResponse reserved = giftCardHoldService.reserve(new ReserveRequest(CARD_CODE, BigDecimal.valueOf(40.0)), UUID.randomUUID().toString());

        Merchant otherMerchant = merchantRepository.save(new Merchant("Other Merchant", "other@example.com"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser("other@example.com", "MERCHANT", otherMerchant.getId()), null, List.of()));

        assertThrows(HoldNotFoundException.class, () -> giftCardHoldService.capture(reserved.holdId()));
        assertThrows(HoldNotFoundException.class, () -> giftCardHoldService.release(reserved.holdId()));
    }

    @Test
    void should_expire_pending_hold_past_ttl_via_scheduler_sweep() {
        GiftCard card = reloadCard();
        GiftCardHold expiredHold = giftCardHoldRepository.save(
                new GiftCardHold(card, merchantId, BigDecimal.valueOf(40.0), LocalDateTime.now().minusMinutes(1)));

        holdExpirationScheduler.sweepExpiredHolds();

        GiftCardHold reloaded = giftCardHoldRepository.findById(expiredHold.getId()).orElseThrow();
        assertEquals(HoldStatus.EXPIRED, reloaded.getStatus());
        assertThrows(HoldAlreadyFinalizedException.class, () -> giftCardHoldService.capture(expiredHold.getId()));
    }
}
