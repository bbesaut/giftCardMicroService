package com.finovago.p2p.scheduler;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.finovago.p2p.service.IdempotencyKeyService;

/** Periodically deletes idempotency keys past their TTL so retries of long-forgotten requests fall through as fresh ones. */
@Component
public class IdempotencyKeyCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyCleanupScheduler.class);

    private final IdempotencyKeyService idempotencyKeyService;

    public IdempotencyKeyCleanupScheduler(IdempotencyKeyService idempotencyKeyService) {
        this.idempotencyKeyService = idempotencyKeyService;
    }

    @Scheduled(fixedDelayString = "${app.idempotency.cleanup-sweep-interval-ms:3600000}")
    public void sweepExpiredKeys() {
        try {
            idempotencyKeyService.deleteExpired(LocalDateTime.now());
        } catch (Exception e) {
            log.warn("Failed to sweep expired idempotency keys: {}", e.getMessage());
        }
    }
}
