package com.finovago.p2p.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(
    name = "RegisterRequest",
    description = "Request object for user registration. Creates a new user account with email and password.",
    example = "{\"email\": \"newuser@example.com\", \"password\": \"securePassword123\"}"
)
public record RegisterRequest(
    @Schema(
        description = "User's email address (must be unique)",
        example = "newuser@example.com",
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
