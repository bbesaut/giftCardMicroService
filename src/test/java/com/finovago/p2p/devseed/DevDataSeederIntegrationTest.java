package com.finovago.p2p.devseed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.finovago.p2p.AbstractIntegrationTest;
import com.finovago.p2p.devseed.SeedCatalog.CardMix;
import com.finovago.p2p.devseed.SeedCatalog.MerchantSeed;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.repository.DailyRedemptionCount;
import com.finovago.p2p.repository.LedgerEntryRepository;
import com.finovago.p2p.repository.MerchantRepository;
import com.finovago.p2p.repository.UserRepository;
import com.finovago.p2p.service.ApiKeyService;
import com.finovago.p2p.service.GiftCardCreditService;
import com.finovago.p2p.service.GiftCardHoldService;
import com.finovago.p2p.service.GiftCardRefundService;
import com.finovago.p2p.service.GiftCardService;
import com.finovago.p2p.service.LedgerService;
import com.finovago.p2p.service.MerchantService;

/**
 * Runs the real seeding job against a real PostgreSQL and checks what it leaves behind. The seeder
 * beans only exist under the dev+seed profiles, so they're assembled by hand from the same services
 * the application uses.
 * <p>
 * The dataset is seeded once for the whole class (it takes a few seconds) and the read-only checks
 * share it. The database is wiped afterwards: every other integration test cleans its own tables but
 * knows nothing about the api_keys/gift_card_hold rows this one creates, so leaving them behind would
 * break a merchant delete elsewhere (see the cleanup-order note in CLAUDE.md).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DevDataSeederIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MerchantRepository merchantRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;
    @Autowired
    private LedgerService ledgerService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ApiKeyService apiKeyService;
    @Autowired
    private GiftCardService giftCardService;
    @Autowired
    private MerchantService merchantService;
    @Autowired
    private GiftCardHoldService giftCardHoldService;
    @Autowired
    private GiftCardRefundService giftCardRefundService;
    @Autowired
    private GiftCardCreditService giftCardCreditService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${spring.flyway.url}")
    private String ownerUrl;
    @Value("${spring.flyway.user}")
    private String ownerUser;
    @Value("${spring.flyway.password}")
    private String ownerPassword;

    private SchemaOwnerJdbc ownerJdbc;
    private DevDatabaseResetter resetter;
    private DevDataSeeder seeder;

    @BeforeAll
    void seedOnce() {
        ownerJdbc = new SchemaOwnerJdbc(ownerUrl, ownerUser, ownerPassword);
        SeedTimeMachine timeMachine = new SeedTimeMachine(ownerJdbc);
        DevActivitySeeder activitySeeder = new DevActivitySeeder(
                giftCardService, giftCardHoldService, giftCardRefundService, giftCardCreditService, timeMachine, 15);
        seeder = new DevDataSeeder(merchantRepository, userRepository, passwordEncoder, apiKeyService,
                giftCardService, merchantService, activitySeeder, timeMachine);
        resetter = new DevDatabaseResetter(ownerJdbc);

        resetter.reset();
        seeder.seed();
    }

    @AfterAll
    void wipe() {
        resetter.reset();
        ownerJdbc.close();
    }

    private static int totalCards(CardMix mix) {
        return mix.active() + mix.highBalance() + mix.expiringSoon() + mix.deactivated() + mix.expired();
    }

    private int count(String sql, Object... args) {
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return result == null ? 0 : result;
    }

    @Test
    void should_createEveryMerchantOfTheCatalog() {
        List<String> seededNames = merchantRepository.findAllByOrderByIdAsc().stream().map(Merchant::getName).toList();

        assertEquals(SeedCatalog.MERCHANTS.stream().map(MerchantSeed::name).toList(), seededNames);
    }

    @Test
    void should_createTheAdminAndEveryMerchantUser() {
        long expectedUsers = 1 + SeedCatalog.MERCHANTS.stream().mapToLong(merchant -> 1 + merchant.employees().size()).sum();

        assertEquals(expectedUsers, userRepository.count());
        assertTrue(userRepository.findByEmail(SeedCatalog.ADMIN_EMAIL).isPresent());
    }

    @Test
    void should_createTheCardsOfEachMerchantMix() {
        for (MerchantSeed merchant : SeedCatalog.MERCHANTS) {
            int cards = count("SELECT COUNT(*) FROM gift_card g JOIN merchants m ON m.id = g.merchant_id WHERE m.name = ?", merchant.name());

            assertEquals(totalCards(merchant.cards()), cards, merchant.name());
        }
    }

    @Test
    void should_leaveEveryBalanceConsistentWithItsLedger() {
        // The same check LedgerReconciliationScheduler runs: any card whose stored balance disagrees with its latest ledger entry.
        assertTrue(ledgerService.findBalanceDiscrepancies().isEmpty());
    }

    @Test
    void should_keepEachCardsLedgerInTheOrderItWasWritten() {
        int outOfOrder = count("""
                SELECT COUNT(*) FROM (
                    SELECT created_at, LAG(created_at) OVER (PARTITION BY gift_card_id ORDER BY id) AS previous_created_at
                    FROM gift_card_ledger
                ) entries WHERE previous_created_at > created_at
                """);

        assertEquals(0, outOfOrder);
    }

    @Test
    void should_notDateAnyLedgerEntryInTheFuture() {
        assertEquals(0, count("SELECT COUNT(*) FROM gift_card_ledger WHERE created_at > ?", Timestamp.valueOf(LocalDateTime.now())));
    }

    @Test
    void should_writeEveryKindOfBalanceOperation() {
        Set<String> entryTypes = Set.copyOf(jdbcTemplate.queryForList("SELECT DISTINCT entry_type FROM gift_card_ledger", String.class));

        assertTrue(entryTypes.containsAll(Set.of(
                "CREATION", "REDEMPTION", "HOLD_PLACED", "HOLD_CAPTURED", "HOLD_RELEASED", "REFUND", "ADJUSTMENT")), entryTypes.toString());
    }

    @Test
    void should_writeHoldsInEveryStatus() {
        Set<String> statuses = Set.copyOf(jdbcTemplate.queryForList("SELECT DISTINCT status FROM gift_card_hold", String.class));

        assertEquals(Set.of("PENDING", "CAPTURED", "RELEASED", "EXPIRED"), statuses);
    }

    @Test
    void should_leaveThePendingHoldsTheCatalogAsksFor_stillOpen() {
        int expected = SeedCatalog.MERCHANTS.stream().mapToInt(MerchantSeed::pendingHolds).sum();

        int pendingAndOpen = count("SELECT COUNT(*) FROM gift_card_hold WHERE status = 'PENDING' AND expires_at > ?", Timestamp.valueOf(LocalDateTime.now()));

        assertEquals(expected, pendingAndOpen);
    }

    @Test
    void should_attributeApiKeyCallsToTheMerchantThatHasAKey() {
        List<String> merchantsUsingApiKey = jdbcTemplate.queryForList(
                "SELECT DISTINCT m.name FROM gift_card_ledger l JOIN merchants m ON m.id = l.merchant_id WHERE l.actor_via_api_key", String.class);

        assertEquals(SeedCatalog.MERCHANTS.stream().filter(MerchantSeed::withApiKey).map(MerchantSeed::name).toList(), merchantsUsingApiKey);
        assertEquals(0, count("SELECT COUNT(*) FROM gift_card_ledger WHERE actor_via_api_key AND actor_user_id IS NOT NULL"));
    }

    @Test
    void should_applyTheFinalStatesOfTheCatalog() {
        for (MerchantSeed spec : SeedCatalog.MERCHANTS) {
            Merchant merchant = merchantRepository.findAllByOrderByIdAsc().stream()
                    .filter(candidate -> candidate.getName().equals(spec.name())).findFirst().orElseThrow();

            assertEquals(spec.active(), merchant.isActive(), spec.name() + " active flag");
            assertEquals(spec.rateLimitCapacity(), merchant.getRateLimitCapacity(), spec.name() + " rate limit");
            spec.employees().forEach(employee -> assertEquals(employee.active(),
                    userRepository.findByEmail(employee.email()).orElseThrow().isActive(), employee.email()));
        }

        int expectedExpired = SeedCatalog.MERCHANTS.stream().mapToInt(merchant -> merchant.cards().expired()).sum();
        int expectedAtLeastInactive = SeedCatalog.MERCHANTS.stream().mapToInt(merchant -> merchant.cards().deactivated()).sum();
        assertEquals(expectedExpired, count("SELECT COUNT(*) FROM gift_card WHERE expiration_date < ?", java.sql.Date.valueOf(LocalDate.now())));
        // At least: a card drained by redemptions is inactive too, on top of the deliberately deactivated ones.
        assertTrue(count("SELECT COUNT(*) FROM gift_card WHERE NOT active") >= expectedAtLeastInactive);
    }

    @Test
    void should_spreadRedemptionsOverTheDashboardWindow() {
        Long demoMerchantId = merchantRepository.findAllByOrderByIdAsc().get(0).getId();

        List<DailyRedemptionCount> perDay = ledgerEntryRepository.getDailyRedemptionCounts(
                demoMerchantId, LocalDate.now().minusDays(29).atStartOfDay());

        // A chart with a handful of dots isn't worth seeding: most days of the default 30-day window should have activity.
        assertTrue(perDay.size() >= 20, "days with activity in the last 30: " + perDay.size());
    }

    @Test
    void should_produceTheSameHistory_when_seededTwice() {
        String fingerprintSql = """
                SELECT m.name || '|' || g.card_code || '|' || l.entry_type || '|' || l.amount || '|' || l.balance_after || '|' || l.created_at
                FROM gift_card_ledger l
                JOIN gift_card g ON g.id = l.gift_card_id
                JOIN merchants m ON m.id = g.merchant_id
                WHERE l.created_at < ?
                ORDER BY m.id, g.card_code, l.id
                """;
        // Today is left out on purpose: which of today's operations already "happened" depends on the time of day the job ran.
        Timestamp startOfToday = Timestamp.valueOf(LocalDate.now().atStartOfDay());
        List<String> first = jdbcTemplate.queryForList(fingerprintSql, String.class, startOfToday);

        resetter.reset();
        seeder.seed();
        List<String> second = jdbcTemplate.queryForList(fingerprintSql, String.class, startOfToday);

        assertFalse(first.isEmpty());
        assertEquals(first, second);
    }
}
