package com.finovago.p2p.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response object for the result of a gift card redemption.")
public record RedemptionResponse(
        @Schema(description = "Status of the redemption", example = "SUCCESS")
        String status,

        @Schema(description = "Amount deducted from the gift card", example = "50.0")
        BigDecimal deductedAmount,

        @Schema(description = "Remaining balance on the gift card", example = "50.0")
        BigDecimal remainingBalance,

        @Schema(description = "Remaining amount to be paid", example = "0.0")
        BigDecimal remainingToPay
) {}
