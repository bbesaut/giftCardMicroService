package com.finovago.p2p.unit;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import com.finovago.p2p.controller.RefreshTokenCookieFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshTokenCookieFactoryUnitTest {

    private static final long REFRESH_EXPIRATION_MS = 604_800_000L; // 7 days
    private static final String TOKEN = "550e8400-e29b-41d4-a716-446655440000";

    private final RefreshTokenCookieFactory factory = new RefreshTokenCookieFactory(REFRESH_EXPIRATION_MS);

    @Test
    void should_namePathAndValueCookie_when_creating() {
        ResponseCookie cookie = factory.create(TOKEN);

        assertEquals("refresh_token", cookie.getName());
        assertEquals(TOKEN, cookie.getValue());
        assertEquals("/api/v1/auth", cookie.getPath());
    }

    @Test
    void should_setSecurityAttributes_when_creating() {
        ResponseCookie cookie = factory.create(TOKEN);

        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.isSecure());
        assertEquals("None", cookie.getSameSite());
    }

    @Test
    void should_convertExpirationToSeconds_when_creating() {
        ResponseCookie cookie = factory.create(TOKEN);

        assertEquals(604_800L, cookie.getMaxAge().getSeconds());
    }

    @Test
    void should_truncateSubSecondExpiration_when_creating() {
        RefreshTokenCookieFactory shortLived = new RefreshTokenCookieFactory(900_500L);

        assertEquals(900L, shortLived.create(TOKEN).getMaxAge().getSeconds());
    }

    @Test
    void should_expireImmediatelyWithEmptyValue_when_clearing() {
        ResponseCookie cookie = factory.clear();

        assertEquals("refresh_token", cookie.getName());
        assertEquals("", cookie.getValue());
        assertEquals(0L, cookie.getMaxAge().getSeconds());
    }

    @Test
    void should_keepSameSecurityAttributesAndPath_when_clearing() {
        ResponseCookie cookie = factory.clear();

        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.isSecure());
        assertEquals("None", cookie.getSameSite());
        assertEquals("/api/v1/auth", cookie.getPath());
    }
}
