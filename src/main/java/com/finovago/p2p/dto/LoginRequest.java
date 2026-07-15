package com.finovago.p2p.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(
    name = "LoginRequest",
    description = "Request object for user authentication. Submits email and password credentials to obtain access and refresh tokens.",
    example = "{\"email\": \"user@example.com\", \"password\": \"securePassword123\"}"
)
public record LoginRequest(
    @Schema(
        description = "User's registered email address (unique identifier)",
        example = "user@example.com",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Email should be valid")
    String email,

    @Schema(
        description = "User's password in plain text (should be transmitted over HTTPS only)",
        example = "securePassword123",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Password cannot be blank")
    String password
) {}
