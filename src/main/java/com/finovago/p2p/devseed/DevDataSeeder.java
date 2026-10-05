package com.finovago.p2p.devseed;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.finovago.p2p.devseed.SeedCatalog.CardKind;
import com.finovago.p2p.devseed.SeedCatalog.CardMix;
import com.finovago.p2p.devseed.SeedCatalog.Employee;
import com.finovago.p2p.devseed.SeedCatalog.MerchantSeed;
import com.finovago.p2p.dto.ApiKeyResponse;
import com.finovago.p2p.dto.GiftCardCreateRequest;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.model.Role;
import com.finovago.p2p.model.User;
import com.finovago.p2p.repository.MerchantRepository;
import com.finovago.p2p.repository.UserRepository;
import com.finovago.p2p.service.ApiKeyService;
import com.finovago.p2p.service.GiftCardService;
import com.finovago.p2p.service.MerchantService;

/**
 * Loads the {@link SeedCatalog} dataset into an empty database, in three phases: accounts and gift
 * cards, then the activity history (see {@link DevActivitySeeder}), then the final states (a
 * deactivated merchant, expired cards...) that only make sense once that history exists.
 * <p>
 * Everything goes through the same services the API uses (never the repositories directly for
 * business data), so balances, ledger entries and merchant lifecycle rules are exactly what a real
 * run would produce - a hand-written INSERT would happily create states the application can't reach.
 * <p>
 * Deterministic on purpose: card codes, balances and expiry dates are drawn from a per-merchant
 * seeded {@link Random}, so a reset gives the same data every time (dates aside, which are relative
 * to today).
 */
@Component
@Profile("dev & seed")
class DevDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final MerchantRepository merchantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApiKeyService apiKeyService;
    private final GiftCardService giftCardService;
    private final MerchantService merchantService;
    private final DevActivitySeeder activitySeeder;
    private final SeedTimeMachine timeMachine;

    DevDataSeeder(MerchantRepository merchantRepository, UserRepository userRepository,
            PasswordEncoder passwordEncoder, ApiKeyService apiKeyService,
            GiftCardService giftCardService, MerchantService merchantService,
            DevActivitySeeder activitySeeder, SeedTimeMachine timeMachine) {
        this.merchantRepository = merchantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.apiKeyService = apiKeyService;
        this.giftCardService = giftCardService;
        this.merchantService = merchantService;
        this.activitySeeder = activitySeeder;
        this.timeMachine = timeMachine;
    }

    void seed() {
        User admin = new User(SeedCatalog.ADMIN_EMAIL, passwordEncoder.encode(SeedCatalog.ADMIN_PASSWORD), Role.ADMIN, null);
        admin.markEmailVerified();
        userRepository.save(admin);

        // One BCrypt hash shared by every account using the default password - hashing is deliberately slow.
        String defaultPasswordHash = passwordEncoder.encode(SeedCatalog.DEFAULT_PASSWORD);

        List<SeededMerchant> seeded = SeedCatalog.MERCHANTS.stream()
                .map(spec -> seedAccountsAndCards(spec, defaultPasswordHash))
                .toList();

        activitySeeder.seed(seeded);

        // Final states last: a deactivated merchant or an expired card is somewhere to end up, and
        // applying it before the history is written would get in the way of writing that history.
        seeded.forEach(this::applyFinalStates);

        log.info("Dev data seeded: {} merchants, {} users, admin login: {}", seeded.size(), userRepository.count(), SeedCatalog.ADMIN_EMAIL);
    }

    private SeededMerchant seedAccountsAndCards(MerchantSeed spec, String defaultPasswordHash) {
        Merchant merchant = merchantRepository.save(new Merchant(spec.name(), spec.ownerEmail()));

        List<User> users = new ArrayList<>();
        String ownerPasswordHash = SeedCatalog.DEFAULT_PASSWORD.equals(spec.ownerPassword())
                ? defaultPasswordHash
                : passwordEncoder.encode(spec.ownerPassword());
        User owner = new User(spec.ownerEmail(), ownerPasswordHash, Role.MERCHANT, merchant, true);
        owner.markEmailVerified();
        users.add(userRepository.save(owner));

        for (Employee employee : spec.employees()) {
            User user = new User(employee.email(), defaultPasswordHash, Role.MERCHANT, merchant);
            user.markEmailVerified();
            user.setActive(employee.active());
            users.add(userRepository.save(user));
        }

        if (spec.withApiKey()) {
            // An API key is its own identity on the merchant, no service-account User behind it.
            ApiKeyResponse apiKey = apiKeyService.generateOrRotate(merchant);
            log.info("API key for {} (dev only): {}", spec.name(), apiKey.apiKeySecret());
        }

        SeededMerchant seeded = new SeededMerchant(spec, merchant, List.copyOf(users), new ArrayList<>());
        SeedSecurityContext.runAs(seeded.principalOf(seeded.owner()), () -> seedCards(seeded));
        return seeded;
    }

    private void seedCards(SeededMerchant merchant) {
        MerchantSeed spec = merchant.spec();
        // String#hashCode is specified by the language, so this seed - and the data drawn from it - is stable across JVMs.
        Random random = new Random(spec.name().hashCode());

        List<CardKind> kinds = new ArrayList<>();
        CardMix mix = spec.cards();
        addKind(kinds, CardKind.ACTIVE, mix.active());
        addKind(kinds, CardKind.HIGH_BALANCE, mix.highBalance());
        addKind(kinds, CardKind.EXPIRING_SOON, mix.expiringSoon());
        addKind(kinds, CardKind.DEACTIVATED, mix.deactivated());
        addKind(kinds, CardKind.EXPIRED, mix.expired());
        // Without the shuffle, codes would come out grouped by kind (DEMO-0001..24 all active, ...).
        Collections.shuffle(kinds, random);

        LocalDate today = LocalDate.now();
        for (int i = 0; i < kinds.size(); i++) {
            String code = "%s-%04d".formatted(spec.cardCodePrefix(), i + 1);
            CardKind kind = kinds.get(i);
            BigDecimal balance = balanceFor(kind, random);
            // DEACTIVATED and EXPIRED cards are created fully usable: they get there after their history.
            LocalDate expiration = switch (kind) {
                case EXPIRING_SOON -> today.plusDays(between(random, 1, 14));
                case HIGH_BALANCE -> today.plusDays(between(random, 180, 720));
                default -> today.plusDays(between(random, 60, 720));
            };
            giftCardService.createGiftCard(new GiftCardCreateRequest(code, balance, true, expiration));

            // Recent expiry (at most 30 days back) so it always falls after the card's own creation date.
            LocalDate finalExpiration = kind == CardKind.EXPIRED ? today.minusDays(between(random, 1, 30)) : null;
            merchant.cards().add(new SeededCard(code, kind, balance, finalExpiration));
        }
    }

    private static void addKind(List<CardKind> kinds, CardKind kind, int count) {
        for (int i = 0; i < count; i++) {
            kinds.add(kind);
        }
    }

    private static BigDecimal balanceFor(CardKind kind, Random random) {
        return switch (kind) {
            case HIGH_BALANCE -> amount(random, 1000, 5000, 100);
            case EXPIRING_SOON -> amount(random, 20, 200, 5);
            case DEACTIVATED, EXPIRED -> amount(random, 10, 300, 5);
            case ACTIVE -> amount(random, 10, 500, 5);
        };
    }

    /** A random amount in [min, max], rounded to a multiple of {@code step} so balances look like real gift card values. */
    private static BigDecimal amount(Random random, int min, int max, int step) {
        int steps = (max - min) / step;
        return BigDecimal.valueOf(min + (long) random.nextInt(steps + 1) * step).setScale(2);
    }

    private static long between(Random random, int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private void applyFinalStates(SeededMerchant seeded) {
        MerchantSeed spec = seeded.spec();

        SeedSecurityContext.runAs(seeded.principalOf(seeded.owner()), () -> seeded.cards().forEach(card -> {
            switch (card.kind()) {
                case DEACTIVATED -> giftCardService.setActive(card.code(), false);
                case EXPIRED -> timeMachine.setCardExpiration(seeded.id(), card.code(), card.finalExpirationDate());
                default -> { }
            }
        }));

        if (spec.rateLimitCapacity() != null) {
            merchantService.setRateLimitCapacity(seeded.id(), spec.rateLimitCapacity());
        }
        if (!spec.active()) {
            merchantService.setMerchantActive(seeded.id(), false);
        }
    }
}
