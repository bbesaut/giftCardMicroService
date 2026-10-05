package com.finovago.p2p.controller;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds the HttpOnly cookie that carries the refresh token. Lives in the web layer on purpose:
 * cookie attributes are an HTTP concern, so AuthService stays unaware of them.
 */
@Component
public class RefreshTokenCookieFactory {

    static final String COOKIE_NAME = "refresh_token";

    // Scoped to the auth endpoints only, so the browser never sends the refresh token to the rest of the API.
    static final String COOKIE_PATH = "/api/v1/auth";

    private final long maxAgeSeconds;

    public RefreshTokenCookieFactory(
            @Value("${application.security.jwt.refresh-token-expiration}") long refreshTokenExpirationMs) {
        this.maxAgeSeconds = Duration.ofMillis(refreshTokenExpirationMs).getSeconds();
    }

    public ResponseCookie create(String refreshToken) {
        return base(refreshToken)
                .maxAge(maxAgeSeconds)
                .build();
    }

    /** Expires the cookie immediately in the browser (Max-Age=0). */
    public ResponseCookie clear() {
        return base("")
                .maxAge(0)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path(COOKIE_PATH);
    }
}
