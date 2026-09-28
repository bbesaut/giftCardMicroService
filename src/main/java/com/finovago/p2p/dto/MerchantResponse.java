package com.finovago.p2p.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "MerchantResponse", description = "A single merchant, as seen by an ADMIN.")
public record MerchantResponse(
    @Schema(description = "Id of the merchant.", requiredMode = Schema.RequiredMode.REQUIRED)
    Long merchantId,

    @Schema(description = "Business name of the merchant.", requiredMode = Schema.RequiredMode.REQUIRED)
    String name,

    @Schema(description = "Contact email of the merchant.", nullable = true)
    String contactEmail,

    @Schema(description = "Whether the merchant is active. An inactive merchant cannot log in, refresh tokens or authenticate with its API key.", requiredMode = Schema.RequiredMode.REQUIRED)
    boolean active,

    @Schema(description = "Requests/minute override for the redeem/lookup/reserve/refund/credit rate limit. Null means the app-wide default applies.", nullable = true)
    Integer rateLimitCapacity
) {}
