package com.finovago.p2p.integration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.finovago.p2p.model.GiftCard;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.repository.GiftCardHoldRepository;
import com.finovago.p2p.repository.GiftCardRepository;
import com.finovago.p2p.repository.IdempotencyKeyRepository;
import com.finovago.p2p.repository.LedgerDiscrepancy;
import com.finovago.p2p.repository.LedgerEntryRepository;
import com.finovago.p2p.repository.MerchantRepository;
import com.finovago.p2p.repository.RefreshTokenRepository;
import com.finovago.p2p.repository.UserRepository;
import com.finovago.p2p.security.AuthenticatedUser;
import com.finovago.p2p.service.GiftCardService;

class LedgerReconciliationIntegrationTest extends AbstractIntegrationTest {

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
    private static final String CARD_CODE = "RECON-CARD";

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
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void findBalanceDiscrepancies_consistentCard_returnsNothing() {
        giftCardService.createGiftCard(new GiftCardCreateRequest(CARD_CODE, BigDecimal.valueOf(100.0), true, LocalDate.now().plusYears(1)));

        assertTrue(ledgerEntryRepository.findBalanceDiscrepancies().isEmpty());
    }

    @Test
    void findBalanceDiscrepancies_driftedCard_isDetected() {
        giftCardService.createGiftCard(new GiftCardCreateRequest(CARD_CODE, BigDecimal.valueOf(100.0), true, LocalDate.now().plusYears(1)));

        GiftCard card = giftCardRepository.findByMerchantIdAndCardCode(merchantId, CARD_CODE).orElseThrow();
        // Simulates a bug/manual edit that moves gift_card.balance without a matching ledger entry.
        card.deductBalance(BigDecimal.valueOf(15.0));
        giftCardRepository.save(card);

        List<LedgerDiscrepancy> discrepancies = ledgerEntryRepository.findBalanceDiscrepancies();

        assertEquals(1, discrepancies.size());
        LedgerDiscrepancy discrepancy = discrepancies.get(0);
        assertEquals(card.getId(), discrepancy.getGiftCardId());
        assertEquals(CARD_CODE, discrepancy.getCardCode());
        assertEquals(merchantId, discrepancy.getMerchantId());
        assertEquals(0, BigDecimal.valueOf(85.0).compareTo(discrepancy.getActualBalance()));
        assertEquals(0, BigDecimal.valueOf(100.0).compareTo(discrepancy.getTheoreticalBalance()));
    }
}
