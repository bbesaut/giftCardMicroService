package com.finovago.p2p.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "ApiKeyInfoResponse",
    description = "Status of the caller's own merchant's API key, without the secret - the secret is only ever "
                + "shown once, in the response of POST /me/api-key. All fields are null/false when the merchant "
                + "has no key at all."
)
public record ApiKeyInfoResponse(
    @Schema(description = "Non-secret prefix identifying this key, safe to log or display. Null if no key exists.",
        example = "fovak_7f3d9c2b1a4e")
    String keyPrefix,

    @Schema(description = "Whether the key is currently active. False if no key exists.",
        requiredMode = Schema.RequiredMode.REQUIRED)
    boolean active,

    @Schema(description = "When the key was generated (or last rotated). Null if no key exists.")
    LocalDateTime createdAt
) {}
