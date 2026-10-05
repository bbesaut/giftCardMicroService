package com.finovago.p2p.devseed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.finovago.p2p.devseed.SeedCatalog.MerchantSeed;

/**
 * The catalog is data, but a copy-paste slip in it (a repeated email, a reused card prefix) only
 * surfaces as a constraint violation halfway through a seeding run - so it's checked here instead.
 */
class SeedCatalogUnitTest {

    @Test
    void should_haveUniqueEmails_acrossAdminOwnersAndEmployees() {
        List<String> emails = new ArrayList<>();
        emails.add(SeedCatalog.ADMIN_EMAIL);
        for (MerchantSeed merchant : SeedCatalog.MERCHANTS) {
            emails.add(merchant.ownerEmail());
            merchant.employees().forEach(employee -> emails.add(employee.email()));
        }

        Set<String> distinct = new HashSet<>();
        emails.forEach(email -> distinct.add(email.toLowerCase(Locale.ROOT)));

        assertEquals(emails.size(), distinct.size());
    }

    @Test
    void should_haveUniqueMerchantNames() {
        List<String> names = SeedCatalog.MERCHANTS.stream().map(MerchantSeed::name).toList();

        assertEquals(names.size(), new HashSet<>(names).size());
    }

    @Test
    void should_haveUniqueCardCodePrefixes() {
        List<String> prefixes = SeedCatalog.MERCHANTS.stream().map(MerchantSeed::cardCodePrefix).toList();

        assertEquals(prefixes.size(), new HashSet<>(prefixes).size());
    }

    @Test
    void should_giveEveryMerchantAtLeastOneCard() {
        for (MerchantSeed merchant : SeedCatalog.MERCHANTS) {
            SeedCatalog.CardMix mix = merchant.cards();
            int total = mix.active() + mix.highBalance() + mix.expiringSoon() + mix.deactivated() + mix.expired();
            assertTrue(total > 0, merchant.name() + " has no cards");
        }
    }

    @Test
    void should_keepTheDemoMerchantFirstAndUsable() {
        // The docs and manual testing revolve around client@finovago.com owning merchant #1 with an API key.
        MerchantSeed demo = SeedCatalog.MERCHANTS.get(0);

        assertEquals("client@finovago.com", demo.ownerEmail());
        assertTrue(demo.active());
        assertTrue(demo.withApiKey());
    }

    @Test
    void should_coverTheMerchantStatesAFrontEndHandles() {
        assertTrue(SeedCatalog.MERCHANTS.stream().anyMatch(merchant -> !merchant.active()), "no deactivated merchant");
        assertTrue(SeedCatalog.MERCHANTS.stream().anyMatch(merchant -> merchant.rateLimitCapacity() != null), "no custom rate limit");
        assertTrue(SeedCatalog.MERCHANTS.stream().anyMatch(merchant -> merchant.employees().stream().anyMatch(e -> !e.active())), "no deactivated employee");
        assertTrue(SeedCatalog.MERCHANTS.stream().anyMatch(merchant -> merchant.employees().isEmpty()), "no owner-only merchant");
    }

    @Test
    void should_notCreateAnApiKeyForEveryMerchant() {
        // An API key is opt-in per merchant (POST /me/api-key) - seeding one everywhere would hide the "no key yet" state.
        assertFalse(SeedCatalog.MERCHANTS.stream().allMatch(MerchantSeed::withApiKey));
    }
}
