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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finovago.p2p.dto.PasswordResetConfirmRequest;
import com.finovago.p2p.exception.InvalidResetTokenException;
import com.finovago.p2p.model.PasswordResetToken;
import com.finovago.p2p.model.User;
import com.finovago.p2p.repository.PasswordResetTokenRepository;
import com.finovago.p2p.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final EmailSender emailSender;
    private final long expirationMinutes;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            EmailSender emailSender,
            @Value("${app.password-reset.expiration-minutes}") long expirationMinutes) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.emailSender = emailSender;
        this.expirationMinutes = expirationMinutes;
    }

    /**
     * Always completes silently, whether or not the email belongs to an account - the caller
     * (unauthenticated) must never be able to distinguish the two, or this becomes an account
     * enumeration oracle.
     */
    @Transactional
    public void requestReset(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.debug("Password reset requested for unknown email - no-op");
            return;
        }

        User user = userOpt.get();
        invalidateOutstandingTokens(user);

        String rawToken = UUID.randomUUID().toString();
        Instant expiryDate = Instant.now().plusSeconds(expirationMinutes * 60);
        passwordResetTokenRepository.save(new PasswordResetToken(hash(rawToken), user, expiryDate));

        emailSender.send(
                user.getEmail(),
                "Password reset request",
                "A password reset was requested for your account.\n\n"
                        + "Reset token: " + rawToken + "\n\n"
                        + "This token expires in " + expirationMinutes + " minutes. "
                        + "If you didn't request this, you can ignore this email.");

        log.info("Password reset token issued for user: {}", user.getEmail());
    }

    @Transactional
    public void confirmReset(PasswordResetConfirmRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(hash(request.token()))
                .orElseThrow(() -> new InvalidResetTokenException("Invalid or expired reset token"));

        if (resetToken.isUsed() || resetToken.isExpired()) {
            log.warn("Password reset failed - token already used or expired for user: {}", resetToken.getUser().getEmail());
            throw new InvalidResetTokenException("Invalid or expired reset token");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        resetToken.markUsed();
        passwordResetTokenRepository.save(resetToken);

        refreshTokenService.revokeAllForUser(user);

        log.info("Password reset completed for user: {}", user.getEmail());
    }

    @Transactional
    public int deleteExpired(Instant cutoff) {
        List<PasswordResetToken> expired = passwordResetTokenRepository.findByExpiryDateBefore(cutoff);
        passwordResetTokenRepository.deleteAll(expired);
        return expired.size();
    }

    private void invalidateOutstandingTokens(User user) {
        List<PasswordResetToken> outstanding = passwordResetTokenRepository.findByUserAndUsedFalse(user);
        outstanding.forEach(PasswordResetToken::markUsed);
        passwordResetTokenRepository.saveAll(outstanding);
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
