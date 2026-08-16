package com.finovago.p2p.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Request object for a free-form manual credit to a gift card, not tied to any prior redemption. Requires a written reason. Only callable by a human merchant account, not a service account.")
public record CreditRequest(
    @Schema(description = "Gift card code", example = "GC-12345")
    @NotBlank(message = "The gift card code cannot be blank")
    String giftCardCode,

    @Schema(description = "Amount to credit", example = "20.0")
    @NotNull(message = "The amount is required")
    @Positive(message = "The amount to credit must be greater than zero")
    @Digits(integer = 17, fraction = 2, message = "The amount cannot have more than 2 decimal places")
    BigDecimal amount,

    @Schema(description = "Justification for this manual credit - mandatory for audit purposes", example = "Goodwill gesture - support ticket #123")
    @NotBlank(message = "The reason is required")
    @Size(max = 500, message = "The reason cannot exceed 500 characters")
    String reason
) {}
