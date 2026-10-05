package com.finovago.p2p.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(
    name = "EmailVerificationConfirmRequest",
    description = "Request object to confirm an email address with the single-use token received by email.",
    example = "{\"token\": \"3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
)
public record EmailVerificationConfirmRequest(
    @Schema(
        description = "Raw single-use verification token received by email",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Token cannot be blank")
    String token
) {}
