package com.finovago.p2p.integration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.finovago.p2p.AbstractIntegrationTest;
import com.finovago.p2p.config.PostgresTestcontainerInitializer;
import com.finovago.p2p.dto.GiftCardCreateRequest;
import com.finovago.p2p.dto.HoldResponse;
import com.finovago.p2p.dto.RedemptionRequest;
import com.finovago.p2p.dto.RedemptionResponse;
import com.finovago.p2p.dto.ReserveRequest;
import com.finovago.p2p.exception.ExpiredGiftCardException;
import com.finovago.p2p.model.GiftCard;
import com.finovago.p2p.model.LedgerEntry;
import com.finovago.p2p.model.LedgerEntryType;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.repository.GiftCardHoldRepository;
import com.finovago.p2p.repository.GiftCardRepository;
import com.finovago.p2p.repository.IdempotencyKeyRepository;
import com.finovago.p2p.repository.LedgerEntryRepository;
import com.finovago.p2p.repository.MerchantRepository;
import com.finovago.p2p.repository.RefreshTokenRepository;
import com.finovago.p2p.repository.UserRepository;
import com.finovago.p2p.security.AuthenticatedUser;
import com.finovago.p2p.service.GiftCardHoldService;
import com.finovago.p2p.service.GiftCardService;

class LedgerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private GiftCardRepository giftCardRepository;

    @Autowired
    private GiftCardHoldRepository giftCardHoldRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GiftCardService giftCardService;

    @Autowired
    private GiftCardHoldService giftCardHoldService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long merchantId;
    private static final String CARD_CODE = "LEDGER-CARD";

    @BeforeEach
    void setUp() {
        // Redemption runs on a separate thread pool, so this class deliberately does NOT use
        // @Transactional - a test-managed transaction wouldn't be visible to that thread.
        idempotencyKeyRepository.deleteAll();
        giftCardHoldRepository.deleteAll();
        // p2p_app (the app role) can't DELETE from gift_card_ledger by design (see
        // V17__restrict_app_role_privileges.sql) - only the migration/owner role can reset it for tests.
        PostgresTestcontainerInitializer.executeAsMigrator("TRUNCATE TABLE gift_card_ledger RESTART IDENTITY");
        giftCardRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        merchantRepository.deleteAll();

        Merchant merchant = merchantRepository.save(new Merchant("Test Merchant", "merchant@example.com"));
        merchantId = merchant.getId();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser("merchant@example.com", "MERCHANT", merchantId, null), null, List.of()));
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

    private BigDecimal reconstructBalanceFromLedger(Long giftCardId) {
        return ledgerEntryRepository.findByGiftCardIdOrderByCreatedAtAsc(giftCardId).stream()
                .filter(entry -> entry.getEntryType() != LedgerEntryType.HOLD_PLACED
                        && entry.getEntryType() != LedgerEntryType.HOLD_RELEASED)
                .map(entry -> entry.getEntryType() == LedgerEntryType.CREATION ? entry.getAmount() : entry.getAmount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void createGiftCard_writesCreationLedgerEntry() {
        giftCardService.createGiftCard(new GiftCardCreateRequest(CARD_CODE, BigDecimal.valueOf(100.0), true, LocalDate.now().plusYears(1)));

        GiftCard card = reloadCard();
        List<LedgerEntry> entries = ledgerEntryRepository.findByGiftCardIdOrderByCreatedAtAsc(card.getId());

        assertEquals(1, entries.size());
        LedgerEntry entry = entries.get(0);
        assertEquals(LedgerEntryType.CREATION, entry.getEntryType());
        assertMoneyEquals(BigDecimal.valueOf(100.0), entry.getAmount());
        assertMoneyEquals(BigDecimal.valueOf(100.0), entry.getBalanceAfter());
        assertEquals(merchantId, entry.getMerchantId());
    }

    @Test
    void fullLifecycle_createRedeemReserveCapture_ledgerReconstructsFinalBalance() {
        giftCardService.createGiftCard(new GiftCardCreateRequest(CARD_CODE, BigDecimal.valueOf(100.0), true, LocalDate.now().plusYears(1)));

        CompletableFuture<RedemptionResponse> redemption = giftCardService.redeemGiftCardAsync(
                new RedemptionRequest(BigDecimal.valueOf(20.0), CARD_CODE), UUID.randomUUID().toString());
        redemption.join();

        HoldResponse reserved = giftCardHoldService.reserve(new ReserveRequest(CARD_CODE, BigDecimal.valueOf(30.0)), UUID.randomUUID().toString());
        giftCardHoldService.capture(reserved.holdId());

        GiftCard card = reloadCard();
        List<LedgerEntry> entries = ledgerEntryRepository.findByGiftCardIdOrderByCreatedAtAsc(card.getId());

        assertEquals(4, entries.size());
        assertEquals(List.of(
                LedgerEntryType.CREATION,
                LedgerEntryType.REDEMPTION,
                LedgerEntryType.HOLD_PLACED,
                LedgerEntryType.HOLD_CAPTURED
        ), entries.stream().map(LedgerEntry::getEntryType).toList());

        assertMoneyEquals(BigDecimal.valueOf(50.0), card.getBalance());
        assertMoneyEquals(card.getBalance(), reconstructBalanceFromLedger(card.getId()));
    }

    @Test
    void placedThenReleasedHold_doesNotAffectReconstructedBalance() {
        giftCardService.createGiftCard(new GiftCardCreateRequest(CARD_CODE, BigDecimal.valueOf(100.0), true, LocalDate.now().plusYears(1)));

        HoldResponse reserved = giftCardHoldService.reserve(new ReserveRequest(CARD_CODE, BigDecimal.valueOf(40.0)), UUID.randomUUID().toString());
        giftCardHoldService.release(reserved.holdId());

        GiftCard card = reloadCard();
        List<LedgerEntry> entries = ledgerEntryRepository.findByGiftCardIdOrderByCreatedAtAsc(card.getId());

        assertEquals(3, entries.size());
        assertMoneyEquals(BigDecimal.valueOf(100.0), card.getBalance());
        assertMoneyEquals(card.getBalance(), reconstructBalanceFromLedger(card.getId()));
    }

    @Test
    void failedRedemption_writesNoLedgerEntry() {
        giftCardService.createGiftCard(new GiftCardCreateRequest(CARD_CODE, BigDecimal.valueOf(100.0), true, LocalDate.now().minusDays(1)));

        CompletableFuture<RedemptionResponse> redemption = giftCardService.redeemGiftCardAsync(
                new RedemptionRequest(BigDecimal.valueOf(20.0), CARD_CODE), UUID.randomUUID().toString());

        try {
            redemption.join();
            fail("Expected ExpiredGiftCardException to be thrown");
        } catch (Exception e) {
            if (!(e.getCause() instanceof ExpiredGiftCardException)) {
                throw e;
            }
        }

        GiftCard card = reloadCard();
        List<LedgerEntry> entries = ledgerEntryRepository.findByGiftCardIdOrderByCreatedAtAsc(card.getId());

        assertEquals(1, entries.size());
        assertEquals(LedgerEntryType.CREATION, entries.get(0).getEntryType());
    }

    @Test
    void ledgerEntries_areScopedPerMerchant_notMixedAcrossTenants() {
        giftCardService.createGiftCard(new GiftCardCreateRequest(CARD_CODE, BigDecimal.valueOf(100.0), true, LocalDate.now().plusYears(1)));

        Merchant otherMerchant = merchantRepository.save(new Merchant("Other Merchant", "other@example.com"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser("other@example.com", "MERCHANT", otherMerchant.getId(), null), null, List.of()));
        giftCardService.createGiftCard(new GiftCardCreateRequest(CARD_CODE, BigDecimal.valueOf(200.0), true, LocalDate.now().plusYears(1)));

        List<LedgerEntry> allEntries = ledgerEntryRepository.findAll();
        assertEquals(2, allEntries.size());

        long merchantIdsRepresented = allEntries.stream().map(LedgerEntry::getMerchantId).distinct().count();
        assertEquals(2, merchantIdsRepresented);
    }

    @Test
    void gift_card_ledger_isAppendOnly_updateIsRejectedByDatabase() {
        giftCardService.createGiftCard(new GiftCardCreateRequest(CARD_CODE, BigDecimal.valueOf(100.0), true, LocalDate.now().plusYears(1)));
        LedgerEntry entry = ledgerEntryRepository.save(
                new LedgerEntry(reloadCard(), merchantId, LedgerEntryType.CREATION,
                        BigDecimal.valueOf(100.0), BigDecimal.valueOf(100.0), null));

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "UPDATE gift_card_ledger SET amount = 999 WHERE id = ?", entry.getId()));
    }

    @Test
    void gift_card_ledger_isAppendOnly_deleteIsRejectedByDatabase() {
        giftCardService.createGiftCard(new GiftCardCreateRequest(CARD_CODE, BigDecimal.valueOf(100.0), true, LocalDate.now().plusYears(1)));
        LedgerEntry entry = ledgerEntryRepository.save(
                new LedgerEntry(reloadCard(), merchantId, LedgerEntryType.CREATION,
                        BigDecimal.valueOf(100.0), BigDecimal.valueOf(100.0), null));

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "DELETE FROM gift_card_ledger WHERE id = ?", entry.getId()));
    }
}
