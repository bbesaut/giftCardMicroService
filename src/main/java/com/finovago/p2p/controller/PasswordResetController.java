package com.finovago.p2p.controller;

import com.finovago.p2p.dto.PasswordResetConfirmRequest;
import com.finovago.p2p.dto.PasswordResetRequestRequest;
import com.finovago.p2p.service.PasswordResetService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth/password-reset")
@Tag(name = "Authentication", description = "Authentication endpoints.")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @Operation(
        summary = "Request a password reset",
        description = "Starts a password reset for a forgotten password: if the email belongs to an active "
                    + "account, emails it a single-use reset token (expires after "
                    + "app.password-reset.expiration-minutes, default 30 min); any previously issued, still-"
                    + "valid token for that account is invalidated. Always answers 202 Accepted regardless of "
                    + "whether the email is registered, to avoid leaking which emails have an account. "
                    + "Unauthenticated, rate-limited per client IP."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Request accepted - an email was sent if the account exists"),
        @ApiResponse(responseCode = "400", description = "Invalid request body (missing or invalid email)",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Bad Request\",\"message\":\"Email should be valid\"}"))),
        @ApiResponse(responseCode = "429", description = "Too Many Requests - rate limit exceeded",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Too Many Requests\",\"message\":\"Too many requests. Please try again later.\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/request")
    public ResponseEntity<Void> requestReset(@Valid @RequestBody PasswordResetRequestRequest request) {
        passwordResetService.requestReset(request.email());
        log.info("Password reset requested");
        return ResponseEntity.accepted().build();
    }

    @Operation(
        summary = "Confirm a password reset",
        description = "Completes a password reset using the single-use token received by email. Revokes all "
                    + "of the account's active refresh tokens on success, logging out every other session. "
                    + "Unauthenticated, rate-limited per client IP."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Password reset - all sessions logged out"),
        @ApiResponse(responseCode = "400", description = "Invalid request body, or the token is invalid, already used, or expired",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Bad Request\",\"message\":\"Invalid or expired reset token\"}"))),
        @ApiResponse(responseCode = "429", description = "Too Many Requests - rate limit exceeded",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Too Many Requests\",\"message\":\"Too many requests. Please try again later.\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/confirm")
    public ResponseEntity<Void> confirmReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request);
        log.info("Password reset confirmed");
        return ResponseEntity.noContent().build();
    }
}
