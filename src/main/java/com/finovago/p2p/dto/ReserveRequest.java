package com.finovago.p2p.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Schema(description = "Request object for reserving a hold against a gift card.")
public record ReserveRequest(
    @Schema(description = "Gift card code", example = "GC-12345")
    @NotBlank(message = "The gift card code cannot be blank")
    String giftCardCode,

    @Schema(description = "Amount to reserve", example = "50.0")
    @Positive(message = "The amount to reserve must be greater than zero")
    double amount
) {}
