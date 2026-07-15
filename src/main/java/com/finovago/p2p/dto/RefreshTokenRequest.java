package com.finovago.p2p.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(
    name = "RefreshTokenRequest",
    description = "Request object carrying a refresh token, used to refresh the access token or revoke the session. "
                + "The refresh token is a long-lived opaque token issued at login.",
    example = "{\"refreshToken\": \"8f14e45f-ceea-4f6c-8f0e-0123456789ab\"}"
)
public record RefreshTokenRequest(
    @Schema(
        description = "The long-lived refresh token issued at login. Used to obtain a new access token when the current one expires, or to revoke the session at logout.",
        example = "8f14e45f-ceea-4f6c-8f0e-0123456789ab",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Refresh token cannot be blank")
    String refreshToken
) {}
