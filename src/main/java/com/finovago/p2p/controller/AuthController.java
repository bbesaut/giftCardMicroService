package com.finovago.p2p.controller;

import com.finovago.p2p.dto.AddMerchantUserRequest;
import com.finovago.p2p.dto.ApiKeyResponse;
import com.finovago.p2p.dto.ApiKeyStatusResponse;
import com.finovago.p2p.dto.AuthResponse;
import com.finovago.p2p.dto.LoginRequest;
import com.finovago.p2p.dto.RefreshTokenRequest;
import com.finovago.p2p.dto.RegisterRequest;
import com.finovago.p2p.dto.UserStatusResponse;
import com.finovago.p2p.exception.OwnerPrivilegeRequiredException;
import com.finovago.p2p.exception.SelfDeactivationException;
import com.finovago.p2p.exception.UserAlreadyExistsException;
import com.finovago.p2p.exception.UserNotFoundException;
import com.finovago.p2p.security.CurrentUserContext;
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
    private final CurrentUserContext currentUserContext;

    public AuthController(AuthService authService, CurrentUserContext currentUserContext) {
        this.authService = authService;
        this.currentUserContext = currentUserContext;
    }

    @Operation(
        summary = "User login",
        description = "Authenticates a user with email and password, returning access and refresh tokens."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login successful",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request body (missing or invalid fields)",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Bad Request\",\"message\":\"Email cannot be blank\"}"))),
        @ApiResponse(responseCode = "401", description = "Invalid email or password",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Unauthorized\",\"message\":\"Invalid email or password\"}"))),
        @ApiResponse(responseCode = "429", description = "Too Many Requests - Rate limit exceeded for this IP (max 10 attempts/minute)",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Too Many Requests\",\"message\":\"Too many requests. Please try again later.\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Internal Server Error\",\"message\":\"Database error occurred\"}")))
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
        summary = "Merchant registration",
        description = "Creates a new Merchant along with its human owner account (the submitted email/password, "
                    + "which can log in and manage the merchant's other users). No automated/integration account "
                    + "is created here - the owner requests an API key explicitly later via POST /me/api-key, "
                    + "whenever they actually need one. Requires authentication (JWT token) and ADMIN role."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Registration successful",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request body (missing or invalid fields)",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Bad Request\",\"message\":\"Email should be valid\"}"))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Unauthorized\",\"message\":\"Full authentication is required to access this resource\"}"))),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (ADMIN role required)",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Forbidden\",\"message\":\"Access is denied\"}"))),
        @ApiResponse(responseCode = "409", description = "Email already registered",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Conflict\",\"message\":\"Email already registered\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Internal Server Error\",\"message\":\"Database error occurred\"}")))
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration attempt for email: {}", sanitizeEmail(request.email()));

        try {
            AuthResponse response = authService.register(request);
            log.info("Registration successful for email: {}", sanitizeEmail(request.email()));
            return ResponseEntity.ok(response);
        } catch (UserAlreadyExistsException e) {
            log.warn("Registration failed - email already exists: {}", sanitizeEmail(request.email()));
            throw e;
        }
    }

    @Operation(
        summary = "Generate or rotate my merchant's API key",
        description = "Lets the caller's own merchant owner create the merchant's API key for automated/backend "
                    + "integration use, or rotate it if one already exists - the previous secret stops working "
                    + "immediately. The key belongs directly to the merchant (no backing user account - ledger "
                    + "entries it produces are attributed to \"SYSTEM\"). The secret is shown only in this response "
                    + "and cannot be retrieved again; present it on subsequent requests as an "
                    + "\"X-Api-Key: {keyPrefix}.{secret}\" header instead of a Bearer JWT. Requires authentication "
                    + "(JWT token), MERCHANT role, and the caller must be that merchant's owner."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "API key generated or rotated",
            content = @Content(schema = @Schema(implementation = ApiKeyResponse.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (caller must be the merchant's owner)"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/me/api-key")
    public ResponseEntity<ApiKeyResponse> generateApiKey() {
        ApiKeyResponse response = authService.generateApiKey(currentUserContext.currentUserIdOrNull());
        log.info("API key generated/rotated via self-service");
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Revoke my merchant's API key",
        description = "Disables the caller's own merchant's API key immediately, e.g. as an emergency response to "
                    + "a leaked secret. Idempotent - calling it with no active key is a no-op. Requires "
                    + "authentication (JWT token), MERCHANT role, and the caller must be that merchant's owner."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "API key revoked (or already inactive)",
            content = @Content(schema = @Schema(implementation = ApiKeyStatusResponse.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (caller must be the merchant's owner)"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/me/api-key/revoke")
    public ResponseEntity<ApiKeyStatusResponse> revokeApiKey() {
        ApiKeyStatusResponse response = authService.revokeApiKey(currentUserContext.currentUserIdOrNull());
        log.info("API key revoked via self-service");
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Add an employee to my own merchant",
        description = "Lets the caller's own merchant owner attach a human employee account to their own merchant, "
                    + "self-service, no admin involved. Always creates a human account - the merchant's automated "
                    + "service account (if any) is managed separately via POST /me/api-key. Requires authentication "
                    + "(JWT token), MERCHANT role, and the caller must be that merchant's owner account (not an "
                    + "employee, not a service account)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User added successfully",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request body (missing or invalid fields)",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Bad Request\",\"message\":\"Email should be valid\"}"))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Unauthorized\",\"message\":\"Full authentication is required to access this resource\"}"))),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (caller must be the merchant's owner)",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Forbidden\",\"message\":\"Only the merchant's owner account can perform this action\"}"))),
        @ApiResponse(responseCode = "409", description = "Email already registered",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Conflict\",\"message\":\"Email already registered\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Internal Server Error\",\"message\":\"Database error occurred\"}")))
    })
    @PostMapping("/me/users")
    public ResponseEntity<AuthResponse> addUserToOwnMerchant(@Valid @RequestBody AddMerchantUserRequest request) {
        log.info("Self-service add-user attempt for email: {}", sanitizeEmail(request.email()));

        try {
            AuthResponse response = authService.addUserToOwnMerchant(currentUserContext.currentUserIdOrNull(), request);
            log.info("Self-service user added successfully: {}", sanitizeEmail(request.email()));
            return ResponseEntity.ok(response);
        } catch (UserAlreadyExistsException | OwnerPrivilegeRequiredException e) {
            log.warn("Self-service add-user failed: {}", e.getMessage());
            throw e;
        }
    }

    @Operation(
        summary = "Deactivate a user in my own merchant",
        description = "Disables a user account under the caller's own merchant and revokes its active refresh tokens, "
                    + "logging it out - including the merchant's own service account, e.g. as an emergency response "
                    + "to leaked credentials. The caller must be that merchant's owner and cannot deactivate their "
                    + "own account. Requires authentication (JWT token) and MERCHANT role."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User deactivated",
            content = @Content(schema = @Schema(implementation = UserStatusResponse.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (caller must be the merchant's owner)"),
        @ApiResponse(responseCode = "404", description = "User not found for the caller's merchant"),
        @ApiResponse(responseCode = "409", description = "Caller attempted to deactivate their own account"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/me/users/{userId}/deactivate")
    public ResponseEntity<UserStatusResponse> deactivateUser(@PathVariable Long userId) {
        return setUserActive(userId, false);
    }

    @Operation(
        summary = "Reactivate a user in my own merchant",
        description = "Re-enables a previously deactivated user account under the caller's own merchant. "
                    + "The caller must be that merchant's owner. Requires authentication (JWT token) and MERCHANT role."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User reactivated",
            content = @Content(schema = @Schema(implementation = UserStatusResponse.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (caller must be the merchant's owner)"),
        @ApiResponse(responseCode = "404", description = "User not found for the caller's merchant"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/me/users/{userId}/activate")
    public ResponseEntity<UserStatusResponse> activateUser(@PathVariable Long userId) {
        return setUserActive(userId, true);
    }

    private ResponseEntity<UserStatusResponse> setUserActive(Long userId, boolean active) {
        try {
            UserStatusResponse response = authService.setUserActive(currentUserContext.currentUserIdOrNull(), userId, active);
            log.info("User {} {} via self-service", userId, active ? "reactivated" : "deactivated");
            return ResponseEntity.ok(response);
        } catch (OwnerPrivilegeRequiredException | SelfDeactivationException | UserNotFoundException e) {
            log.warn("Self-service set-active failed for userId {}: {}", userId, e.getMessage());
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
        @ApiResponse(responseCode = "400", description = "Invalid request body (missing refresh token)",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Bad Request\",\"message\":\"Refresh token cannot be blank\"}"))),
        @ApiResponse(responseCode = "401", description = "Refresh token expired, revoked, or invalid",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Unauthorized\",\"message\":\"Refresh token has expired\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Internal Server Error\",\"message\":\"Database error occurred\"}")))
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
        @ApiResponse(responseCode = "400", description = "Invalid request body (missing refresh token)",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Bad Request\",\"message\":\"Refresh token cannot be blank\"}"))),
        @ApiResponse(responseCode = "401", description = "Refresh token not found or already revoked",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Unauthorized\",\"message\":\"Refresh token not found\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Internal Server Error\",\"message\":\"Database error occurred\"}")))
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
