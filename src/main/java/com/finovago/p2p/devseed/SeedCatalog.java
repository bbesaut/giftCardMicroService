package com.finovago.p2p.devseed;

import java.util.List;

import jakarta.annotation.Nullable;

/**
 * The dev dataset, as plain data: who exists and what their gift cards look like. Kept apart from
 * {@link DevDataSeeder} so the shape of the demo data can change without touching the logic that
 * loads it.
 * <p>
 * The merchants are picked so a front-end has something to show for every state it handles: a
 * regular one, a big one, a tiny one, a deactivated one and one with a custom rate limit.
 */
final class SeedCatalog {

    static final String ADMIN_EMAIL = "admin@finovago.com";
    static final String ADMIN_PASSWORD = "admin123";

    /** Password of every seeded account except the admin and the demo merchant's owner. Dev fixture only. */
    static final String DEFAULT_PASSWORD = "Passw0rd!";

    private static final String DEMO_OWNER_PASSWORD = "client123";

    record Employee(String email, boolean active) {
    }

    /** How many gift cards of each kind to create for a merchant. */
    record CardMix(int active, int highBalance, int expiringSoon, int deactivated, int expired) {
    }

    record MerchantSeed(
            String name,
            String cardCodePrefix,
            String ownerEmail,
            String ownerPassword,
            List<Employee> employees,
            CardMix cards,
            boolean active,
            @Nullable Integer rateLimitCapacity,
            boolean withApiKey) {
    }

    static final List<MerchantSeed> MERCHANTS = List.of(
            // The account the docs and manual testing revolve around: owner + employees (one disabled),
            // a mix of every card state, and an API key.
            new MerchantSeed("Finovago Demo Merchant", "DEMO", "client@finovago.com", DEMO_OWNER_PASSWORD,
                    List.of(new Employee("sophie.martin@example.com", true),
                            new Employee("lucas.bernard@example.com", true),
                            new Employee("emma.petit@example.com", false)),
                    new CardMix(24, 4, 3, 3, 2), true, null, true),

            // Big catalogue - the one that makes pagination and sorting worth having.
            new MerchantSeed("Librairie du Coin", "LIB", "owner@librairie-du-coin.example.com", DEFAULT_PASSWORD,
                    List.of(new Employee("julie.moreau@example.com", true),
                            new Employee("thomas.roux@example.com", true)),
                    new CardMix(42, 6, 5, 4, 3), true, null, false),

            // Tiny merchant: an owner and a handful of cards, no team.
            new MerchantSeed("Boulangerie Petit Pain", "BOUL", "owner@petit-pain.example.com", DEFAULT_PASSWORD,
                    List.of(),
                    new CardMix(5, 0, 1, 1, 1), true, null, false),

            // Deactivated by an admin: login, refresh and API key are all blocked for it.
            new MerchantSeed("Vintage Vinyl Shop", "VINYL", "owner@vintage-vinyl.example.com", DEFAULT_PASSWORD,
                    List.of(new Employee("hugo.leroy@example.com", true)),
                    new CardMix(8, 1, 1, 1, 1), false, null, false),

            // Custom rate limit (default is app.rate-limit.merchant-capacity).
            new MerchantSeed("MegaMart Online", "MEGA", "owner@megamart.example.com", DEFAULT_PASSWORD,
                    List.of(new Employee("chloe.simon@example.com", true)),
                    new CardMix(16, 4, 2, 2, 1), true, 1000, false));

    private SeedCatalog() {
    }
}
