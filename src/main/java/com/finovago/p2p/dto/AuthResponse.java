package com.finovago.p2p.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response containing access and refresh tokens.")
public record AuthResponse(
    @Schema(description = "Short-lived JWT access token", example = "eyJhbGciOiJIUzI1NiJ9...")
    String accessToken,

    @Schema(description = "Long-lived opaque refresh token", example = "8f14e45f-ceea-4f6c-8f0e-0123456789ab")
    String refreshToken
) {}
