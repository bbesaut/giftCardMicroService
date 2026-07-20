package com.finovago.p2p.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response object for gift card details.")
public record GiftCardResponse(
    @Schema(description = "Unique gift card code", example = "GC-12345")
    String giftCardCode,

    @Schema(description = "Current balance", example = "100.0")
    double balance,

    @Schema(description = "Indicates if the gift card is active", example = "true")
    boolean active,

    @Schema(description = "Expiration date", example = "2025-12-31")
    LocalDate expirationDate,

    @Schema(description = "Id of the merchant that owns this gift card", example = "1")
    Long merchantId
) {}