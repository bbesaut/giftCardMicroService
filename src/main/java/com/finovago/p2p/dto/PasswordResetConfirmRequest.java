package com.finovago.p2p.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(
    name = "PasswordResetConfirmRequest",
    description = "Request object to complete a password reset with the token received by email.",
    example = "{\"token\": \"3fa85f64-5717-4562-b3fc-2c963f66afa6\", \"newPassword\": \"N3wSecureP@ss\"}"
)
public record PasswordResetConfirmRequest(
    @Schema(
        description = "Raw reset token received by email",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Token cannot be blank")
    String token,

    @Schema(
        description = "New password: at least 8 characters, with at least one uppercase letter, one "
                    + "lowercase letter, one digit, and one special character",
        example = "N3wSecureP@ss",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "New password cannot be blank")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).{8,}$",
        message = "New password must be at least 8 characters long and include an uppercase letter, "
                + "a lowercase letter, a digit, and a special character"
    )
    String newPassword
) {}
