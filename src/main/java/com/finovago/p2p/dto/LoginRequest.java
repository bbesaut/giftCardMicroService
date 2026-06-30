package com.finovago.p2p.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request object for user login.")
public record LoginRequest(
    @Schema(description = "User's email address", example = "user@example.com")
    @NotBlank(message = "Email cannot be blank")
    String email,

    @Schema(description = "User's password", example = "password123")
    @NotBlank(message = "Password cannot be blank")
    String password
) {}
