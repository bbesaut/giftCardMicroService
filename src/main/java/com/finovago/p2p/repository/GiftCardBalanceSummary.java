package com.finovago.p2p.repository;

import java.math.BigDecimal;

/** Projection for a merchant's dashboard-level gift card totals (active balance + card counts). */
public interface GiftCardBalanceSummary {
    BigDecimal getTotalActiveBalance();
    long getActiveCards();
    long getInactiveCards();
}
