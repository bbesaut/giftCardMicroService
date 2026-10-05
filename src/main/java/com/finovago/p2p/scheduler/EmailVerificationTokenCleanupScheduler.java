package com.finovago.p2p.scheduler;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.finovago.p2p.service.EmailVerificationService;

/** Periodically deletes expired email verification tokens so the table doesn't grow unbounded. */
@Component
public class EmailVerificationTokenCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(EmailVerificationTokenCleanupScheduler.class);

    private final EmailVerificationService emailVerificationService;

    public EmailVerificationTokenCleanupScheduler(EmailVerificationService emailVerificationService) {
        this.emailVerificationService = emailVerificationService;
    }

    @Scheduled(fixedDelayString = "${app.email-verification.cleanup-sweep-interval-ms:3600000}")
    public void sweepExpiredTokens() {
        try {
            int deleted = emailVerificationService.deleteExpired(Instant.now());
            log.info("Email verification token cleanup: {} deleted", deleted);
        } catch (Exception e) {
            log.warn("Failed to sweep expired email verification tokens: {}", e.getMessage());
        }
    }
}
