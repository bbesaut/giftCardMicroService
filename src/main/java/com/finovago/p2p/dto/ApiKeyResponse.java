package com.finovago.p2p.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "ApiKeyResponse",
    description = "Response for generating or rotating the merchant's API key. The secret is shown here once "
                + "only and cannot be retrieved again - if lost, it must be rotated."
)
public record ApiKeyResponse(
    @Schema(description = "Non-secret prefix identifying this key, safe to log or display.",
        example = "fovak_7f3d9c2b1a4e", requiredMode = Schema.RequiredMode.REQUIRED)
    String keyPrefix,

    @Schema(description = "Plaintext API key secret. Shown only in this response - store it securely. "
                        + "Present it on subsequent requests as \"keyPrefix.secret\" in the X-Api-Key header.",
        requiredMode = Schema.RequiredMode.REQUIRED)
    String apiKeySecret
) {}
