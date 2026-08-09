package com.finovago.p2p.repository;

import java.math.BigDecimal;

/** Projection for a gift card whose stored balance disagrees with its ledger's last balanceAfter. */
public interface LedgerDiscrepancy {
    Long getGiftCardId();
    String getCardCode();
    Long getMerchantId();
    BigDecimal getActualBalance();
    BigDecimal getTheoreticalBalance();
}
