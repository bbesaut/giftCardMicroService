package com.finovago.p2p.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "AuthResponse",
    description = "Response containing both access and refresh tokens after successful authentication. "
                + "The access token is a short-lived JWT used to authorize API requests. "
                + "The refresh token is a long-lived opaque token used to obtain new access tokens when they expire."
)
public record AuthResponse(
    @Schema(
        description = "Short-lived JWT access token (Bearer token) used to authenticate API requests. "
                    + "Includes user email and roles as claims. Expires in ~15 minutes.",
        example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyQGV4YW1wbGUuY29tIiwicm9sZXMiOlsiQ0xJRU5UIl0sImlhdCI6MTY4MzAwMDAwMCwiZXhwIjoxNjgzMDAwOTAwfQ.signature",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String accessToken,

    @Schema(
        description = "Long-lived opaque refresh token used to obtain new access tokens. "
                    + "Stored as a hash in the database with expiry tracking. Automatically rotated on each refresh. "
                    + "Expires in ~7 days. Must be kept secure (e.g., in HTTP-only cookies).",
        example = "8f14e45f-ceea-4f6c-8f0e-0123456789ab",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String refreshToken
) {}
