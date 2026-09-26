package com.finovago.p2p.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.finovago.p2p.dto.MerchantResponse;
import com.finovago.p2p.dto.MerchantStatusResponse;
import com.finovago.p2p.dto.MerchantUserResponse;
import com.finovago.p2p.dto.RateLimitCapacityResponse;
import com.finovago.p2p.dto.SetRateLimitCapacityRequest;
import com.finovago.p2p.service.MerchantService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/merchants")
@Tag(name = "Admin - Merchants", description = "ADMIN-only merchant management: list every tenant, activate/deactivate, override rate limit.")
public class AdminMerchantController {

    private final MerchantService merchantService;

    public AdminMerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @Operation(
        summary = "List every merchant",
        description = "Lists every merchant across the platform, for the ADMIN merchant-management screen. "
                    + "Not paginated - see PagedResponse-backed endpoints for unbounded collections like gift cards. "
                    + "Requires authentication (ADMIN role)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "All merchants",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = MerchantResponse.class)))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (ADMIN role required)"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping
    public ResponseEntity<List<MerchantResponse>> listMerchants() {
        List<MerchantResponse> response = merchantService.listMerchants();
        log.info("Listed {} merchants", response.size());
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "List a merchant's users",
        description = "Lists the human user accounts belonging to a given merchant, for the ADMIN merchant-detail "
                    + "screen (e.g. alongside its activate/deactivate/rate-limit controls). Not paginated, same "
                    + "reasoning as GET /me/users. Requires authentication (ADMIN role)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Users of the given merchant",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = MerchantUserResponse.class)))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (ADMIN role required)"),
        @ApiResponse(responseCode = "404", description = "Merchant not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/{merchantId}/users")
    public ResponseEntity<List<MerchantUserResponse>> listMerchantUsers(@PathVariable Long merchantId) {
        List<MerchantUserResponse> response = merchantService.listMerchantUsers(merchantId);
        log.info("Listed {} users for merchant {} via admin", response.size(), merchantId);
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Deactivate a merchant",
        description = "Disables a merchant: blocks login and refresh for all of its users, blocks its API key, and "
                    + "revokes all of its users' active refresh tokens. An access token already issued before "
                    + "deactivation stays valid until its own ~15 min expiry (stateless JWT, same trade-off as "
                    + "user-level deactivation); an already-cached API key can take up to app.api-key-cache.ttl-minutes "
                    + "to be cut off. Requires authentication (ADMIN role)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Merchant deactivated",
            content = @Content(schema = @Schema(implementation = MerchantStatusResponse.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (ADMIN role required)"),
        @ApiResponse(responseCode = "404", description = "Merchant not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/{merchantId}/deactivate")
    public ResponseEntity<MerchantStatusResponse> deactivateMerchant(@PathVariable Long merchantId) {
        return setMerchantActive(merchantId, false);
    }

    @Operation(
        summary = "Reactivate a merchant",
        description = "Re-enables a previously deactivated merchant. Requires authentication (ADMIN role)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Merchant reactivated",
            content = @Content(schema = @Schema(implementation = MerchantStatusResponse.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (ADMIN role required)"),
        @ApiResponse(responseCode = "404", description = "Merchant not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/{merchantId}/activate")
    public ResponseEntity<MerchantStatusResponse> activateMerchant(@PathVariable Long merchantId) {
        return setMerchantActive(merchantId, true);
    }

    private ResponseEntity<MerchantStatusResponse> setMerchantActive(Long merchantId, boolean active) {
        MerchantStatusResponse response = merchantService.setMerchantActive(merchantId, active);
        log.info("Merchant {} {} by admin", merchantId, active ? "reactivated" : "deactivated");
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Set a merchant's rate limit capacity override",
        description = "Overrides the requests/minute capacity used for that merchant's redeem/lookup/reserve/refund/"
                    + "credit rate limit (see RateLimitFilter). A null rateLimitCapacity clears the override, falling "
                    + "back to the app-wide default (app.rate-limit.merchant-capacity). Requires authentication "
                    + "(ADMIN role)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Rate limit capacity updated",
            content = @Content(schema = @Schema(implementation = RateLimitCapacityResponse.class))),
        @ApiResponse(responseCode = "400", description = "rateLimitCapacity is not null and not greater than zero"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (ADMIN role required)"),
        @ApiResponse(responseCode = "404", description = "Merchant not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/{merchantId}/rate-limit-capacity")
    public ResponseEntity<RateLimitCapacityResponse> setRateLimitCapacity(
            @PathVariable Long merchantId, @Valid @RequestBody SetRateLimitCapacityRequest request) {
        RateLimitCapacityResponse response = merchantService.setRateLimitCapacity(merchantId, request.rateLimitCapacity());
        log.info("Rate limit capacity for merchant {} set to {} by admin", merchantId, request.rateLimitCapacity());
        return ResponseEntity.ok(response);
    }
}
