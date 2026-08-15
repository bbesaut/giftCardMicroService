package com.finovago.p2p.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Request object for refunding a gift card against a specific prior redemption. Capped at what's left to refund on that redemption.")
public record RefundRequest(
    @Schema(description = "Gift card code", example = "GC-12345")
    @NotBlank(message = "The gift card code cannot be blank")
    String giftCardCode,

    @Schema(description = "Amount to refund", example = "30.0")
    @NotNull(message = "The amount is required")
    @Positive(message = "The amount to refund must be greater than zero")
    @Digits(integer = 17, fraction = 2, message = "The amount cannot have more than 2 decimal places")
    BigDecimal amount,

    @Schema(description = "Id of the REDEMPTION ledger entry being refunded (from GET /{code}/ledger)", example = "987")
    @NotNull(message = "redemptionLedgerEntryId is required")
    Long redemptionLedgerEntryId,

    @Schema(description = "Optional justification - the link to the original redemption is usually enough on its own", example = "Customer return - order #4521", nullable = true)
    @Size(max = 500, message = "The reason cannot exceed 500 characters")
    String reason
) {}
