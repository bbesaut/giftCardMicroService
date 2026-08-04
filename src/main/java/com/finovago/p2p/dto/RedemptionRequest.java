package com.finovago.p2p.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Request object for redeeming a gift card.")
public record RedemptionRequest(
    @Schema(description = "Amount to redeem", example = "50.0")
    @NotNull(message = "The amount is required")
    @Positive(message = "The amount to redeem must be greater than zero")
    @Digits(integer = 17, fraction = 2, message = "The amount cannot have more than 2 decimal places")
    BigDecimal amount,

    @Schema(description = "Gift card code", example = "GC-12345")
    @NotBlank(message = "The gift card code cannot be blank")
    String giftCardCode
) {}