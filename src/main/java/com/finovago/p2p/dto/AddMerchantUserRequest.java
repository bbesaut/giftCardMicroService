package com.finovago.p2p.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(
    name = "AddMerchantUserRequest",
    description = "Request object for a merchant owner to add a human employee account to their own Merchant. "
                + "Always creates a human account, never a service account - each merchant has exactly one "
                + "service account, created once at registration.",
    example = "{\"email\": \"employee@example.com\", \"password\": \"securePassword123\"}"
)
public record AddMerchantUserRequest(
    @Schema(
        description = "User's email address (must be unique)",
        example = "employee@example.com",
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
