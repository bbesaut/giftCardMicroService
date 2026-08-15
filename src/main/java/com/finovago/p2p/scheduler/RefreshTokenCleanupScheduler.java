package com.finovago.p2p.scheduler;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.finovago.p2p.service.RefreshTokenService;

/** Periodically deletes expired refresh tokens so the table doesn't grow unbounded with one row per login/refresh. */
@Component
public class RefreshTokenCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupScheduler.class);

    private final RefreshTokenService refreshTokenService;

    public RefreshTokenCleanupScheduler(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    @Scheduled(fixedDelayString = "${app.refresh-token.cleanup-sweep-interval-ms:3600000}")
    public void sweepExpiredTokens() {
        try {
            int deleted = refreshTokenService.deleteExpired(Instant.now());
            log.info("Refresh token cleanup: {} deleted", deleted);
        } catch (Exception e) {
            log.warn("Failed to sweep expired refresh tokens: {}", e.getMessage());
        }
    }
}
