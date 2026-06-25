package com.finovago.p2p.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(description = "Request object for redeeming a gift card.")
public record RedemptionRequest(
    @Schema(description = "Amount to redeem", example = "50.0")
    @PositiveOrZero(message = "The amount to redeem cannot be negative")
    double amount,

    @Schema(description = "Gift card code", example = "GC-12345")
    @NotBlank(message = "The gift card code cannot be blank")
    String giftCardCode
) {}