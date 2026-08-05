package com.finovago.p2p.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A single append-only ledger entry recording a balance-affecting operation on a gift card.")
public record LedgerEntryResponse(
    @Schema(description = "Kind of operation that produced this entry", example = "REDEMPTION")
    String entryType,

    @Schema(description = "Amount involved in this operation", example = "50.00")
    BigDecimal amount,

    @Schema(description = "Gift card balance immediately after this operation", example = "100.00")
    BigDecimal balanceAfter,

    @Schema(description = "Identifier of the related hold, if this entry stems from a hold capture/release", example = "42")
    Long referenceId,

    @Schema(description = "Timestamp at which this entry was recorded", example = "2026-07-23T15:30:00")
    LocalDateTime createdAt
) {}
