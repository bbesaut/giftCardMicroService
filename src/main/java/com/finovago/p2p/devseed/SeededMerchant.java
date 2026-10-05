package com.finovago.p2p.devseed;

import java.util.List;

import com.finovago.p2p.devseed.SeedCatalog.MerchantSeed;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.model.Role;
import com.finovago.p2p.model.User;
import com.finovago.p2p.security.AuthenticatedUser;

/**
 * A merchant once its accounts and cards exist: the handle the later seeding phases (activity
 * history, lifecycle) work from.
 *
 * @param users the merchant's human accounts, owner first
 */
record SeededMerchant(MerchantSeed spec, Merchant merchant, List<User> users, List<SeededCard> cards) {

    Long id() {
        return merchant.getId();
    }

    User owner() {
        return users.get(0);
    }

    /** The principal a call made by this human account would carry. */
    AuthenticatedUser principalOf(User user) {
        return new AuthenticatedUser(user.getEmail(), Role.MERCHANT.name(), merchant.getId(), user.getId());
    }

    /** The principal of a call authenticated with the merchant's API key: no user behind it. */
    AuthenticatedUser apiKeyPrincipal() {
        return new AuthenticatedUser("api-key", Role.MERCHANT.name(), merchant.getId(), null, true);
    }
}
