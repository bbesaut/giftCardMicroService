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
import com.finovago.p2p.security.AuthenticatedUser;
import com.finovago.p2p.service.ApiKeyService;
import com.finovago.p2p.service.GiftCardService;
import com.finovago.p2p.service.MerchantService;

/**
 * Loads the {@link SeedCatalog} dataset into an empty database. Everything goes through the same
 * services the API uses (never the repositories directly for business data), so balances, ledger
 * entries and merchant lifecycle rules are exactly what a real run would produce - a hand-written
 * INSERT would happily create states the application can't reach.
 * <p>
 * Deterministic on purpose: card codes, balances and expiry dates are drawn from a per-merchant
 * seeded {@link Random}, so a reset gives the same data every time (dates aside, which are relative
 * to today).
 */
@Component
@Profile("dev & seed")
class DevDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private enum CardKind { ACTIVE, HIGH_BALANCE, EXPIRING_SOON, DEACTIVATED, EXPIRED }

    private record SeededMerchant(MerchantSeed spec, Merchant merchant, AuthenticatedUser owner) {
    }

    private final MerchantRepository merchantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApiKeyService apiKeyService;
    private final GiftCardService giftCardService;
    private final MerchantService merchantService;

    DevDataSeeder(MerchantRepository merchantRepository, UserRepository userRepository,
            PasswordEncoder passwordEncoder, ApiKeyService apiKeyService,
            GiftCardService giftCardService, MerchantService merchantService) {
        this.merchantRepository = merchantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.apiKeyService = apiKeyService;
        this.giftCardService = giftCardService;
        this.merchantService = merchantService;
    }

    void seed() {
        userRepository.save(new User(SeedCatalog.ADMIN_EMAIL, passwordEncoder.encode(SeedCatalog.ADMIN_PASSWORD), Role.ADMIN, null));

        // One BCrypt hash shared by every account using the default password - hashing is deliberately slow.
        String defaultPasswordHash = passwordEncoder.encode(SeedCatalog.DEFAULT_PASSWORD);

        List<SeededMerchant> seeded = SeedCatalog.MERCHANTS.stream()
                .map(spec -> seedAccountsAndCards(spec, defaultPasswordHash))
                .toList();

        // Merchant lifecycle last: a deactivated merchant is a state to end up in, and applying it
        // before the data is loaded would get in the way of loading that data.
        seeded.forEach(this::applyLifecycle);

        log.info("Dev data seeded: {} merchants, {} users, admin login: {}", seeded.size(), userRepository.count(), SeedCatalog.ADMIN_EMAIL);
    }

    private SeededMerchant seedAccountsAndCards(MerchantSeed spec, String defaultPasswordHash) {
        Merchant merchant = merchantRepository.save(new Merchant(spec.name(), spec.ownerEmail()));

        String ownerPasswordHash = SeedCatalog.DEFAULT_PASSWORD.equals(spec.ownerPassword())
                ? defaultPasswordHash
                : passwordEncoder.encode(spec.ownerPassword());
        User owner = userRepository.save(new User(spec.ownerEmail(), ownerPasswordHash, Role.MERCHANT, merchant, true));

        for (Employee employee : spec.employees()) {
            User user = new User(employee.email(), defaultPasswordHash, Role.MERCHANT, merchant);
            user.setActive(employee.active());
            userRepository.save(user);
        }

        if (spec.withApiKey()) {
            // An API key is its own identity on the merchant, no service-account User behind it.
            ApiKeyResponse apiKey = apiKeyService.generateOrRotate(merchant);
            log.info("API key for {} (dev only): {}", spec.name(), apiKey.apiKeySecret());
        }

        AuthenticatedUser ownerPrincipal = new AuthenticatedUser(owner.getEmail(), Role.MERCHANT.name(), merchant.getId(), owner.getId());
        SeedSecurityContext.runAs(ownerPrincipal, () -> seedCards(spec));

        return new SeededMerchant(spec, merchant, ownerPrincipal);
    }

    private void seedCards(MerchantSeed spec) {
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

        for (int i = 0; i < kinds.size(); i++) {
            String code = "%s-%04d".formatted(spec.cardCodePrefix(), i + 1);
            giftCardService.createGiftCard(buildCard(code, kinds.get(i), random));
        }
    }

    private static void addKind(List<CardKind> kinds, CardKind kind, int count) {
        for (int i = 0; i < count; i++) {
            kinds.add(kind);
        }
    }

    private static GiftCardCreateRequest buildCard(String code, CardKind kind, Random random) {
        LocalDate today = LocalDate.now();
        return switch (kind) {
            case ACTIVE -> new GiftCardCreateRequest(code, amount(random, 10, 500, 5), true, today.plusDays(between(random, 60, 720)));
            case HIGH_BALANCE -> new GiftCardCreateRequest(code, amount(random, 1000, 5000, 100), true, today.plusDays(between(random, 180, 720)));
            case EXPIRING_SOON -> new GiftCardCreateRequest(code, amount(random, 20, 200, 5), true, today.plusDays(between(random, 1, 14)));
            case DEACTIVATED -> new GiftCardCreateRequest(code, amount(random, 10, 300, 5), false, today.plusDays(between(random, 60, 720)));
            // A past date is fine here: the @Future rule lives on the request DTO, only enforced by the controller.
            case EXPIRED -> new GiftCardCreateRequest(code, amount(random, 10, 300, 5), true, today.minusDays(between(random, 1, 90)));
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

    private void applyLifecycle(SeededMerchant seeded) {
        MerchantSeed spec = seeded.spec();
        Long merchantId = seeded.merchant().getId();
        if (spec.rateLimitCapacity() != null) {
            merchantService.setRateLimitCapacity(merchantId, spec.rateLimitCapacity());
        }
        if (!spec.active()) {
            merchantService.setMerchantActive(merchantId, false);
        }
    }
}
