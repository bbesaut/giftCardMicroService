package com.finovago.p2p.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Projection for one day's worth of REDEMPTION activity for a merchant, used by the dashboard stats endpoint. */
public interface DailyRedemptionCount {
    LocalDate getEntryDate();
    long getRedemptionCount();
    BigDecimal getTotalAmount();
}
