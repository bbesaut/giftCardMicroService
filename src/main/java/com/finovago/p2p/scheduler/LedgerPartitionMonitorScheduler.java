package com.finovago.p2p.scheduler;

import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.finovago.p2p.service.LedgerService;

/**
 * gift_card_ledger is RANGE-partitioned by year (see V21__partition_gift_card_ledger_by_date.sql),
 * with only a fixed window of future partitions pre-created. Extending that window requires a new
 * Flyway migration - p2p_app has no DDL rights (V17), so it can't be done automatically at runtime.
 * This scheduler only ever reads: it surfaces, via logs, when that follow-up migration is due, so
 * the gap between "someone needs to act" and "someone finds out" isn't silence.
 */
@Component
public class LedgerPartitionMonitorScheduler {
    private static final Logger log = LoggerFactory.getLogger(LedgerPartitionMonitorScheduler.class);

    // Warn once less than a year of runway remains - matches the yearly top-up cadence, giving
    // whoever's on call a full year of lead time to schedule the follow-up migration.
    private static final int WARNING_THRESHOLD_MONTHS = 12;

    private final LedgerService ledgerService;

    public LedgerPartitionMonitorScheduler(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @Scheduled(fixedDelayString = "${app.ledger.partition-monitor-interval-ms:604800000}")
    public void checkPartitionRunway() {
        try {
            Optional<LocalDate> latestPartitionYear = ledgerService.findLatestLedgerPartitionYear();
            latestPartitionYear.ifPresentOrElse(this::warnIfRunwayIsLow,
                    () -> log.warn("No dated partitions found for gift_card_ledger - partition maintenance may not have run"));

            if (ledgerService.existsAnyRowInDefaultLedgerPartition()) {
                log.error("gift_card_ledger_default has rows: the pre-created partition range was exhausted "
                        + "before a follow-up migration extended it. Add a new Flyway migration with the next batch "
                        + "of yearly partitions.");
            }
        } catch (Exception e) {
            log.warn("Failed to check gift_card_ledger partition runway: {}", e.getMessage());
        }
    }

    private void warnIfRunwayIsLow(LocalDate latestPartitionYear) {
        long monthsRemaining = Period.between(YearMonth.now().atDay(1), latestPartitionYear).toTotalMonths();
        if (monthsRemaining < WARNING_THRESHOLD_MONTHS) {
            log.warn("Only {} month(s) of gift_card_ledger partitions remain (furthest created: {}). "
                    + "Add a new Flyway migration extending the partition range before it runs out.",
                    monthsRemaining, latestPartitionYear);
        } else {
            log.info("Ledger partition runway check: {} month(s) remaining (furthest created: {})",
                    monthsRemaining, latestPartitionYear);
        }
    }
}
