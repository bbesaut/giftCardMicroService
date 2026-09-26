package com.finovago.p2p.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "MerchantUserResponse", description = "A single human user account belonging to the caller's merchant.")
public record MerchantUserResponse(
    @Schema(description = "Id of the user.", requiredMode = Schema.RequiredMode.REQUIRED)
    Long userId,

    @Schema(description = "Email of the user.", requiredMode = Schema.RequiredMode.REQUIRED)
    String email,

    @Schema(description = "Whether this user owns the merchant (can manage its other users).", requiredMode = Schema.RequiredMode.REQUIRED)
    boolean owner,

    @Schema(description = "Whether the user is active.", requiredMode = Schema.RequiredMode.REQUIRED)
    boolean active
) {}
