package com.finovago.p2p.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(
    name = "ChangePasswordRequest",
    description = "Request object for a human user to change their own password. Requires the current "
                + "password for confirmation; revokes all of the caller's active refresh tokens on success, "
                + "logging out every other session.",
    example = "{\"currentPassword\": \"oldSecurePass123\", \"newPassword\": \"N3wSecureP@ss\"}"
)
public record ChangePasswordRequest(
    @Schema(
        description = "Caller's current password, for confirmation",
        example = "oldSecurePass123",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Current password cannot be blank")
    String currentPassword,

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
