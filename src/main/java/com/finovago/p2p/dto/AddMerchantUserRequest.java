package com.finovago.p2p.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(
    name = "AddMerchantUserRequest",
    description = "Request object to attach an additional user account to an existing Merchant.",
    example = "{\"email\": \"employee@example.com\", \"password\": \"securePassword123\", \"serviceAccount\": false}"
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
    String password,

    @Schema(
        description = "Whether this account represents the merchant's automated integration (e.g. checkout backend) "
                    + "rather than a human employee. Defaults to false.",
        example = "false"
    )
    boolean serviceAccount
) {}
