package com.finovago.p2p.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "MerchantStatusResponse", description = "Response confirming a merchant's active status after an activate/deactivate call.")
public record MerchantStatusResponse(
    @Schema(description = "Id of the affected merchant.", requiredMode = Schema.RequiredMode.REQUIRED)
    Long merchantId,

    @Schema(description = "Business name of the affected merchant.", requiredMode = Schema.RequiredMode.REQUIRED)
    String name,

    @Schema(description = "Whether the merchant is now active.", requiredMode = Schema.RequiredMode.REQUIRED)
    boolean active
) {}
