package com.finovago.p2p.integration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.finovago.p2p.AbstractIntegrationTest;
import com.finovago.p2p.config.PostgresTestcontainerInitializer;
import com.finovago.p2p.dto.GiftCardCreateRequest;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.repository.GiftCardHoldRepository;
import com.finovago.p2p.repository.GiftCardRepository;
import com.finovago.p2p.repository.IdempotencyKeyRepository;
import com.finovago.p2p.repository.LedgerEntryRepository;
import com.finovago.p2p.repository.MerchantRepository;
import com.finovago.p2p.repository.RefreshTokenRepository;
import com.finovago.p2p.repository.UserRepository;
import com.finovago.p2p.security.AuthenticatedUser;
import com.finovago.p2p.service.GiftCardService;

// Exercises the pg_catalog query directly against real Postgres partitions created by V21 -
// a Mockito unit test can't catch mistakes in the native SQL itself.
class LedgerPartitionMonitorIntegrationTest extends AbstractIntegrationTest {

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
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GiftCardService giftCardService;

    private Long merchantId;
    private Long giftCardId;
    private static final String CARD_CODE = "PARTITION-CARD";

    @BeforeEach
    void setUp() {
        idempotencyKeyRepository.deleteAll();
        giftCardHoldRepository.deleteAll();
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

        giftCardService.createGiftCard(new GiftCardCreateRequest(CARD_CODE, BigDecimal.valueOf(100.0), true, LocalDate.now().plusYears(1)));
        giftCardId = giftCardRepository.findByMerchantIdAndCardCode(merchantId, CARD_CODE).orElseThrow().getId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void findLatestLedgerPartitionYear_returnsTheFurthestPreCreatedYear() {
        Optional<LocalDate> latest = ledgerEntryRepository.findLatestLedgerPartitionYear();

        assertTrue(latest.isPresent());
        assertEquals(Year.of(2029).atDay(1), latest.get());
    }

    @Test
    void existsAnyRowInDefaultLedgerPartition_noOverflow_returnsFalse() {
        assertFalse(ledgerEntryRepository.existsAnyRowInDefaultLedgerPartition());
    }

    @Test
    void existsAnyRowInDefaultLedgerPartition_rowLandsOutsidePreCreatedRange_returnsTrue() {
        // 2030 is past the last pre-created yearly partition (2029), so this row is only accepted
        // because gift_card_ledger_default (the DEFAULT catch-all) exists.
        PostgresTestcontainerInitializer.executeAsMigrator(
                "INSERT INTO gift_card_ledger (gift_card_id, merchant_id, entry_type, amount, balance_after, created_at) "
                        + "VALUES (" + giftCardId + ", " + merchantId + ", 'CREATION', 100.00, 100.00, '2030-06-15')");

        assertTrue(ledgerEntryRepository.existsAnyRowInDefaultLedgerPartition());
    }
}
