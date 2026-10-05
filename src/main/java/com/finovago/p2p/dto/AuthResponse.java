package com.finovago.p2p.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "AuthResponse",
    description = "Response carrying the access token after successful authentication. "
                + "The refresh token is never returned in the body: it is set as an HttpOnly refresh_token cookie."
)
public record AuthResponse(
    @Schema(
        description = "Short-lived JWT access token (Bearer token) used to authenticate API requests. "
                    + "Includes user email and roles as claims. Expires in ~15 minutes.",
        example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyQGV4YW1wbGUuY29tIiwicm9sZXMiOlsiQ0xJRU5UIl0sImlhdCI6MTY4MzAwMDAwMCwiZXhwIjoxNjgzMDAwOTAwfQ.signature",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String accessToken
) {}
