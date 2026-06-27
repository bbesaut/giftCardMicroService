package com.finovago.p2p.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
    boolean active
) {}