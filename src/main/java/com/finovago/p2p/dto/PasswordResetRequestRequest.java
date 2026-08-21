package com.finovago.p2p.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(
    name = "PasswordResetRequestRequest",
    description = "Request object to start a password reset for a forgotten password. Always answers "
                + "202 Accepted regardless of whether the email is registered, to avoid leaking which "
                + "emails have an account.",
    example = "{\"email\": \"user@example.com\"}"
)
public record PasswordResetRequestRequest(
    @Schema(
        description = "Email address to send the reset token to, if it belongs to an active account",
        example = "user@example.com",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Email should be valid")
    String email
) {}
