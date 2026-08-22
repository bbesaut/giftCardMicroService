package com.finovago.p2p.unit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.crypto.password.PasswordEncoder;

import com.finovago.p2p.dto.PasswordResetConfirmRequest;
import com.finovago.p2p.exception.InvalidResetTokenException;
import com.finovago.p2p.model.PasswordResetToken;
import com.finovago.p2p.model.Role;
import com.finovago.p2p.model.User;
import com.finovago.p2p.repository.PasswordResetTokenRepository;
import com.finovago.p2p.repository.UserRepository;
import com.finovago.p2p.service.EmailSender;
import com.finovago.p2p.service.PasswordResetService;
import com.finovago.p2p.service.RefreshTokenService;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceUnitTest {

    private static final long EXPIRATION_MINUTES = 30L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private EmailSender emailSender;

    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        passwordResetService = new PasswordResetService(
                userRepository, passwordResetTokenRepository, passwordEncoder, refreshTokenService, emailSender, EXPIRATION_MINUTES);
    }

    @Test
    void should_createTokenAndSendEmail_when_emailBelongsToAnAccount() {
        User user = new User("client@example.com", "hashed", Role.MERCHANT, null);
        when(userRepository.findByEmail("client@example.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.findByUserAndUsedFalse(user)).thenReturn(List.of());

        passwordResetService.requestReset("client@example.com");

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(captor.capture());
        assertEquals(user, captor.getValue().getUser());

        verify(emailSender).send(org.mockito.ArgumentMatchers.eq("client@example.com"), anyString(), anyString());
    }

    @Test
    void should_invalidatePreviousOutstandingTokens_when_requestingAgain() {
        User user = new User("client@example.com", "hashed", Role.MERCHANT, null);
        PasswordResetToken outstanding = new PasswordResetToken("old-hash", user, Instant.now().plusSeconds(600));
        when(userRepository.findByEmail("client@example.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.findByUserAndUsedFalse(user)).thenReturn(List.of(outstanding));

        passwordResetService.requestReset("client@example.com");

        assertTrue(outstanding.isUsed());
        verify(passwordResetTokenRepository).saveAll(List.of(outstanding));
    }

    @Test
    void should_doNothing_when_emailIsUnknown() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        passwordResetService.requestReset("unknown@example.com");

        verify(emailSender, never()).send(anyString(), anyString(), anyString());
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    void should_resetPasswordAndRevokeTokens_when_resetTokenIsValid() {
        User user = new User("client@example.com", "hashed", Role.MERCHANT, null);
        PasswordResetToken resetToken = new PasswordResetToken("hash", user, Instant.now().plusSeconds(600));
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest("raw-token", "NewPass456!");

        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("NewPass456!")).thenReturn("newHashed");

        passwordResetService.confirmReset(request);

        assertEquals("newHashed", user.getPassword());
        assertTrue(resetToken.isUsed());
        verify(refreshTokenService).revokeAllForUser(user);
    }

    @Test
    void should_throwInvalidResetTokenException_when_tokenNotFound() {
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest("unknown-token", "NewPass456!");

        assertThrows(InvalidResetTokenException.class, () -> passwordResetService.confirmReset(request));
    }

    @Test
    void should_throwInvalidResetTokenException_when_tokenAlreadyUsed() {
        User user = new User("client@example.com", "hashed", Role.MERCHANT, null);
        PasswordResetToken resetToken = new PasswordResetToken("hash", user, Instant.now().plusSeconds(600));
        resetToken.markUsed();

        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(resetToken));

        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest("raw-token", "NewPass456!");

        assertThrows(InvalidResetTokenException.class, () -> passwordResetService.confirmReset(request));
    }

    @Test
    void should_throwInvalidResetTokenException_when_tokenExpired() {
        User user = new User("client@example.com", "hashed", Role.MERCHANT, null);
        PasswordResetToken resetToken = new PasswordResetToken("hash", user, Instant.now().minusSeconds(60));

        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(resetToken));

        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest("raw-token", "NewPass456!");

        assertThrows(InvalidResetTokenException.class, () -> passwordResetService.confirmReset(request));
    }
}
