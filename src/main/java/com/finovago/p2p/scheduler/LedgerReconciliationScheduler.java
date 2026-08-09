package com.finovago.p2p.scheduler;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.finovago.p2p.repository.LedgerDiscrepancy;
import com.finovago.p2p.service.LedgerService;

/**
 * Periodically recomputes each gift card's theoretical balance from its ledger and compares it
 * against gift_card.balance. This is what makes the ledger a control tool rather than a passive
 * history: a mismatch here means balance and ledger drifted apart and needs manual investigation.
 * Read-only - it never corrects the drift itself, since doing that automatically could mask which
 * side (balance or ledger) is actually wrong.
 */
@Component
public class LedgerReconciliationScheduler {
    private static final Logger log = LoggerFactory.getLogger(LedgerReconciliationScheduler.class);

    private final LedgerService ledgerService;

    public LedgerReconciliationScheduler(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @Scheduled(fixedDelayString = "${app.ledger.reconciliation-interval-ms:300000}")
    public void reconcileBalances() {
        try {
            List<LedgerDiscrepancy> discrepancies = ledgerService.findBalanceDiscrepancies();
            for (LedgerDiscrepancy discrepancy : discrepancies) {
                log.error("Ledger discrepancy detected: giftCardId={} cardCode={} merchantId={} actualBalance={} theoreticalBalance={}",
                        discrepancy.getGiftCardId(), discrepancy.getCardCode(), discrepancy.getMerchantId(),
                        discrepancy.getActualBalance(), discrepancy.getTheoreticalBalance());
            }
        } catch (Exception e) {
            log.warn("Failed to run ledger reconciliation sweep: {}", e.getMessage());
        }
    }
}
