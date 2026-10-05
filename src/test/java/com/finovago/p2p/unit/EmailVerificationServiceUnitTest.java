package com.finovago.p2p.unit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.finovago.p2p.exception.InvalidVerificationTokenException;
import com.finovago.p2p.model.EmailVerificationToken;
import com.finovago.p2p.model.Role;
import com.finovago.p2p.model.User;
import com.finovago.p2p.repository.EmailVerificationTokenRepository;
import com.finovago.p2p.repository.UserRepository;
import com.finovago.p2p.service.EmailSender;
import com.finovago.p2p.service.EmailVerificationService;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceUnitTest {

    private static final long EXPIRATION_MINUTES = 1440L;
    private static final String EMAIL = "owner@example.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Mock
    private EmailSender emailSender;

    private EmailVerificationService emailVerificationService;
    private User user;

    @BeforeEach
    void setUp() {
        emailVerificationService = new EmailVerificationService(
                userRepository, emailVerificationTokenRepository, emailSender, EXPIRATION_MINUTES);
        user = new User(EMAIL, "encoded-password", Role.MERCHANT, null, true);
    }

    @Test
    void should_storeOnlyHashAndEmailRawToken_when_verificationIsSent() {
        emailVerificationService.sendVerification(user);

        ArgumentCaptor<EmailVerificationToken> saved = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(emailVerificationTokenRepository).save(saved.capture());
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(any(), any(), body.capture());

        String rawToken = body.getValue().lines()
                .filter(line -> line.startsWith("Verification token: "))
                .findFirst()
                .orElseThrow()
                .substring("Verification token: ".length());
        assertFalse(saved.getValue().getTokenHash().equals(rawToken), "raw token must never be stored");
        assertEquals(64, saved.getValue().getTokenHash().length(), "stored value is a SHA-256 hex digest");
    }

    @Test
    void should_sendVerificationToUserEmail_when_verificationIsSent() {
        emailVerificationService.sendVerification(user);

        verify(emailSender).send(eq(EMAIL), anyString(), anyString());
    }

    @Test
    void should_setExpiryInTheFuture_when_verificationIsSent() {
        Instant before = Instant.now();

        emailVerificationService.sendVerification(user);

        ArgumentCaptor<EmailVerificationToken> saved = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(emailVerificationTokenRepository).save(saved.capture());
        assertTrue(saved.getValue().getExpiryDate().isAfter(before.plusSeconds(EXPIRATION_MINUTES * 60 - 5)));
    }

    @Test
    void should_invalidateOutstandingTokens_when_newVerificationIsSent() {
        EmailVerificationToken previous = new EmailVerificationToken("old-hash", user, Instant.now().plusSeconds(60));
        when(emailVerificationTokenRepository.findByUserAndUsedFalse(user)).thenReturn(List.of(previous));

        emailVerificationService.sendVerification(user);

        assertTrue(previous.isUsed(), "previous outstanding token must be marked used");
        verify(emailVerificationTokenRepository).saveAll(List.of(previous));
    }

    @Test
    void should_doNothing_when_resendIsRequestedForUnknownEmail() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        emailVerificationService.resendVerification(EMAIL);

        verify(emailSender, never()).send(any(), any(), any());
        verify(emailVerificationTokenRepository, never()).save(any());
    }

    @Test
    void should_doNothing_when_resendIsRequestedForAlreadyVerifiedAccount() {
        user.markEmailVerified();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        emailVerificationService.resendVerification(EMAIL);

        verify(emailSender, never()).send(any(), any(), any());
    }

    @Test
    void should_sendNewVerification_when_resendIsRequestedForUnverifiedAccount() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        emailVerificationService.resendVerification(EMAIL);

        verify(emailSender).send(eq(EMAIL), anyString(), anyString());
    }

    @Test
    void should_markUserVerifiedAndConsumeToken_when_tokenIsValid() {
        EmailVerificationToken token = new EmailVerificationToken("hash", user, Instant.now().plusSeconds(600));
        when(emailVerificationTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        emailVerificationService.verifyEmail("raw-token");

        assertTrue(user.isEmailVerified());
        assertTrue(token.isUsed());
        verify(userRepository).save(user);
    }

    @Test
    void should_throw_when_tokenIsUnknown() {
        when(emailVerificationTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThrows(InvalidVerificationTokenException.class,
                () -> emailVerificationService.verifyEmail("unknown-token"));
        assertFalse(user.isEmailVerified());
    }

    @Test
    void should_throwAndNotVerify_when_tokenWasAlreadyUsed() {
        EmailVerificationToken token = new EmailVerificationToken("hash", user, Instant.now().plusSeconds(600));
        token.markUsed();
        when(emailVerificationTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThrows(InvalidVerificationTokenException.class,
                () -> emailVerificationService.verifyEmail("raw-token"));
        assertFalse(user.isEmailVerified());
    }

    @Test
    void should_throwAndNotVerify_when_tokenIsExpired() {
        EmailVerificationToken token = new EmailVerificationToken("hash", user, Instant.now().minusSeconds(1));
        when(emailVerificationTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThrows(InvalidVerificationTokenException.class,
                () -> emailVerificationService.verifyEmail("raw-token"));
        assertFalse(user.isEmailVerified());
    }

    @Test
    void should_deleteExpiredTokensAndReturnCount_when_sweepRuns() {
        Instant cutoff = Instant.now();
        EmailVerificationToken expired = new EmailVerificationToken("hash", user, cutoff.minusSeconds(60));
        when(emailVerificationTokenRepository.findByExpiryDateBefore(cutoff)).thenReturn(List.of(expired));

        int deleted = emailVerificationService.deleteExpired(cutoff);

        assertEquals(1, deleted);
        verify(emailVerificationTokenRepository).deleteAll(List.of(expired));
    }
}
