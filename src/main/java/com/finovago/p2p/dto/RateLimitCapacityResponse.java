package com.finovago.p2p.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "RateLimitCapacityResponse", description = "Response confirming a merchant's rate limit capacity after a change.")
public record RateLimitCapacityResponse(
    @Schema(description = "Id of the affected merchant.", requiredMode = Schema.RequiredMode.REQUIRED)
    Long merchantId,

    @Schema(description = "Requests/minute override now in effect. Null means the app-wide default applies.", nullable = true)
    Integer rateLimitCapacity
) {}
