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
 * gift_card_ledger is RANGE-partitioned by year (see V21__partition_gift_card_ledger_by_date.sql).
 * From 2030 onward, partitions are created automatically by pg_partman on a pg_cron schedule (see
 * V24__automate_ledger_partition_maintenance.sql) - Neon-only, not exercised by Testcontainers/dev.
 * This scheduler only ever reads: it's a safety net that catches that automation silently failing
 * (e.g. the cron job stops running), rather than the primary mechanism for keeping runway healthy.
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
                log.error("gift_card_ledger_default has rows: the partition range was exhausted before a new one "
                        + "was created. Check whether the pg_cron job (partman-maintenance-ledger) is still running "
                        + "before falling back to a manual Flyway migration.");
            }
        } catch (Exception e) {
            log.warn("Failed to check gift_card_ledger partition runway: {}", e.getMessage());
        }
    }

    private void warnIfRunwayIsLow(LocalDate latestPartitionYear) {
        long monthsRemaining = Period.between(YearMonth.now().atDay(1), latestPartitionYear).toTotalMonths();
        if (monthsRemaining < WARNING_THRESHOLD_MONTHS) {
            log.warn("Only {} month(s) of gift_card_ledger partitions remain (furthest created: {}). "
                    + "Check whether the pg_cron job (partman-maintenance-ledger) is still running before "
                    + "falling back to a manual Flyway migration.",
                    monthsRemaining, latestPartitionYear);
        } else {
            log.info("Ledger partition runway check: {} month(s) remaining (furthest created: {})",
                    monthsRemaining, latestPartitionYear);
        }
    }
}
