package com.finovago.p2p.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Redemption activity for a single day, one point of the dashboard chart's time series.")
public record DailyRedemptionStat(
    @Schema(description = "Calendar day this entry covers", example = "2026-09-27")
    LocalDate date,

    @Schema(description = "Number of REDEMPTION ledger entries recorded on this day", example = "12")
    long count,

    @Schema(description = "Total amount redeemed on this day", example = "450.00")
    BigDecimal totalAmount
) {}
