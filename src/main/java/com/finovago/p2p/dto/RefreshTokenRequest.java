package com.finovago.p2p.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request object carrying a refresh token, used to refresh or revoke it.")
public record RefreshTokenRequest(
    @Schema(description = "The refresh token issued at login", example = "8f14e45f-ceea-4f6c-8f0e-0123456789ab")
    @NotBlank(message = "Refresh token cannot be blank")
    String refreshToken
) {}
