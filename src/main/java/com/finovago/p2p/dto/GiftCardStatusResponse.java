package com.finovago.p2p.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "GiftCardStatusResponse", description = "Response confirming a gift card's active status after an activate/deactivate call.")
public record GiftCardStatusResponse(
    @Schema(description = "Code of the affected gift card.", requiredMode = Schema.RequiredMode.REQUIRED)
    String giftCardCode,

    @Schema(description = "Whether the gift card is now active.", requiredMode = Schema.RequiredMode.REQUIRED)
    boolean active
) {}
