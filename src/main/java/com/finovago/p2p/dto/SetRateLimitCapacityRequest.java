package com.finovago.p2p.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

@Schema(description = "Request object for overriding a merchant's rate limit capacity.")
public record SetRateLimitCapacityRequest(
    @Schema(description = "Requests/minute override for the redeem/lookup/reserve/refund/credit rate limit. Null clears the override and falls back to the app-wide default.", example = "500", nullable = true)
    @Min(value = 1, message = "The rate limit capacity must be greater than zero")
    Integer rateLimitCapacity
) {}
