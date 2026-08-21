package com.finovago.p2p.security;

import jakarta.annotation.Nullable;

public record AuthenticatedUser(String email, String role, @Nullable Long merchantId, @Nullable Long userId, boolean apiKey) {
    public AuthenticatedUser(String email, String role, @Nullable Long merchantId, @Nullable Long userId) {
        this(email, role, merchantId, userId, false);
    }

    @Override
    public String toString() {
        return email;
    }
}
