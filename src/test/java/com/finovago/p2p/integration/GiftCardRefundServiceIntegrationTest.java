package com.finovago.p2p.integration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.finovago.p2p.AbstractIntegrationTest;
import com.finovago.p2p.config.PostgresTestcontainerInitializer;
import com.finovago.p2p.dto.GiftCardCreateRequest;
import com.finovago.p2p.dto.RedemptionRequest;
import com.finovago.p2p.dto.RedemptionResponse;
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
import com.finovago.p2p.model.Role;
import com.finovago.p2p.model.User;
import com.finovago.p2p.repository.GiftCardRepository;
import com.finovago.p2p.repository.IdempotencyKeyRepository;
import com.finovago.p2p.repository.LedgerEntryRepository;
import com.finovago.p2p.repository.MerchantRepository;
import com.finovago.p2p.repository.RefreshTokenRepository;
import com.finovago.p2p.repository.UserRepository;
import com.finovago.p2p.security.AuthenticatedUser;
import com.finovago.p2p.service.GiftCardRefundService;
import com.finovago.p2p.service.GiftCardService;

class GiftCardRefundServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private GiftCardRepository giftCardRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private GiftCardService giftCardService;

    @Autowired
    private GiftCardRefundService giftCardRefundService;

    private Long merchantId;
    private Long userId;
    private static final String CARD_CODE = "REFUND-CARD";

    @BeforeEach
    void setUp() {
        // Same non-@Transactional + manual cleanup pattern as GiftCardServiceIntegrationTest for
        // consistency, even though refund() itself is synchronous.
        idempotencyKeyRepository.deleteAll();
        PostgresTestcontainerInitializer.executeAsMigrator("TRUNCATE TABLE gift_card_ledger RESTART IDENTITY");
        giftCardRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        merchantRepository.deleteAll();

        Merchant merchant = merchantRepository.save(new Merchant("Test Merchant", "giftcard-refund-test@example.com"));
        merchantId = merchant.getId();
        userId = userRepository.save(new User("giftcard-refund-test@example.com", "hashed", Role.MERCHANT, merchant)).getId();

        AuthenticatedUser authenticatedUser = new AuthenticatedUser("giftcard-refund-test@example.com", "MERCHANT", merchantId, userId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(authenticatedUser, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void assertMoneyEquals(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }

    private Long createCardAndRedeem(BigDecimal initialBalance, BigDecimal redeemAmount) {
        giftCardService.createGiftCard(new GiftCardCreateRequest(CARD_CODE, initialBalance, true, LocalDate.now().plusYears(1)));
        CompletableFuture<RedemptionResponse> future = giftCardService.redeemGiftCardAsync(
                new RedemptionRequest(redeemAmount, CARD_CODE), UUID.randomUUID().toString());
        future.join();

        GiftCard card = giftCardRepository.findByMerchantIdAndCardCode(merchantId, CARD_CODE).orElseThrow();
        List<LedgerEntry> entries = ledgerEntryRepository.findByGiftCardIdOrderByCreatedAtAsc(card.getId());
        return entries.stream()
                .filter(e -> e.getEntryType() == LedgerEntryType.REDEMPTION)
                .findFirst().orElseThrow()
                .getId();
    }

    @Test
    void should_refund_a_redemption_and_persist_related_entry_id() {
        Long redemptionEntryId = createCardAndRedeem(BigDecimal.valueOf(100.0), BigDecimal.valueOf(40.0));

        RefundResponse response = giftCardRefundService.refund(
                new RefundRequest(CARD_CODE, BigDecimal.valueOf(40.0), redemptionEntryId, null),
                UUID.randomUUID().toString());

        assertEquals("SUCCESS", response.status());
        GiftCard card = giftCardRepository.findByMerchantIdAndCardCode(merchantId, CARD_CODE).orElseThrow();
        assertMoneyEquals(BigDecimal.valueOf(100.0), card.getBalance());

        List<LedgerEntry> entries = ledgerEntryRepository.findByGiftCardIdOrderByCreatedAtAsc(card.getId());
        LedgerEntry refundEntry = entries.stream().filter(e -> e.getEntryType() == LedgerEntryType.REFUND).findFirst().orElseThrow();
        assertEquals(redemptionEntryId, refundEntry.getRelatedEntryId());
        assertEquals(userId, refundEntry.getActorUserId());
    }

    @Test
    void should_reject_refund_exceeding_remaining_refundable_amount() {
        Long redemptionEntryId = createCardAndRedeem(BigDecimal.valueOf(100.0), BigDecimal.valueOf(40.0));

        giftCardRefundService.refund(
                new RefundRequest(CARD_CODE, BigDecimal.valueOf(25.0), redemptionEntryId, null),
                UUID.randomUUID().toString());

        assertThrows(RefundExceedsOriginalAmountException.class, () -> giftCardRefundService.refund(
                new RefundRequest(CARD_CODE, BigDecimal.valueOf(20.0), redemptionEntryId, null),
                UUID.randomUUID().toString()));
    }

    @Test
    void should_reject_refund_of_a_non_redemption_entry() {
        giftCardService.createGiftCard(new GiftCardCreateRequest(CARD_CODE, BigDecimal.valueOf(50.0), true, LocalDate.now().plusYears(1)));
        GiftCard card = giftCardRepository.findByMerchantIdAndCardCode(merchantId, CARD_CODE).orElseThrow();
        LedgerEntry creationEntry = ledgerEntryRepository.findByGiftCardIdOrderByCreatedAtAsc(card.getId()).get(0);

        assertThrows(InvalidRefundTargetException.class, () -> giftCardRefundService.refund(
                new RefundRequest(CARD_CODE, BigDecimal.valueOf(10.0), creationEntry.getId(), null),
                UUID.randomUUID().toString()));
    }

    @Test
    void should_reject_refund_targeting_another_merchants_ledger_entry() {
        Long redemptionEntryId = createCardAndRedeem(BigDecimal.valueOf(100.0), BigDecimal.valueOf(40.0));

        Merchant otherMerchant = merchantRepository.save(new Merchant("Other Merchant", "other-refund-test@example.com"));
        Long otherUserId = userRepository.save(new User("other-refund-test@example.com", "hashed", Role.MERCHANT, otherMerchant)).getId();
        giftCardRepository.save(new GiftCard(otherMerchant, CARD_CODE, BigDecimal.valueOf(10.0), true, LocalDate.now().plusYears(1)));

        AuthenticatedUser otherUser = new AuthenticatedUser("other-refund-test@example.com", "MERCHANT", otherMerchant.getId(), otherUserId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(otherUser, null, List.of()));

        assertThrows(LedgerEntryNotFoundException.class, () -> giftCardRefundService.refund(
                new RefundRequest(CARD_CODE, BigDecimal.valueOf(5.0), redemptionEntryId, null),
                UUID.randomUUID().toString()));
    }

    @Test
    void should_throw_exception_when_refunding_another_merchants_card() {
        createCardAndRedeem(BigDecimal.valueOf(100.0), BigDecimal.valueOf(40.0));

        Merchant otherMerchant = merchantRepository.save(new Merchant("Other Merchant 2", "other-refund-test-2@example.com"));
        Long otherUserId = userRepository.save(new User("other-refund-test-2@example.com", "hashed", Role.MERCHANT, otherMerchant)).getId();
        AuthenticatedUser otherUser = new AuthenticatedUser("other-refund-test-2@example.com", "MERCHANT", otherMerchant.getId(), otherUserId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(otherUser, null, List.of()));

        assertThrows(UnknownGiftCardException.class, () -> giftCardRefundService.refund(
                new RefundRequest(CARD_CODE, BigDecimal.valueOf(5.0), 1L, null),
                UUID.randomUUID().toString()));
    }

    @Test
    void should_allow_refund_from_an_api_key() {
        Long redemptionEntryId = createCardAndRedeem(BigDecimal.valueOf(100.0), BigDecimal.valueOf(40.0));

        AuthenticatedUser apiKeyCaller = new AuthenticatedUser("api-key:fovak_test", "MERCHANT", merchantId, null, true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(apiKeyCaller, null, List.of()));

        RefundResponse response = giftCardRefundService.refund(
                new RefundRequest(CARD_CODE, BigDecimal.valueOf(40.0), redemptionEntryId, null),
                UUID.randomUUID().toString());

        assertEquals("SUCCESS", response.status());
    }
}
