package com.finovago.p2p.controller;

import com.finovago.p2p.dto.AuthResponse;
import com.finovago.p2p.dto.LoginRequest;
import com.finovago.p2p.dto.RefreshTokenRequest;
import com.finovago.p2p.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Authentication endpoints.")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(
        summary = "User login",
        description = "Authenticates a user with email and password, returning access and refresh tokens."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login successful",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request body (missing or invalid fields)"),
        @ApiResponse(responseCode = "401", description = "Invalid email or password"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt for email: {}", sanitizeEmail(request.email()));

        try {
            AuthResponse response = authService.login(request);
            log.info("Login successful for email: {}", sanitizeEmail(request.email()));
            return ResponseEntity.ok(response);
        } catch (BadCredentialsException e) {
            log.warn("Login failed - invalid credentials for email: {}", sanitizeEmail(request.email()));
            throw e;
        }
    }

    @Operation(
        summary = "Refresh access token",
        description = "Uses a valid refresh token to obtain a new access token and a rotated refresh token. "
                    + "The old refresh token is automatically revoked after successful rotation."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token refreshed successfully",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request body (missing refresh token)"),
        @ApiResponse(responseCode = "401", description = "Refresh token expired, revoked, or invalid"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        log.debug("Refresh token attempt");

        try {
            AuthResponse response = authService.refresh(request);
            log.info("Token refreshed successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Refresh failed: {}", e.getMessage());
            throw e;
        }
    }

    @Operation(
        summary = "User logout",
        description = "Revokes the refresh token, invalidating any future token refresh attempts for this token. "
                    + "Returns 204 No Content on success."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Logout successful - refresh token revoked"),
        @ApiResponse(responseCode = "400", description = "Invalid request body (missing refresh token)"),
        @ApiResponse(responseCode = "401", description = "Refresh token not found or already revoked"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        try {
            authService.logout(request);
            log.info("User logged out successfully");
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.warn("Logout failed: {}", e.getMessage());
            throw e;
        }
    }

    private String sanitizeEmail(String email) {
        if (email == null || email.length() < 3) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return email.substring(0, 1) + "***";
        }
        return email.substring(0, 1) + "***" + email.substring(atIndex);
    }
}
