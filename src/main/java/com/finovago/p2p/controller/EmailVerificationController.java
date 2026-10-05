package com.finovago.p2p.controller;

import com.finovago.p2p.dto.EmailVerificationConfirmRequest;
import com.finovago.p2p.dto.EmailVerificationResendRequest;
import com.finovago.p2p.service.EmailVerificationService;

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
@RequestMapping("/api/v1/auth/email-verification")
@Tag(name = "Authentication", description = "Authentication endpoints.")
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    public EmailVerificationController(EmailVerificationService emailVerificationService) {
        this.emailVerificationService = emailVerificationService;
    }

    @Operation(
        summary = "Confirm an email address",
        description = "Completes email verification using the single-use token received by email. Once confirmed, "
                    + "the account can log in. Unauthenticated, rate-limited per client IP (token-guessing surface)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Email address verified"),
        @ApiResponse(responseCode = "400", description = "Invalid request body, or the token is invalid, already used, or expired",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Bad Request\",\"message\":\"Invalid or expired verification token\"}"))),
        @ApiResponse(responseCode = "429", description = "Too Many Requests - rate limit exceeded",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Too Many Requests\",\"message\":\"Too many requests. Please try again later.\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/confirm")
    public ResponseEntity<Void> confirmVerification(@Valid @RequestBody EmailVerificationConfirmRequest request) {
        emailVerificationService.verifyEmail(request.token());
        log.info("Email verification confirmed");
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Resend the verification email",
        description = "Sends a fresh verification email to an unverified account, invalidating any previously issued "
                    + "token for it. Always answers 202 Accepted regardless of whether the email is registered or "
                    + "already verified, to avoid leaking which accounts exist. Unauthenticated, rate-limited per client IP."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Request accepted - an email was sent if the account is unverified"),
        @ApiResponse(responseCode = "400", description = "Invalid request body (missing or invalid email)",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Bad Request\",\"message\":\"Email should be valid\"}"))),
        @ApiResponse(responseCode = "429", description = "Too Many Requests - rate limit exceeded",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Too Many Requests\",\"message\":\"Too many requests. Please try again later.\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/resend")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody EmailVerificationResendRequest request) {
        emailVerificationService.resendVerification(request.email());
        log.info("Email verification resend requested");
        return ResponseEntity.accepted().build();
    }
}
