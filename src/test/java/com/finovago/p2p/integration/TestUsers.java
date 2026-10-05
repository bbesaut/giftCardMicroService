package com.finovago.p2p.integration;

import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.model.Role;
import com.finovago.p2p.model.User;

/**
 * Builds users that can log in. A plain {@code new User(...)} starts unverified (login is refused until
 * the email is confirmed), so integration tests that need a usable account must go through here.
 */
final class TestUsers {

    private TestUsers() {
    }

    static User verified(String email, String encodedPassword, Role role, Merchant merchant) {
        return verified(email, encodedPassword, role, merchant, false);
    }

    static User verified(String email, String encodedPassword, Role role, Merchant merchant, boolean owner) {
        User user = new User(email, encodedPassword, role, merchant, owner);
        user.markEmailVerified();
        return user;
    }
}
