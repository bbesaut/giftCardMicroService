package com.finovago.p2p.integration;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import com.finovago.p2p.config.PostgresTestcontainerInitializer;
import com.finovago.p2p.dto.CreditRequest;
import com.finovago.p2p.dto.CreditResponse;
import com.finovago.p2p.dto.GiftCardCreateRequest;
import com.finovago.p2p.exception.ServiceAccountNotAllowedException;
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
import com.finovago.p2p.service.GiftCardCreditService;
import com.finovago.p2p.service.GiftCardService;

class GiftCardCreditServiceIntegrationTest extends AbstractIntegrationTest {

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
    private GiftCardCreditService giftCardCreditService;

    private Merchant merchant;
    private Long merchantId;
    private static final String CARD_CODE = "CREDIT-CARD";

    @BeforeEach
    void setUp() {
        // Same non-@Transactional + manual cleanup pattern as GiftCardServiceIntegrationTest for
        // consistency, even though credit() itself is synchronous.
        idempotencyKeyRepository.deleteAll();
        PostgresTestcontainerInitializer.executeAsMigrator("TRUNCATE TABLE gift_card_ledger RESTART IDENTITY");
        giftCardRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        merchantRepository.deleteAll();

        merchant = merchantRepository.save(new Merchant("Test Merchant", "giftcard-credit-test@example.com"));
        merchantId = merchant.getId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void assertMoneyEquals(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }

    private void authenticateAs(User user) {
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(user.getEmail(), "MERCHANT", merchantId, user.getId());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(authenticatedUser, null, List.of()));
    }

    @Test
    void should_credit_an_adjustment_with_reason_persisted_when_caller_is_human() {
        User human = userRepository.save(new User("employee-credit-test@example.com", "hashed", Role.MERCHANT, merchant, false));
        authenticateAs(human);

        giftCardService.createGiftCard(new GiftCardCreateRequest(CARD_CODE, BigDecimal.valueOf(50.0), true, LocalDate.now().plusYears(1)));

        CreditResponse response = giftCardCreditService.credit(
                new CreditRequest(CARD_CODE, BigDecimal.valueOf(15.0), "Support ticket #42"),
                UUID.randomUUID().toString());

        assertEquals("SUCCESS", response.status());
        GiftCard card = giftCardRepository.findByMerchantIdAndCardCode(merchantId, CARD_CODE).orElseThrow();
        assertMoneyEquals(BigDecimal.valueOf(65.0), card.getBalance());

        List<LedgerEntry> entries = ledgerEntryRepository.findByGiftCardIdOrderByCreatedAtAsc(card.getId());
        LedgerEntry adjustmentEntry = entries.stream().filter(e -> e.getEntryType() == LedgerEntryType.ADJUSTMENT).findFirst().orElseThrow();
        assertEquals("Support ticket #42", adjustmentEntry.getReason());
        assertEquals(human.getId(), adjustmentEntry.getActorUserId());
    }

    @Test
    void should_reject_credit_when_caller_is_a_service_account() {
        User human = userRepository.save(new User("employee-credit-test-2@example.com", "hashed", Role.MERCHANT, merchant, false));
        authenticateAs(human);
        giftCardService.createGiftCard(new GiftCardCreateRequest(CARD_CODE, BigDecimal.valueOf(50.0), true, LocalDate.now().plusYears(1)));

        User serviceAccount = userRepository.save(new User("integration-credit-test@example.com", "hashed", Role.MERCHANT, merchant, true));
        authenticateAs(serviceAccount);

        CreditRequest request = new CreditRequest(CARD_CODE, BigDecimal.valueOf(15.0), "Should be rejected");

        assertThrows(ServiceAccountNotAllowedException.class, () -> giftCardCreditService.credit(request, UUID.randomUUID().toString()));

        GiftCard card = giftCardRepository.findByMerchantIdAndCardCode(merchantId, CARD_CODE).orElseThrow();
        assertMoneyEquals(BigDecimal.valueOf(50.0), card.getBalance());
    }

    @Test
    void should_throw_exception_when_crediting_another_merchants_card() {
        User human = userRepository.save(new User("employee-credit-test-3@example.com", "hashed", Role.MERCHANT, merchant, false));
        authenticateAs(human);
        giftCardService.createGiftCard(new GiftCardCreateRequest(CARD_CODE, BigDecimal.valueOf(50.0), true, LocalDate.now().plusYears(1)));

        Merchant otherMerchant = merchantRepository.save(new Merchant("Other Merchant", "other-credit-test@example.com"));
        User otherHuman = userRepository.save(new User("other-credit-test@example.com", "hashed", Role.MERCHANT, otherMerchant, false));
        AuthenticatedUser otherUser = new AuthenticatedUser(otherHuman.getEmail(), "MERCHANT", otherMerchant.getId(), otherHuman.getId());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(otherUser, null, List.of()));

        CreditRequest request = new CreditRequest(CARD_CODE, BigDecimal.valueOf(5.0), "reason");

        assertThrows(UnknownGiftCardException.class, () -> giftCardCreditService.credit(request, UUID.randomUUID().toString()));
    }
}
