package com.finovago.p2p.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "ApiKeyStatusResponse",
    description = "Response for revoking the merchant's API key."
)
public record ApiKeyStatusResponse(
    @Schema(description = "Non-secret prefix of the affected key.", example = "fovak_7f3d9c2b1a4e")
    String keyPrefix,

    @Schema(description = "The key's active status after this call.", requiredMode = Schema.RequiredMode.REQUIRED)
    boolean active
) {}
