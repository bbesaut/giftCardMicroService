package com.finovago.p2p.unit;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import org.mockito.junit.jupiter.MockitoExtension;

import com.finovago.p2p.exception.InvalidRefreshTokenException;
import com.finovago.p2p.model.RefreshToken;
import com.finovago.p2p.model.Role;
import com.finovago.p2p.model.User;
import com.finovago.p2p.repository.RefreshTokenRepository;
import com.finovago.p2p.service.RefreshTokenService;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceUnitTest {

    private static final long REFRESH_EXPIRATION_MS = 604_800_000L;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, REFRESH_EXPIRATION_MS);
    }

    @Test
    void should_createAndPersistRefreshToken_when_userIsValid() {
        User user = new User("client@example.com", "hashed", Role.MERCHANT, null);

        String rawToken = refreshTokenService.createRefreshToken(user);

        assertNotNull(rawToken);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());

        assertEquals(user, captor.getValue().getUser());
        assertFalse(captor.getValue().isRevoked());
        assertFalse(captor.getValue().isExpired());
    }

    @Test
    void should_returnUserAndRevokeOldToken_when_rotatingValidToken() {
        User user = new User("client@example.com", "hashed", Role.MERCHANT, null);
        RefreshToken existing = new RefreshToken("hash", user, Instant.now().plusSeconds(60));

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(existing));

        User result = refreshTokenService.validateAndRotate("raw-token");

        assertEquals(user, result);
        assertTrue(existing.isRevoked());
        verify(refreshTokenRepository).save(existing);
    }

    @Test
    void should_throwInvalidRefreshTokenException_when_tokenNotFound() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThrows(InvalidRefreshTokenException.class,
                () -> refreshTokenService.validateAndRotate("unknown-token"));
    }

    @Test
    void should_throwInvalidRefreshTokenException_when_tokenRevoked() {
        User user = new User("client@example.com", "hashed", Role.MERCHANT, null);
        RefreshToken revoked = new RefreshToken("hash", user, Instant.now().plusSeconds(60));
        revoked.revoke();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revoked));

        assertThrows(InvalidRefreshTokenException.class,
                () -> refreshTokenService.validateAndRotate("raw-token"));
    }

    @Test
    void should_throwInvalidRefreshTokenException_when_tokenExpired() {
        User user = new User("client@example.com", "hashed", Role.MERCHANT, null);
        RefreshToken expired = new RefreshToken("hash", user, Instant.now().minusSeconds(60));

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThrows(InvalidRefreshTokenException.class,
                () -> refreshTokenService.validateAndRotate("raw-token"));
    }

    @Test
    void should_revokeToken_when_loggingOut() {
        User user = new User("client@example.com", "hashed", Role.MERCHANT, null);
        RefreshToken existing = new RefreshToken("hash", user, Instant.now().plusSeconds(60));

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(existing));

        refreshTokenService.revoke("raw-token");

        assertTrue(existing.isRevoked());
        verify(refreshTokenRepository).save(existing);
    }
}
