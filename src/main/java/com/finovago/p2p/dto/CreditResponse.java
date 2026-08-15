package com.finovago.p2p.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response for a successful manual gift card credit.")
public record CreditResponse(
    @Schema(description = "Credit status", example = "SUCCESS")
    String status,

    @Schema(description = "Amount credited to the card", example = "20.00")
    BigDecimal creditedAmount,

    @Schema(description = "Balance after this credit", example = "70.00")
    BigDecimal newBalance
) {}
