package com.finovago.p2p.security;

import jakarta.annotation.Nullable;

public record AuthenticatedUser(String email, String role, @Nullable Long merchantId) {
    @Override
    public String toString() {
        return email;
    }
}
