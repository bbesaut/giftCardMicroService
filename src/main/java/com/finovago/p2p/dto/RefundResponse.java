package com.finovago.p2p.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response for a successful gift card refund.")
public record RefundResponse(
    @Schema(description = "Refund status", example = "SUCCESS")
    String status,

    @Schema(description = "Amount refunded to the card", example = "30.00")
    BigDecimal refundedAmount,

    @Schema(description = "Balance after this refund", example = "130.00")
    BigDecimal newBalance
) {}
