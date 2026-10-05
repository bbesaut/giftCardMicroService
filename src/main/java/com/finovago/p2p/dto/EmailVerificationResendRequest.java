package com.finovago.p2p.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(
    name = "EmailVerificationResendRequest",
    description = "Request object to resend the verification email. Always answers 202 Accepted regardless of "
                + "whether the email is registered or already verified, to avoid leaking which accounts exist.",
    example = "{\"email\": \"user@example.com\"}"
)
public record EmailVerificationResendRequest(
    @Schema(
        description = "Email address of the account to resend the verification email to",
        example = "user@example.com",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Email should be valid")
    String email
) {}
