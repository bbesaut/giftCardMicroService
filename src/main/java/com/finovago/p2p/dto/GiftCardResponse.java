package com.finovago.p2p.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response object for gift card details.")
public record GiftCardResponse(
    @Schema(description = "Unique gift card code", example = "GC-12345")
    String giftCardCode,

    @Schema(description = "Current balance", example = "100.0")
    double balance,

    @Schema(description = "Indicates if the gift card is active", example = "true")
    boolean active
) {}