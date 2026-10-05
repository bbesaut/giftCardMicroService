package com.finovago.p2p.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finovago.p2p.exception.InvalidVerificationTokenException;
import com.finovago.p2p.model.EmailVerificationToken;
import com.finovago.p2p.model.User;
import com.finovago.p2p.repository.EmailVerificationTokenRepository;
import com.finovago.p2p.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EmailVerificationService {

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final EmailSender emailSender;
    private final long expirationMinutes;

    public EmailVerificationService(
            UserRepository userRepository,
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            EmailSender emailSender,
            @Value("${app.email-verification.expiration-minutes}") long expirationMinutes) {
        this.userRepository = userRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.emailSender = emailSender;
        this.expirationMinutes = expirationMinutes;
    }

    /**
     * Issues a fresh verification token and emails it. Any token still outstanding for this user is
     * invalidated first, so at most one valid token exists per user at a time.
     */
    @Transactional
    public void sendVerification(User user) {
        invalidateOutstandingTokens(user);

        String rawToken = UUID.randomUUID().toString();
        Instant expiryDate = Instant.now().plusSeconds(expirationMinutes * 60);
        emailVerificationTokenRepository.save(new EmailVerificationToken(hash(rawToken), user, expiryDate));

        emailSender.send(
                user.getEmail(),
                "Verify your email address",
                "Welcome! Please verify your email address with this token.\n\n"
                        + "Verification token: " + rawToken + "\n\n"
                        + "This token expires in " + expirationMinutes + " minutes.");

        log.info("Email verification token issued for user: {}", user.getEmail());
    }

    /**
     * Resends a verification email. Completes silently for unknown or already-verified emails, so the
     * unauthenticated caller cannot use it to learn which accounts exist.
     */
    @Transactional
    public void resendVerification(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty() || userOpt.get().isEmailVerified()) {
            log.debug("Verification resend requested for unknown or already verified email - no-op");
            return;
        }

        sendVerification(userOpt.get());
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidVerificationTokenException("Invalid or expired verification token"));

        if (verificationToken.isUsed() || verificationToken.isExpired()) {
            log.warn("Email verification failed - token already used or expired for user: {}",
                    verificationToken.getUser().getEmail());
            throw new InvalidVerificationTokenException("Invalid or expired verification token");
        }

        User user = verificationToken.getUser();
        user.markEmailVerified();
        userRepository.save(user);

        verificationToken.markUsed();
        emailVerificationTokenRepository.save(verificationToken);

        log.info("Email verified for user: {}", user.getEmail());
    }

    @Transactional
    public int deleteExpired(Instant cutoff) {
        List<EmailVerificationToken> expired = emailVerificationTokenRepository.findByExpiryDateBefore(cutoff);
        emailVerificationTokenRepository.deleteAll(expired);
        return expired.size();
    }

    private void invalidateOutstandingTokens(User user) {
        List<EmailVerificationToken> outstanding = emailVerificationTokenRepository.findByUserAndUsedFalse(user);
        outstanding.forEach(EmailVerificationToken::markUsed);
        emailVerificationTokenRepository.saveAll(outstanding);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
