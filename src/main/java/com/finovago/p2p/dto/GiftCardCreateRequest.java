package com.finovago.p2p.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(description = "Request object for creating a new gift card.")
public record GiftCardCreateRequest(
    @Schema(description = "Unique gift card code", example = "GC-12345")
    @NotBlank(message = "The card code cannot be blank")
    String giftCardCode,

    @Schema(description = "Initial balance", example = "100.0")
    @PositiveOrZero(message = "The balance cannot be negative")
    double balance,

    @Schema(description = "Indicates if the gift card is active", example = "true")
    boolean active,

    @Schema(description = "Expiration date (optional, defaults to 2 years from now)", example = "2025-12-31")
    @Nullable
    @Future(message = "Expiration date must be in the future")
    LocalDate expirationDate
) {}