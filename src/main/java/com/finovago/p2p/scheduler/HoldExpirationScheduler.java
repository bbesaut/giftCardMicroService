package com.finovago.p2p.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.finovago.p2p.model.GiftCardHold;
import com.finovago.p2p.model.HoldStatus;
import com.finovago.p2p.repository.GiftCardHoldRepository;
import com.finovago.p2p.service.GiftCardHoldService;

/**
 * Periodically releases holds that were never captured or released before their TTL. Each hold
 * is expired in its own transaction (GiftCardHoldService#expireIfStillPending) so a failure on
 * one row doesn't roll back or block the rest of the sweep.
 */
@Component
public class HoldExpirationScheduler {
    private static final Logger log = LoggerFactory.getLogger(HoldExpirationScheduler.class);

    private final GiftCardHoldRepository giftCardHoldRepository;
    private final GiftCardHoldService giftCardHoldService;

    public HoldExpirationScheduler(GiftCardHoldRepository giftCardHoldRepository, GiftCardHoldService giftCardHoldService) {
        this.giftCardHoldRepository = giftCardHoldRepository;
        this.giftCardHoldService = giftCardHoldService;
    }

    @Scheduled(fixedDelayString = "${app.holds.expiration-sweep-interval-ms:60000}")
    public void sweepExpiredHolds() {
        List<GiftCardHold> candidates = giftCardHoldRepository
                .findByStatusAndExpiresAtBefore(HoldStatus.PENDING, LocalDateTime.now());

        int expired = 0;
        for (GiftCardHold hold : candidates) {
            try {
                giftCardHoldService.expireIfStillPending(hold.getId());
                expired++;
            } catch (Exception e) {
                log.warn("Failed to expire hold {}: {}", hold.getId(), e.getMessage());
            }
        }

        if (expired > 0) {
            log.info("Hold expiration sweep: {} checked, {} expired", candidates.size(), expired);
        } else {
            log.debug("Hold expiration sweep: {} checked, {} expired", candidates.size(), expired);
        }
    }
}
