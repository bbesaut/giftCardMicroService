package com.finovago.p2p.dto;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Dashboard-level aggregate stats for the caller's own merchant: current balance/card counts, "
        + "plus a daily redemption time series for a chart.")
public record DashboardStatsResponse(
    @Schema(description = "Sum of the balance of every active gift card", example = "15420.50")
    BigDecimal totalActiveBalance,

    @Schema(description = "Number of active gift cards", example = "142")
    long activeCards,

    @Schema(description = "Number of inactive (deactivated) gift cards", example = "8")
    long inactiveCards,

    @Schema(description = "Sum of amounts redeemed over the requested period", example = "3200.00")
    BigDecimal totalRedeemedInPeriod,

    @Schema(description = "Daily redemption counts/amounts over the requested period, oldest first. "
            + "Every day in the period is included, even with zero activity (count: 0, totalAmount: 0.00) - "
            + "no gaps for the chart to fill.")
    List<DailyRedemptionStat> redemptionsPerDay
) {}
