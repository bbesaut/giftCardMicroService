package com.finovago.p2p.controller;

import com.finovago.p2p.dto.AddMerchantUserRequest;
import com.finovago.p2p.dto.ApiKeyInfoResponse;
import com.finovago.p2p.dto.ApiKeyResponse;
import com.finovago.p2p.dto.ApiKeyStatusResponse;
import com.finovago.p2p.dto.AuthResponse;
import com.finovago.p2p.dto.ChangePasswordRequest;
import com.finovago.p2p.dto.CurrentUserResponse;
import com.finovago.p2p.dto.LoginRequest;
import com.finovago.p2p.dto.MerchantUserResponse;
import com.finovago.p2p.dto.RegisterRequest;
import com.finovago.p2p.dto.UserStatusResponse;
import com.finovago.p2p.exception.MissingRefreshTokenException;
import com.finovago.p2p.exception.OwnerPrivilegeRequiredException;
import com.finovago.p2p.exception.SamePasswordException;
import com.finovago.p2p.exception.SelfDeactivationException;
import com.finovago.p2p.exception.ServiceAccountNotAllowedException;
import com.finovago.p2p.exception.UserAlreadyExistsException;
import com.finovago.p2p.exception.UserNotFoundException;
import com.finovago.p2p.security.CurrentUserContext;
import com.finovago.p2p.service.AuthService;
import com.finovago.p2p.service.AuthTokens;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Authentication endpoints.")
public class AuthController {

    private static final String REFRESH_COOKIE_HEADER_DESCRIPTION =
            "Sets the refresh_token cookie (HttpOnly, Secure, SameSite=None, Path=/api/v1/auth)";

    private final AuthService authService;
    private final CurrentUserContext currentUserContext;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    public AuthController(
            AuthService authService,
            CurrentUserContext currentUserContext,
            RefreshTokenCookieFactory refreshTokenCookieFactory) {
        this.authService = authService;
        this.currentUserContext = currentUserContext;
        this.refreshTokenCookieFactory = refreshTokenCookieFactory;
    }

    @Operation(
        summary = "User login",
        description = "Authenticates a user with email and password. Returns the access token in the body; the refresh "
                    + "token is never in the body and is only set as an HttpOnly cookie named refresh_token, scoped to /api/v1/auth."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login successful",
            headers = @Header(name = "Set-Cookie", description = REFRESH_COOKIE_HEADER_DESCRIPTION,
                schema = @Schema(type = "string")),
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
            AuthTokens tokens = authService.login(request);
            log.info("Login successful for email: {}", sanitizeEmail(request.email()));
            return sessionResponse(HttpStatus.OK, tokens);
        } catch (BadCredentialsException e) {
            log.warn("Login failed - invalid credentials for email: {}", sanitizeEmail(request.email()));
            throw e;
        }
    }

    @Operation(
        summary = "Merchant registration",
        description = "Public self-service signup: creates a new Merchant along with its human owner account (the submitted "
                    + "email/password, which is the owner's login). No session is issued: a verification email is sent "
                    + "to the owner, and login is refused until the address is verified (see POST /api/v1/auth/email-verification/confirm). "
                    + "No automated/integration account is created here - the owner requests an API key explicitly later "
                    + "via POST /me/api-key. Unauthenticated, rate-limited per client IP."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Merchant and owner created, verification email sent (no session)"),
        @ApiResponse(responseCode = "400", description = "Invalid request body (missing or invalid fields)",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Bad Request\",\"message\":\"Email should be valid\"}"))),
        @ApiResponse(responseCode = "409", description = "Email already registered",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Conflict\",\"message\":\"Email already registered\"}"))),
        @ApiResponse(responseCode = "429", description = "Too Many Requests - Rate limit exceeded for this IP (max 10 attempts/minute)",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Too Many Requests\",\"message\":\"Too many requests. Please try again later.\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Internal Server Error\",\"message\":\"Database error occurred\"}")))
    })
    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration attempt for email: {}", sanitizeEmail(request.email()));

        try {
            authService.register(request);
            log.info("Registration successful for email: {}", sanitizeEmail(request.email()));
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (UserAlreadyExistsException e) {
            log.warn("Registration failed - email already exists: {}", sanitizeEmail(request.email()));
            throw e;
        }
    }

    @Operation(
        summary = "Get my profile",
        description = "Returns the profile of the authenticated user (id, email, role, whether they own their "
                    + "merchant, and their merchant's id and name - null for an ADMIN), so a front-end can "
                    + "bootstrap its session after login or refresh and decide which screens to show. Read from "
                    + "the database, not from the JWT claims: a user deactivated after their access token was "
                    + "issued is rejected with 401 here, which lets the front log them out immediately. Requires "
                    + "authentication (JWT token), any role - rejected if the caller authenticated via API key, "
                    + "since a profile belongs to a human account, not an automated integration."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Profile of the authenticated user",
            content = @Content(schema = @Schema(implementation = CurrentUserResponse.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token, or the account no longer exists or has been deactivated",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Unauthorized\",\"message\":\"Account no longer exists or has been deactivated\"}"))),
        @ApiResponse(responseCode = "403", description = "Caller authenticated via API key, not a human account",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Forbidden\",\"message\":\"Current user profile is only available to a human account, not an API key\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Internal Server Error\",\"message\":\"Database error occurred\"}")))
    })
    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> getCurrentUser() {
        return ResponseEntity.ok(authService.getCurrentUser(currentUserContext.currentUserIdOrNull()));
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
        summary = "Get my merchant's API key status",
        description = "Returns the current status of the caller's own merchant's API key (non-secret prefix, "
                    + "whether it's active, and when it was created/last rotated), for the key-management screen "
                    + "to render without any side effect - unlike POST /me/api-key (which generates or rotates) "
                    + "or its /revoke counterpart. The secret itself is never returned here; it is only ever shown "
                    + "once, in the response of POST /me/api-key. All fields are null/false if the merchant has no "
                    + "key yet. Requires authentication (JWT token), MERCHANT role, and the caller must be that "
                    + "merchant's owner."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Current API key status",
            content = @Content(schema = @Schema(implementation = ApiKeyInfoResponse.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (caller must be the merchant's owner)"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/me/api-key")
    public ResponseEntity<ApiKeyInfoResponse> getApiKeyStatus() {
        return ResponseEntity.ok(authService.getApiKeyStatus(currentUserContext.currentUserIdOrNull()));
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
        @ApiResponse(responseCode = "201", description = "User created (no tokens returned - the new user logs in with the password the owner shared)"),
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
    public ResponseEntity<Void> addUserToOwnMerchant(@Valid @RequestBody AddMerchantUserRequest request) {
        log.info("Self-service add-user attempt for email: {}", sanitizeEmail(request.email()));

        try {
            authService.addUserToOwnMerchant(currentUserContext.currentUserIdOrNull(), request);
            log.info("Self-service user added successfully: {}", sanitizeEmail(request.email()));
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (UserAlreadyExistsException | OwnerPrivilegeRequiredException e) {
            log.warn("Self-service add-user failed: {}", e.getMessage());
            throw e;
        }
    }

    @Operation(
        summary = "List my own merchant's users",
        description = "Lets the caller's own merchant owner list every human user account under their merchant "
                    + "(including themselves), to build a team-management page (list + create + activate/deactivate). "
                    + "Not paginated - a merchant's employee headcount is small by nature. Requires authentication "
                    + "(JWT token), MERCHANT role, and the caller must be that merchant's owner."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Users of the caller's merchant",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = MerchantUserResponse.class)))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (caller must be the merchant's owner)"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/me/users")
    public ResponseEntity<List<MerchantUserResponse>> listMyUsers() {
        List<MerchantUserResponse> response = authService.listMyUsers(currentUserContext.currentUserIdOrNull());
        log.info("Listed {} users via self-service", response.size());
        return ResponseEntity.ok(response);
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
        summary = "Change my own password",
        description = "Lets any authenticated human user change their own password, given the current one for "
                    + "confirmation. Revokes all of the caller's active refresh tokens on success, logging out "
                    + "every other session (the access token already in use for this call stays valid until its "
                    + "own ~15 min expiry, same trade-off as user deactivation). Requires authentication (JWT "
                    + "token) - rejected if the caller authenticated via API key, since a password belongs to a "
                    + "human account, not an automated integration."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Password changed - all other sessions logged out"),
        @ApiResponse(responseCode = "400", description = "Invalid request body (missing fields, or new password does not meet complexity rules)",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Bad Request\",\"message\":\"New password must be at least 8 characters long and include an uppercase letter, a lowercase letter, a digit, and a special character\"}"))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token, or current password is incorrect",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Unauthorized\",\"message\":\"Invalid email or password\"}"))),
        @ApiResponse(responseCode = "403", description = "Caller authenticated via API key, not a human account",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Forbidden\",\"message\":\"Password change must be performed by a human account, not an API key\"}"))),
        @ApiResponse(responseCode = "422", description = "New password is the same as the current password",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Unprocessable Entity\",\"message\":\"New password must be different from the current password\"}"))),
        @ApiResponse(responseCode = "429", description = "Too Many Requests - rate limit exceeded",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Too Many Requests\",\"message\":\"Too many requests. Please try again later.\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/me/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        try {
            authService.changePassword(currentUserContext.currentUserIdOrNull(), request);
            log.info("Password changed via self-service");
            return ResponseEntity.noContent().build();
        } catch (BadCredentialsException | SamePasswordException | ServiceAccountNotAllowedException e) {
            log.warn("Self-service password change failed: {}", e.getMessage());
            throw e;
        }
    }

    @Operation(
        summary = "Refresh access token",
        description = "Uses the refresh_token cookie to obtain a new access token (in the body) and a rotated refresh "
                    + "token (set back as the cookie). The old refresh token is automatically revoked after successful rotation. "
                    + "No request body is accepted."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token refreshed successfully",
            headers = @Header(name = "Set-Cookie", description = REFRESH_COOKIE_HEADER_DESCRIPTION,
                schema = @Schema(type = "string")),
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "400", description = "refresh_token cookie missing or blank",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Bad Request\",\"message\":\"Refresh token cookie is missing\"}"))),
        @ApiResponse(responseCode = "401", description = "Refresh token expired, revoked, or invalid",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Unauthorized\",\"message\":\"Refresh token has expired\"}"))),
        @ApiResponse(responseCode = "403", description = "Origin header present but not in the CORS allowlist (requests without Origin are not checked)",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Forbidden\",\"message\":\"Request origin is not allowed.\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Internal Server Error\",\"message\":\"Database error occurred\"}")))
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        log.debug("Refresh token attempt");

        try {
            AuthTokens tokens = authService.refresh(requireRefreshToken(refreshToken));
            log.info("Token refreshed successfully");
            return sessionResponse(HttpStatus.OK, tokens);
        } catch (Exception e) {
            log.warn("Refresh failed: {}", e.getMessage());
            throw e;
        }
    }

    @Operation(
        summary = "User logout",
        description = "Revokes the refresh token, invalidating any future token refresh attempts for this token. "
                    + "The refresh token is read from the refresh_token cookie. No request body is accepted. "
                    + "Returns 204 No Content on success, and clears the refresh_token cookie."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Logout successful - refresh token revoked, cookie cleared",
            headers = @Header(name = "Set-Cookie", description = "Expires the refresh_token cookie (Max-Age=0)",
                schema = @Schema(type = "string"))),
        @ApiResponse(responseCode = "400", description = "refresh_token cookie missing or blank",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Bad Request\",\"message\":\"Refresh token cookie is missing\"}"))),
        @ApiResponse(responseCode = "401", description = "Refresh token not found or already revoked",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Unauthorized\",\"message\":\"Refresh token not found\"}"))),
        @ApiResponse(responseCode = "403", description = "Origin header present but not in the CORS allowlist (requests without Origin are not checked)",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Forbidden\",\"message\":\"Request origin is not allowed.\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Internal Server Error\",\"message\":\"Database error occurred\"}")))
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        try {
            authService.logout(requireRefreshToken(refreshToken));
            log.info("User logged out successfully");
            return ResponseEntity.noContent()
                    .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.clear().toString())
                    .build();
        } catch (Exception e) {
            log.warn("Logout failed: {}", e.getMessage());
            throw e;
        }
    }

    /** Access token in the JSON body; refresh token only in the HttpOnly refresh_token cookie. */
    private ResponseEntity<AuthResponse> sessionResponse(HttpStatus status, AuthTokens tokens) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.create(tokens.refreshToken()).toString())
                .body(new AuthResponse(tokens.accessToken()));
    }

    private String requireRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new MissingRefreshTokenException("Refresh token cookie is missing");
        }
        return refreshToken;
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
