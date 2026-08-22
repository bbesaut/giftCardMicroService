package com.finovago.p2p.scheduler;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.finovago.p2p.service.PasswordResetService;

/** Periodically deletes expired password reset tokens so the table doesn't grow unbounded. */
@Component
public class PasswordResetTokenCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetTokenCleanupScheduler.class);

    private final PasswordResetService passwordResetService;

    public PasswordResetTokenCleanupScheduler(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @Scheduled(fixedDelayString = "${app.password-reset.cleanup-sweep-interval-ms:3600000}")
    public void sweepExpiredTokens() {
        try {
            int deleted = passwordResetService.deleteExpired(Instant.now());
            log.info("Password reset token cleanup: {} deleted", deleted);
        } catch (Exception e) {
            log.warn("Failed to sweep expired password reset tokens: {}", e.getMessage());
        }
    }
}
